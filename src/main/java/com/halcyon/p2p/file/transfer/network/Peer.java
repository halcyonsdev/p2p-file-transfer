package com.halcyon.p2p.file.transfer.network;

import com.halcyon.p2p.file.transfer.config.PeerConfig;
import com.halcyon.p2p.file.transfer.proto.File.*;
import com.halcyon.p2p.file.transfer.proto.General.ProtobufMessage;
import com.halcyon.p2p.file.transfer.proto.Ping.PingMessage;

import com.halcyon.p2p.file.transfer.proto.Pong.*;
import com.halcyon.p2p.file.transfer.service.ConnectionService;
import com.halcyon.p2p.file.transfer.service.FileService;
import com.halcyon.p2p.file.transfer.service.PingPongService;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static java.lang.Math.min;

public class Peer {
    private static final Logger LOGGER = LoggerFactory.getLogger(Peer.class);

    public static final Random RANDOM = new Random();

    private final PeerConfig peerConfig;
    private final ConnectionService connectionService;
    private final PingPongService pingPongService;
    private final FileService fileService;
    private Channel bindChannel;
    private boolean running = true;

    public Peer(PeerConfig peerConfig, ConnectionService connectionService, PingPongService pingPongService, FileService fileService) {
        this.peerConfig = peerConfig;
        this.connectionService = connectionService;
        this.pingPongService = pingPongService;
        this.fileService = fileService;
    }

    public void handleConnectionOpening(Connection connection) {
        if (isDisabled()) {
            LOGGER.warn("The new connection {} is ignore because the peer {} is disabled", connection, peerConfig.getPeerName());
            return;
        }

        if (connection.getPeerName().equals(peerConfig.getPeerName())) {
            LOGGER.error("Can't connect to itself. Closing new connection");
            connection.close();
            return;
        }

        connectionService.addConnection(connection);
    }

    private boolean isDisabled() {
        return !running;
    }

    public void handleConnectionClosing(Connection connection) {
        String connectionPeerName = connection.getPeerName();

        if (connectionPeerName.equals(peerConfig.getPeerName())) {
            return;
        }

        if (connectionService.removeConnection(connection)) {
            cancelPings(connection, connectionPeerName);
            cancelPongs(connectionPeerName);
        }

        connection.close();
    }

    public void cancelPings(Connection connection, String disconnectedPeerName) {
        if (isDisabled()) {
            LOGGER.warn("Pings of {} can't be cancelled because the peer is disabled", disconnectedPeerName);
        } else {
            pingPongService.cancelPings(connection, disconnectedPeerName);
        }
    }

    public void cancelPongs(String disconnectedPeerName) {
        if (isDisabled()) {
            LOGGER.warn("Pongs of {} can't be cancelled because the peer is disabled", disconnectedPeerName);
        } else {
            pingPongService.cancelPongs(disconnectedPeerName);
        }
    }

    public void connectTo(String host, int port, CompletableFuture<Void> futureToNotify) {
        if (isDisabled()) {
            futureToNotify.completeExceptionally(new RuntimeException("The peer is disabled"));
        } else {
            connectionService.connect(this, host, port, futureToNotify);
        }
    }

    public void leave(CompletableFuture<Void> futureToNotify) {
        String peerName = peerConfig.getPeerName();

        if (isDisabled()) {
            LOGGER.warn("{} has already been disabled", peerName);
            futureToNotify.complete(null);
            return;
        }

        bindChannel.closeFuture().addListener(future -> {
            if (future.isSuccess()) {
                futureToNotify.complete(null);
            } else {
                futureToNotify.completeExceptionally(future.cause());
            }
        });

        pingPongService.cancelOwnPing();
        pingPongService.cancelPongs(peerName);

        closeConnectionsAndSendCancelPongsMessage(peerName);

        bindChannel.close();
        running = false;
    }

    private void closeConnectionsAndSendCancelPongsMessage(String peerName) {
        var cancelPongs = CancelPongsMessage.newBuilder()
                .setPeerName(peerName)
                .build();

        var protobufMessage = ProtobufMessage.newBuilder()
                .setCancelPongs(cancelPongs)
                .build();

        for (Connection connection : connectionService.getServerNameToConnectionMap()) {
            connection.send(protobufMessage);
            connection.close();
        }
    }

    public void disconnect(String peerName) {
        if (isDisabled()) {
            LOGGER.warn("Can't disconnect because the peer {} is disabled", peerConfig.getPeerName());
            return;
        }

        Connection connection = connectionService.getConnection(peerName);
        if (connection != null) {
            LOGGER.info("Disconnecting this peer {} from {}", peerConfig.getPeerName(), peerName);
            connection.close();
        } else {
            LOGGER.warn("This peer {} is not connected to {}", peerConfig.getPeerName(), peerName);
        }
    }

    public void ping(CompletableFuture<Collection<String>> futureToNotify) {
        if (isDisabled()) {
            futureToNotify.completeExceptionally(new RuntimeException("The peer is disabled"));
        } else {
            pingPongService.ping(futureToNotify);
        }
    }

    public void handlePing(Connection connection, PingMessage ping) {
        if (isDisabled()) {
            LOGGER.warn("Ping of {} is ignored because the peer is disabled", connection.getPeerName());
        } else {
            pingPongService.handlePing((InetSocketAddress) bindChannel.localAddress(), connection, ping);
        }
    }

    public void handlePong(Connection connection, PongMessage pong) {
        if (isDisabled()) {
            LOGGER.warn("Pong of {} is ignored because the peer is disabled", connection.getPeerName());
        } else {
            pingPongService.handlePong(pong);
        }
    }

    public void timeoutPings() {
        if (isDisabled()) {
            LOGGER.warn("Timeout pings are ignored because the peer is disabled");
            return;
        }

        Collection<PongMessage> pongs = pingPongService.timeoutPings();
        int availableConnectionSlots = peerConfig.getMaxNumberOfActiveConnections() - connectionService.getNumberOfConnections();

        if (availableConnectionSlots > 0) {
            List<PongMessage> notConnectedPeers = new ArrayList<>();

            for (PongMessage pong : pongs) {
                if (!peerConfig.getPeerName().equals(pong.getPingPeerName()) && !connectionService.hasConnection(pong.getPeerName())) {
                    notConnectedPeers.add(pong);
                }
            }

            autoConnectToPeers(notConnectedPeers);
        }
    }

    private void autoConnectToPeers(List<PongMessage> notConnectedPeers) {
        int availableConnectionSlots = peerConfig.getMaxNumberOfActiveConnections() - connectionService.getNumberOfConnections();
        Collections.shuffle(notConnectedPeers);

        for (int i = 0; i < min(availableConnectionSlots, notConnectedPeers.size()); i++) {
            PongMessage peerToConnect = notConnectedPeers.get(i);
            String host = peerToConnect.getServerHost();
            int port = peerToConnect.getServerPort();

            LOGGER.info("Auto-connecting to {} via {}:{}", peerToConnect.getPeerName(), host, port);

            connectTo(host, port, null);
        }
    }

    public void keepAlivePing() {
        if (isDisabled()) {
            LOGGER.warn("Periodic ping is ignored because the peer is disabled");
            return;
        }

        int numberOfConnections = connectionService.getNumberOfConnections();

        if (numberOfConnections > 0) {
            boolean discoveryPingEnabled = numberOfConnections < peerConfig.getMaxNumberOfActiveConnections();
            pingPongService.keepAlive(discoveryPingEnabled);
        } else {
            LOGGER.info("The auto ping wasn't started because there are no connections");
        }
    }

    public void sendGetFilesRequest(String peerName) {
        if (isDisabled()) {
            LOGGER.warn("Sending GetFilesRequest is ignored because the peer is disabled");
        } else {
            Connection connection = connectionService.getConnection(peerName);
            fileService.sendGetFilesRequest(connection);
        }
    }

    public void handleGetFilesRequest(Connection connection) {
        if (isDisabled()) {
            LOGGER.warn("GetFilesRequest from {} is ignored because the peer is disabled", connection.getPeerName());
        } else {
            fileService.handleGetFilesRequest(connection);
        }
    }

    public void handleGetFilesResponse(Connection connection, GetFilesResponse response) {
        if (isDisabled()) {
            LOGGER.warn("GetFilesResponse from {} is ignored because the peer is disabled", connection.getPeerName());
        } else {
            fileService.handleGetFilesResponse(response);
        }
    }

    public void sendFileRequest(String peerName, String fileName) {
        if (isDisabled()) {
            LOGGER.warn("Sending FileRequest is ignored because the peer is disabled");
        } else {
            Connection connection = connectionService.getConnection(peerName);
            fileService.sendFileRequest(connection, fileName);
        }
    }

    public void handleFileRequest(Connection connection, FileRequest request) {
        if (isDisabled()) {
            LOGGER.warn("FileRequest from {} is ignored because the peer is disabled", request.getFileName());
        } else {
            fileService.handleFileRequest(connection, request);
        }
    }

    public void handleFileResponse(FileResponse response) {
        if (isDisabled()) {
            LOGGER.warn("FileResponse from {} is ignored because the peer is disabled", response.getFileName());
        } else {
            fileService.handleFileResponse(response);
        }
    }

    public void setBindChannel(Channel bindChannel) {
        this.bindChannel = bindChannel;
    }

    public String getPeerName() {
        return peerConfig.getPeerName();
    }
}

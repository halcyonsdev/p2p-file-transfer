package com.halcyon.p2p.file.transfer.network;

import com.halcyon.p2p.file.transfer.config.PeerConfig;
import com.halcyon.p2p.file.transfer.proto.Ping.PingMessage;

import com.halcyon.p2p.file.transfer.proto.Pong.PongMessage;
import com.halcyon.p2p.file.transfer.service.ConnectionService;
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
    private Channel bindChannel;
    private boolean running = true;

    public Peer(PeerConfig peerConfig, ConnectionService connectionService, PingPongService pingPongService) {
        this.peerConfig = peerConfig;
        this.connectionService = connectionService;
        this.pingPongService = pingPongService;
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

    public void handleConnectionClosing(Connection connection) {
        String connectionPeerName = connection.getPeerName();

        if (!connection.isOpen() || connectionPeerName.equals(peerConfig.getPeerName())) {
            return;
        }

        connection.close();
    }

    private boolean isDisabled() {
        return !running;
    }

    public void connectTo(String host, int port, CompletableFuture<Void> futureToNotify) {
        if (isDisabled()) {
            futureToNotify.completeExceptionally(new RuntimeException("The peer is disabled"));
        } else {
            connectionService.connect(this, host, port, futureToNotify);
        }
    }

    public void leave(CompletableFuture<Void> futureToNotify) {
        if (isDisabled()) {
            LOGGER.warn("{} has already been disabled", peerConfig.getPeerName());
            return;
        }

        bindChannel.closeFuture().addListener(future -> {
            if (future.isSuccess()) {
                futureToNotify.complete(null);
            } else {
                futureToNotify.completeExceptionally(future.cause());
            }
        });

        for (Connection connection : connectionService.getServerNameToConnectionMap()) {
            connection.close();
        }

        bindChannel.close();
        running = false;
    }

    public void disconnect(String peerName) {
        if (isDisabled()) {
            LOGGER.warn("Can't disconnect because the peer {} is disabled", peerConfig.getPeerName());
            return;
        }

        Connection connection = connectionService.removeConnection(peerName);
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

    public void setBindChannel(Channel bindChannel) {
        this.bindChannel = bindChannel;
    }

    public String getPeerName() {
        return peerConfig.getPeerName();
    }
}

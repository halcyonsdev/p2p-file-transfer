package com.halcyon.p2p.file.transfer.service;

import com.halcyon.p2p.file.transfer.config.PeerConfig;
import com.halcyon.p2p.file.transfer.network.Connection;
import com.halcyon.p2p.file.transfer.proto.General.ProtobufMessage;
import com.halcyon.p2p.file.transfer.proto.KeepAlive.KeepAliveMessage;
import com.halcyon.p2p.file.transfer.proto.Ping.*;
import com.halcyon.p2p.file.transfer.proto.Pong.*;
import com.halcyon.p2p.file.transfer.util.PingPongUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.halcyon.p2p.file.transfer.util.PingPongUtil.nextPing;

public class PingPongService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PingPongService.class);

    private final ConnectionService connectionService;
    private final PeerConfig peerConfig;
    private final Map<String, PingContext> peerNameToPingContextMap = new HashMap<>();

    private int autoPingCount;

    public PingPongService(ConnectionService connectionService, PeerConfig peerConfig) {
        this.connectionService = connectionService;
        this.peerConfig = peerConfig;
    }

    public void ping(CompletableFuture<Collection<String>> future) {
        PingContext pingContext = peerNameToPingContextMap.get(peerConfig.getPeerName());

        if (pingContext == null) {
            pingContext = discoveryPing();
        } else {
            LOGGER.info("Attaching to the already existing {} ping context", peerConfig.getPeerName());
        }

        if (future != null) {
            pingContext.addFuture(future);
        }
    }

    private PingContext discoveryPing() {
        String peerName = peerConfig.getPeerName();
        int ttl = peerConfig.getPingTtl();

        LOGGER.info("Doing a full ping with ttl={}", ttl);

        var ping = PingMessage.newBuilder()
                .setPeerName(peerName)
                .setTtl(ttl)
                .setHops(0)
                .setPingTimeoutDurationInMillis(peerConfig.getPingTimeoutMillis())
                .setPingStartTimestamp(System.currentTimeMillis())
                .build();

        var protobufMessage = ProtobufMessage.newBuilder()
                .setPing(ping)
                .build();

        PingContext pingContext = new PingContext(ping, null);
        peerNameToPingContextMap.put(peerName, pingContext);

        for (Connection connection : connectionService.getConnections()) {
            connection.send(protobufMessage);
        }

        return pingContext;
    }

    public void handlePing(InetSocketAddress bindAddress, Connection connection, PingMessage ping) {
        String pingPeerName = ping.getPeerName();

        if (peerNameToPingContextMap.containsKey(pingPeerName)) {
            LOGGER.info("Skipping ping of {} because it has already been handled", pingPeerName);
            return;
        }

        if (connection.getPeerName().equals(pingPeerName)) {
            LOGGER.info("Handling ping of initiator {} with ttl={}", pingPeerName, ping.getTtl());
        } else {
            LOGGER.info("Handling ping of initiator {} and forwarder {} with ttl={} and hops={}", pingPeerName,
                    connection.getPeerName(), ping.getTtl(), ping.getHops());
        }

        ping = ping.toBuilder().setPingStartTimestamp(System.currentTimeMillis()).build();
        peerNameToPingContextMap.put(pingPeerName, new PingContext(ping, connection));

        sendPong(connection, ping, bindAddress);

        forwardNextPingToNeighbours(connection, ping);
    }

    private void sendPing(Connection connection, PingMessage ping) {
        var protobufMessage = ProtobufMessage.newBuilder()
                .setPing(ping)
                .build();
        connection.send(protobufMessage);
    }

    private void sendPong(Connection connection, PingMessage ping, InetSocketAddress bindAddress) {
        var pong = PongMessage.newBuilder()
                .setPingPeerName(ping.getPeerName())
                .setSenderPeerName(peerConfig.getPeerName())
                .setPeerName(peerConfig.getPeerName())
                .setServerHost(bindAddress.getAddress().getHostName())
                .setServerPort(bindAddress.getPort())
                .setTtl(ping.getTtl())
                .setHops(ping.getHops() + 1)
                .build();

        var protobufMessage = ProtobufMessage.newBuilder()
                .setPong(pong)
                .build();
        connection.send(protobufMessage);
    }

    private void forwardNextPingToNeighbours(Connection connection, PingMessage ping) {
        Optional<PingMessage> nextPingOptional = nextPing(ping);
        if (nextPingOptional.isPresent()) {
            var next = nextPingOptional.get();

            for (Connection neighbour : connectionService.getConnections()) {
                if (!neighbour.equals(connection) && !neighbour.getPeerName().equals(ping.getPeerName())) {
                    LOGGER.info("Forwarding next ping from {} to {} for initiator {}", next.getPeerName(), neighbour.getPeerName(), ping.getPeerName());
                    sendPing(connection, next);
                }
            }
        }
    }

    public void handlePong(PongMessage pong) {
        if (pong.getPeerName().equals(peerConfig.getPeerName())) {
            LOGGER.warn("Received pong from itself");
            return;
        }

        String pingPeerName = pong.getPingPeerName();
        PingContext pingContext = peerNameToPingContextMap.get(pingPeerName);

        if (pingContext != null) {
            pingContext.handlePong(peerConfig.getPeerName(), pong);
        } else {
            LOGGER.warn("No ping context found for pong from {} for initiator {}", pong.getPeerName(), pingPeerName);
        }
    }

    public Collection<PongMessage> timeoutPings() {
        Collection<PongMessage> pongs = Collections.emptyList();
        Iterator<Map.Entry<String, PingContext>> pingIterator = peerNameToPingContextMap.entrySet().iterator();

        while (pingIterator.hasNext()) {
            Map.Entry<String, PingContext> pingEntry = pingIterator.next();
            String pingPeerName = pingEntry.getKey();
            PingContext pingContext = pingEntry.getValue();

            if (pingContext.isTimeout()) {
                pingIterator.remove();

                if (peerConfig.getPeerName().equals(pingPeerName)) {
                    pongs = notifyPingTimeout(pingContext, pingPeerName);
                } else {
                    LOGGER.info("Ping for {} has timed out", pingPeerName);
                }
            }
        }

        return pongs;
    }

    private Collection<PongMessage> notifyPingTimeout(PingContext pingContext, String pingPeerName) {
        Collection<PongMessage> pongs = pingContext.getPongs();

        Set<String> peers = new HashSet<>();
        for (PongMessage pong : pongs) {
            peers.add(pong.getPeerName());
        }
        peers.add(peerConfig.getPeerName());

        LOGGER.info("Ping for {} has timed out. Notifying futures with {} peers", pingPeerName, peers.size());

        for (CompletableFuture<Collection<String>> future : pingContext.getFutures()) {
            future.complete(peers);
        }

        return pongs;
    }

    public void cancelOwnPing() {
        PingContext pingContext = peerNameToPingContextMap.get(peerConfig.getPeerName());

        if (pingContext != null) {
            LOGGER.info("Cancelling own ping");

            for (CompletableFuture<Collection<String>> future : pingContext.getFutures()) {
                future.cancel(true);
            }
        }
    }

    public void cancelPings(Connection connection, String disconnectedPeerName) {
        Iterator<Map.Entry<String, PingContext>> pingIterator = peerNameToPingContextMap.entrySet().iterator();

        while (pingIterator.hasNext()) {
            Map.Entry<String, PingContext> pingEntry = pingIterator.next();
            String pingPeerName = pingEntry.getKey();
            PingContext pingContext = pingEntry.getValue();

            Connection pingOwnerConnection = pingContext.getConnection();
            boolean shouldBeDeleted = pingPeerName.equals(disconnectedPeerName) && connection.equals(pingOwnerConnection);

            if (!shouldBeDeleted) {
                shouldBeDeleted = pingOwnerConnection != null && pingOwnerConnection.getPeerName().equals(disconnectedPeerName);
            }

            if (shouldBeDeleted) {
                LOGGER.info("Removing a ping of {} because the peer is disabled", pingPeerName);
                pingIterator.remove();

                notifyPongSendersAboutCancellingPings(connection, pingContext, disconnectedPeerName);
            }
        }
    }

    private void notifyPongSendersAboutCancellingPings(Connection connection, PingContext pingContext, String disconnectedPeerName) {
        var cancelPings = CancelPingsMessage.newBuilder()
                .setPeerName(disconnectedPeerName)
                .build();

        for (PongMessage pong : pingContext.getPongs()) {
            String pongPeerName = pong.getPeerName();
            Connection pongConnection = connectionService.getConnection(pongPeerName);

            if (pongConnection != null) {
                var protobufMessage = ProtobufMessage.newBuilder()
                        .setCancelPings(cancelPings)
                        .build();

                connection.send(protobufMessage);

                LOGGER.info("CancelPingsMessage has been sent to {}", pongPeerName);
            } else {
                LOGGER.warn("CancelPingsMessage hasn't been sent to {} because there are no connections", pongPeerName);
            }
        }
    }

    public void cancelPongs(String disconnectedPeerName) {
        for (Map.Entry<String, PingContext> pingEntry : peerNameToPingContextMap.entrySet()) {
            PingContext pingContext = pingEntry.getValue();

            removePongsAndSendCancelPongsMessage(pingEntry, disconnectedPeerName);

            for (PongMessage pong : new ArrayList<>(pingContext.getPongs())) {
                String pongPeerName = pong.getPeerName();

                if (pongPeerName.equals(disconnectedPeerName)) {
                    removePongOfDisconnectedPeer(pingEntry, pongPeerName, disconnectedPeerName);
                    propagatePing(pingEntry, disconnectedPeerName);
                }
            }
        }
    }

    private void removePongsAndSendCancelPongsMessage(Map.Entry<String, PingContext> pingEntry, String disconnectedPeerName) {
        String pingPeerName = pingEntry.getKey();
        PingContext pingContext = pingEntry.getValue();

        var cancelPongs = CancelPongsMessage.newBuilder()
                .setPeerName(disconnectedPeerName)
                .build();

        if (pingContext.removePong(disconnectedPeerName)) {
            Connection pingOwnerConnection = pingContext.getConnection();

            if (pingOwnerConnection != null) {
                LOGGER.info("Pong of {} removed in ping of {}. Sending CancelPongsMessage to {}",
                        disconnectedPeerName, pingPeerName, pingOwnerConnection.getPeerName());

                sendCancelPongsMessage(pingOwnerConnection, cancelPongs);
            } else {
                LOGGER.info("Pong of {} removed in ping of {}", disconnectedPeerName, pingPeerName);
            }
        }
    }

    private void sendCancelPongsMessage(Connection connection, CancelPongsMessage cancelPongs) {
        var protobufMessage = ProtobufMessage.newBuilder()
                .setCancelPongs(cancelPongs)
                .build();

        connection.send(protobufMessage);
    }

    private void removePongOfDisconnectedPeer(Map.Entry<String, PingContext> pingEntry, String pongPeerName, String disconnectedPeerName) {
        String pingPeerName = pingEntry.getKey();
        PingContext pingContext = pingEntry.getValue();

        pingContext.removePong(pongPeerName);

        LOGGER.info("Pong of {} removed in ping of {} because it was sent by disconnected peer {}",
                pongPeerName, pingPeerName, disconnectedPeerName);

        Connection connection = pingContext.getConnection();

        if (connection != null) {
            var cancelPongs = CancelPongsMessage.newBuilder()
                    .setPeerName(pongPeerName)
                    .build();

            sendCancelPongsMessage(connection, cancelPongs);

            LOGGER.info("CancelPongsMessage has been sent to {} for ping of {}", connection.getPeerName(), pingPeerName);
        }
    }

    private void propagatePing(Map.Entry<String, PingContext> pingEntry, String disconnectedPeerName) {
        String pingPeerName = pingEntry.getKey();
        PingContext pingContext = pingEntry.getValue();

        Optional<PingMessage> nextPingOptional = nextPing(pingContext.getPing());

        if (nextPingOptional.isPresent()) {
            var next = nextPingOptional.get();
            String nextPeerName = next.getPeerName();

            LOGGER.info("The ping with the name \"{}\" will be re-sent", nextPeerName);

            for (Connection connection : connectionService.getConnections()) {
                if (!connection.getPeerName().equals(pingPeerName) && !connection.getPeerName().equals(disconnectedPeerName)
                        && connection.equals(pingContext.getConnection())) {

                    sendPing(connection, next);
                    LOGGER.info("The ping of {} has been re-sent because {} left", nextPeerName, disconnectedPeerName);
                }
            }
        }
    }

    public void keepAlive(boolean discoveryPingEnabled) {
        boolean hasPingSent = peerNameToPingContextMap.containsKey(peerConfig.getPeerName());

        if (!hasPingSent) {
            if (incrementAutoPingCountAndCheckIfFullPing() && discoveryPingEnabled) {
                discoveryPing();
            } else {
                sendKeepAliveMessage();
            }
        } else {
            LOGGER.info("The periodic ping is skipped because such a ping already exists");
        }
    }

    private boolean incrementAutoPingCountAndCheckIfFullPing() {
        return ++autoPingCount % peerConfig.getAutoDiscoveryPingFrequency() == 0;
    }

    private void sendKeepAliveMessage() {
        LOGGER.info("Sending a keep-alive message");

        var keepAlive = KeepAliveMessage.getDefaultInstance();
        var protobufMessage = ProtobufMessage.newBuilder()
                .setKeepAlive(keepAlive)
                .build();

        for (Connection connection : connectionService.getConnections()) {
            connection.send(protobufMessage);
        }
    }
}

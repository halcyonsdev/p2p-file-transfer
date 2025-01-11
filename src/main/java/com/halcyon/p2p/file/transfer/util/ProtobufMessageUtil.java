package com.halcyon.p2p.file.transfer.util;

import com.halcyon.p2p.file.transfer.network.Connection;
import com.halcyon.p2p.file.transfer.network.Peer;
import com.halcyon.p2p.file.transfer.proto.Handshake.HandshakeMessage;
import com.halcyon.p2p.file.transfer.proto.Ping.*;
import com.halcyon.p2p.file.transfer.proto.Pong.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProtobufMessageUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProtobufMessageUtil.class);

    private ProtobufMessageUtil() {}

    public static void handleHandshake(Peer peer, Connection connection, HandshakeMessage handshake) {
        String peerName = handshake.getSenderPeerName();

        if (!connection.isOpen()) {
            connection.open(peerName);
            peer.handleConnectionOpening(connection);
        } else if (!connection.getPeerName().equals(peerName)) {
            LOGGER.warn("Mismatching of peer names! Handshake: {} Connection: {}", peerName, connection.getPeerName());
        }
    }

    public static void handlePing(Peer peer, Connection connection, PingMessage ping) {
        peer.handlePing(connection, ping);
    }

    public static void handlePong(Peer peer, Connection connection, PongMessage pong) {
        peer.handlePong(connection, pong);
    }

    public static void handleKeepAlive(Connection connection) {
        LOGGER.info("Keep alive ping received from {}", connection);
    }

    public static void handleCancelPings(Peer peer, Connection connection, CancelPingsMessage cancelPings) {
        peer.cancelPings(connection, cancelPings.getPeerName());
    }

    public static void handleCancelPongs(Peer peer, CancelPongsMessage cancelPongs) {
        peer.cancelPongs(cancelPongs.getPeerName());
    }
}

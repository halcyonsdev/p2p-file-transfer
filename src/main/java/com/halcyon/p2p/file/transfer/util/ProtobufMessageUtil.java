package com.halcyon.p2p.file.transfer.util;

import com.halcyon.p2p.file.transfer.network.Connection;
import com.halcyon.p2p.file.transfer.network.Peer;
import com.halcyon.p2p.file.transfer.proto.Handshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProtobufMessageUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProtobufMessageUtil.class);

    private ProtobufMessageUtil() {}

    public static void handleHandshake(Peer peer, Connection connection, Handshake.HandshakeMessage handshake) {
        String peerName = handshake.getSenderPeerName();
        if (!connection.isOpen()) {
            connection.open(peerName);
            peer.handleConnectionOpening(connection);
        } else if (!connection.getPeerName().equals(peerName)) {
            LOGGER.warn("Mismatching peer name received from connection! Existing: {}, received: {}", handshake.getSenderPeerName(), peerName);
        }
    }
}

package com.halcyon.p2p.file.transfer.network;

import com.halcyon.p2p.file.transfer.config.PeerConfig;
import com.halcyon.p2p.file.transfer.service.ConnectionService;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public class Peer {
    private static final Logger LOGGER = LoggerFactory.getLogger(Peer.class);

    private final PeerConfig peerConfig;
    private final ConnectionService connectionService;
    private Channel bindChannel;
    private boolean running = true;

    public Peer(PeerConfig peerConfig, ConnectionService connectionService) {
        this.peerConfig = peerConfig;
        this.connectionService = connectionService;
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
            futureToNotify.completeExceptionally(new RuntimeException("Peer is disabled"));
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

        for (Connection connection : connectionService.getConnections()) {
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

        Connection connection = connectionService.getConnection(peerName);
        if (connection != null) {
            LOGGER.info("Disconnecting this peer {} from {}", peerConfig.getPeerName(), peerName);
            connection.close();
        } else {
            LOGGER.warn("This peer {} is not connected to {}", peerConfig.getPeerName(), peerName);
        }
    }

    public void setBindChannel(Channel bindChannel) {
        this.bindChannel = bindChannel;
    }

    public String getPeerName() {
        return peerConfig.getPeerName();
    }
}

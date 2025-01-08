package com.halcyon.p2p.file.transfer.service;

import com.halcyon.p2p.file.transfer.config.PeerConfig;
import com.halcyon.p2p.file.transfer.network.Connection;
import com.halcyon.p2p.file.transfer.network.Peer;
import com.halcyon.p2p.file.transfer.network.PeerChannelHandler;
import com.halcyon.p2p.file.transfer.network.PeerChannelInitializer;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ConnectionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectionService.class);

    private final PeerConfig peerConfig;
    private final EventLoopGroup networkEventLoopGroup;
    private final EventLoopGroup peerEventLoopGroup;

    private final Map<String, Connection> connections = new HashMap<>();

    public ConnectionService(PeerConfig peerConfig, EventLoopGroup networkEventLoopGroup, EventLoopGroup peerEventLoopGroup) {
        this.peerConfig = peerConfig;
        this.networkEventLoopGroup = networkEventLoopGroup;
        this.peerEventLoopGroup = peerEventLoopGroup;
    }

    public void connect(Peer peer, String host, int port, CompletableFuture<Void> futureToNotify) {
        PeerChannelHandler peerChannelHandler = new PeerChannelHandler(peer);
        PeerChannelInitializer peerChannelInitializer = new PeerChannelInitializer(peerConfig, peerEventLoopGroup, peerChannelHandler);

        Bootstrap clientBootstrap = new Bootstrap();
        clientBootstrap.group(networkEventLoopGroup).channel(NioSocketChannel.class).option(ChannelOption.TCP_NODELAY, true)
                .handler(peerChannelInitializer);

        ChannelFuture connectFuture = clientBootstrap.connect(host, port);

        if (futureToNotify != null) {
            connectFuture.addListener((ChannelFutureListener) channelFuture -> {
                if (channelFuture.isSuccess()) {
                    futureToNotify.complete(null);
                    LOGGER.info("Successful connection to {}:{}", host, port);
                } else {
                    futureToNotify.completeExceptionally(channelFuture.cause());
                    LOGGER.error("Can't connect to {}:{}", host, port, channelFuture.cause());
                }
            });
        }
    }

    public void addConnection(Connection connection) {
        String peerName = connection.getPeerName();
        Connection previousConnection = connections.put(peerName, connection);

        LOGGER.info("Connection {} is added", peerName);

        if (previousConnection != null) {
            previousConnection.close();
            LOGGER.info("Previous connection to {} is closed", peerName);
        }
    }

    public Connection getConnection(String peerName) {
        return connections.get(peerName);
    }

    public boolean removeConnection(Connection connection) {
        String peerName = connection.getPeerName();
        boolean removed = connections.remove(peerName) != null;

        if (removed) {
            LOGGER.info("{} is removed from connections", connection);
        } else {
            LOGGER.warn("The connection to {} is not removed because it doesn't exist", peerName);
        }

        return removed;
    }

    public int getNumberOfConnections() {
        return connections.size();
    }

    public boolean hasConnection(String peerName) {
        return connections.containsKey(peerName);
    }

    public Collection<Connection> getConnections() {
        return Collections.unmodifiableCollection(connections.values());
    }
}

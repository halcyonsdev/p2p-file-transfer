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

    private final Map<String, Connection> serverNameToConnectionMap = new HashMap<>();

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
        Connection previousConnection = serverNameToConnectionMap.put(peerName, connection);

        LOGGER.info("Connection to {} is added", peerName);

        if (previousConnection != null) {
            previousConnection.close();
            LOGGER.info("Previous connection to {} is closed", peerName);
        }
    }

    public Connection getConnection(String peerName) {
        return serverNameToConnectionMap.get(peerName);
    }

    public boolean removeConnection(Connection connection) {
        return removeConnection(connection.getPeerName()) != null;
    }

    public Connection removeConnection(String peerName) {
        Connection removedConnection = serverNameToConnectionMap.remove(peerName);

        if (removedConnection != null) {
            LOGGER.info("{} is removed from connections", removedConnection);
        } else {
            LOGGER.warn("The connection to {} is not removed because it doesn't exist", peerName);
        }

        return removedConnection;
    }

    public Collection<Connection> getConnections() {
        return Collections.unmodifiableCollection(serverNameToConnectionMap.values());
    }

    public int getNumberOfConnections() {
        return serverNameToConnectionMap.size();
    }

    public boolean hasConnection(String peerName) {
        return serverNameToConnectionMap.containsKey(peerName);
    }

    public Collection<Connection> getServerNameToConnectionMap() {
        return Collections.unmodifiableCollection(serverNameToConnectionMap.values());
    }
}

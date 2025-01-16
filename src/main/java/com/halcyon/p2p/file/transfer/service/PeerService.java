package com.halcyon.p2p.file.transfer.service;

import com.google.common.util.concurrent.SettableFuture;
import com.halcyon.p2p.file.transfer.config.PeerConfig;
import com.halcyon.p2p.file.transfer.network.Peer;
import com.halcyon.p2p.file.transfer.network.PeerChannelHandler;
import com.halcyon.p2p.file.transfer.network.PeerChannelInitializer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.concurrent.*;

public class PeerService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PeerService.class);

    private final PeerConfig peerConfig;
    private final int portToBind;
    private final Peer peer;

    private final EventLoopGroup acceptorEventLoopGroup = new NioEventLoopGroup(1);
    private final EventLoopGroup networkEventLoopGroup = new NioEventLoopGroup(6);
    private final EventLoopGroup peerEventLoopGroup = new NioEventLoopGroup(1);

    private Future<?> keepAliveFuture;
    private Future<?> timeoutPingsFuture;

    public PeerService(PeerConfig peerConfig, int portToBind) {
        this.peerConfig = peerConfig;
        this.portToBind = portToBind;

        ConnectionService connectionService = new ConnectionService(peerConfig, networkEventLoopGroup, peerEventLoopGroup);
        PingPongService pingPongService = new PingPongService(connectionService, peerConfig);
        FileService fileService = new FileService(peerConfig, connectionService);

        this.peer = new Peer(peerConfig, connectionService, pingPongService, fileService);
    }

    public void start() throws InterruptedException {
        PeerChannelHandler peerChannelHandler = new PeerChannelHandler(peer);
        PeerChannelInitializer peerChannelInitializer = new PeerChannelInitializer(peerConfig, peerEventLoopGroup, peerChannelHandler);

        ServerBootstrap serverBootstrap = new ServerBootstrap();
        serverBootstrap.group(acceptorEventLoopGroup, networkEventLoopGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.SO_BACKLOG, 100)
                .handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(peerChannelInitializer);

        ChannelFuture bindFuture = serverBootstrap.bind(portToBind).sync();
        bindServerChannel(bindFuture);

        int initialDelay = Peer.RANDOM.nextInt(peerConfig.getKeepAlivePeriodSeconds());

        this.keepAliveFuture = peerEventLoopGroup.scheduleAtFixedRate(peer::keepAlivePing, initialDelay, peerConfig.getKeepAlivePeriodSeconds(), TimeUnit.SECONDS);
        this.timeoutPingsFuture = peerEventLoopGroup.scheduleAtFixedRate(peer::timeoutPings, 0, 100, TimeUnit.MILLISECONDS);
    }

    private void bindServerChannel(ChannelFuture bindFuture) {
        if (bindFuture.isSuccess()) {
            LOGGER.info("{} successfully bound to {}", peerConfig.getPeerName(), portToBind);

            Channel serverChannel = bindFuture.channel();
            SettableFuture<Void> setServerChannelFuture = SettableFuture.create();

            peerEventLoopGroup.execute(() -> {
                try {
                    peer.setBindChannel(serverChannel);
                    setServerChannelFuture.set(null);
                } catch (Exception e) {
                    setServerChannelFuture.setException(e);
                }
            });

            handleChannelBinding(setServerChannelFuture);

        } else {
            LOGGER.error("{} couldn't bind to {}", peerConfig.getPeerName(), portToBind, bindFuture.cause());
            System.exit(-1);
        }
    }

    private void handleChannelBinding(SettableFuture<Void> setServerChannelFuture) {
        try {
            setServerChannelFuture.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            LOGGER.error("Execution error while binding channel to server {}", peerConfig.getPeerName(), e);
        } catch (TimeoutException e) {
            LOGGER.error("Timeout occurred while binding channel to server {}", peerConfig.getPeerName(), e);
        } catch (InterruptedException e) {
            LOGGER.error("Thread interrupted while binding channel to server {}", peerConfig.getPeerName(), e);
            Thread.currentThread().interrupt();
        } finally {
            if (!setServerChannelFuture.isDone()) {
                LOGGER.error("Channel binding wasn't completed, exiting...");
                System.exit(-1);
            }
        }
    }

    public CompletableFuture<Void> connect(String host, int port) {
        CompletableFuture<Void> connectToHostFuture = new CompletableFuture<>();
        peerEventLoopGroup.execute(() -> peer.connectTo(host, port, connectToHostFuture));
        return connectToHostFuture;
    }

    public void disconnect(String peerName) {
        peerEventLoopGroup.execute(() -> peer.disconnect(peerName));
    }

    public CompletableFuture<Void> leave() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        peerEventLoopGroup.execute(() -> peer.leave(future));

        if (keepAliveFuture != null && timeoutPingsFuture != null) {
            keepAliveFuture.cancel(false);
            keepAliveFuture = null;

            timeoutPingsFuture.cancel(false);
            timeoutPingsFuture = null;
        }

        return future;
    }

    public CompletableFuture<Collection<String>> ping() {
        CompletableFuture<Collection<String>> future = new CompletableFuture<>();
        peerEventLoopGroup.execute(() -> peer.ping(future));
        return future;
    }

    public void sendGetFilesRequest(String peerName) {
        peerEventLoopGroup.execute(() -> peer.sendGetFilesRequest(peerName));
    }

    public void sendFileRequest(String peerName, String fileName) {
        peerEventLoopGroup.execute(() -> peer.sendFileRequest(peerName, fileName));
    }
}

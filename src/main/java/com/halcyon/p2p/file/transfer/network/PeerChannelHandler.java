package com.halcyon.p2p.file.transfer.network;

import com.halcyon.p2p.file.transfer.proto.General.ProtobufMessage;
import com.halcyon.p2p.file.transfer.proto.Handshake.HandshakeMessage;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ChannelHandler.Sharable
public class PeerChannelHandler extends SimpleChannelInboundHandler<ProtobufMessage> {
    private static final Logger LOGGER = LoggerFactory.getLogger(PeerChannelHandler.class);

    private static final String SESSION_ATTRIBUTE_KEY = "session";
    private final Peer peer;

    public PeerChannelHandler(Peer peer) {
        this.peer = peer;
    }

    static Attribute<Connection> getSessionConnection(ChannelHandlerContext ctx) {
        return ctx.channel().attr(AttributeKey.valueOf(SESSION_ATTRIBUTE_KEY));
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ProtobufMessage message) {
        Connection connection = getSessionConnection(ctx).get();

        if (message.hasHandshake()) {
            handleHandshake(connection, message.getHandshake());
        } else if (message.hasPing()) {
            peer.handlePing(connection, message.getPing());
        } else if (message.hasPong()) {
            peer.handlePong(connection, message.getPong());
        } else if (message.hasKeepAlive()) {
            LOGGER.info("Keep alive ping received from {}", connection);
        } else if (message.hasCancelPings()) {
            peer.cancelPings(connection, message.getCancelPings().getPeerName());
        } else if (message.hasCancelPongs()) {
            peer.cancelPongs(message.getCancelPongs().getPeerName());
        } else if (message.hasGetFilesRequest()) {
            peer.handleGetFilesRequest(connection);
        } else if (message.hasGetFilesResponse()) {
            peer.handleGetFilesResponse(connection, message.getGetFilesResponse());
        } else if (message.hasFileRequest()) {
            peer.handleFileRequest(connection, message.getFileRequest());
        } else if (message.hasFileResponse()) {
            peer.handleFileResponse(message.getFileResponse());
        }
    }

    private void handleHandshake(Connection connection, HandshakeMessage handshake) {
        String peerName = handshake.getSenderPeerName();

        if (!connection.isOpen()) {
            connection.open(peerName);
            peer.handleConnectionOpening(connection);
        } else if (!connection.getPeerName().equals(peerName)) {
            LOGGER.warn("Mismatching of peer names! Handshake: {} Connection: {}", peerName, connection.getPeerName());
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        LOGGER.info("The channel {} is active", ctx.channel().remoteAddress());

        Connection connection = new Connection(ctx);
        getSessionConnection(ctx).set(connection);

        var handshakeMessage = HandshakeMessage.newBuilder()
                .setSenderPeerName(peer.getPeerName())
                .build();

        var protobufMessage = ProtobufMessage.newBuilder()
                .setHandshake(handshakeMessage)
                .build();

        ctx.writeAndFlush(protobufMessage);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        LOGGER.info("Channel {} is inactive", ctx.channel().remoteAddress());
        Connection connection = getSessionConnection(ctx).get();
        peer.handleConnectionClosing(connection);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.error("Channel {} failure occurred", ctx.channel().remoteAddress(), cause);
        ctx.close();

        Connection connection = getSessionConnection(ctx).get();
        peer.handleConnectionClosing(connection);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent idleStateEvent && idleStateEvent.state() == IdleState.READER_IDLE) {
            LOGGER.warn("The channel {} is idle", ctx.channel().remoteAddress());
            ctx.close();
        }
    }
}

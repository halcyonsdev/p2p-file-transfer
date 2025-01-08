package com.halcyon.p2p.file.transfer.network;

import com.halcyon.p2p.file.transfer.config.PeerConfig;
import com.halcyon.p2p.file.transfer.proto.General;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.EventExecutorGroup;

public class PeerChannelInitializer extends ChannelInitializer<SocketChannel> {
    private final PeerConfig peerConfig;
    private final EventExecutorGroup peerChannelHandlerExecutorGroup;
    private final PeerChannelHandler peerChannelHandler;

    public PeerChannelInitializer(PeerConfig peerConfig, EventExecutorGroup peerChannelHandlerExecutorGroup, PeerChannelHandler peerChannelHandler) {
        this.peerConfig = peerConfig;
        this.peerChannelHandlerExecutorGroup = peerChannelHandlerExecutorGroup;
        this.peerChannelHandler = peerChannelHandler;
    }

    @Override
    protected void initChannel(SocketChannel socketChannel) {
        ChannelPipeline pipeline = socketChannel.pipeline();

        pipeline.addLast(new ProtobufVarint32FrameDecoder());
        pipeline.addLast(new ProtobufDecoder(General.ProtobufMessage.getDefaultInstance()));
        pipeline.addLast(new ProtobufVarint32LengthFieldPrepender());
        pipeline.addLast(new ProtobufEncoder());

        pipeline.addLast(new IdleStateHandler(peerConfig.getMaxReadIdleSeconds(), 0, 0));
        pipeline.addLast(peerChannelHandlerExecutorGroup, peerChannelHandler);
    }
}

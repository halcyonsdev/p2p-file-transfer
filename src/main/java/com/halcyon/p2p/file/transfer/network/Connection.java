package com.halcyon.p2p.file.transfer.network;

import com.halcyon.p2p.file.transfer.proto.General.ProtobufMessage;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class Connection {
    private static final Logger LOGGER = LoggerFactory.getLogger(Connection.class);

    private final ChannelHandlerContext ctx;
    private String peerName;
    private boolean isOpen = false;

    public Connection(ChannelHandlerContext ctx) {
        this.ctx = ctx;
    }

    public void send(ProtobufMessage message) {
        if (isOpen()) {
            ctx.writeAndFlush(message);
        } else {
            LOGGER.warn("Can't send message because {} is closed", this);
        }
    }

    public void close() {
        LOGGER.info("Closing session for {}", this);

        if (isOpen()) {
            ctx.close();
            isOpen = false;
        }
    }

    public void open(String peerName) {
        this.isOpen = true;
        this.peerName = peerName;
    }

    public boolean isOpen() {
        return isOpen;
    }

    public String getPeerName() {
        return peerName;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other == null || getClass() != other.getClass()) {
            return false;
        }

        Connection that = (Connection) other;
        return Objects.equals(peerName, that.peerName);
    }

    @Override
    public int hashCode() {
        return peerName != null ? peerName.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "Connection{" +
                "isOpen=" + isOpen() +
                ", peerName='" + peerName + '\'' +
                '}';
    }
}

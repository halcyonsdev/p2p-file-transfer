package com.halcyon.p2p.file.transfer.util;

import com.halcyon.p2p.file.transfer.proto.Ping.PingMessage;
import com.halcyon.p2p.file.transfer.proto.Pong.PongMessage;

import java.util.Optional;

public class PingPongUtil {
    private PingPongUtil() {};

    public static Optional<PingMessage> nextPing(PingMessage previousPing) {
        if (previousPing.getTtl() > 1) {
            var ping = PingMessage.newBuilder()
                    .setPeerName(previousPing.getPeerName())
                    .setTtl(previousPing.getTtl() - 1)
                    .setHops(previousPing.getHops() + 1)
                    .setPingTimeoutDurationInMillis(previousPing.getPingTimeoutDurationInMillis())
                    .build();

            return Optional.of(ping);
        }

        return Optional.empty();
    }

    public static Optional<PongMessage> nextPong(PongMessage previousPong, String currentPongPeerName) {
        if (previousPong.getTtl() > 1) {
            var pong = PongMessage.newBuilder()
                    .setPingPeerName(previousPong.getPingPeerName())
                    .setSenderPeerName(currentPongPeerName)
                    .setPeerName(previousPong.getPeerName())
                    .setServerHost(previousPong.getServerHost())
                    .setServerPort(previousPong.getServerPort())
                    .setTtl(previousPong.getTtl() - 1)
                    .setHops(previousPong.getHops() + 1)
                    .build();

            return Optional.of(pong);
        }

        return Optional.empty();
    }
}

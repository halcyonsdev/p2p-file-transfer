package com.halcyon.p2p.file.transfer.network;

import com.halcyon.p2p.file.transfer.proto.General.ProtobufMessage;
import com.halcyon.p2p.file.transfer.proto.Ping.PingMessage;
import com.halcyon.p2p.file.transfer.proto.Pong.PongMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.halcyon.p2p.file.transfer.util.PingPongUtil.nextPong;

public class PingContext {
    private static final Logger LOGGER = LoggerFactory.getLogger(PingContext.class);

    private final PingMessage ping;
    private final Connection connection;

    private final Map<String, PongMessage> serverNameToPongMap = new HashMap<>();
    private final List<CompletableFuture<Collection<String>>> futures = new ArrayList<>();

    public PingContext(PingMessage ping, Connection connection) {
        this.ping = ping;
        this.connection = connection;
    }

    public boolean handlePong(String serverName, PongMessage pong) {
        String pingServerName = ping.getPeerName();
        String pongServerName = pong.getPeerName();

        if (serverNameToPongMap.containsKey(pongServerName)) {
            LOGGER.info("{} from {} is already handled in {}", pong, pongServerName, pingServerName);
            return false;
        }

        serverNameToPongMap.put(pongServerName, pong);
        LOGGER.info("Handling {} №{} from {} in {}", pong, serverNameToPongMap.size(), pongServerName, pingServerName);

        if (!pingServerName.equals(serverName)) {
            if (connection != null) {
                determineNextPong(serverName, pong);
            } else {
                LOGGER.error("No connection found in the {} ping context for {} from {}", ping.getPeerName(), pong, pongServerName);
            }
        }

        return true;
    }

    private void determineNextPong(String serverName, PongMessage pong) {
        Optional<PongMessage> pongOptional = nextPong(pong, serverName);

        if (pongOptional.isPresent()) {
            LOGGER.info("Forwarding {} from {} for initiator {}", pong, connection.getPeerName(), ping.getPeerName());

            var protobufMessage = ProtobufMessage.newBuilder()
                    .setPong(pongOptional.get())
                    .build();

            connection.send(protobufMessage);
        } else {
            LOGGER.error("Invalid {} received from {} for {}", pong, pong.getPeerName(), ping.getPeerName());
        }
    }

    public boolean removePong(String pongServerName) {
        return serverNameToPongMap.remove(pongServerName) != null;
    }

    public void addFuture(CompletableFuture<Collection<String>> future) {
        futures.add(future);
    }

    public boolean isTimeout() {
        return ping.getPingStartTimestamp() + ping.getPingTimeoutDurationInMillis() <= System.currentTimeMillis();
    }

    public List<CompletableFuture<Collection<String>>> getFutures() {
        return Collections.unmodifiableList(futures);
    }

    @Override
    public String toString() {
        return "PingContext{" +
                "ping=" + ping +
                ", connection=" + connection +
                ", serverNameToPongMap=" + serverNameToPongMap +
                '}';
    }
}

package com.halcyon.p2p.file.transfer.service;

import com.halcyon.p2p.file.transfer.network.Connection;
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

    public void handlePong(String serverName, PongMessage pong) {
        String pingServerName = ping.getPeerName();
        String pongServerName = pong.getPeerName();

        if (serverNameToPongMap.containsKey(pongServerName)) {
            LOGGER.info("Pong from {} is already handled in {}", pongServerName, pingServerName);
            return;
        }

        serverNameToPongMap.put(pongServerName, pong);
        LOGGER.info("Handling pong №{} from {} in {}", serverNameToPongMap.size(), pongServerName, pingServerName);

        if (!pingServerName.equals(serverName)) {
            if (connection != null) {
                determineNextPong(serverName, pong);
            } else {
                LOGGER.error("No connection found in the {} ping context for {} from {}", ping.getPeerName(), pong, pongServerName);
            }
        }
    }

    private void determineNextPong(String serverName, PongMessage pong) {
        Optional<PongMessage> pongOptional = nextPong(pong, serverName);

        if (pongOptional.isPresent()) {
            LOGGER.info("Forwarding pong from {} for initiator {}", connection.getPeerName(), ping.getPeerName());

            var protobufMessage = ProtobufMessage.newBuilder()
                    .setPong(pongOptional.get())
                    .build();

            connection.send(protobufMessage);
        } else {
            LOGGER.error("Invalid pong received from {} for {}", pong.getPeerName(), ping.getPeerName());
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

    public Collection<PongMessage> getPongs() {
        return Collections.unmodifiableCollection(serverNameToPongMap.values());
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

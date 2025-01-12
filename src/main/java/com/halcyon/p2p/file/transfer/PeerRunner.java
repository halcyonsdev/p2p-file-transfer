package com.halcyon.p2p.file.transfer;

import com.halcyon.p2p.file.transfer.config.PeerConfig;
import com.halcyon.p2p.file.transfer.service.PeerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Date;
import java.util.function.BiConsumer;

public class PeerRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(PeerRunner.class);

    private final PeerService peerService;

    public PeerRunner(PeerConfig peerConfig, int portToBind) {
        this.peerService = new PeerService(peerConfig, portToBind);
    }

    public void start() throws InterruptedException {
        peerService.start();
    }

    public CommandResult handleCommand(String command) {
        CommandResult result = CommandResult.CONTINUE;

        if (command.equals("ping")) {
            peerService.ping().whenComplete(new PingFutureListener());
        } else if (command.equals("leave")) {
            peerService.leave().whenComplete(new LeaveFutureListener());
            result = CommandResult.SHUTDOWN;
        } else if (command.startsWith("connect ")) {
            String[] tokens = command.split(" ", 2)[1].split(":");
            String hostToConnect = tokens[0];
            int portToConnect = Integer.parseInt(tokens[1]);

            peerService.connect(hostToConnect, portToConnect).whenComplete(new ConnectFutureListener(hostToConnect, portToConnect));
        } else if (command.startsWith("disconnect ")) {
            String peerName = command.split(" ")[1];
            peerService.disconnect(peerName);
        } else if (command.startsWith("getFiles ")) {
            String peerName = command.split(" ")[1];
            peerService.sendGetFilesRequest(peerName);
        } else {
            result = CommandResult.INVALID;
        }

        return result;
    }

    public enum CommandResult {
        CONTINUE,
        SHUTDOWN,
        INVALID
    }

    private static class PingFutureListener implements BiConsumer<Collection<String>, Throwable> {
        @Override
        public void accept(Collection<String> peerNames, Throwable throwable) {
            if (peerNames != null) {
                LOGGER.info("Connected peers: {}", peerNames);
            } else {
                LOGGER.error("Ping operation failed", throwable);
            }
        }
    }

    private static class LeaveFutureListener implements BiConsumer<Void, Throwable> {
        @Override
        public void accept(Void unused, Throwable throwable) {
            if (throwable == null) {
                LOGGER.info("Left the cluster at {}", new Date());
            } else {
                LOGGER.error("Exception occurred during leaving the cluster!", throwable);
            }
        }
    }

    private record ConnectFutureListener(String hostToConnect, int portToConnect) implements BiConsumer<Void, Throwable> {
        @Override
        public void accept(Void unused, Throwable throwable) {
            if (throwable == null) {
                LOGGER.info("Successfully connected to {}:{}", hostToConnect, portToConnect);
            } else {
                LOGGER.error("Connection to {}:{} failed!", hostToConnect, portToConnect);
            }
        }
    }
}

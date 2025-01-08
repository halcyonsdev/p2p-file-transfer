package com.halcyon.p2p.file.transfer;

import com.halcyon.p2p.file.transfer.config.PeerConfig;
import com.halcyon.p2p.file.transfer.service.PeerService;
import io.netty.channel.ChannelFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiConsumer;

public class PeerRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(PeerRunner.class);

    private final PeerService peerService;

    public PeerRunner(PeerConfig peerConfig, int portToBind) {
        this.peerService = new PeerService(peerConfig, portToBind);
    }

    public ChannelFuture start() throws InterruptedException {
        return peerService.start();
    }

    public CommandResult handleCommand(String command) {
        CommandResult result = CommandResult.CONTINUE;

        if (command.startsWith("connect ")) {
            String[] tokens = command.split(" ", 2)[1].split(":");
            String hostToConnect = tokens[0];
            int portToConnect = Integer.parseInt(tokens[1]);

            peerService.connect(hostToConnect, portToConnect).whenComplete(new ConnectFutureListener(hostToConnect, portToConnect));
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

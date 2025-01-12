package com.halcyon.p2p.file.transfer;

import com.halcyon.p2p.file.transfer.config.PeerConfig;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class Application {
    private static final Logger LOGGER = LoggerFactory.getLogger(Application.class);

    private static final String PEER_NAME_SYSTEM_PROPERTY = "peerName";
    private static final String PEER_NAME_PARAMETER = "peerName";
    private static final String BIND_PORT_PARAMETER = "bindPort";
    private static final String HELP_PARAMETER = "help";

    public static void main(String[] args) throws IOException, InterruptedException {
        if (System.getProperty(PEER_NAME_SYSTEM_PROPERTY) == null) {
            LOGGER.error("System property \"peerName\" should be provided!");
            System.exit(-1);
        }

        OptionSet options = parseArguments(args);
        PeerRunner peerRunner = createPeerRunner(options);

        peerRunner.start();

        String line;
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

        while ((line = reader.readLine()) != null) {
            PeerRunner.CommandResult result = peerRunner.handleCommand(line);

            if (result == PeerRunner.CommandResult.SHUTDOWN) {
                break;
            } else if (result == PeerRunner.CommandResult.INVALID && !line.isEmpty()) {
                printHelp(line);
            }
        }
    }

    private static OptionSet parseArguments(String[] args) throws IOException {
        OptionParser optionParser = new OptionParser();

        optionParser.accepts(PEER_NAME_PARAMETER).withRequiredArg().ofType(String.class).describedAs("peer name");
        optionParser.accepts(BIND_PORT_PARAMETER).withRequiredArg().ofType(Integer.class).describedAs("port to bind");
        optionParser.accepts(HELP_PARAMETER).forHelp();

        OptionSet options = optionParser.parse(args);

        if (options.has(HELP_PARAMETER)) {
            optionParser.printHelpOn(System.out);
        }

        if (!options.has(PEER_NAME_PARAMETER) || !options.has(BIND_PORT_PARAMETER)) {
            if (!options.has(HELP_PARAMETER)) {
                optionParser.printHelpOn(System.out);
            }

            LOGGER.error("Missing arguments!");
            System.exit(-1);
        }

        return options;
    }

    private static PeerRunner createPeerRunner(OptionSet options) {
        String peerName = (String) options.valueOf(PEER_NAME_PARAMETER);
        int portToBind = (int) options.valueOf(BIND_PORT_PARAMETER);

        PeerConfig peerConfig = new PeerConfig(peerName);
        return new PeerRunner(peerConfig, portToBind);
    }

    private static void printHelp(String line) {
        if (!"help".equalsIgnoreCase(line.trim())) {
            System.out.println("Invalid input command:  " + line);
        }

        System.out.println(
                "############################################## COMMANDS ###############################################");
        System.out.println(
                "# 1) ping                       >>> Lists peers in the network                                               #");
        System.out.println(
                "# 2) leave                      >>> Leaves the network                                                       #");
        System.out.println(
                "# 3) connect host port          >>> Connects to the peer specified by host:port pair                         #");
        System.out.println(
                "# 4) disconnect peerName        >>> Disconnects from the peer specified with peerName                        #");
        System.out.println(
                "# 5) getFiles peerName          >>> Gets file names from peer                                                #");
        System.out.println(
                "# 6) download peerName fileName >>> Requests a file to download from peer                                    #");
        System.out.println(
                "#######################################################################################################");
    }
}

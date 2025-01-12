package com.halcyon.p2p.file.transfer.service;

import com.halcyon.p2p.file.transfer.config.PeerConfig;
import com.halcyon.p2p.file.transfer.network.Connection;
import com.halcyon.p2p.file.transfer.proto.File.*;
import com.halcyon.p2p.file.transfer.proto.General.ProtobufMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class FileService {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileService.class);

    private final PeerConfig peerConfig;

    public FileService(PeerConfig peerConfig) {
        this.peerConfig = peerConfig;
    }

    public void sendGetFilesRequest(Connection connection) {
        var getFilesRequest = GetFilesRequest.newBuilder()
                .setPeerName(peerConfig.getPeerName())
                .build();

        var protobufMessage = ProtobufMessage.newBuilder()
                .setGetFilesRequest(getFilesRequest)
                .build();

        connection.send(protobufMessage);
        LOGGER.info("Sending GetFilesRequest from {} to {}", peerConfig.getPeerName(), connection.getPeerName());
    }

    public void handleGetFilesRequest(Connection connection) {
        File dir = new File("shared_directory/");
        String[] fileNames = dir.list();

        if (fileNames == null) {
            sendGetFilesResponse(connection, GetFilesResponse.getDefaultInstance());
            LOGGER.info("An empty GetFilesResponse was sent from {} to {} because there are no files", peerConfig.getPeerName(), connection.getPeerName());
            return;
        }

        var getFilesResponseBuilder = GetFilesResponse.newBuilder().setPeerName(peerConfig.getPeerName());
        for (String fileName : fileNames) {
            getFilesResponseBuilder.addFileNames(fileName);
        }

        sendGetFilesResponse(connection, getFilesResponseBuilder.build());

        LOGGER.info("A GetFilesResponse with {} files was sent from {} to {}", fileNames.length, peerConfig.getPeerName(), connection.getPeerName());
    }

    private void sendGetFilesResponse(Connection connection, GetFilesResponse response) {
        var protobufMessage = ProtobufMessage.newBuilder()
                .setGetFilesResponse(response)
                .build();

        connection.send(protobufMessage);
    }

    public void handleGetFilesResponse(GetFilesResponse response) {
        System.out.printf("Files in %s:%n", response.getPeerName());

        for (int i = 0; i < response.getFileNamesCount(); i++) {
            System.out.printf("%s. %s%n", i + 1, response.getFileNames(i));
        }
    }
}

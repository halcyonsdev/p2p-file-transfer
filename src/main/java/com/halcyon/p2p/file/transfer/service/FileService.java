package com.halcyon.p2p.file.transfer.service;

import com.google.protobuf.ByteString;
import com.halcyon.p2p.file.transfer.config.PeerConfig;
import com.halcyon.p2p.file.transfer.network.Connection;
import com.halcyon.p2p.file.transfer.proto.File.*;
import com.halcyon.p2p.file.transfer.proto.General.ProtobufMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class FileService {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileService.class);

    private final PeerConfig peerConfig;
    private final ConnectionService connectionService;

    private final Map<String, FileOutputStream> fileNameToStreamMap = new HashMap<>();

    public FileService(PeerConfig peerConfig, ConnectionService connectionService) {
        this.peerConfig = peerConfig;
        this.connectionService = connectionService;
    }

    public void sendGetFilesRequest(Connection connection) {
        var getFilesRequest = GetFilesRequest.getDefaultInstance();

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

    public void sendFileRequest(String peerName, String fileName) {
        if (!connectionService.hasConnection(peerName)) {
            LOGGER.warn("There is no connection to the {}", peerName);
            return;
        }

        var fileRequest = FileRequest.newBuilder()
                .setFileName(fileName)
                .build();

        var protobufMessage = ProtobufMessage.newBuilder()
                .setFileRequest(fileRequest)
                .build();

        Connection connection = connectionService.getConnection(peerName);
        connection.send(protobufMessage);

        LOGGER.info("A FileRequest for {} was sent from {} to {}", fileName, peerConfig.getPeerName(), connection.getPeerName());
    }

    public void handleFileRequest(Connection connection, FileRequest request) {
        File file = new File("shared_directory/" + request.getFileName());

        if (!file.exists()) {
            LOGGER.warn("The file with name {} was not found", file.getName());
            return;
        }

        try (RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r")) {
            long fileSize = file.length();
            byte[] buffer = new byte[8192];
            long bytesSent = 0;
            System.out.println(fileSize);

            while (bytesSent < fileSize) {
                int bytesRead = randomAccessFile.read(buffer);
                LOGGER.info("Reading {} bytes. {} bytes have already been read", bytesRead, bytesSent + bytesRead);

                boolean isLastChunk = (bytesSent + bytesRead) == fileSize;

                var fileResponse = FileResponse.newBuilder()
                        .setData(ByteString.copyFrom(Arrays.copyOf(buffer, bytesRead)))
                        .setFileName(file.getName())
                        .setIsLastChunk(isLastChunk)
                        .build();

                var protobufMessage = ProtobufMessage.newBuilder()
                        .setFileResponse(fileResponse)
                        .build();

                connection.send(protobufMessage);
                bytesSent += bytesRead;
            }
        } catch (IOException e) {
            LOGGER.error("Error occurred while reading file {}", request.getFileName(), e);
        }
    }

    public void handleFileResponse(FileResponse response) {
        String responseFileName = response.getFileName();

        try {
            FileOutputStream fileOutputStream = fileNameToStreamMap.get(responseFileName);

            if (fileOutputStream == null) {
                fileOutputStream = new FileOutputStream("downloads/" + response.getFileName());
                fileNameToStreamMap.put(responseFileName, fileOutputStream);
            }

            fileOutputStream.write(response.getData().toByteArray());

            if (response.getIsLastChunk()) {
                fileOutputStream.close();
                fileNameToStreamMap.remove(responseFileName);

                LOGGER.info("The file {} was saved successfully", responseFileName);
            }

        } catch (IOException e) {
            LOGGER.error("Error occurred while writing file {}", responseFileName, e);
        }
    }
}

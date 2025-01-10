package com.halcyon.p2p.file.transfer.config;

import java.util.concurrent.TimeUnit;

public class PeerConfig {
    public static final int DEFAULT_MAX_READ_IDLE_SECONDS = 120;
    public static final int DEFAULT_PING_TIMEOUT_SECONDS = 5;
    public static final int DEFAULT_PING_TTL = 7;
    public static final int DEFAULT_MIN_NUMBER_OF_ACTIVE_CONNECTIONS = 5;

    private String peerName;
    private int maxReadIdleSeconds = DEFAULT_MAX_READ_IDLE_SECONDS;
    private int pingTimeoutSeconds = DEFAULT_PING_TIMEOUT_SECONDS;
    private int pingTtl = DEFAULT_PING_TTL;
    private int minNumberOfActiveConnections = DEFAULT_MIN_NUMBER_OF_ACTIVE_CONNECTIONS;

    public PeerConfig(String peerName) {
        this.peerName = peerName;
    }

    public String getPeerName() {
        return peerName;
    }

    public void setPeerName(String peerName) {
        this.peerName = peerName;
    }

    public int getMaxReadIdleSeconds() {
        return maxReadIdleSeconds;
    }

    public void setMaxReadIdleSeconds(int maxReadIdleSeconds) {
        this.maxReadIdleSeconds = maxReadIdleSeconds;
    }

    public long getPingTimeoutMillis() {
        return TimeUnit.SECONDS.toMillis(pingTimeoutSeconds);
    }

    public void setPingTimeoutSeconds(int pingTimeoutSeconds) {
        this.pingTimeoutSeconds = pingTimeoutSeconds;
    }

    public int getPingTtl() {
        return pingTtl;
    }

    public void setPingTtl(int pingTtl) {
        this.pingTtl = pingTtl;
    }

    public int getMinNumberOfActiveConnections() {
        return minNumberOfActiveConnections;
    }

    public void setMinNumberOfActiveConnections(int minNumberOfActiveConnections) {
        this.minNumberOfActiveConnections = minNumberOfActiveConnections;
    }

    @Override
    public String toString() {
        return "PeerConfig{" +
                "peerName='" + peerName + '\'' +
                ", maxReadIdleSeconds=" + maxReadIdleSeconds +
                '}';
    }
}

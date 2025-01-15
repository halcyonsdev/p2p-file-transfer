package com.halcyon.p2p.file.transfer.config;

import java.util.concurrent.TimeUnit;

public class PeerConfig {
    public static final int DEFAULT_MAX_READ_IDLE_SECONDS = 120;
    public static final int DEFAULT_PING_TIMEOUT_SECONDS = 5;
    public static final int DEFAULT_PING_TTL = 7;
    public static final int DEFAULT_MAX_NUMBER_OF_ACTIVE_CONNECTIONS = 5;
    public static final int DEFAULT_AUTO_DISCOVERY_PING_FREQUENCY = 10;
    public static final int DEFAULT_KEEP_ALIVE_SECONDS = 15;

    private String peerName;
    private int maxReadIdleSeconds = DEFAULT_MAX_READ_IDLE_SECONDS;
    private int pingTimeoutSeconds = DEFAULT_PING_TIMEOUT_SECONDS;
    private int pingTtl = DEFAULT_PING_TTL;
    private int maxNumberOfActiveConnections = DEFAULT_MAX_NUMBER_OF_ACTIVE_CONNECTIONS;
    private int autoDiscoveryPingFrequency = DEFAULT_AUTO_DISCOVERY_PING_FREQUENCY;
    private int keepAlivePeriodSeconds = DEFAULT_KEEP_ALIVE_SECONDS;

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

    public int getMaxNumberOfActiveConnections() {
        return maxNumberOfActiveConnections;
    }

    public void setMaxNumberOfActiveConnections(int maxNumberOfActiveConnections) {
        this.maxNumberOfActiveConnections = maxNumberOfActiveConnections;
    }

    public int getAutoDiscoveryPingFrequency() {
        return autoDiscoveryPingFrequency;
    }

    public void setAutoDiscoveryPingFrequency(int autoDiscoveryPingFrequency) {
        this.autoDiscoveryPingFrequency = autoDiscoveryPingFrequency;
    }

    public int getKeepAlivePeriodSeconds() {
        return keepAlivePeriodSeconds;
    }

    public void setKeepAlivePeriodSeconds(int keepAlivePeriodSeconds) {
        this.keepAlivePeriodSeconds = keepAlivePeriodSeconds;
    }

    @Override
    public String toString() {
        return "PeerConfig{" +
                "peerName='" + peerName + '\'' +
                ", maxReadIdleSeconds=" + maxReadIdleSeconds +
                ", pingTimeoutSeconds=" + pingTimeoutSeconds +
                ", pingTtl=" + pingTtl +
                ", maxNumberOfActiveConnections=" + maxNumberOfActiveConnections +
                ", autoDiscoveryPingFrequency=" + autoDiscoveryPingFrequency +
                ", keepAlivePeriodSeconds=" + keepAlivePeriodSeconds +
                '}';
    }
}

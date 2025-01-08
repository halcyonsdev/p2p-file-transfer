package com.halcyon.p2p.file.transfer.config;

public class PeerConfig {
    public static final int DEFAULT_MAX_READ_IDLE_SECONDS = 120;

    private String peerName;
    private int maxReadIdleSeconds = DEFAULT_MAX_READ_IDLE_SECONDS;

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

    @Override
    public String toString() {
        return "PeerConfig{" +
                "peerName='" + peerName + '\'' +
                ", maxReadIdleSeconds=" + maxReadIdleSeconds +
                '}';
    }
}

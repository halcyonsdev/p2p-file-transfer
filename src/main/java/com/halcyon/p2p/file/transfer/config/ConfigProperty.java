package com.halcyon.p2p.file.transfer.config;

public enum ConfigProperty {
    MAX_READ_IDLE_SECONDS("maxReadIdleSeconds") {
        @Override
        public void setValue(int value, PeerConfig peerConfig) {
            peerConfig.setMaxReadIdleSeconds(value);
        }
    },

    PING_TIMEOUT_SECONDS("pingTimeoutSeconds") {
        @Override
        public void setValue(int value, PeerConfig peerConfig) {
            peerConfig.setPingTimeoutSeconds(value);
        }
    },

    PING_TTL("pingTTL") {
        @Override
        public void setValue(int value, PeerConfig peerConfig) {
            peerConfig.setPingTtl(value);
        }
    },

    MAX_NUMBER_OF_ACTIVE_CONNECTIONS("maxNumberOfActiveConnections") {
        @Override
        public void setValue(int value, PeerConfig peerConfig) {
            peerConfig.setMaxNumberOfActiveConnections(value);
        }
    },

    AUTO_DISCOVERY_PING_FREQUENCY("autoDiscoveryPingFrequency") {
        @Override
        public void setValue(int value, PeerConfig peerConfig) {
            peerConfig.setAutoDiscoveryPingFrequency(value);
        }
    },

    KEEP_ALIVE_SECONDS("keepAliveSeconds") {
        @Override
        public void setValue(int value, PeerConfig peerConfig) {
            peerConfig.setKeepAlivePeriodSeconds(value);
        }
    };

    private final String propertyName;

    ConfigProperty(String propertyName) {
        this.propertyName = propertyName;
    }

    public abstract void setValue(int value, PeerConfig peerConfig);

    public static ConfigProperty getByName(String propertyName) {
        for (ConfigProperty property : values()) {
            if (property.propertyName.equals(propertyName)) {
                return property;
            }
        }

        throw new IllegalArgumentException("Invalid config property " + propertyName);
    }
}

# P2P-File-Transfer | Java, Netty, Protobuf
It is an unstructured peer-to-peer file-sharing network using Java 21 and Netty 4

## Features

### 1. **Peer-to-Peer Network**
- Supports a decentralized network where each peer can directly communicate with others.
- Uses TCP connections managed through Netty.

### 2. **File Transfer**
- Allows peers to request and share files efficiently.
- Supports transferring files in chunks to handle large files.

### 3. **Keep-Alive Mechanism**
- Periodic pings `KeepAliveMessage` are sent to ensure the connection between peers remains active.
- Timeouts trigger reconnection attempts or removal of inactive peers.

### 4. **Protobuf-Based Communication**
- Defines a flexible communication protocol using Protocol Buffers (Protobuf).
- Messages include handshakes, file requests, and other network events.

### 5. **Ping-Pong System**
The ping-pong system is a critical component of the network that:
- Sends periodic **ping** messages to discover and verify active peers.
- Receives **pong** responses from peers, which include their metadata.
- Times out inactive peers and removes them from the network.
- Notifies other components about discovered peers, allowing dynamic adjustment of connections.

#### Workflow:
1. **Ping:** Each peer periodically sends a `PingMessage` to its connected peers.
2. **Pong:** Upon receiving a ping, peers respond with a `PongMessage` that includes their details.
3. **Timeout Handling:** If no pong is received within a specific timeout period, the pinging peer assumes the target is inactive and cleans up resources associated with that peer.

### 6. **Auto-Connection Management**
- Automatically connects to available peers when connection slots are free.
- Prioritizes unconnected peers for establishing new connections.

### 7. **Custom Configuration**
- Configuration can be provided via a `.properties` file.
- Properties include settings for connection limits, ping intervals, and timeout durations.

### 8. **Netty-Based Networking**
- High-performance networking with Netty as the underlying framework.
- Supports asynchronous communication for scalability.

### 9. **Logging**
- Provides detailed logs for network events, file transfers, and system actions.

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

---

## How to Run

1. Clone the repository:
   ```bash
   git clone https://github.com/halcyonsdev/p2p-file-transfer.git
   ```

2. Navigate to the project directory:
   ```bash
   cd p2p-file-transfer
   ```

3. Build the project:
   ```bash
   mvn clean install
   ```

4. Run the application:
   ```bash
   java -DpeerName=peer -jar target/p2p-file-transfer.jar --config=config/config.properties --peerName=peer --bindPort=8080
   ```

---

## How It Works

1. **Startup:** Each peer starts and loads its configuration from the provided `.properties` file or by default properties.
2. **Handshake:** Peers perform an initial handshake to establish connections and exchange metadata.
3. **Ping-Pong:** The ping-pong system ensures active peers are discovered and maintains a healthy network.
4. **File Requests:** Peers can request files from others by sending a `GetFilesRequest` message and receiving the response with available file names.
5. **File Transfer:** File chunks are sent between peers upon request.

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

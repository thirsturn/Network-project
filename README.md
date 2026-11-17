# Campus Collaboration Hub
 
Network Programming – Assignment 02
 
A multi-protocol Java application that demonstrates five networking concepts working together in one realistic workflow: secure login, real-time chat, presence notifications, message history over HTTP, and group announcements.
 
The focus of this project is the implementation and demonstration of networking concepts, not UI polish. The client is a simple console application.
 
## Networking Concepts Demonstrated
 
| Member | Concept | Java API | Role in the app |
|---|---|---|---|
| 1 | TCP + Multithreading | `ServerSocket`, `Socket`, `ExecutorService` | Multi-client chat server |
| 2 | UDP | `DatagramSocket`, `DatagramPacket` | Online / offline notifications |
| 3 | HTTP / REST | `HttpServer`, `HttpClient` | Message history API |
| 4 | SSL/TLS | `SSLServerSocket`, `SSLSocket` | Secure login and token issuing |
| 5 | Multicast | `MulticastSocket` | Group announcements |
 
## Team
 
| Member | Name | Index No. | Concept |
|---|---|---|---|
| 1 | Thiranjaya | | TCP + Multithreading |
| 2 | Asanka | | UDP |
| 3 | Pasindu | | HTTP / REST |
| 4 | Kaveen | | SSL/TLS |
| 5 | Vihanga | | Multicast |
 
## Architecture
 
```
Clients (console)
   │  TLS (5443)       → Auth service     (Member 4)
   │  TCP (5000)       → Chat server      (Member 1)
   │  UDP (5001)       → Status notifier  (Member 2)
   │  HTTP (8080)      → REST API         (Member 3)
   └  Multicast (5002)  → Announcements   (Member 5)
```
 
All server components run inside one Java process started by `Main`. Two shared in-memory stores in the `common` package connect the modules:
 
- `SessionStore` maps session tokens to usernames. The auth service writes to it, the chat server reads from it.
- `MessageStore` holds chat messages. The chat server writes to it, the REST API reads from it.
All communication between clients and servers uses real network connections.
 
## Ports
 
| Service | Port | Protocol |
|---|---|---|
| Auth service | 5443 | TLS over TCP |
| Chat server | 5000 | TCP |
| Status notifier | 5001 | UDP |
| REST API | 8080 | HTTP |
| Announcements | 5002 | Multicast group `230.0.0.1` |
 
Ports and the multicast address are defined in `common/Config.java`.
 
## Project Structure
 
```
src/main/java/
├── common/      Config, Message, SessionStore, MessageStore
├── tcp/         ChatServer, ChatClient                (Member 1)
├── udp/         StatusNotifier, StatusListener        (Member 2)
├── http/        HistoryServer, HistoryClient          (Member 3)
├── tls/         AuthServer, AuthClient                (Member 4)
├── multicast/   AnnouncementSender, AnnouncementListener (Member 5)
├── ConsoleClient.java   Client entry point            (Member 1)
└── Main.java    Starts all server components
```
 
## Application Workflow
 
1. **Login (TLS)** – The client connects to the auth service over TLS and sends `LOGIN|user|password`. On success the server stores a token in `SessionStore` and replies `OK|<token>`.
2. **Connect (TCP)** – The client connects to the chat server and sends `AUTH|<token>|<udpPort>`. The server validates the token and assigns a pooled thread to the client.
3. **Presence (UDP)** – The chat server calls `StatusNotifier.notifyOnline(user)`, which sends `ONLINE|user` datagrams to the other clients.
4. **Chat (TCP)** – Messages (`MSG|text`) are stored in `MessageStore` and broadcast to every connected client.
5. **History (HTTP)** – `GET /messages` returns all stored messages as JSON.
6. **Announcement (Multicast)** – The admin tool sends `ANNOUNCE|text` to the multicast group and every joined client receives it at once.
7. **Logout** – On `QUIT` or disconnect, the server removes the client and sends `OFFLINE|user` over UDP.
## Client Application
 
The client is a Java console application (`ConsoleClient`). A console client is used instead of a browser frontend because browsers cannot open raw TCP, UDP or multicast sockets, and the project needs to demonstrate those protocols directly.
 
The client runs several threads at once:
 
| Thread | Responsibility | Concept |
|---|---|---|
| Main thread | Reads user input and sends it over TCP | TCP |
| TCP reader | Receives chat messages | TCP |
| UDP listener | Receives online / offline notifications | UDP |
| Multicast listener | Receives announcements | Multicast |
 
### Client commands
 
| Command | Action |
|---|---|
| `/login <user> <password>` | Log in through the TLS auth service and receive a token |
| `/history` | Fetch message history from the REST API |
| `/quit` | Disconnect from the chat server |
| any other text | Send as a chat message |
 
### Client components
 
Each member implements the client side of their own concept. `ConsoleClient` combines them.
 
| Member | Client component |
|---|---|
| 1 | `ChatClient` and `ConsoleClient` |
| 2 | `StatusListener` |
| 3 | `HistoryClient` |
| 4 | `AuthClient` |
| 5 | `AnnouncementListener` |
 
## Message Formats
 
| Channel | Format |
|---|---|
| Login (TLS) | `LOGIN\|user\|password` → `OK\|token` or `ERROR\|reason` |
| Chat (TCP) | `AUTH\|token\|udpPort`, `MSG\|text`, `QUIT`; server sends `MSG\|sender\|text` |
| Status (UDP) | `ONLINE\|user`, `OFFLINE\|user` |
| History (HTTP) | `GET /messages` → JSON array |
| Announcement (Multicast) | `ANNOUNCE\|text` |
 
## Prerequisites
 
- Java 17 or later
- `keytool` (included with the JDK)
- Wireshark (optional, for packet inspection during the demo)
## Setup
 
### 1. Generate the TLS keystore
 
```bash
keytool -genkeypair -alias campus -keyalg RSA -keysize 2048 -validity 365 \
  -keystore keystore.jks -storepass changeit -dname "CN=localhost"
```
 
### 2. Compile
 
```bash
mkdir -p out
javac -d out $(find src/main/java -name "*.java")
```
 
## Running
 
### Start all servers
 
```bash
java -Djavax.net.ssl.keyStore=keystore.jks \
     -Djavax.net.ssl.keyStorePassword=changeit \
     -cp out Main
```
 
### Start a client (run in several terminals)
 
```bash
java -Djavax.net.ssl.trustStore=keystore.jks \
     -Djavax.net.ssl.trustStorePassword=changeit \
     -cp out ConsoleClient
```
 
### Fetch message history
 
```bash
curl http://localhost:8080/messages
```
 
### Send an announcement
 
```bash
java -cp out multicast.AnnouncementSender
```
 
Class names above follow the project structure. Update them if the final names differ.
 
## Demo Flow (10 minutes)
 
| Time | Member | Demonstration |
|---|---|---|
| 0:00 – 0:30 | Any | Introduction and architecture |
| 0:30 – 2:30 | 4 | TLS login, encrypted traffic in Wireshark |
| 2:30 – 4:30 | 1 | Multi-client TCP chat, thread pool |
| 4:30 – 6:30 | 2 | UDP online / offline notifications |
| 6:30 – 8:30 | 3 | HTTP history via browser and curl |
| 8:30 – 10:00 | 5 | Multicast announcement received by all clients |
 
## Wireshark Filters
 
| Concept | Filter |
|---|---|
| TCP chat | `tcp.port == 5000` |
| UDP status | `udp.port == 5001` |
| HTTP API | `tcp.port == 8080 && http` |
| TLS login | `tcp.port == 5443 && tls` |
| Multicast | `ip.dst == 230.0.0.1` |
 
## Notes
 
- Session tokens and chat history are kept in memory only and are lost when the server stops.
- Credentials are stored in a simple in-memory map for demonstration purposes.
- Run the server and clients on the same machine, or on the same local network for multicast to work across machines.
 
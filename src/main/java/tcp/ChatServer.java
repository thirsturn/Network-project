package tcp;

import common.Config;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

/**
 * Multi-threaded TCP Chat Server.
 * Concept: TCP Sockets + Multithreading (ServerSocket, Socket, ExecutorService)
 * Member: Thiranjaya (Member 1)
 */
public class ChatServer implements Runnable {

    private final int port;
    private final List<ClientHandler> connectedClients = new CopyOnWriteArrayList<>();
    private ServerSocket serverSocket;
    private ExecutorService threadPool;
    private volatile boolean isRunning = false;

    // Optional callbacks for presence notification integration (Member 2 - UDP)
    private BiConsumer<String, Integer> onUserOnlineListener;
    private BiConsumer<String, Integer> onUserOfflineListener;

    public ChatServer() {
        this(Config.CHAT_TCP_PORT);
    }

    public ChatServer(int port) {
        this.port = port;
    }

    /**
     * Starts the TCP Chat Server listening loop.
     */
    public void start() {
        if (isRunning) return;

        isRunning = true;
        // Using cached thread pool to dynamically allocate threads to concurrent clients
        threadPool = Executors.newCachedThreadPool();

        try {
            serverSocket = new ServerSocket(port);
            System.out.println("==================================================");
            System.out.println("[ChatServer] TCP Chat Server started on port: " + port);
            System.out.println("[ChatServer] Thread Pool initialized. Awaiting clients...");
            System.out.println("==================================================");

            while (isRunning) {
                try {
                    // Accept incoming TCP client socket connection (blocks until client arrives)
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("[ChatServer] New TCP connection accepted from " + clientSocket.getRemoteSocketAddress());

                    // Hand off client socket to worker thread from ExecutorService pool
                    ClientHandler handler = new ClientHandler(clientSocket, this);
                    threadPool.submit(handler);

                } catch (IOException e) {
                    if (!isRunning) {
                        break; // Server stopped intentionally
                    }
                    System.err.println("[ChatServer] Error accepting connection: " + e.getMessage());
                }
            }

        } catch (IOException e) {
            System.err.println("[ChatServer] Failed to bind to port " + port + ": " + e.getMessage());
        } finally {
            stop();
        }
    }

    @Override
    public void run() {
        start();
    }

    /**
     * Registers a successfully authenticated client.
     */
    public void registerClient(ClientHandler client) {
        connectedClients.add(client);
        System.out.println("[ChatServer] Active clients count: " + connectedClients.size());

        if (onUserOnlineListener != null && client.getUsername() != null) {
            onUserOnlineListener.accept(client.getUsername(), client.getUdpPort());
        }
    }

    /**
     * Unregisters a disconnected client.
     */
    public void unregisterClient(ClientHandler client) {
        connectedClients.remove(client);

        if (onUserOfflineListener != null && client.getUsername() != null) {
            onUserOfflineListener.accept(client.getUsername(), client.getUdpPort());
        }
    }

    /**
     * Broadcasts a message to all connected clients.
     */
    public void broadcast(String message) {
        broadcast(message, null);
    }

    /**
     * Broadcasts a message to connected clients, optionally excluding one sender.
     */
    public void broadcast(String message, ClientHandler exclude) {
        for (ClientHandler client : connectedClients) {
            if (exclude == null || client != exclude) {
                client.sendMessage(message);
            }
        }
    }

    /**
     * Sets listener for user online event (Used for Member 2 UDP presence notification).
     */
    public void setOnUserOnlineListener(BiConsumer<String, Integer> listener) {
        this.onUserOnlineListener = listener;
    }

    /**
     * Sets listener for user offline event (Used for Member 2 UDP presence notification).
     */
    public void setOnUserOfflineListener(BiConsumer<String, Integer> listener) {
        this.onUserOfflineListener = listener;
    }

    /**
     * Stops the server and releases all socket and thread resources.
     */
    public synchronized void stop() {
        if (!isRunning) return;
        isRunning = false;

        System.out.println("[ChatServer] Stopping TCP Chat Server...");

        // Disconnect all connected clients
        for (ClientHandler client : connectedClients) {
            client.closeConnection();
        }
        connectedClients.clear();

        // Close server socket
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {}

        // Shutdown thread pool
        if (threadPool != null && !threadPool.isShutdown()) {
            threadPool.shutdownNow();
        }

        System.out.println("[ChatServer] TCP Chat Server stopped.");
    }

    public List<ClientHandler> getConnectedClients() {
        return connectedClients;
    }

    public boolean isRunning() {
        return isRunning;
    }

    /**
     * Standalone main method to test ChatServer independently.
     */
    public static void main(String[] args) {
        ChatServer server = new ChatServer();
        // Add shutdown hook to handle Ctrl+C cleanly
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        server.start();
    }
}

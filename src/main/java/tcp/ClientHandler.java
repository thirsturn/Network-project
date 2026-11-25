package tcp;

import common.Message;
import common.MessageStore;
import common.SessionStore;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Handles communication with a single connected TCP client.
 * Runs on a worker thread provided by ChatServer's ExecutorService thread pool.
 */
public class ClientHandler implements Runnable {

    private final Socket socket;
    private final ChatServer server;
    private BufferedReader in;
    private PrintWriter out;
    private String username;
    private int udpPort = -1;
    private volatile boolean running = true;

    public ClientHandler(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            // Setup character streams with UTF-8 encoding
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

            // Step 1: Authentication Handshake
            // Expected protocol format: AUTH|<token>|<udpPort>
            String authLine = in.readLine();
            if (authLine == null || !authLine.startsWith("AUTH|")) {
                sendMessage("ERROR|Expected authentication: AUTH|<token>|<udpPort>");
                closeConnection();
                return;
            }

            String[] authParts = authLine.split("\\|", 3);
            if (authParts.length < 2) {
                sendMessage("ERROR|Malformed AUTH packet");
                closeConnection();
                return;
            }

            String token = authParts[1];
            if (authParts.length >= 3) {
                try {
                    this.udpPort = Integer.parseInt(authParts[2]);
                } catch (NumberFormatException e) {
                    this.udpPort = -1;
                }
            }

            // Validate token against shared SessionStore
            String authenticatedUser = SessionStore.getInstance().getUsername(token);

            // Developer fallback for standalone testing if SessionStore has no token
            if (authenticatedUser == null && token.startsWith("DEV_")) {
                authenticatedUser = token.substring(4);
            }

            if (authenticatedUser == null || authenticatedUser.trim().isEmpty()) {
                sendMessage("ERROR|Invalid or expired session token");
                System.out.println("[ChatServer] Rejected unauthenticated connection from " + socket.getRemoteSocketAddress());
                closeConnection();
                return;
            }

            this.username = authenticatedUser;
            server.registerClient(this);
            sendMessage("OK|Authenticated as " + username);

            System.out.println("[ChatServer] User '" + username + "' connected from " + 
                    socket.getRemoteSocketAddress() + (udpPort > 0 ? " (UDP Port: " + udpPort + ")" : ""));

            // Broadcast join notification to other chat clients
            server.broadcast("SYS|" + username + " joined the chat", this);

            // Step 2: Message Processing Loop
            String line;
            while (running && (line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                if (line.equalsIgnoreCase("QUIT")) {
                    break;
                } else if (line.startsWith("MSG|")) {
                    String chatText = line.substring(4);
                    // Store message in shared MessageStore for HTTP REST History API
                    Message message = new Message(username, chatText);
                    MessageStore.getInstance().addMessage(message);

                    // Broadcast formatted message to all active clients: MSG|<sender>|<text>
                    server.broadcast("MSG|" + username + "|" + chatText);
                    System.out.println("[ChatServer] [" + username + "]: " + chatText);
                } else {
                    sendMessage("ERROR|Unknown command format. Use MSG|<text> or QUIT");
                }
            }

        } catch (IOException e) {
            if (running) {
                System.out.println("[ChatServer] Connection issue with user '" + username + "': " + e.getMessage());
            }
        } finally {
            closeConnection();
        }
    }

    /**
     * Sends a raw line to this connected client.
     */
    public synchronized void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }

    /**
     * Closes socket and resources and unregisters from the server.
     */
    public void closeConnection() {
        running = false;
        server.unregisterClient(this);

        if (username != null) {
            System.out.println("[ChatServer] User '" + username + "' disconnected.");
            server.broadcast("SYS|" + username + " left the chat", this);
        }

        try {
            if (in != null) in.close();
        } catch (IOException ignored) {}

        if (out != null) out.close();

        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {}
    }

    public String getUsername() {
        return username;
    }

    public int getUdpPort() {
        return udpPort;
    }

    public Socket getSocket() {
        return socket;
    }
}

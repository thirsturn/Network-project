package tcp;

import common.Config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.function.Consumer;

/**
 * TCP Chat Client implementation.
 * Connects to the ChatServer, authenticates with a session token,
 * runs a dedicated reader thread for incoming messages, and provides methods to send chat messages.
 * 
 * Concept: TCP Sockets + Client Multithreading (Socket, Background Reader Thread)
 * Member: Thiranjaya (Member 1)
 */
public class ChatClient {

    private final String host;
    private final int port;
    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private Thread listenerThread;
    private volatile boolean isConnected = false;
    private String username;

    // Optional listener to receive parsed incoming messages in ConsoleClient UI
    private Consumer<String> onMessageReceived;
    private Consumer<String> onSystemNotification;
    private Runnable onDisconnected;

    public ChatClient() {
        this(Config.DEFAULT_HOST, Config.CHAT_TCP_PORT);
    }

    public ChatClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * Connects to the TCP Chat Server and authenticates using a session token.
     * 
     * @param token   session token obtained from TLS Auth Server (or DEV_<user>)
     * @param udpPort local UDP port where this client listens for presence notifications (or -1)
     * @return true if connected and authenticated successfully, false otherwise
     */
    public boolean connect(String token, int udpPort) {
        if (isConnected) {
            System.out.println("[ChatClient] Already connected.");
            return true;
        }

        try {
            // Establish TCP socket connection
            socket = new Socket(host, port);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

            // Send authentication packet: AUTH|<token>|<udpPort>
            writer.println("AUTH|" + token + "|" + udpPort);

            // Read authentication response from server
            String response = reader.readLine();
            if (response == null || !response.startsWith("OK|")) {
                String errorMsg = (response != null) ? response : "Connection closed by server.";
                System.err.println("[ChatClient] Authentication failed: " + errorMsg);
                disconnect();
                return false;
            }

            // Extract username from OK response (e.g., OK|Authenticated as <username>)
            String authDetails = response.substring(3);
            if (authDetails.startsWith("Authenticated as ")) {
                this.username = authDetails.substring("Authenticated as ".length()).trim();
            } else {
                this.username = authDetails.trim();
            }

            isConnected = true;
            System.out.println("[ChatClient] " + response);

            // Start dedicated TCP Reader thread to receive messages concurrently
            startMessageListener();
            return true;

        } catch (IOException e) {
            System.err.println("[ChatClient] Connection error: " + e.getMessage());
            disconnect();
            return false;
        }
    }

    /**
     * Dedicated background thread that continuously reads broadcast messages from the server.
     */
    private void startMessageListener() {
        listenerThread = new Thread(() -> {
            try {
                String line;
                while (isConnected && (line = reader.readLine()) != null) {
                    handleIncomingPacket(line);
                }
            } catch (IOException e) {
                if (isConnected) {
                    System.out.println("[ChatClient] Server connection lost.");
                }
            } finally {
                disconnect();
                if (onDisconnected != null) {
                    onDisconnected.run();
                }
            }
        }, "TCP-Message-Listener");

        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    /**
     * Parses and routes packets received from the server.
     */
    private void handleIncomingPacket(String line) {
        if (line.startsWith("MSG|")) {
            // Format: MSG|<sender>|<text>
            String[] parts = line.split("\\|", 3);
            if (parts.length >= 3) {
                String sender = parts[1];
                String text = parts[2];
                String display = "[" + sender + "]: " + text;
                if (onMessageReceived != null) {
                    onMessageReceived.accept(display);
                } else {
                    System.out.println(display);
                }
            }
        } else if (line.startsWith("SYS|")) {
            // Format: SYS|<text>
            String text = line.substring(4);
            String display = "*** " + text + " ***";
            if (onSystemNotification != null) {
                onSystemNotification.accept(display);
            } else {
                System.out.println(display);
            }
        } else if (line.startsWith("ERROR|")) {
            String errorMsg = line.substring(6);
            System.err.println("[Server Error] " + errorMsg);
        } else {
            System.out.println(line);
        }
    }

    /**
     * Sends a chat message to the server.
     * 
     * @param text the message body
     */
    public boolean sendMessage(String text) {
        if (!isConnected || writer == null) {
            System.err.println("[ChatClient] Cannot send message: Not connected to chat server.");
            return false;
        }

        if (text == null || text.trim().isEmpty()) {
            return false;
        }

        writer.println("MSG|" + text.trim());
        return true;
    }

    /**
     * Gracefully disconnects from the chat server.
     */
    public synchronized void disconnect() {
        if (!isConnected && socket == null) {
            return;
        }

        isConnected = false;

        // Inform the server about quitting
        if (writer != null) {
            try {
                writer.println("QUIT");
            } catch (Exception ignored) {}
        }

        // Close streams and socket
        try {
            if (reader != null) reader.close();
        } catch (IOException ignored) {}

        if (writer != null) writer.close();

        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {}

        socket = null;
        reader = null;
        writer = null;
    }

    public boolean isConnected() {
        return isConnected;
    }

    public String getUsername() {
        return username;
    }

    public void setOnMessageReceived(Consumer<String> onMessageReceived) {
        this.onMessageReceived = onMessageReceived;
    }

    public void setOnSystemNotification(Consumer<String> onSystemNotification) {
        this.onSystemNotification = onSystemNotification;
    }

    public void setOnDisconnected(Runnable onDisconnected) {
        this.onDisconnected = onDisconnected;
    }

    /**
     * Standalone main method to test ChatClient from terminal.
     */
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("==================================================");
        System.out.println("            Campus Chat Client (Standalone)       ");
        System.out.println("==================================================");

        System.out.print("Enter your username: ");
        String user = scanner.nextLine().trim();
        if (user.isEmpty()) {
            user = "User" + (int)(Math.random() * 1000);
        }

        ChatClient client = new ChatClient();
        // Uses DEV_ token prefix for instant standalone testing without TLS
        System.out.println("Connecting to chat server at " + Config.DEFAULT_HOST + ":" + Config.CHAT_TCP_PORT + "...");
        boolean ok = client.connect("DEV_" + user, 5001);

        if (!ok) {
            System.err.println("Could not connect. Ensure ChatServer is running!");
            return;
        }

        System.out.println("Type your messages below. Type '/quit' to exit.");
        System.out.println("--------------------------------------------------");

        while (client.isConnected()) {
            if (scanner.hasNextLine()) {
                String input = scanner.nextLine();
                if (input.equalsIgnoreCase("/quit") || input.equalsIgnoreCase("QUIT")) {
                    client.disconnect();
                    break;
                }
                client.sendMessage(input);
            }
        }

        System.out.println("Disconnected. Goodbye!");
    }
}

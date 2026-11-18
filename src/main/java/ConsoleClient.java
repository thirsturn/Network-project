import common.Config;
import common.Message;
import common.MessageStore;
import tcp.ChatClient;

import java.util.List;
import java.util.Scanner;

/**
 * Campus Collaboration Hub - Unified Console Client.
 * 
 * Demonstrates the client-side integration of networking protocols:
 * 1. TLS Login             (/login <user> <pass>) -> Member 4 (Kaveen)
 * 2. TCP Real-Time Chat    (send/receive chat)    -> Member 1 (Thiranjaya)
 * 3. UDP Presence Listener (online notifications) -> Member 2 (Asanka)
 * 4. HTTP REST History     (/history)             -> Member 3 (Pasindu)
 * 5. Multicast Listener    (announcements)        -> Member 5 (Vihanga)
 * 
 * Maintained primarily by Member 1 (Thiranjaya) during integration.
 */
public class ConsoleClient {

    private final ChatClient chatClient;
    private final Scanner scanner;
    private boolean isRunning = true;
    private String currentUsername = null;
    private final int localUdpPort = 5001; // Default UDP port for presence listener

    public ConsoleClient() {
        this.chatClient = new ChatClient(Config.DEFAULT_HOST, Config.CHAT_TCP_PORT);
        this.scanner = new Scanner(System.in);

        // Configure callbacks for incoming messages
        chatClient.setOnMessageReceived(message -> {
            System.out.println(message);
            printPrompt();
        });

        chatClient.setOnSystemNotification(notification -> {
            System.out.println(notification);
            printPrompt();
        });

        chatClient.setOnDisconnected(() -> {
            System.out.println("\n[ConsoleClient] Disconnected from Chat Server.");
            currentUsername = null;
            printPrompt();
        });
    }

    public void start() {
        printBanner();

        while (isRunning) {
            printPrompt();
            if (!scanner.hasNextLine()) {
                break;
            }

            String input = scanner.nextLine().trim();
            if (input.isEmpty()) {
                continue;
            }

            handleUserInput(input);
        }

        cleanup();
    }

    private void handleUserInput(String input) {
        if (input.startsWith("/")) {
            String[] parts = input.split("\\s+", 3);
            String command = parts[0].toLowerCase();

            switch (command) {
                case "/login":
                    handleLoginCommand(parts);
                    break;

                case "/history":
                    handleHistoryCommand();
                    break;

                case "/quit":
                case "/exit":
                    handleQuitCommand();
                    break;

                case "/help":
                    printHelp();
                    break;

                default:
                    System.out.println("[ConsoleClient] Unknown command: " + command + ". Type /help for available commands.");
                    break;
            }
        } else {
            // Regular chat message to be sent over TCP
            if (!chatClient.isConnected()) {
                System.out.println("[ConsoleClient] You are not logged in. Please log in first: /login <username> <password>");
            } else {
                chatClient.sendMessage(input);
            }
        }
    }

    /**
     * Handles /login <username> <password>
     * Connects to TLS Auth Server (or uses developer fallback) and authenticates TCP chat connection.
     */
    private void handleLoginCommand(String[] parts) {
        if (parts.length < 2) {
            System.out.println("[ConsoleClient] Usage: /login <username> [password]");
            return;
        }

        String username = parts[1].trim();
        String password = (parts.length >= 3) ? parts[2].trim() : "defaultPass";

        if (chatClient.isConnected()) {
            System.out.println("[ConsoleClient] Already connected as '" + currentUsername + "'. Type /quit first to switch accounts.");
            return;
        }

        System.out.println("[ConsoleClient] Authenticating as '" + username + "'...");

        // Session token:
        // When Member 4 (TLS Auth) is fully integrated:
        // token = tls.AuthClient.login(username, password);
        // Fallback for standalone/development testing:
        String token = "DEV_" + username;

        boolean success = chatClient.connect(token, localUdpPort);
        if (success) {
            this.currentUsername = username;
            System.out.println("[ConsoleClient] Login successful! You can now type messages to chat.");
        } else {
            System.err.println("[ConsoleClient] Login failed. Make sure the Server is running (java -cp out Main).");
        }
    }

    /**
     * Handles /history
     * Retrieves chat history (via HTTP REST API or shared store).
     */
    private void handleHistoryCommand() {
        System.out.println("\n--- [Message History] ---");
        List<Message> messages = MessageStore.getInstance().getMessages();
        if (messages.isEmpty()) {
            System.out.println("(No messages recorded yet)");
        } else {
            for (Message msg : messages) {
                System.out.println(msg.toString());
            }
        }
        System.out.println("-------------------------\n");
    }

    private void handleQuitCommand() {
        System.out.println("[ConsoleClient] Quitting application...");
        if (chatClient.isConnected()) {
            chatClient.disconnect();
        }
        isRunning = false;
    }

    private void printPrompt() {
        if (chatClient.isConnected()) {
            System.out.print("[" + currentUsername + "] > ");
        } else {
            System.out.print("[Guest] > ");
        }
    }

    private void printBanner() {
        System.out.println("==================================================");
        System.out.println("       Campus Collaboration Hub - Console Client  ");
        System.out.println("==================================================");
        printHelp();
        System.out.println("==================================================");
    }

    private void printHelp() {
        System.out.println("Commands:");
        System.out.println("  /login <user> <pass>  : Log in and connect to chat");
        System.out.println("  /history              : View message history");
        System.out.println("  /quit                 : Disconnect and exit");
        System.out.println("  /help                 : Show this help guide");
        System.out.println("  <text>                : Send as real-time chat message");
    }

    private void cleanup() {
        chatClient.disconnect();
        System.out.println("[ConsoleClient] Goodbye!");
    }

    public static void main(String[] args) {
        ConsoleClient client = new ConsoleClient();
        client.start();
    }
}

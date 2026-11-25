import common.Config;
import common.MessageStore;
import common.SessionStore;
import tcp.ChatServer;

/**
 * Server entry point for Campus Collaboration Hub.
 * Starts and orchestrates all server components across the five networking protocols:
 * 
 * 1. TLS Auth Server       - Port 5443 (Member 4 - Kaveen)
 * 2. TCP Chat Server       - Port 5000 (Member 1 - Thiranjaya)
 * 3. UDP Status Notifier   - Port 5001 (Member 2 - Asanka)
 * 4. HTTP REST API Server  - Port 8080 (Member 3 - Pasindu)
 * 5. Multicast Announcer   - Port 5002 (Member 5 - Vihanga)
 * 
 * Maintained primarily by Member 1 (Thiranjaya) during integration.
 */
public class Main {

    private static ChatServer chatServer;

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("      Campus Collaboration Hub - Server Host      ");
        System.out.println("==================================================");
        System.out.println("Configuration:");
        System.out.println(" - Auth TLS Port        : " + Config.AUTH_TLS_PORT);
        System.out.println(" - Chat TCP Port        : " + Config.CHAT_TCP_PORT);
        System.out.println(" - Status UDP Port      : " + Config.STATUS_UDP_PORT);
        System.out.println(" - REST HTTP Port       : " + Config.REST_HTTP_PORT);
        System.out.println(" - Multicast Port       : " + Config.ANNOUNCEMENT_MULTICAST_PORT);
        System.out.println(" - Multicast Address    : " + Config.MULTICAST_GROUP_ADDRESS);
        System.out.println("--------------------------------------------------");

        // Initialize shared in-memory stores
        SessionStore sessionStore = SessionStore.getInstance();
        MessageStore messageStore = MessageStore.getInstance();
        System.out.println("[Main] Shared SessionStore and MessageStore initialized.");

        // -----------------------------------------------------------------
        // Component 2: TCP Chat Server (Member 1 - Thiranjaya)
        // -----------------------------------------------------------------
        chatServer = new ChatServer(Config.CHAT_TCP_PORT);
        Thread chatServerThread = new Thread(chatServer, "TCP-ChatServer-Thread");
        chatServerThread.start();
        System.out.println("[Main] TCP Chat Server thread launched on port " + Config.CHAT_TCP_PORT);

        // -----------------------------------------------------------------
        // Integration Placeholders for other members:
        // Member 4 (TLS): tls.AuthServer.start();
        // Member 2 (UDP): udp.StatusNotifier.initialize();
        // Member 3 (HTTP): http.HistoryServer.start();
        // Member 5 (Multicast): Announcement integration
        // -----------------------------------------------------------------

        // Graceful shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[Main] Shutdown signal received. Stopping servers...");
            if (chatServer != null) {
                chatServer.stop();
            }
            System.out.println("[Main] All servers stopped cleanly.");
        }));

        System.out.println("==================================================");
        System.out.println("[Main] Server host running. Press Ctrl+C to terminate.");
        System.out.println("==================================================");

        try {
            // Keep the main thread alive waiting on the chat server
            chatServerThread.join();
        } catch (InterruptedException e) {
            System.out.println("[Main] Server host interrupted.");
        }
    }

    public static ChatServer getChatServer() {
        return chatServer;
    }
}

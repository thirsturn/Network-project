import common.Config;
import common.MessageStore;
import common.SessionStore;

/**
 * Server entry point for Campus Collaboration Hub.
 * Starts all server components (TLS Auth, TCP Chat, UDP Notifier, HTTP REST API, Multicast).
 * Maintained primarily by Member 1 during integration.
 */
public class Main {

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("   Campus Collaboration Hub - Server Starting     ");
        System.out.println("==================================================");
        System.out.println("Loaded Configuration:");
        System.out.println(" - Auth TLS Port        : " + Config.AUTH_TLS_PORT);
        System.out.println(" - Chat TCP Port        : " + Config.CHAT_TCP_PORT);
        System.out.println(" - Status UDP Port      : " + Config.STATUS_UDP_PORT);
        System.out.println(" - REST HTTP Port       : " + Config.REST_HTTP_PORT);
        System.out.println(" - Multicast Port       : " + Config.ANNOUNCEMENT_MULTICAST_PORT);
        System.out.println(" - Multicast Address    : " + Config.MULTICAST_GROUP_ADDRESS);
        System.out.println("--------------------------------------------------");

        // Shared stores
        SessionStore sessionStore = SessionStore.getInstance();
        MessageStore messageStore = MessageStore.getInstance();

        // TODO: Start Server Components as each member completes their module:
        // 1. Member 4: tls.AuthServer (Port 5443)
        // 2. Member 1: tcp.ChatServer (Port 5000)
        // 3. Member 2: udp.StatusNotifier (Port 5001)
        // 4. Member 3: http.HistoryServer (Port 8080)
        // 5. Member 5: multicast.AnnouncementSender (Port 5002)

        System.out.println("Base stores initialized. Ready for component integration.");
        System.out.println("==================================================");
    }
}

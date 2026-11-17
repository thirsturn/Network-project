import common.Config;

import java.util.Scanner;

/**
 * Client entry point for Campus Collaboration Hub.
 * Console application that combines TLS Auth, TCP Chat, UDP Presence, and Multicast announcements.
 * Maintained primarily by Member 1 during integration.
 */
public class ConsoleClient {

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("   Campus Collaboration Hub - Console Client      ");
        System.out.println("==================================================");
        System.out.println("Available commands:");
        System.out.println("  /login <user> <password> : Authenticate via TLS");
        System.out.println("  /history                 : View chat history via REST API");
        System.out.println("  /quit                    : Disconnect and exit");
        System.out.println("  <any other text>         : Send as chat message");
        System.out.println("--------------------------------------------------");

        try (Scanner scanner = new Scanner(System.in)) {
            System.out.print("Client started. Type a command or message: ");
            if (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                System.out.println("Echo: " + line);
            }
        }
    }
}

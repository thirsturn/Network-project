package http;

import common.MessageStore;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

/**
 * Manual demonstration fixture for Member 3.
 *
 * <p>Seeds the real {@link MessageStore} with two sample messages and starts
 * the real {@link HistoryServer} on {@link common.Config#REST_HTTP_PORT}
 * (8080). Member 1's TCP ChatServer does not exist on this branch yet, so
 * these messages simulate what the chat server will later write.</p>
 *
 * <p><strong>This is not a replacement for end-to-end TCP chat testing.</strong>
 * It exists so the HTTP endpoint, curl commands and Wireshark capture can be
 * demonstrated before the TCP module is merged.</p>
 */
public final class HistoryDemoServer {

    private HistoryDemoServer() {
    }

    public static void main(String[] args) {
        MessageStore store = MessageStore.getInstance();
        store.clear();
        store.addMessage("alice", "Hello everyone");
        store.addMessage("bob", "Hi Alice");

        HistoryServer server = new HistoryServer();
        try {
            server.start();
        } catch (IOException e) {
            System.err.println("[HTTP] " + e.getMessage());
            System.exit(1);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        System.out.println("[HTTP] Seeded 2 demo messages into the real MessageStore.");
        System.out.println("[HTTP] Press Ctrl+C to stop.");

        try {
            new CountDownLatch(1).await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

package integration;

import common.Config;
import common.MessageStore;
import http.HistoryClient;
import http.HistoryServer;
import tcp.ChatClient;
import tcp.ChatServer;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * End-to-end integration test verifying the TCP-to-HTTP workflow:
 * 1. TCP Server starts on port 5000.
 * 2. HTTP HistoryServer starts on port 8080.
 * 3. Client connects over TCP and sends MSG|Hello from Alice.
 * 4. Verify message is stored in shared MessageStore.
 * 5. HistoryClient fetches GET /messages over HTTP (port 8080).
 * 6. Verify HTTP response contains Alice's TCP message.
 */
public class TcpToHttpIntegrationTest {

    public static void main(String[] args) throws Exception {
        System.out.println("==================================================");
        System.out.println("   Running End-to-End TCP-to-HTTP Integration Test");
        System.out.println("==================================================");

        // Clear store for clean test
        MessageStore.getInstance().clear();

        // 1. Start TCP Chat Server
        ChatServer chatServer = new ChatServer(Config.CHAT_TCP_PORT);
        Thread chatThread = new Thread(chatServer, "TCP-Server");
        chatThread.start();
        Thread.sleep(300);

        // 2. Start HTTP History Server
        HistoryServer historyServer = new HistoryServer(Config.REST_HTTP_PORT);
        historyServer.start();
        Thread.sleep(300);

        try {
            // 3. Connect TCP Client and send message
            ChatClient aliceClient = new ChatClient(Config.DEFAULT_HOST, Config.CHAT_TCP_PORT);
            CountDownLatch messageAckLatch = new CountDownLatch(1);

            aliceClient.setOnMessageReceived(msg -> {
                System.out.println("[Test TCP Client Received] " + msg);
                if (msg.contains("Hello from Alice over TCP!")) {
                    messageAckLatch.countDown();
                }
            });

            boolean connected = aliceClient.connect("DEV_Alice", 5001);
            if (!connected) {
                throw new RuntimeException("Failed to connect Alice over TCP");
            }
            System.out.println("[Test] Alice connected over TCP.");

            // Send message over TCP
            aliceClient.sendMessage("Hello from Alice over TCP!");

            boolean received = messageAckLatch.await(3, TimeUnit.SECONDS);
            if (!received) {
                System.err.println("[Test Warning] TCP broadcast latch timed out, checking MessageStore directly...");
            }
            Thread.sleep(200);

            // 4. Verify in MessageStore
            int storeCount = MessageStore.getInstance().getMessages().size();
            System.out.println("[Test] MessageStore message count: " + storeCount);
            if (storeCount == 0) {
                throw new AssertionError("FAIL: MessageStore did not receive the TCP message!");
            }
            System.out.println("PASS: TCP message successfully stored in MessageStore.");

            // 5. Query HTTP HistoryClient
            HistoryClient historyClient = new HistoryClient();
            String jsonHistory = historyClient.fetchHistory();
            System.out.println("[Test] HTTP History Response:\n" + jsonHistory);

            if (!jsonHistory.contains("\"sender\":\"Alice\"") || !jsonHistory.contains("Hello from Alice over TCP!")) {
                throw new AssertionError("FAIL: HTTP History does not contain Alice's TCP message!");
            }
            System.out.println("PASS: HTTP HistoryServer successfully returned Alice's TCP message!");

            aliceClient.disconnect();
            System.out.println("==================================================");
            System.out.println("   INTEGRATION TEST PASSED SUCCESSFULLY (100%)");
            System.out.println("==================================================");

        } finally {
            chatServer.stop();
            historyServer.stop();
        }
    }
}

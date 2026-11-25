package http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import common.Config;
import common.MessageStore;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * HTTP REST server for message history (Member 3).
 *
 * <p>Exposes one endpoint:</p>
 * <pre>
 *   GET /messages  -> 200 OK, JSON array of every message in MessageStore
 * </pre>
 *
 * <p>Unknown paths return 404. Methods other than GET on /messages return 405.
 * Unexpected failures while building the response return 500. Every response
 * body is JSON encoded as UTF-8.</p>
 */
public class HistoryServer {

    /** The only endpoint exposed by this server. */
    public static final String MESSAGES_PATH = "/messages";

    private static final String JSON_CONTENT_TYPE = "application/json; charset=UTF-8";
    private static final int THREAD_POOL_SIZE = 8;

    private final int configuredPort;

    private volatile HttpServer server;
    private volatile ExecutorService executor;

    /**
     * Creates a server that listens on {@link Config#REST_HTTP_PORT} (8080).
     */
    public HistoryServer() {
        this(Config.REST_HTTP_PORT);
    }

    /**
     * Creates a server on the given port. Used by tests; port 0 asks the
     * operating system to choose a free port.
     */
    public HistoryServer(int port) {
        this.configuredPort = port;
    }

    /**
     * Binds the configured port and starts accepting requests.
     *
     * @throws IOException if the port cannot be bound or the server cannot start
     */
    public synchronized void start() throws IOException {
        if (server != null) {
            throw new IllegalStateException("HistoryServer is already running");
        }

        HttpServer newServer;
        try {
            InetSocketAddress address = new InetSocketAddress(Config.DEFAULT_HOST, configuredPort);
            newServer = HttpServer.create(address, 0);
        } catch (IOException e) {
            throw new IOException(
                    "Failed to start History REST API on "
                            + Config.DEFAULT_HOST + ":" + configuredPort
                            + ": " + e.getMessage(), e);
        }

        ExecutorService newExecutor =
                Executors.newFixedThreadPool(THREAD_POOL_SIZE, newThreadFactory());
        newServer.setExecutor(newExecutor);

        // Longest-prefix routing: /messages is handled here, everything else by "/".
        newServer.createContext(MESSAGES_PATH, new MessagesHandler());
        newServer.createContext("/", new NotFoundHandler());

        newServer.start();

        this.server = newServer;
        this.executor = newExecutor;

        System.out.println("[HTTP] History REST API started: " + getBaseUrl() + MESSAGES_PATH);
    }

    /**
     * Stops accepting requests, closes the listening socket and releases the
     * worker threads. Safe to call even if the server was never started.
     */
    public synchronized void stop() {
        HttpServer currentServer = server;
        if (currentServer == null) {
            return;
        }

        ExecutorService currentExecutor = executor;
        server = null;
        executor = null;

        currentServer.stop(1);
        if (currentExecutor != null) {
            currentExecutor.shutdownNow();
        }
        System.out.println("[HTTP] History REST API stopped");
    }

    public boolean isRunning() {
        return server != null;
    }

    /**
     * Returns the actual bound port after {@link #start()}, otherwise the
     * configured port. Useful when tests bind port 0.
     */
    public int getPort() {
        HttpServer currentServer = server;
        return currentServer != null ? currentServer.getAddress().getPort() : configuredPort;
    }

    public String getBaseUrl() {
        return "http://" + Config.DEFAULT_HOST + ":" + getPort();
    }

    /**
     * Reads the shared message history and serializes it.
     *
     * <p>Kept as a protected method so a test can override it and simulate a
     * server-side failure, verifying the 500 response without modifying
     * {@code common.MessageStore}.</p>
     */
    protected String buildHistoryJson() {
        return MessageStore.getInstance().toJsonArray();
    }

    /**
     * Handles requests routed to the /messages prefix. HttpServer matches
     * contexts by prefix, so the exact path is checked explicitly to stop
     * paths such as /messagesfoo from being accepted.
     */
    private final class MessagesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String path = exchange.getRequestURI().getPath();
                if (!MESSAGES_PATH.equals(path)) {
                    sendError(exchange, 404, "Not Found", "Unknown endpoint: " + path);
                    return;
                }

                if (!"GET".equals(exchange.getRequestMethod())) {
                    exchange.getResponseHeaders().set("Allow", "GET");
                    sendError(exchange, 405, "Method Not Allowed",
                            exchange.getRequestMethod() + " is not supported for " + MESSAGES_PATH);
                    return;
                }

                try {
                    sendJson(exchange, 200, buildHistoryJson());
                } catch (RuntimeException e) {
                    System.err.println("[HTTP] Could not build message history: " + e.getMessage());
                    sendError(exchange, 500, "Internal Server Error",
                            "Could not build message history");
                }
            } finally {
                exchange.close();
            }
        }
    }

    /** Handles every path that is not exactly /messages. */
    private final class NotFoundHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                sendError(exchange, 404, "Not Found",
                        "Unknown endpoint: " + exchange.getRequestURI().getPath());
            } finally {
                exchange.close();
            }
        }
    }

    private static void sendJson(HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", JSON_CONTENT_TYPE);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");

        // sendResponseHeaders also sets Content-Length for us.
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static void sendError(HttpExchange exchange, int status,
                                  String error, String message) throws IOException {
        sendJson(exchange, status,
                "{\"status\":" + status
                        + ",\"error\":\"" + escapeJson(error) + "\""
                        + ",\"message\":\"" + escapeJson(message) + "\"}");
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static ThreadFactory newThreadFactory() {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable,
                    "http-history-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    /**
     * Standalone entry point so Member 3's module can be demonstrated without
     * Member 1's Main.java being modified yet.
     */
    public static void main(String[] args) {
        HistoryServer server = new HistoryServer();
        try {
            server.start();
        } catch (IOException e) {
            System.err.println("[HTTP] " + e.getMessage());
            System.exit(1);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        System.out.println("[HTTP] Press Ctrl+C to stop.");

        try {
            new CountDownLatch(1).await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

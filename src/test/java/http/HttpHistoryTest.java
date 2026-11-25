package http;

import common.Config;
import common.Message;
import common.MessageStore;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Plain-Java test harness for Member 3's HTTP/REST module.
 *
 * <p>The project has no build tool or test framework, so this class is run
 * with its {@code main} method and exits with status 1 if any check fails.</p>
 *
 * <p>Tests use the real {@link MessageStore}. Because Member 1's TCP chat
 * server is not present on this branch, messages are inserted directly into
 * the store by the test. This is isolated integration testing of the HTTP
 * layer, not full end-to-end TCP chat testing.</p>
 */
public final class HttpHistoryTest {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final String JSON_TYPE = "application/json; charset=UTF-8";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static final MessageStore STORE = MessageStore.getInstance();

    private static HistoryServer server;
    private static String baseUrl;
    private static int passed;
    private static int failed;

    private HttpHistoryTest() {
    }

    public static void main(String[] args) {
        try {
            testDefaultConfiguration();
            startTestServer();
            testEmptyHistory();
            testActualHistory();
            testUnknownEndpoint();
            testExactPathMatching();
            testMethodNotAllowed("POST");
            testMethodNotAllowed("PUT");
            testMethodNotAllowed("DELETE");
            testHistoryClient();
            testClientConnectionFailure();
            testConcurrentAccess();
            testStartupFailure();
            testInternalServerError();
        } catch (Exception e) {
            failed++;
            System.out.println("FAIL - test harness aborted: " + e);
            e.printStackTrace(System.out);
        } finally {
            stopTestServer();
        }

        System.out.println();
        System.out.println("==========================================");
        System.out.println("RESULT: " + passed + " passed, " + failed + " failed");
        System.out.println("==========================================");
        if (failed > 0) {
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------
    // Test 1: configuration and startup
    // ------------------------------------------------------------------

    private static void testDefaultConfiguration() {
        HistoryServer defaultServer = new HistoryServer();
        check("testDefaultConfiguration",
                Config.REST_HTTP_PORT == 8080 && defaultServer.getPort() == 8080,
                "Config.REST_HTTP_PORT=" + Config.REST_HTTP_PORT
                        + ", default server port=" + defaultServer.getPort());
    }

    private static void startTestServer() {
        server = new HistoryServer(0);
        try {
            server.start();
            baseUrl = "http://" + Config.DEFAULT_HOST + ":" + server.getPort();
            check("testServerStartup",
                    server.isRunning() && server.getPort() > 0,
                    "isRunning=" + server.isRunning() + ", port=" + server.getPort());
        } catch (IOException e) {
            check("testServerStartup", false, e.getMessage());
            throw new IllegalStateException("Cannot continue without a running server", e);
        }
    }

    // ------------------------------------------------------------------
    // Tests 2-4: response body, headers and status
    // ------------------------------------------------------------------

    private static void testEmptyHistory() throws IOException, InterruptedException {
        STORE.clear();

        HttpResponse<String> response = send("GET", "/messages");

        check("testEmptyHistory [status]", response.statusCode() == 200,
                "got " + response.statusCode());
        check("testEmptyHistory [empty JSON array]", "[]".equals(response.body()),
                "got " + response.body());
        checkContentType("testEmptyHistory [Content-Type]", response);
    }

    private static void testActualHistory() throws IOException, InterruptedException {
        STORE.clear();
        STORE.addMessage(new Message("alice", "Hello everyone"));
        STORE.addMessage(new Message("bob", "Hi Alice"));
        STORE.addMessage(new Message("carol",
                "Quote \" backslash \\ newline\nTab\tEnd"));

        HttpResponse<String> response = send("GET", "/messages");
        String json = response.body();

        check("testActualHistory [status]", response.statusCode() == 200,
                "got " + response.statusCode());
        check("testActualHistory [matches MessageStore]",
                json.equals(STORE.toJsonArray()),
                "expected " + STORE.toJsonArray() + " but got " + json);
        check("testActualHistory [sender field]",
                json.contains("\"sender\":\"alice\""), "body=" + json);
        check("testActualHistory [text field]",
                json.contains("\"text\":\"Hello everyone\""), "body=" + json);
        check("testActualHistory [timestamp field]",
                json.contains("\"timestamp\":\""), "body=" + json);
        check("testActualHistory [JSON escaping]",
                json.contains("Quote \\\" backslash \\\\ newline\\nTab\\tEnd"),
                "body=" + json);
        checkContentType("testActualHistory [Content-Type]", response);
    }

    private static void testUnknownEndpoint() throws IOException, InterruptedException {
        HttpResponse<String> response = send("GET", "/unknown");

        check("testUnknownEndpoint [status]", response.statusCode() == 404,
                "got " + response.statusCode());
        check("testUnknownEndpoint [JSON error]",
                response.body().contains("\"status\":404")
                        && response.body().contains("\"error\":\"Not Found\""),
                "body=" + response.body());
        checkContentType("testUnknownEndpoint [Content-Type]", response);
    }

    private static void testExactPathMatching() throws IOException, InterruptedException {
        HttpResponse<String> nested = send("GET", "/messages/extra");
        check("testExactPathMatching [/messages/extra]",
                nested.statusCode() == 404, "got " + nested.statusCode());

        HttpResponse<String> suffixed = send("GET", "/messagesfoo");
        check("testExactPathMatching [/messagesfoo]",
                suffixed.statusCode() == 404, "got " + suffixed.statusCode());
    }

    // ------------------------------------------------------------------
    // Test 5: unsupported methods
    // ------------------------------------------------------------------

    private static void testMethodNotAllowed(String method)
            throws IOException, InterruptedException {
        STORE.clear();

        HttpResponse<String> response = send(method, "/messages");
        String allow = response.headers().firstValue("allow").orElse("<missing>");

        check("testMethodNotAllowed [" + method + " status]",
                response.statusCode() == 405, "got " + response.statusCode());
        check("testMethodNotAllowed [" + method + " Allow header]",
                allow.contains("GET"), "got " + allow);
        check("testMethodNotAllowed [" + method + " JSON error]",
                response.body().contains("\"status\":405"),
                "body=" + response.body());
        check("testMethodNotAllowed [" + method + " no side effect]",
                STORE.getMessages().isEmpty(),
                "store size=" + STORE.getMessages().size());
    }

    // ------------------------------------------------------------------
    // Tests 6-7: Java HttpClient
    // ------------------------------------------------------------------

    private static void testHistoryClient() throws IOException, InterruptedException {
        STORE.clear();
        STORE.addMessage("alice", "Hello everyone");
        STORE.addMessage("bob", "Hi Alice");

        HistoryClient client = new HistoryClient(baseUrl + "/messages");
        String body = client.fetchHistory();

        check("testHistoryClient", body.equals(STORE.toJsonArray()),
                "expected " + STORE.toJsonArray() + " but got " + body);
    }

    private static void testClientConnectionFailure() throws IOException {
        HistoryServer doomed = new HistoryServer(0);
        doomed.start();
        int deadPort = doomed.getPort();
        doomed.stop();

        HistoryClient client = new HistoryClient(
                "http://" + Config.DEFAULT_HOST + ":" + deadPort + "/messages");

        try {
            String body = client.fetchHistory();
            check("testClientConnectionFailure", false,
                    "expected an IOException but received: " + body);
        } catch (IOException e) {
            check("testClientConnectionFailure", true, "");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            check("testClientConnectionFailure", false, "interrupted");
        }
    }

    // ------------------------------------------------------------------
    // Test 8: concurrent readers while the store is being written
    // ------------------------------------------------------------------

    private static void testConcurrentAccess() throws Exception {
        STORE.clear();

        final int readers = 8;
        final int requestsPerReader = 25;
        final int messagesToAdd = 200;

        ExecutorService pool = Executors.newFixedThreadPool(readers + 1);
        CountDownLatch gate = new CountDownLatch(1);
        List<Future<String>> readerTasks = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < readers; i++) {
            readerTasks.add(pool.submit(() -> {
                gate.await();
                for (int j = 0; j < requestsPerReader; j++) {
                    try {
                        HttpResponse<String> response = send("GET", "/messages");
                        if (response.statusCode() != 200) {
                            return "HTTP " + response.statusCode();
                        }
                        String body = response.body();
                        if (!body.startsWith("[") || !body.endsWith("]")) {
                            return "malformed JSON array: " + body;
                        }
                    } catch (IOException | InterruptedException e) {
                        return e.toString();
                    }
                }
                return null;
            }));
        }

        Future<?> writerTask = pool.submit(() -> {
            gate.await();
            for (int i = 0; i < messagesToAdd; i++) {
                STORE.addMessage("writer", "concurrent-" + i);
            }
            return null;
        });

        gate.countDown();

        try {
            writerTask.get(30, TimeUnit.SECONDS);
            for (Future<String> task : readerTasks) {
                String error = task.get(30, TimeUnit.SECONDS);
                if (error != null) {
                    errors.add(error);
                }
            }
        } finally {
            pool.shutdownNow();
        }

        check("testConcurrentAccess [no request errors]",
                errors.isEmpty(), "errors=" + errors);
        check("testConcurrentAccess [all messages stored]",
                STORE.getMessages().size() == messagesToAdd,
                "store size=" + STORE.getMessages().size());

        HttpResponse<String> finalResponse = send("GET", "/messages");
        check("testConcurrentAccess [final response consistent]",
                finalResponse.statusCode() == 200
                        && finalResponse.body().equals(STORE.toJsonArray()),
                "status=" + finalResponse.statusCode()
                        + ", bodyLength=" + finalResponse.body().length());
    }

    // ------------------------------------------------------------------
    // Test 9: startup failure and 500 handling
    // ------------------------------------------------------------------

    private static void testStartupFailure() throws IOException {
        HistoryServer duplicate = new HistoryServer(server.getPort());
        try {
            duplicate.start();
            check("testStartupFailure", false,
                    "second server unexpectedly bound port " + server.getPort());
            duplicate.stop();
        } catch (IOException e) {
            check("testStartupFailure",
                    e.getMessage() != null
                            && e.getMessage().contains("Failed to start History REST API"),
                    "message=" + e.getMessage());
        }
    }

    private static void testInternalServerError() throws IOException, InterruptedException {
        HistoryServer faulty = new HistoryServer(0) {
            @Override
            protected String buildHistoryJson() {
                throw new IllegalStateException("simulated failure");
            }
        };

        try {
            faulty.start();
            String url = "http://" + Config.DEFAULT_HOST + ":" + faulty.getPort() + "/messages";
            HttpResponse<String> response = HTTP.send(
                    HttpRequest.newBuilder(URI.create(url))
                            .timeout(REQUEST_TIMEOUT)
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            check("testInternalServerError [status]",
                    response.statusCode() == 500, "got " + response.statusCode());
            check("testInternalServerError [JSON error]",
                    response.body().contains("\"status\":500")
                            && response.body().contains("Internal Server Error"),
                    "body=" + response.body());
            checkContentType("testInternalServerError [Content-Type]", response);
        } finally {
            faulty.stop();
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static HttpResponse<String> send(String method, String path)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json");

        if ("GET".equals(method)) {
            builder.GET();
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }

        return HTTP.send(builder.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static void checkContentType(String name, HttpResponse<String> response) {
        String actual = response.headers().firstValue("content-type").orElse("<missing>");
        check(name, JSON_TYPE.equals(actual), "got " + actual);
    }

    private static void check(String name, boolean condition, String details) {
        if (condition) {
            passed++;
            System.out.println("PASS - " + name);
        } else {
            failed++;
            System.out.println("FAIL - " + name + " :: " + details);
        }
    }

    private static void stopTestServer() {
        if (server != null) {
            server.stop();
            server = null;
        }
    }
}

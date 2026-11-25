package http;

import common.Config;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Java HTTP client for the message history REST API (Member 3).
 *
 * <p>Uses the JDK's {@link HttpClient} to send {@code GET /messages} and
 * returns the raw JSON response. The class is kept independent of
 * ConsoleClient so Member 1 can call {@link #printHistory()} from the
 * {@code /history} command during integration.</p>
 */
public class HistoryClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;
    private final URI messagesUri;

    /**
     * Creates a client for {@code http://localhost:8080/messages}.
     */
    public HistoryClient() {
        this(defaultMessagesUrl());
    }

    /**
     * Creates a client for an explicit URL. Used by tests and by other
     * components that need a non-default host or port.
     */
    public HistoryClient(String messagesUrl) {
        this.messagesUri = URI.create(messagesUrl);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    public static String defaultMessagesUrl() {
        return "http://" + Config.DEFAULT_HOST + ":" + Config.REST_HTTP_PORT + "/messages";
    }

    /**
     * Sends GET /messages and returns the JSON body.
     *
     * @return the response body exactly as sent by the server
     * @throws IOException          if the server cannot be reached, times out,
     *                              or returns a status other than 200
     * @throws InterruptedException if the calling thread is interrupted
     */
    public String fetchHistory() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(messagesUri)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() != 200) {
            throw new IOException("History API returned HTTP " + response.statusCode()
                    + " (" + response.body() + ")");
        }
        return response.body();
    }

    /**
     * Fetches and prints the history for console use. All expected network
     * failures are reported as short, user-friendly messages.
     */
    public void printHistory() {
        System.out.println("---- Message History ----");
        System.out.println("GET " + messagesUri);
        try {
            System.out.println(fetchHistory());
        } catch (ConnectException e) {
            System.err.println("[HTTP] Could not connect to " + messagesUri
                    + ". Is HistoryServer running?");
        } catch (HttpTimeoutException e) {
            System.err.println("[HTTP] The history request timed out.");
        } catch (IOException e) {
            System.err.println("[HTTP] " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[HTTP] History request interrupted.");
        }
    }

    /** Standalone entry point for testing the client on its own. */
    public static void main(String[] args) {
        new HistoryClient().printHistory();
    }
}

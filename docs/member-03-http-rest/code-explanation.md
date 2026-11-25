# Detailed Code Explanation — HistoryServer and HistoryClient

This document explains the actual implementation line by line at the method level. It is written for a third-year IT undergraduate preparing for a viva.

Files:

- `src/main/java/http/HistoryServer.java`
- `src/main/java/http/HistoryClient.java`

Supporting shared classes (not modified):

- `common/Config.java`
- `common/Message.java`
- `common/MessageStore.java`

---

## 1. One request end-to-end

```text
curl / HistoryClient
        |
        | TCP connect to localhost:8080
        v
HistoryServer dispatcher thread
        |
        | accepts connection, parses HTTP request
        v
HttpContext "/messages" selected
        |
        | executor thread from fixed pool
        v
MessagesHandler.handle(exchange)
        |
        | method == GET? path exactly "/messages"?
        v
buildHistoryJson()
        |
        | MessageStore.getInstance().toJsonArray()
        v
sendJson(200, json)
        |
        | Content-Type, Content-Length, UTF-8 bytes
        v
client receives HTTP/1.1 200 OK + JSON array
```

The same path applies to curl/browser and to `HistoryClient`. The only difference is who builds the HTTP request.

---

## 2. HistoryServer.java

### 2.1 Package and imports

```java
package http;
```

The class belongs to the `http` package, as required by the team structure. It is compiled with the rest of the project; no module path or external library is needed.

Important imports:

| Import | Why |
|---|---|
| `com.sun.net.httpserver.HttpServer` | The JDK's built-in HTTP server |
| `com.sun.net.httpserver.HttpExchange` | One request/response exchange |
| `com.sun.net.httpserver.HttpHandler` | Functional interface for handlers |
| `common.Config` | Port and host constants |
| `common.MessageStore` | Shared message history |
| `java.net.InetSocketAddress` | Host + port binding |
| `java.nio.charset.StandardCharsets` | UTF-8 encoding |
| `java.util.concurrent.*` | Executor, thread factory, latch |
| `java.util.concurrent.atomic.AtomicInteger` | Thread numbering |

`com.sun.net.httpserver` is in the `jdk.httpserver` module (confirmed with Java 17's module list). It is included with the JDK; it is not an external dependency.

### 2.2 Constants and fields

```java
public static final String MESSAGES_PATH = "/messages";
private static final String JSON_CONTENT_TYPE =
        "application/json; charset=UTF-8";
private static final int THREAD_POOL_SIZE = 8;

private final int configuredPort;
private volatile HttpServer server;
private volatile ExecutorService executor;
```

- `MESSAGES_PATH` is a single source of truth for the endpoint path. It is also used by tests and documentation.
- `JSON_CONTENT_TYPE` includes the charset because HTTP does not assume UTF-8 unless declared.
- `THREAD_POOL_SIZE = 8` bounds concurrency. Small enough for a demo, large enough for the concurrent test.
- `configuredPort` is final: the object's target port never changes.
- `server` and `executor` are `volatile` so `getPort()` and `isRunning()` see the latest values after `start()`/`stop()`. `start()` and `stop()` are also `synchronized`.

### 2.3 Constructors

```java
public HistoryServer() {
    this(Config.REST_HTTP_PORT);
}

public HistoryServer(int port) {
    this.configuredPort = port;
}
```

- The default constructor uses **`Config.REST_HTTP_PORT`**, so 8080 is not hard-coded in the HTTP module.
- The second constructor exists for tests and alternative ports. Port `0` is a special value: the operating system chooses a free port. After `start()`, `getPort()` returns the actual port. This is how the harness avoids clashing with a real server on 8080.

### 2.4 `start()`

```java
public synchronized void start() throws IOException {
    if (server != null) {
        throw new IllegalStateException("HistoryServer is already running");
    }

    HttpServer newServer;
    try {
        InetSocketAddress address =
                new InetSocketAddress(Config.DEFAULT_HOST, configuredPort);
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

    newServer.createContext(MESSAGES_PATH, new MessagesHandler());
    newServer.createContext("/", new NotFoundHandler());

    newServer.start();

    this.server = newServer;
    this.executor = newExecutor;

    System.out.println("[HTTP] History REST API started: "
            + getBaseUrl() + MESSAGES_PATH);
}
```

Step by step:

1. **Already running?** Throw `IllegalStateException`. This prevents creating two listeners in the same object.
2. **Bind address.** `Config.DEFAULT_HOST` is `"localhost"`, so the server listens on the loopback interface. That matches the assignment URL and does not expose history to the network.
3. **Create the server.** `HttpServer.create(address, 0)` creates the server and binds the socket. The second parameter is the backlog; `0` means the system default. If the port is already in use, a `BindException` (an `IOException`) is thrown.
4. **Wrap the failure.** The new `IOException` includes host, port and the original message. This is the "handle startup failures clearly" requirement. The original exception is passed as the cause, so the stack trace still contains the low-level reason.
5. **Create the executor.** `Executors.newFixedThreadPool(8, factory)` creates up to eight worker threads. Without an executor, `HttpServer` runs handlers on its dispatcher thread, effectively one at a time.
6. **Set the executor.** `newServer.setExecutor(newExecutor)` tells `HttpServer` to dispatch handlers to the pool.
7. **Register contexts.**
   - `/messages` → `MessagesHandler`.
   - `/` → `NotFoundHandler`.
   - `HttpServer` picks the longest matching prefix.
8. **Start.** `newServer.start()` creates the dispatcher thread and begins accepting connections.
9. **Publish fields.** Assign `server` and `executor` only after a successful start, so `getPort()` and `stop()` do not see a half-initialized object.

Why assign to local variables first? If `HttpServer.create()` or `start()` throws, the object's `server` field remains `null`. The caller can retry or exit cleanly, and `stop()` remains a no-op.

### 2.5 `stop()`

```java
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
```

- **Idempotent:** if the server was never started or was already stopped, the method returns immediately. This makes shutdown hooks safe even after a failed startup.
- **Clear fields before stopping:** prevents re-entrant calls from seeing a server that is being shut down.
- **`stop(1)`:** closes the listening socket and allows up to one second for active exchanges to finish. A larger delay is more graceful but slows shutdown.
- **`shutdownNow()`:** interrupts and cancels queued worker tasks. Because the listening socket is already closed, no new requests should arrive.
- In-flight requests may be interrupted if they exceed the one-second grace period; that is acceptable for this demonstration. A production server would use a longer grace period and a proper shutdown strategy.

### 2.6 `isRunning()`, `getPort()`, `getBaseUrl()`

```java
public boolean isRunning() {
    return server != null;
}

public int getPort() {
    HttpServer currentServer = server;
    return currentServer != null
            ? currentServer.getAddress().getPort()
            : configuredPort;
}

public String getBaseUrl() {
    return "http://" + Config.DEFAULT_HOST + ":" + getPort();
}
```

- `isRunning()` is used by the test harness.
- `getPort()` returns the **actual** port after binding. With port `0`, that is an ephemeral port chosen by the OS. Before `start()`, it returns the configured port (8080 by default).
- `getBaseUrl()` builds the URL from config and the actual port. If the port were hard-coded, tests on ephemeral ports could not work.

### 2.7 `buildHistoryJson()`

```java
protected String buildHistoryJson() {
    return MessageStore.getInstance().toJsonArray();
}
```

- This is the integration point with Member 01's chat server.
- `MessageStore.getInstance()` returns the process-wide singleton. The HTTP module does **not** create a store.
- `toJsonArray()` returns the complete array as a string.
- It is `protected` so the test harness can override it to throw and exercise the 500 path. In normal use it only reads the store.
- It is not `private` for testability; this is a deliberate, small seam.

### 2.8 `MessagesHandler.handle()`

```java
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
                    exchange.getRequestMethod() + " is not supported for "
                            + MESSAGES_PATH);
            return;
        }

        try {
            sendJson(exchange, 200, buildHistoryJson());
        } catch (RuntimeException e) {
            System.err.println("[HTTP] Could not build message history: "
                    + e.getMessage());
            sendError(exchange, 500, "Internal Server Error",
                    "Could not build message history");
        }
    } finally {
        exchange.close();
    }
}
```

Order of checks matters:

1. **Path check first.** Because `/messages` is registered as a prefix context, paths such as `/messages/extra` and `/messagesfoo` also reach this handler. The exact `equals()` check returns 404 for them. Without this check, the API would silently accept a non-contract path.
2. **Method check second.** If the path is correct but the method is not GET, return 405. Set `Allow: GET` before sending the response.
3. **Build and send third.** Only a valid GET reaches `buildHistoryJson()`.
4. **500 only around JSON building.** The catch is scoped to the operation that can fail due to application state. `sendJson()` itself throws `IOException` if the client disconnects; the handler declares `throws IOException`, and the server deals with the broken exchange. Trying to send a 500 after headers have already been sent would be invalid, so the catch does not wrap the whole send.
5. **`finally { exchange.close(); }`** releases the request and response streams even on early return or exception. `HttpExchange` implements `AutoCloseable` in Java 17, so try-with-resources would also work; explicit `finally` is clear and works if the code is compiled on older Java versions.

### 2.9 `NotFoundHandler.handle()`

```java
public void handle(HttpExchange exchange) throws IOException {
    try {
        sendError(exchange, 404, "Not Found",
                "Unknown endpoint: " + exchange.getRequestURI().getPath());
    } finally {
        exchange.close();
    }
}
```

The root context catches every path not matched by a longer context. It always returns 404, including for `/`, `/unknown`, `/admin`, etc. This is why unknown endpoints are handled consistently.

### 2.10 `sendJson()`

```java
private static void sendJson(HttpExchange exchange, int status, String body)
        throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

    exchange.getResponseHeaders().set("Content-Type", JSON_CONTENT_TYPE);
    exchange.getResponseHeaders().set("Cache-Control", "no-store");
    exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");

    exchange.sendResponseHeaders(status, bytes.length);
    try (OutputStream out = exchange.getResponseBody()) {
        out.write(bytes);
    }
}
```

- **UTF-8 first.** `String.getBytes(UTF_8)` converts characters to bytes. `bytes.length` is the byte count, not the character count. This is essential for `Content-Length`: accented or non-ASCII characters can take more than one byte.
- **Headers before status.** `sendResponseHeaders` sends the status line and the headers. All header values must be set before this call.
- **`Content-Type`** declares JSON and the charset.
- **`Cache-Control: no-store`** stops caches from serving stale history.
- **`X-Content-Type-Options: nosniff`** stops a browser from guessing a different type. It is a small hardening header.
- **`sendResponseHeaders(status, bytes.length)`** uses fixed-length mode and sets `Content-Length`. A positive length means the server writes exactly that many bytes.
- **try-with-resources** closes the response body stream, which finalizes the response. The outer `exchange.close()` then releases the exchange.

### 2.11 `sendError()`

```java
private static void sendError(HttpExchange exchange, int status,
                              String error, String message) throws IOException {
    sendJson(exchange, status,
            "{\"status\":" + status
                    + ",\"error\":\"" + escapeJson(error) + "\""
                    + ",\"message\":\"" + escapeJson(message) + "\"}");
}
```

This builds the JSON error body:

```json
{"status":404,"error":"Not Found","message":"Unknown endpoint: /unknown"}
```

`status` is an `int`, so it is safe to concatenate without quotes. `error` and `message` are strings, so they are escaped and wrapped in quotes. The response is still `application/json`, which means clients can parse errors the same way as success responses.

### 2.12 `escapeJson()`

```java
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
```

The order is important: escape backslash **first**. If quotes were escaped first and backslash later, the backslash introduced for a quote would itself be escaped again, producing invalid JSON. After the backslash, escape the quote and the named control characters.

This mirrors the existing `Message.escapeJson()` logic in `common/`, so the API has one consistent escaping style. It is not a complete JSON library.

### 2.13 Thread factory

```java
private static ThreadFactory newThreadFactory() {
    AtomicInteger counter = new AtomicInteger();
    return runnable -> {
        Thread thread = new Thread(runnable,
                "http-history-" + counter.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    };
}
```

- Names make thread dumps readable: `http-history-1`, `http-history-2`, etc.
- `AtomicInteger` is thread-safe, so names are unique even when threads are created concurrently.
- Daemon threads do not keep the JVM alive by themselves. The `HttpServer` dispatcher and the standalone `main` latch control the process lifetime. During tests, daemon workers do not prevent the JVM from exiting.

### 2.14 Standalone `main()`

```java
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
```

- Starts on the default port (8080).
- On bind failure, prints the wrapped message and exits with status 1.
- Registers a shutdown hook so Ctrl+C closes the listener and executor.
- `new CountDownLatch(1).await()` blocks forever. The latch is never counted down; it exists to keep `main` alive in a readable way. Ctrl+C terminates the JVM and runs the hook.
- If the thread is interrupted, the interrupt flag is restored so the caller can react correctly.

This entry point exists so Member 03 can demonstrate the module independently. It is **not** the final integration; Member 01's `Main` should start and stop the server later.

---

## 3. HistoryClient.java

### 3.1 Fields and constants

```java
private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

private final HttpClient httpClient;
private final URI messagesUri;
```

- **Connect timeout (5 s):** how long to wait for the TCP connection to be established.
- **Request timeout (10 s):** how long to wait for the complete HTTP exchange after sending the request.
- `HttpClient` is immutable and thread-safe; a single instance can be reused.
- `messagesUri` is immutable after construction.

### 3.2 Constructors

```java
public HistoryClient() {
    this(defaultMessagesUrl());
}

public HistoryClient(String messagesUrl) {
    this.messagesUri = URI.create(messagesUrl);
    this.httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();
}
```

- The default constructor uses the project's host and port.
- The explicit-URL constructor is used by tests to point at an ephemeral port.
- `URI.create` parses the URL once at construction, not on every request.
- `HttpClient.newBuilder().connectTimeout(...)` sets the connection timeout. The request timeout is set per request.

### 3.3 `defaultMessagesUrl()`

```java
public static String defaultMessagesUrl() {
    return "http://" + Config.DEFAULT_HOST + ":" + Config.REST_HTTP_PORT
            + "/messages";
}
```

The URL is built from `Config`, not hard-coded. If the port changes in `Config`, both server and client follow automatically. This is why `HistoryServer` and `HistoryClient` stay consistent.

### 3.4 `fetchHistory()`

```java
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
        throw new IOException("History API returned HTTP "
                + response.statusCode() + " (" + response.body() + ")");
    }
    return response.body();
}
```

Step by step:

1. **Build an immutable request.** `.GET()` sets the method. `.timeout(10s)` limits the exchange. `.header("Accept", "application/json")` tells the server the client prefers JSON.
2. **Send synchronously.** `send()` blocks the calling thread until the response arrives or an exception occurs. A console client is fine with synchronous I/O.
3. **Decode with UTF-8.** `BodyHandlers.ofString(StandardCharsets.UTF_8)` reads the whole body and decodes it with the same charset the server declared. If you omitted the charset, the platform default could differ from UTF-8.
4. **Check status explicitly.** `HttpClient` does **not** throw for 404/405/500. If the status is not 200, throw an `IOException` with the status and body. Callers cannot accidentally display an error body as if it were history.
5. **Return the body.** The raw JSON string is returned unchanged. This satisfies the approved decision not to add a custom JSON parser.

The method declares `IOException` and `InterruptedException` because both can arise from network I/O and blocking waits.

### 3.5 `printHistory()`

```java
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
```

Catch order is essential:

- `ConnectException` extends `SocketException` extends `IOException`. It must be caught before `IOException` to give the "server not running" message.
- `HttpTimeoutException` extends `IOException`. It must also be caught before the general `IOException` catch.
- The remaining `IOException` covers HTTP status errors and other network failures.
- `InterruptedException` is separate. Restoring the interrupt flag (`Thread.currentThread().interrupt()`) preserves the signal for the caller instead of swallowing it.

`printHistory()` is what Member 01 can call from `/history`. It handles expected failures and does not throw to the console loop.

### 3.6 Standalone `main()`

```java
public static void main(String[] args) {
    new HistoryClient().printHistory();
}
```

A one-line entry point for testing the client against a running server. It uses the default URL.

---

## 4. Java networking concepts in this module

### 4.1 TCP and HTTP

- `HttpServer` and `HttpClient` use TCP sockets internally.
- TCP guarantees reliable, ordered bytes. HTTP defines the request/response format on top.
- A TCP connection is identified by four values: source IP, source port, destination IP, destination port. On loopback, the IPs are the same; the ports distinguish client and server.
- HTTP/1.1 can reuse a TCP connection for multiple requests (keep-alive). A new `curl.exe` process uses a new connection; the Java `HttpClient` may reuse pooled connections.

### 4.2 Server socket and binding

- `HttpServer.create(new InetSocketAddress(host, port), backlog)` binds a listening socket.
- Binding to `localhost` restricts access to the local machine.
- A bind failure usually means the port is already in use or permission is denied.
- `getAddress()` returns the actual bound address; with port 0, the OS chooses a free port.

### 4.3 Threads and concurrency

- The dispatcher thread accepts connections and dispatches requests.
- The fixed pool runs handlers concurrently, up to the pool size.
- More requests wait in the executor queue.
- Shared state (`MessageStore`) must be thread-safe; `CopyOnWriteArrayList` provides snapshot iteration.

### 4.4 HTTP message structure

Request:

```text
METHOD PATH VERSION
Header: value
Header: value

optional body
```

Response:

```text
VERSION STATUS REASON
Header: value
Header: value

optional body
```

The blank line separates headers from the body. `Content-Length` tells the receiver exactly how many body bytes to read, which is why byte length matters.

### 4.5 Status codes

- `200 OK`: success.
- `404 Not Found`: no resource at the path.
- `405 Method Not Allowed`: resource exists, method does not.
- `500 Internal Server Error`: the server failed while processing a valid request.

The first digit is the class: 2xx success, 4xx client error, 5xx server error.

### 4.6 Headers

- `Content-Type` (response): what the body is.
- `Accept` (request): what the client wants.
- `Content-Length`: body length in bytes.
- `Date`: generated by the server.
- `Allow`: methods supported by the resource, used with 405.
- `Cache-Control`: caching policy.
- `Host`: required in HTTP/1.1 to identify the target host and port.

### 4.7 Character encoding

- JSON text is converted to UTF-8 bytes before sending.
- `Content-Type: ...; charset=UTF-8` declares it.
- The client decodes with UTF-8.
- `Content-Length` counts bytes, not Java `char` values. A character outside ASCII may take two or more UTF-8 bytes.

### 4.8 Timeouts

- **Connect timeout:** limits TCP connection establishment. A dead host can otherwise hang for a long time.
- **Request timeout:** limits the whole request/response exchange.
- Timeouts turn network problems into exceptions instead of indefinite blocking.

### 4.9 Resource management

- Server: close the exchange, close the response stream, stop the listener, shut down the executor.
- Client: the JDK manages its connection pool. In Java 17, `HttpClient` is not `AutoCloseable`, so no `close()` is required.
- `HttpExchange` **is** `AutoCloseable` in Java 17; this code uses explicit `finally` for clarity and compatibility.

---

## 5. Common viva traps

1. **"Why not `MessageStore.all()`?"** That method does not exist. The real API is `getMessages()` for a list and `toJsonArray()` for JSON.
2. **"Why does `/messages/extra` return 404 if the context is `/messages`?"** `HttpServer` uses longest-prefix matching; the handler explicitly checks the exact path.
3. **"Why 405 and not 404 for POST?"** The resource exists; the method is wrong. `Allow: GET` communicates the supported method.
4. **"Is `toJsonArray()` synchronized?"** No, and it does not need to be. `CopyOnWriteArrayList` gives a snapshot for iteration.
5. **"Why is there no request body for GET?"** GET retrieves a representation; it does not submit data. The server does not read a body for this endpoint.
6. **"Why not return 201?"** 201 means a new resource was created. This endpoint is read-only.
7. **"Why is `Content-Length` a byte count?"** HTTP counts bytes. `String.length()` counts UTF-16 code units, which is not the same.
8. **"Why wrap `BindException`?"** To report the host and port clearly and preserve the original cause.
9. **"Why is `buildHistoryJson()` protected?"** A test subclass can force a failure and verify the 500 response without modifying `common/`.
10. **"Why bind localhost?"** The assignment uses localhost; it avoids exposing history to the network. LAN access would require binding a non-loopback address and handling firewall rules.

---

## 6. What not to change

- Do not modify `common/` without the team's agreement.
- Do not modify `Main.java` or `ConsoleClient.java` (Member 01 owns them).
- Do not create a second message store.
- Do not change the JSON field names; the client and the assignment expect `sender`, `text` and `timestamp`.
- Do not hard-code 8080 in new code; use `Config.REST_HTTP_PORT`.

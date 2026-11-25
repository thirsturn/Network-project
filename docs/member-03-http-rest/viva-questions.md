# Lecturer Viva Preparation — Member 03

Forty-two questions with technically correct answers. Answers are deliberately short enough to say aloud.

---

## HTTP and REST

### 1. What is HTTP?

HTTP is an application-layer request-response protocol that runs over TCP. A client sends a method, path, headers and optional body; the server returns a status code, headers and optional body. It is text-based and standardized, which is why browsers, curl and Java clients can all use it.

### 2. What does "stateless" mean?

It means each request is independent: the server does not need to remember previous requests to answer the current one. The `GET /messages` request carries everything needed. HTTP can still use cookies or tokens to simulate sessions, but the protocol itself does not require session state.

### 3. What is REST? Is your API RESTful?

REST is an architectural style: resources are identified by URIs, standard HTTP methods are used, communication is stateless, and representations such as JSON are transferred. Our API is **REST-style** for one resource: `GET /messages` reads the message collection. It is not a full REST system with hypermedia controls, but it follows the core principles.

### 4. Why use HTTP instead of a custom TCP protocol for history?

HTTP gives us a standard method, standard status codes, standard headers, and clients that already exist — browsers, curl, and Java's `HttpClient`. A custom TCP protocol would require custom framing, custom error handling and a custom client. The real-time chat keeps TCP; history is a separate, request-response concern.

### 5. Why is GET safe and idempotent?

`GET` is **safe** because it does not change server state; our handler only reads `MessageStore`. It is **idempotent** because repeating it has the same logical effect: the same history is read, with no additional side effect. POST/PUT/DELETE are rejected, so there is no mutation path.

---

## Server and HttpServer

### 6. What is `com.sun.net.httpserver.HttpServer`? Is it part of Java?

It is a lightweight HTTP/1.1 server included with the JDK in the `jdk.httpserver` module. It provides `HttpServer`, `HttpContext`, `HttpHandler` and `HttpExchange`. It is not a full application server like Tomcat, but it is sufficient for this assignment and needs no external dependency.

### 7. How does HttpServer handle multiple clients?

`start()` creates a dispatcher thread that accepts connections and parses requests. Handlers run on the executor set with `setExecutor(...)`. This implementation uses a fixed pool of eight worker threads. More requests wait in the pool queue. Without an executor, handlers run on the dispatcher thread and are effectively serialized.

### 8. What is an `HttpContext`? Why did you check the exact path?

An `HttpContext` maps a path **prefix** to an `HttpHandler`. The longest matching prefix wins. Because the `/messages` context also matches `/messages/extra` and `/messagesfoo`, the handler checks `MESSAGES_PATH.equals(path)` and returns 404 for anything that is not exactly `/messages`. This enforces the documented contract.

### 9. What is `HttpExchange`?

`HttpExchange` represents one request/response exchange. It exposes the request method, URI, request headers and input stream, plus the response headers and output stream. It is `AutoCloseable` in Java 17; this code closes it explicitly in a `finally` block.

### 10. Why call `sendResponseHeaders` before writing the body?

`sendResponseHeaders(status, length)` sends the status line and headers, including `Content-Length` when the length is positive. Once headers are sent, they cannot be changed. The body must be written after this call. This implementation passes the exact UTF-8 byte length.

### 11. Why close the exchange?

Closing releases the request and response streams and finishes the exchange. It prevents resource leaks and lets the server clean up the connection. This code closes the response body with try-with-resources and the whole exchange in `finally`.

### 12. Why is the server bound to localhost? How would you allow LAN access?

`Config.DEFAULT_HOST` is `"localhost"`, so the listener is on the loopback interface. That matches `http://localhost:8080` and avoids exposing history to the network. For LAN access, bind a wildcard or explicit interface address, for example `new InetSocketAddress(port)`, and allow the port through the firewall. That is a security decision, not just a code change.

### 13. What happens if port 8080 is already in use? Did you test it?

`HttpServer.create` throws a `BindException`, which is an `IOException`. `start()` wraps it in an `IOException` that names the host and port, preserving the original cause. The harness tests this by starting a second server on the same port; the test passes.

### 14. What is the purpose of `buildHistoryJson()` being `protected`?

It is the seam between the HTTP module and `MessageStore`. In normal operation it returns `MessageStore.getInstance().toJsonArray()`. A test subclass overrides it to throw, which exercises the 500 response path without modifying `common/`. It is a small testability decision, not a public API.

---

## Status codes and headers

### 15. What is the difference between 404 and 405?

404 means the requested **resource/path** does not exist. 405 means the resource exists but the **HTTP method** is unsupported. `POST /messages` returns 405 because `/messages` exists but only GET is allowed. `/unknown` returns 404 because no resource matches.

### 16. What is the `Allow` header for?

On a 405 response, `Allow: GET` tells the client which methods the resource supports. It is part of correct HTTP behaviour and lets a client correct its request. This implementation sets it before sending the 405 body.

### 17. What is the difference between 404 and 500?

404 is a client error: the client requested a path that does not exist. 500 is a server error: the request was valid, but the server failed while processing it. Our 500 test forces `buildHistoryJson()` to throw, simulating an internal failure.

### 18. Why return 200 and not 201?

201 means a new resource was created. This endpoint is a read-only GET; it creates nothing. 200 OK is the correct success status for returning an existing representation.

### 19. Which response headers do you set?

- `Content-Type: application/json; charset=UTF-8` — representation and encoding.
- `Content-Length` — exact UTF-8 byte count, set through `sendResponseHeaders`.
- `Cache-Control: no-store` — history changes, so do not cache.
- `X-Content-Type-Options: nosniff` — prevent MIME sniffing.
- `Allow: GET` — only on 405 responses.
- `Date` — added automatically by the HTTP server.

### 20. What is the difference between `Content-Type` and `Accept`?

`Content-Type` describes the body being sent. `Accept` tells the server which representation the client prefers. Our response uses `Content-Type: application/json`; the Java client sends `Accept: application/json`.

### 21. What is `Content-Length`? Who sets it?

It is the exact number of bytes in the body. The server sets it through `exchange.sendResponseHeaders(status, bytes.length)`. It must be a byte count, not a Java character count, because UTF-8 characters can occupy multiple bytes.

### 22. Why `Cache-Control: no-store`?

Chat history changes as new messages arrive. A cached response could show stale history. `no-store` tells clients and intermediaries not to store the response. It is appropriate for this dynamic resource.

### 23. Why UTF-8?

JSON is defined in terms of Unicode, and UTF-8 is the standard encoding for JSON on the web. The server converts the string with `getBytes(StandardCharsets.UTF_8)`, declares `charset=UTF-8`, and the client decodes with `BodyHandlers.ofString(StandardCharsets.UTF_8)`. This avoids platform-default encoding differences.

---

## JSON and MessageStore

### 24. How is the JSON response built? Is it valid?

`MessageStore.toJsonArray()` maps each `Message` through `Message.toJson()` and joins the objects with commas inside `[` and `]`. `Message.toJson()` escapes backslash, double quote, backspace, form feed, newline, carriage return and tab. The result is a valid JSON array for normal chat text. It is a hand-written serializer, not a full JSON library, so exotic control characters would need additional escaping.

### 25. Why not use an external JSON library?

The project has no build system and no external dependencies, and the approved decision was to avoid unnecessary libraries. The existing `common.Message` already provides JSON serialization. For a larger production system, a maintained library such as Jackson or Gson would be safer and more complete.

### 26. How is `MessageStore` thread-safe?

It uses `CopyOnWriteArrayList`. Writes create a new internal array; readers iterate over a stable snapshot. `toJsonArray()` streams over that snapshot, so a concurrent `addMessage()` does not cause `ConcurrentModificationException` or a partially seen list. The trade-off is copying on each write, acceptable for this demonstration.

### 27. Could a response contain half a message?

No. The list holds whole `Message` objects, and a snapshot iteration sees entire objects. A response may represent an earlier moment than another response, which is normal, but each individual response is internally consistent.

### 28. How does the HTTP module integrate with the chat server?

In the integrated design, both use the same `MessageStore` singleton: the chat server will call `addMessage(...)`; the HTTP handler calls `toJsonArray()`. They must run in the same JVM, because the singleton is in-memory. If they were separate processes, the HTTP server would have its own empty store. `Main` is responsible for starting both in one process.

### 29. What happens to history when the server restarts?

It is lost. `MessageStore` is in-memory only. Persistence would require a file or database, which is outside this module and was not requested. The README already documents this limitation.

### 30. Does the REST API modify the store?

No. It only reads. There is no POST/PUT/DELETE handler that mutates messages; those methods return 405. This keeps the API safe and avoids interfering with the chat server's writes once it is integrated.

---

## HttpClient and client behaviour

### 31. Why use `java.net.http.HttpClient`?

It is the modern JDK HTTP client (Java 11+), supports HTTP/1.1 and HTTP/2, handles connection pooling, and has clean support for timeouts and body handlers. It requires no external dependency. `HttpURLConnection` is older and more cumbersome.

### 32. What is the difference between the connect timeout and the request timeout?

The **connect timeout** (`HttpClient.newBuilder().connectTimeout`) limits how long TCP connection establishment may take. The **request timeout** (`HttpRequest.Builder.timeout`) limits the whole exchange after the request is sent. Both are set here: 5 seconds for connect, 10 seconds for the request.

### 33. What does `BodyHandlers.ofString(StandardCharsets.UTF_8)` do?

It reads the entire response body as bytes and decodes them into a Java `String` using UTF-8. It matches the server's declared charset. The response body is fully consumed, so there is no remaining stream to close.

### 34. How does the client handle a non-200 response?

`HttpClient` does not throw for 404/405/500. `fetchHistory()` checks `response.statusCode() != 200` and throws an `IOException` containing the status and body. This prevents a caller from displaying an error body as valid history.

### 35. How does the client handle connection refused, timeout and interruption?

`printHistory()` catches `ConnectException` for a refused connection, `HttpTimeoutException` for timeouts, `IOException` for other network/status errors, and `InterruptedException` separately. On interruption it restores the interrupt flag with `Thread.currentThread().interrupt()`.

### 36. Is `HttpClient` `AutoCloseable` in Java 17?

No. In Java 17 it does not implement `AutoCloseable`, so there is no `close()` call. It manages its own connection pool and becomes eligible for garbage collection when no longer referenced. The code creates one client per `HistoryClient` instance and reuses it for requests.

---

## Testing and limitations

### 37. How did you test concurrency, and what was the result?

Eight reader threads each made 25 `GET /messages` requests while one writer added 200 messages, all starting together. All 200 requests returned 200 with a bracket-valid JSON array, the store ended with 200 messages, and the final response exactly matched `MessageStore.toJsonArray()`. No crashes or malformed responses.

### 38. What did you not test, and why?

Full TCP chat → store → HTTP integration, because the `ChatServer` is not on this branch. UDP/TLS/Multicast non-interference, because those modules do not exist yet. Wireshark, because Wireshark/Npcap were not found on PATH or in their standard install locations. Browser screenshots, because they are manual. These are stated as not tested rather than claimed as passing.

### 39. How would you add pagination?

Parse a query parameter such as `?limit=20`, validate it, and return a sublist of the store — or a dedicated method on `MessageStore`. Add it as a separate method or a small branch in the handler. The current implementation deliberately returns everything and ignores query strings. Pagination is not implemented, so it should be described as future work.

### 40. Is the API secure? Does it use HTTPS?

No. It uses plain HTTP on port 8080, bound to localhost, with no authentication. The TLS module on port 5443 is for login, not for this endpoint. For production, use HTTPS, authentication and authorization, and do not expose the API on an untrusted network. The assignment's history demo is local and read-only, which limits but does not eliminate the risk.

### 41. Why is full TCP integration not available yet?

Member 01's `ChatServer` and `ChatClient` are not implemented on this branch. The shared `MessageStore` exists, but nothing writes chat messages into it yet. The HTTP module is tested against the real store with test-injected messages, and the demo fixture is clearly labelled. Once `ChatServer` calls `addMessage(...)`, the HTTP code needs no change.

### 42. What must change in `Main` and `ConsoleClient`?

`Main` must create, start and stop `http.HistoryServer`, ideally with a shutdown hook, in the same JVM as the chat server. `ConsoleClient` must handle `/history` by calling `new http.HistoryClient().printHistory()`. Both files are owned by Member 01, so they were not modified here; the exact minimal snippets are in the main README.

---

## Three facts to remember

1. **`GET /messages` → 200 + JSON array; unknown path → 404; wrong method → 405 + `Allow: GET`; internal failure → 500.**
2. **The data comes from the existing `MessageStore` singleton, read through `toJsonArray()` — no separate database and no modification of `common/`.**
3. **The HTTP server and chat server must run in the same JVM to share that in-memory singleton.**

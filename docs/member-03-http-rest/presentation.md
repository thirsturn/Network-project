# Two-Minute Presentation Speech — Member 03

**Slot:** 6:30–8:30  
**Topic:** HTTP/REST Message History API  
**Target length:** about 2 minutes (roughly 300–330 words)

Speak naturally. The brackets are stage directions, not lines to read aloud.

---

## Speech

Good morning everyone. I'm Pasindu, and I'm responsible for the HTTP and REST module — the message history API.

In our application, the chat server and the HTTP server are designed to share a `MessageStore`. The chat server will write each message there, and my job is to expose that history through a standard interface, so any HTTP client can read it without opening a raw TCP connection.

I used HTTP and REST for three reasons. First, HTTP is already understood by browsers, curl and Java clients. Second, REST gives us a clean resource: `GET /messages` means "read the message collection". Third, HTTP has standard status codes, so success and failure are unambiguous.

The server is called `HistoryServer`, and it uses Java's built-in `HttpServer`. It listens on port 8080, which comes from our shared `Config` class. When a `GET /messages` request arrives, an eight-thread executor runs the handler. The handler reads `MessageStore.getInstance().toJsonArray()` and returns `200 OK` with `Content-Type: application/json` and UTF-8 encoding. An unknown path returns `404`. A method other than GET returns `405` with an `Allow: GET` header. Internal failures return `500`. Every error response is JSON as well.

On the client side, `HistoryClient` uses `java.net.http.HttpClient`. It sends `GET /messages` with a connection timeout and a request timeout, checks that the status is 200, and prints the JSON. It is ready to be called from the console client's `/history` command once Member 1 integrates it. Because the store is in memory, the chat server and the HTTP server must run in the same JVM to share the same `MessageStore` singleton.

[Switch to the terminal or browser.]

Here is the live response, and here is the 404 for an unknown endpoint.

[Switch to Wireshark, if available.]

With the filter `tcp.port == 8080 && http`, we can see the GET request, the 200 response, the headers and the JSON body.

To conclude, this module gives our application a simple, standard and testable way to read chat history, without changing the existing TCP chat design. Thank you.

---

## Timing plan

| Time | What to do |
|---|---|
| 0:00–0:15 | Greeting, name, module responsibility |
| 0:15–0:35 | Why HTTP/REST and what the API does |
| 0:35–1:05 | HistoryServer, port 8080, executor, status codes |
| 1:05–1:30 | HistoryClient, timeouts, same-JVM MessageStore |
| 1:30–1:50 | Live curl/browser demonstration |
| 1:50–2:00 | Wireshark sentence and conclusion |

If you are running long, shorten the Wireshark sentence; do not cut the live demo entirely.

## Delivery tips

- Do not read code line by line. The lecturer can see the code; explain the **flow**.
- Point to the architecture diagram when you say "the chat server will store messages" and "the HTTP server reads the store".
- Pause briefly after "200 OK", "404" and "405" so the status-code pattern is clear.
- Say "REST-style" rather than claiming a large REST system; this is one resource with one method.
- Be honest about integration: say "ready for `/history` once Member 1 integrates it", not "the console client already uses it".
- If Wireshark is not available, say so and show the curl/browser output instead. Never pretend a capture exists.
- Do not call the endpoint "secure". It is plain HTTP on localhost; TLS is used by Member 4 for authentication, not by this endpoint.

## One-sentence summary

`GET /messages` lets any HTTP client read the shared in-memory chat history as UTF-8 JSON, with correct status codes and no changes to the TCP chat protocol.

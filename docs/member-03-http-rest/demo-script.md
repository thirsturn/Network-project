# Live Demonstration Script — Member 03

**Slot:** 6:30–8:30  
**Goal:** show the REST endpoint, the status codes, the Java client, and (if available) the Wireshark capture without wasting time on setup.

---

## 1. Ten minutes before the presentation

Do these checks before your slot:

- [ ] Project compiled:

```powershell
javac -d out (Get-ChildItem -Recurse -Filter *.java src/main/java | Select-Object -ExpandProperty FullName)
javac -cp out -d out-test (Get-ChildItem -Recurse -Filter *.java src/test/java | Select-Object -ExpandProperty FullName)
```

- [ ] Automated checks still pass:

```powershell
java -cp "out;out-test" http.HttpHistoryTest
```

Expected final line: `RESULT: 38 passed, 0 failed`.

- [ ] Port 8080 is free:

```powershell
Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
```

If a process is listed, stop it before the demo. Do not change `Config.REST_HTTP_PORT` just for the demo.

- [ ] Terminal A is ready to run the server.
- [ ] Terminal B is ready for curl.
- [ ] Browser tab is open on `http://localhost:8080/messages` (it will show an error until the server starts; that is fine).
- [ ] Wireshark is installed, the loopback interface is selected, and the display filter is `tcp.port == 8080 && http` (if Wireshark is available).
- [ ] The architecture diagram slide is open.
- [ ] The test-result screenshot or terminal output is ready as a fallback.

> If Wireshark is not installed, do not pretend. Say: "Wireshark is not installed on this machine; I have the exact capture procedure and can show the curl evidence." The runbook is in [wireshark-demo.md](wireshark-demo.md).

---

## 2. Screen layout

| Area | Content |
|---|---|
| Left | Architecture slide or code slide |
| Right top | Terminal A: server logs |
| Right middle | Terminal B: curl commands |
| Right bottom | Browser, then Wireshark |
| Back | Test result output, in case a live step fails |

Keep font size large. Do not show a full IDE with tiny text.

---

## 3. Opening (about 20 seconds)

Say:

> "This is the HTTP/REST part of our application. The chat server will store messages in the shared `MessageStore`; my `HistoryServer` exposes that history at `GET /messages` on port 8080."

Point to the architecture diagram:

- Client → HistoryServer
- HistoryServer → MessageStore
- ChatServer → MessageStore (future)

---

## 4. Start the server (about 20 seconds)

In Terminal A, from the repository root:

```powershell
java -cp "out;out-test" http.HistoryDemoServer
```

Expected output:

```text
[HTTP] History REST API started: http://localhost:8080/messages
[HTTP] Seeded 2 demo messages into the real MessageStore.
[HTTP] Press Ctrl+C to stop.
```

Say clearly:

> "This is a demonstration fixture. It seeds two messages into the **real** `MessageStore` because Member 1's TCP `ChatServer` is not merged on this branch yet. The HTTP server and the store are real; the chat injection is the only test shortcut."

Do not claim that two chat clients are running.

---

## 5. Demonstrate 200 OK (about 20 seconds)

In Terminal B:

```powershell
curl.exe -sS -i --http1.1 -H "Accept: application/json" http://localhost:8080/messages
```

Expected:

```text
HTTP/1.1 200 OK
Content-type: application/json; charset=UTF-8
Content-length: ...
Cache-control: no-store

[{"sender":"alice","text":"Hello everyone","timestamp":"..."},{"sender":"bob","text":"Hi Alice","timestamp":"..."}]
```

Say:

> "The request is a GET to `/messages`. The server returns 200 OK, the body is a JSON array read from `MessageStore`, and the Content-Type declares UTF-8."

Point out the fields: `sender`, `text`, `timestamp`.

---

## 6. Demonstrate 404 (about 15 seconds)

```powershell
curl.exe -sS -i --http1.1 http://localhost:8080/unknown
```

Expected:

```text
HTTP/1.1 404 Not Found
Content-type: application/json; charset=UTF-8

{"status":404,"error":"Not Found","message":"Unknown endpoint: /unknown"}
```

Say:

> "The path does not exist, so the server returns 404 with a JSON error body. `/messages/extra` also returns 404, because the handler checks the exact path even though HttpServer uses prefix matching."

Optional second command:

```powershell
curl.exe -sS -i --http1.1 http://localhost:8080/messages/extra
```

---

## 7. Demonstrate 405 (about 15 seconds)

```powershell
curl.exe -sS -i --http1.1 -X POST http://localhost:8080/messages
```

Expected:

```text
HTTP/1.1 405 Method Not Allowed
Allow: GET
Content-type: application/json; charset=UTF-8

{"status":405,"error":"Method Not Allowed","message":"POST is not supported for /messages"}
```

Say:

> "The resource exists, but POST is not supported, so the correct status is 405, not 404. The `Allow: GET` header tells the client which method is allowed."

If time permits, show PUT or DELETE with the same pattern.

---

## 8. Browser demonstration (about 10 seconds)

Switch to the browser:

```text
http://localhost:8080/messages
```

The browser displays the JSON array. Say:

> "Because this is a standard HTTP GET, the browser works without any custom client. There is no HTML page; the response is data."

Open `http://localhost:8080/unknown` to show the 404 JSON body.

---

## 9. Java client demonstration (optional, about 15 seconds)

In Terminal B, with the server still running:

```powershell
java -cp out http.HistoryClient
```

Expected:

```text
---- Message History ----
GET http://localhost:8080/messages
[{"sender":"alice",...},{"sender":"bob",...}]
```

Say:

> "This is the Java `HttpClient` side. It applies timeouts, checks for status 200, and prints the raw JSON. It is ready for the `/history` command once Member 1 integrates it."

This step is optional if time is tight.

---

## 10. Wireshark demonstration (about 30 seconds, if installed)

Before the demo, Wireshark should already be capturing on the **loopback** interface with:

```text
tcp.port == 8080 && http
```

During the demo:

1. Send the GET /messages command again.
2. Click the `GET /messages HTTP/1.1` packet.
3. Expand the request headers: Host, User-Agent, Accept.
4. Click the `HTTP/1.1 200 OK` response.
5. Expand the response headers: Content-Type, Content-Length, Cache-Control.
6. Expand the JSON body.
7. Point out the 404 and 405 responses.

Say:

> "The filter shows only HTTP on port 8080. Here is the GET request, the 200 response, the headers and the JSON body. The 404 and 405 are visible as separate HTTP responses."

If something does not appear, use the troubleshooting section in [wireshark-demo.md](wireshark-demo.md). Do not fabricate a packet capture.

---

## 11. Closing (about 10 seconds)

Say:

> "So the history API is a standard, testable HTTP resource that reads the same MessageStore the chat server will use. The only remaining integration step is for Member 1 to start the server in Main and wire the `/history` command."

Stop the server with **Ctrl + C** in Terminal A. Expected:

```text
[HTTP] History REST API stopped
```

---

## 12. Fallback plan

| Problem | Response |
|---|---|
| Port 8080 is in use | Find the process, stop it, restart the demo server. Do not silently change the port. |
| Server will not start | Read the `IOException` message aloud; explain that startup failures are handled. Show the automated `testStartupFailure` result. |
| Curl is not recognized | Use `curl.exe`, not `curl`, in PowerShell. |
| Browser shows a connection error | The server is not running, or it failed to bind. Check Terminal A. |
| Wireshark has no loopback interface | Explain that Npcap loopback support is required; show the runbook instead of a fake capture. |
| Wireshark shows TCP but not HTTP | Right-click → Decode As → HTTP for port 8080. |
| Wi-Fi/Ethernet selected in Wireshark | Switch to the loopback interface; localhost traffic does not leave the machine. |
| Live demo fails completely | Show the saved test output (`38 passed, 0 failed`) and the actual curl output from [testing-results.md](testing-results.md). Be explicit that it is saved evidence, not a live run. |
| Time is short | Do 200, 404, 405 and one Wireshark screenshot; skip the Java client and browser. |

---

## 13. Exact command summary

```powershell
# Terminal A
java -cp "out;out-test" http.HistoryDemoServer

# Terminal B
curl.exe -sS -i --http1.1 -H "Accept: application/json" http://localhost:8080/messages
curl.exe -sS -i --http1.1 http://localhost:8080/unknown
curl.exe -sS -i --http1.1 -X POST http://localhost:8080/messages
java -cp out http.HistoryClient

# Browser
# http://localhost:8080/messages
```

Wireshark filter:

```text
tcp.port == 8080 && http
```

---

## 14. What to say if asked about full TCP integration

> "The HTTP layer is complete and tested against the real `MessageStore`. Full end-to-end chat integration is blocked because the TCP `ChatServer` is not on this branch yet. Once Member 1 calls `MessageStore.addMessage(...)`, the same `GET /messages` will return those chat messages with no HTTP code changes. `Main` also needs to start `HistoryServer` in the same JVM."

That is the honest status. Do not claim a working two-client chat demo in this module.

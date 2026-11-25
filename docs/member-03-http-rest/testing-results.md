# Testing Evidence — Member 03 HTTP/REST

**Environment:** Windows 11, OpenJDK 17.0.21.1, javac 17.0.21.1, curl 8.22.0  
**Date of run:** 2026-11-25  
**Result:** 38 checks passed, 0 failed

This file contains the commands that were actually run and the output that was actually observed. Values that vary between runs (ports, timestamps, content lengths) are noted.

---

## 1. Compile

From the repository root in PowerShell:

```powershell
if (!(Test-Path out)) { New-Item -ItemType Directory -Path out | Out-Null }
$mainSources = @(Get-ChildItem -Recurse -Filter *.java 'src/main/java' | Select-Object -ExpandProperty FullName)
javac -d out $mainSources

if (!(Test-Path out-test)) { New-Item -ItemType Directory -Path out-test | Out-Null }
$testSources = @(Get-ChildItem -Recurse -Filter *.java 'src/test/java' | Select-Object -ExpandProperty FullName)
javac -cp out -d out-test $testSources
```

Observed:

```text
COMPILE OK
```

The production build includes all existing `common/` classes, `Main`, `ConsoleClient`, and the new `http/HistoryServer` and `http/HistoryClient`. The test build includes `HttpHistoryTest` and `HistoryDemoServer`.

---

## 2. Automated test harness

Command:

```powershell
java -cp "out;out-test" http.HttpHistoryTest
```

The harness binds a real `HistoryServer` on port `0` (an OS-assigned free port) and uses the real `MessageStore`. It sends real HTTP requests with `java.net.http.HttpClient`.

### Actual output

```text
PASS - testDefaultConfiguration
[HTTP] History REST API started: http://localhost:63524/messages
PASS - testServerStartup
PASS - testEmptyHistory [status]
PASS - testEmptyHistory [empty JSON array]
PASS - testEmptyHistory [Content-Type]
PASS - testActualHistory [status]
PASS - testActualHistory [matches MessageStore]
PASS - testActualHistory [sender field]
PASS - testActualHistory [text field]
PASS - testActualHistory [timestamp field]
PASS - testActualHistory [JSON escaping]
PASS - testActualHistory [Content-Type]
PASS - testUnknownEndpoint [status]
PASS - testUnknownEndpoint [JSON error]
PASS - testUnknownEndpoint [Content-Type]
PASS - testExactPathMatching [/messages/extra]
PASS - testExactPathMatching [/messagesfoo]
PASS - testMethodNotAllowed [POST status]
PASS - testMethodNotAllowed [POST Allow header]
PASS - testMethodNotAllowed [POST JSON error]
PASS - testMethodNotAllowed [POST no side effect]
PASS - testMethodNotAllowed [PUT status]
PASS - testMethodNotAllowed [PUT Allow header]
PASS - testMethodNotAllowed [PUT JSON error]
PASS - testMethodNotAllowed [PUT no side effect]
PASS - testMethodNotAllowed [DELETE status]
PASS - testMethodNotAllowed [DELETE Allow header]
PASS - testMethodNotAllowed [DELETE JSON error]
PASS - testMethodNotAllowed [DELETE no side effect]
PASS - testHistoryClient
[HTTP] History REST API started: http://localhost:63527/messages
[HTTP] History REST API stopped
PASS - testClientConnectionFailure
PASS - testConcurrentAccess [no request errors]
PASS - testConcurrentAccess [all messages stored]
PASS - testConcurrentAccess [final response consistent]
PASS - testStartupFailure
[HTTP] History REST API started: http://localhost:63537/messages
[HTTP] Could not build message history: simulated failure
PASS - testInternalServerError [status]
PASS - testInternalServerError [JSON error]
PASS - testInternalServerError [Content-Type]
[HTTP] History REST API stopped
[HTTP] History REST API stopped

==========================================
RESULT: 38 passed, 0 failed
==========================================
```

Ports `63524`, `63527` and `63537` are ephemeral ports chosen by the OS for that run. They will be different on every run.

### Re-run after the implementation commit

After the implementation was committed as `fce200a`, the generated `out/` and `out-test/` directories were deleted, the project was rebuilt from source, and the suite was run again. Observed result:

```text
RESULT: 38 passed, 0 failed
```

The rerun used ephemeral ports `58961`, `58964` and `58974`. No production or test source was changed between the two runs; only the documentation was added.

### What each group proves

| Group | What it proves |
|---|---|
| `testDefaultConfiguration` | `Config.REST_HTTP_PORT` is 8080 and a default `HistoryServer` uses it |
| `testServerStartup` | A real HTTP server can bind and accept requests |
| `testEmptyHistory` | Empty store returns `200` and `[]` |
| `testActualHistory` | Real store data is returned with `sender`/`text`/`timestamp` and JSON escaping |
| `testUnknownEndpoint` | Unknown paths return `404` with JSON |
| `testExactPathMatching` | `/messages/extra` and `/messagesfoo` are not accepted |
| `testMethodNotAllowed` | POST/PUT/DELETE return `405`, `Allow: GET`, and do not modify the store |
| `testHistoryClient` | Java `HttpClient` retrieves exactly the store's JSON |
| `testClientConnectionFailure` | A stopped server produces an `IOException` rather than a false success |
| `testConcurrentAccess` | 200 requests while 200 messages are added; no errors or malformed bodies |
| `testStartupFailure` | A second server on the same port fails with a clear exception |
| `testInternalServerError` | The 500 path returns JSON with status 500 |

### Important limitation

`testActualHistory`, `testHistoryClient` and `testConcurrentAccess` insert messages directly into the **real** `MessageStore`. Member 01's `ChatServer` does not exist on this branch, so this is isolated HTTP integration testing, not end-to-end TCP chat testing. The HTTP layer itself is real: real sockets, real requests, real responses.

---

## 3. Manual verification on port 8080

This verifies the actual configured port, not an ephemeral test port.

Server:

```powershell
java -cp "out;out-test" http.HistoryDemoServer
```

Observed server stdout:

```text
[HTTP] History REST API started: http://localhost:8080/messages
[HTTP] Seeded 2 demo messages into the real MessageStore.
[HTTP] Press Ctrl+C to stop.
```

### 3.1 GET /messages — 200

```powershell
curl.exe -sS -i --http1.1 http://localhost:8080/messages
```

Actual observed response (progress meter omitted):

```text
HTTP/1.1 200 OK
Date: Wed, 25 Nov 2026 03:25:11 GMT
Content-type: application/json; charset=UTF-8
Content-length: 147
X-content-type-options: nosniff
Cache-control: no-store

[{"sender":"alice","text":"Hello everyone","timestamp":"2026-11-25 08:55:11"},{"sender":"bob","text":"Hi Alice","timestamp":"2026-11-25 08:55:11"}]
```

The `Date` header is GMT. The JSON timestamp is local time (`2026-11-25 08:55:11`), because `Message` formats with the system time zone. Both are correct for their respective formats.

### 3.2 GET /unknown — 404

```powershell
curl.exe -sS -i --http1.1 http://localhost:8080/unknown
```

Actual observed response:

```text
HTTP/1.1 404 Not Found
Date: Wed, 25 Nov 2026 03:25:11 GMT
Content-type: application/json; charset=UTF-8
Content-length: 73
X-content-type-options: nosniff
Cache-control: no-store

{"status":404,"error":"Not Found","message":"Unknown endpoint: /unknown"}
```

### 3.3 POST /messages — 405

```powershell
curl.exe -sS -i --http1.1 -X POST http://localhost:8080/messages
```

Actual observed response:

```text
HTTP/1.1 405 Method Not Allowed
Date: Wed, 25 Nov 2026 03:25:11 GMT
Allow: GET
Content-type: application/json; charset=UTF-8
Content-length: 91
X-content-type-options: nosniff
Cache-control: no-store

{"status":405,"error":"Method Not Allowed","message":"POST is not supported for /messages"}
```

PUT and DELETE produced equivalent 405 responses with the corresponding method name in the message. `/messages/extra` returned 404.

### 3.4 Java HistoryClient — live server

```powershell
java -cp out http.HistoryClient
```

Observed:

```text
---- Message History ----
GET http://localhost:8080/messages
[{"sender":"alice","text":"Hello everyone","timestamp":"2026-11-25 08:55:17"},{"sender":"bob","text":"Hi Alice","timestamp":"2026-11-25 08:55:17"}]
```

Exit code: `0`.

### 3.5 Java HistoryClient — no server

After the server was stopped:

```powershell
java -cp out http.HistoryClient
```

Observed (stderr):

```text
[HTTP] Could not connect to http://localhost:8080/messages. Is HistoryServer running?
```

The client did not crash and did not print a fake history.

---

## 4. Cleanup checks

After the manual run:

```powershell
Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
```

Observed: no output, meaning no process was still listening on 8080.

```powershell
git status --short --branch
git diff --stat
```

Observed:

```text
## feature/http-history...origin/feature/http-history
?? src/main/java/http/HistoryClient.java
?? src/main/java/http/HistoryServer.java
?? src/test/
```

`git diff --stat` produced no output, confirming that **no tracked file was modified**. The new documentation files were added after this check.

At the time of the original run, the HTTP source and test files were untracked. They were later committed by the student as `fce200a` (`Implement HTTP client and server for message history API`). A clean rebuild after that commit was also tested and still passed 38/0.

---

## 5. What is not covered

- **Full TCP integration:** no `ChatServer` on this branch. The real `MessageStore` was populated by test code.
- **UDP/TLS/Multicast non-interference:** those modules do not exist here yet; only port separation and the absence of shared-file changes can be verified.
- **Wireshark:** Wireshark/Npcap/tshark were not found on PATH or in their standard install locations. No capture was performed.
- **Browser screenshots:** manual task; instructions are in [README.md](README.md#17-browser-testing).
- **Long-running/load testing:** the concurrency test is a correctness test at demonstration scale, not a benchmark.

These are stated instead of being claimed as passing.

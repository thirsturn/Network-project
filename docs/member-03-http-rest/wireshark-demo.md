# Wireshark Demonstration — Member 03 HTTP/REST API

**Target:** `http://localhost:8080/messages`  
**Required display filter:** `tcp.port == 8080 && http`

> **Status on the development machine (checked 2026-11-25):** Wireshark, tshark, dumpcap and Npcap were **not found on PATH or in their standard install locations**. No packet capture was performed, and no capture or screenshot is claimed. Every step below is a manual task for you.

This runbook is written for Windows 11 and Wireshark 4.x. Linux/macOS notes are included where the interface name differs.

---

## 0. What you will produce

At the end you should have:

- a live Wireshark capture showing:
  - `GET /messages HTTP/1.1`
  - `HTTP/1.1 200 OK` with the JSON body;
  - `HTTP/1.1 404 Not Found` for `/unknown`;
  - `HTTP/1.1 405 Method Not Allowed` for `POST /messages`;
- a saved capture file, for example `member03-http-history.pcapng`;
- screenshots listed in [screenshots-checklist.md](screenshots-checklist.md).

Keep the original capture file. Do not edit it after saving.

---

## 1. Install Wireshark and Npcap

1. Open <https://www.wireshark.org/download.html>.
2. Download the **Windows x64 Installer**.
3. Run the installer as Administrator.
4. When the installer offers **Npcap**, keep it selected and install it.
   - Npcap is the packet capture driver Wireshark needs on Windows.
   - Enable the **loopback capture** option. The wording varies by version; look for an option such as **Support loopback traffic** or **Install Npcap Loopback Adapter**.
   - If you already have Npcap installed without loopback support, run the Npcap installer again in repair/reinstall mode and enable loopback.
5. Finish the installation. A restart may be required before the capture driver works.
6. Start Wireshark. The welcome screen should list interfaces with live sparklines.

If Wireshark is installed but `tshark` is not on `PATH`, use the full path:

```text
C:\Program Files\Wireshark\tshark.exe
```

**Manual check:** no Wireshark/Npcap installation exists on the current machine, so this section must be performed by you.

---

## 2. Select the correct interface

This is the most common mistake. The URL is `localhost`, so all traffic stays inside the machine on the **loopback** interface. It does **not** pass through Wi-Fi or Ethernet.

### Windows

In the Wireshark welcome screen or `Capture → Options`, select one of:

- **Adapter for loopback traffic capture** (most common Npcap name),
- **Npcap Loopback Adapter**, or
- **Loopback**.

Do **not** select Wi-Fi or Ethernet.

If no loopback interface appears:

1. Reinstall or repair Npcap with loopback support enabled.
2. Restart Wireshark.
3. Open `Capture → Options` again.

### Linux / macOS

Select `lo` (Linux) or `lo0` (macOS).

### Optional command-line check

If `tshark` is available:

```powershell
& 'C:\Program Files\Wireshark\tshark.exe' -D
```

The output lists interface numbers and names. Find the loopback entry and note its number.

---

## 3. Start capturing

1. Double-click the loopback interface, or select it and click the blue shark-fin **Start** button.
2. Wireshark begins capturing all loopback traffic.
3. In the display-filter bar at the top, type:

```text
tcp.port == 8080 && http
```

4. Press **Enter**. The bar should turn green. If it turns red, the filter syntax is wrong (see Troubleshooting).

A **capture filter** can also be set before capture (`Capture → Options → Capture filter`):

```text
tcp port 8080
```

Capture filters discard packets before they reach the capture file. Display filters only hide packets from view. For this demonstration, use the required display filter so you can also inspect the TCP handshake if needed.

To see the TCP handshake, temporarily remove `&& http`:

```text
tcp.port == 8080
```

---

## 4. Start the server and generate HTTP requests

### 4.1 Build the project (once)

From the repository root in PowerShell:

```powershell
javac -d out (Get-ChildItem -Recurse -Filter *.java src/main/java | Select-Object -ExpandProperty FullName)
javac -cp out -d out-test (Get-ChildItem -Recurse -Filter *.java src/test/java | Select-Object -ExpandProperty FullName)
```

### 4.2 Start the seeded demo server

Terminal 1:

```powershell
java -cp "out;out-test" http.HistoryDemoServer
```

Wait for:

```text
[HTTP] History REST API started: http://localhost:8080/messages
[HTTP] Seeded 2 demo messages into the real MessageStore.
```

`HistoryDemoServer` is a test fixture. It seeds the real `MessageStore` because Member 01's TCP `ChatServer` is not on this branch yet. In the final integrated application, `Main` will start `HistoryServer` and the chat server will write the messages instead. Do not claim full TCP integration before that exists.

If you prefer an empty history for the `[]` demonstration:

```powershell
java -cp out http.HistoryServer
```

### 4.3 Send the requests

Terminal 2, one command at a time. Pause 2–3 seconds between commands so the packets are easy to identify.

```powershell
# 200 OK — normal history request
curl.exe -sS -i --http1.1 -H "Accept: application/json" http://localhost:8080/messages

# 404 Not Found — unknown endpoint
curl.exe -sS -i --http1.1 http://localhost:8080/unknown

# 404 Not Found — proves exact path matching
curl.exe -sS -i --http1.1 http://localhost:8080/messages/extra

# 405 Method Not Allowed — wrong method
curl.exe -sS -i --http1.1 -X POST http://localhost:8080/messages

# 405 — other unsupported methods
curl.exe -sS -i --http1.1 -X PUT http://localhost:8080/messages
curl.exe -sS -i --http1.1 -X DELETE http://localhost:8080/messages
```

Windows notes:

- Use **`curl.exe`**, not `curl`. In Windows PowerShell 5.1, `curl` is an alias for `Invoke-WebRequest`.
- `-sS` hides the progress meter but still shows errors.
- `-i` includes response headers.
- `--http1.1` keeps the request on HTTP/1.1, which is what `HttpServer` serves.
- `-H "Accept: application/json"` makes curl resemble the Java `HistoryClient`.

Each `curl.exe` command starts a new process and therefore a new TCP connection. That makes each request/response pair easy to isolate in Wireshark.

---

## 5. Identify `GET /messages`

With the display filter applied:

```text
tcp.port == 8080 && http
```

Look at the **Info** column in the packet list:

```text
GET /messages HTTP/1.1
HTTP/1.1 200 OK
```

Typical packet list columns:

| No. | Time | Source | Destination | Protocol | Length | Info |
|---:|---|---|---|---|---:|---|
| … | … | 127.0.0.1 | 127.0.0.1 | HTTP | … | GET /messages HTTP/1.1 |
| … | … | 127.0.0.1 | 127.0.0.1 | HTTP | … | HTTP/1.1 200 OK |

Both directions are loopback, so source and destination IP are both `127.0.0.1` (or `::1`). The distinguishing detail is the **port**: the request goes to destination port `8080`; the response comes from source port `8080`.

Useful narrower filters:

```text
tcp.port == 8080 && http.request.method == "GET"
tcp.port == 8080 && http.request.uri == "/messages"
tcp.port == 8080 && http.response.code == 200
tcp.port == 8080 && http.response.code == 404
tcp.port == 8080 && http.response.code == 405
tcp.port == 8080 && http.request.method == "POST"
```

Why the `&& http` part? It hides pure TCP packets such as SYN, ACK and FIN, leaving the HTTP request and response messages. To show the TCP three-way handshake, use `tcp.port == 8080` without `&& http`.

If Wireshark shows plain `TCP` instead of `HTTP`, port 8080 was not decoded as HTTP. Fix it with:

- Right-click one of the packets → **Decode As…** → **HTTP** for TCP port 8080, or
- **Analyze → Decode As → TCP port 8080 → HTTP**.

Then re-apply the display filter.

---

## 6. Inspect the HTTP request headers

Click the `GET /messages HTTP/1.1` packet. In the middle pane, expand:

```text
Hypertext Transfer Protocol
    GET /messages HTTP/1.1\r\n
        Request Method: GET
        Request URI: /messages
        Request Version: HTTP/1.1
    Host: localhost:8080\r\n
    User-Agent: curl/8.22.0\r\n
    Accept: application/json\r\n
    \r\n
```

Expected fields:

| Field | Meaning |
|---|---|
| Request Method | `GET` — read-only request |
| Request URI | `/messages` — the history resource |
| Request Version | `HTTP/1.1` |
| Host | `localhost:8080` — HTTP/1.1 requires a Host header |
| User-Agent | client identification; the version depends on your curl |
| Accept | `application/json` because the command included `-H` |

There is no request body for GET.

### Follow the whole conversation

Right-click the request → **Follow → TCP Stream**. You will see the request headers, the response headers and the JSON body in one view. Change the stream selector from ASCII to Raw if you want to inspect exact bytes.

---

## 7. Inspect the HTTP response headers

Click the `HTTP/1.1 200 OK` packet. Expand:

```text
Hypertext Transfer Protocol
    HTTP/1.1 200 OK\r\n
        Version: HTTP/1.1
        Status Code: 200
        Response Phrase: OK
    Content-type: application/json; charset=UTF-8\r\n
    Content-length: 147\r\n
    X-content-type-options: nosniff\r\n
    Cache-control: no-store\r\n
    Date: ...\r\n
    \r\n
```

| Header | What it proves |
|---|---|
| `Content-type` | The body is JSON, encoded in UTF-8 |
| `Content-length` | Exact byte length of the body; set by `sendResponseHeaders(status, length)` |
| `X-content-type-options` | The server explicitly prevents MIME sniffing |
| `Cache-control` | History is not cached (`no-store`) |
| `Date` | Added by the HTTP server; GMT, not local time |

The `Content-length` value depends on the number of messages and their text. In the recorded verification run it was `147`.

---

## 8. Inspect the JSON response body

Wireshark represents the response body in one of these ways, depending on its dissector version:

- `JavaScript Object Notation: application/json`
- `Line-based text data: application/json`
- `Line-based text data: text/plain`

Click the body item and look at the lower pane. Expected content from the seeded demo:

```json
[{"sender":"alice","text":"Hello everyone","timestamp":"2026-11-25 08:55:11"},{"sender":"bob","text":"Hi Alice","timestamp":"2026-11-25 08:55:11"}]
```

The server sends compact JSON on one line. The timestamp format is the project's actual format: `yyyy-MM-dd HH:mm:ss`, not ISO-8601 with `T`.

If the body is split across TCP segments, Wireshark may show a reassembly indicator such as `[Reassembled TCP Segments]`. Expand it or use **Follow → TCP Stream** to see the complete body.

The JSON body is evidence that:
- the endpoint read the real `MessageStore`;
- the fields are `sender`, `text` and `timestamp`;
- UTF-8 and `Content-Length` are correct.

---

## 9. Identify 200, 404 and 405 responses

### 200 OK

- Request: `GET /messages HTTP/1.1`
- Response: `HTTP/1.1 200 OK`
- Body: JSON array (or `[]` when the store is empty)
- Filter: `tcp.port == 8080 && http.response.code == 200`

### 404 Not Found

- Request: `GET /unknown HTTP/1.1` or `GET /messages/extra HTTP/1.1`
- Response: `HTTP/1.1 404 Not Found`
- Body: `{"status":404,"error":"Not Found","message":"Unknown endpoint: /unknown"}`
- Filter: `tcp.port == 8080 && http.response.code == 404`

### 405 Method Not Allowed

- Request: `POST /messages HTTP/1.1` (also PUT/DELETE)
- Response: `HTTP/1.1 405 Method Not Allowed`
- Response header: `Allow: GET`
- Body: `{"status":405,"error":"Method Not Allowed","message":"POST is not supported for /messages"}`
- Filter: `tcp.port == 8080 && http.response.code == 405`

To pair a 405 response with its request, temporarily clear the filter and look near the response in time order, or use **Follow → TCP Stream**. The request packet shows the method (`POST`, `PUT` or `DELETE`); the response packet shows `405`.

> A packet capture cannot prove the Java code path by itself. It proves what went over the wire. Combine it with the curl output and the test result when presenting.

---

## 10. Stop and save the capture

1. Click the red square **Stop capture** button.
2. `File → Save As…`
3. Choose a filename, for example:

```text
member03-http-history.pcapng
```

4. Save it in a folder you will use for evidence, for example:

```text
docs\member-03-http-rest\evidence\
```

Do not commit a capture file unless your team agrees. `.gitignore` does not ignore `.pcapng` files, so it would appear in `git status`. If the file is only for your own submission, keep it outside the repository or in an untracked folder.

Optional verification:

- `Statistics → Conversations → TCP` shows the loopback conversations on port 8080.
- `Statistics → HTTP → Requests` should list `GET /messages`, `GET /unknown`, `POST /messages`, etc., depending on what you captured.

---

## 11. Screenshots to capture

Use **Win + Shift + S** (Snipping Tool) or Alt + PrintScreen. Save PNG files with these suggested names:

| File | What must be visible |
|---|---|
| `01-wireshark-filter-get-200.png` | Display filter `tcp.port == 8080 && http`, packet list with GET and 200 |
| `02-request-headers.png` | Expanded GET request: method, URI, Host, User-Agent, Accept |
| `03-response-headers-json.png` | Expanded response: 200, Content-Type, Content-Length, JSON body |
| `04-http-404.png` | 404 response and JSON error body |
| `05-http-405-allow.png` | 405 response, `Allow: GET`, JSON error body |
| `06-curl-200.png` | Terminal showing `curl.exe -sS -i` output |
| `07-browser-json.png` | Browser showing `http://localhost:8080/messages` |
| `08-test-results.png` | Test harness showing `RESULT: 38 passed, 0 failed` |

The full checklist is in [screenshots-checklist.md](screenshots-checklist.md). Do not fabricate any screenshot. If a step fails, record the failure and fix it rather than editing the image.

---

## 12. Troubleshooting

### The packet list is empty

- You selected Wi-Fi/Ethernet instead of loopback. Localhost traffic never reaches those interfaces.
- The server is not running, or it failed to bind 8080.
- The display filter is too narrow. Clear it and check for any packets.
- The curl command used a proxy. For localhost, add `--noproxy '*'`:

```powershell
curl.exe --noproxy '*' -sS -i --http1.1 http://localhost:8080/messages
```

### No loopback interface in Wireshark

- Npcap is missing, or it was installed without loopback support.
- Reinstall/repair Npcap with loopback enabled and restart Wireshark.
- On locked-down university machines you may need Administrator rights.

### The display filter bar is red

- The filter syntax is wrong. Use exactly:

```text
tcp.port == 8080 && http
```

- In Wireshark, `&&` and `and` are both valid. Do not use shell-specific syntax.
- Clear the filter and retype it; a typo in `tcp.port` is easy to miss.

### Packets show `TCP`, not `HTTP`

- Right-click a packet → **Decode As…** → **HTTP** for TCP port 8080.
- Or use **Analyze → Decode As → TCP port 8080 → HTTP**.
- Re-apply the filter.

### Port 8080 is already in use

Check:

```powershell
Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue |
    Select-Object LocalAddress, LocalPort, OwningProcess
```

Stop the process that owns the port, or use `HistoryServer(int)` in a test. For the official demo, use 8080.

### `localhost` fails but the server is running

Try `http://127.0.0.1:8080/messages`. The server is bound to `localhost`; on some systems the IPv4 and IPv6 loopback names differ.

### HTTP response body is not visible in the packet list

Expand the response packet in the middle pane, or use **Follow → TCP Stream**. Wireshark may store the body in a later TCP segment that is reassembled by the HTTP dissector.

---

## 13. Optional tshark (command-line) evidence

If Wireshark is installed, `tshark` can produce text evidence without screenshots. These commands are **manual**; tshark was not found on PATH or in the standard Wireshark install directory on the development machine.

List interfaces:

```powershell
& 'C:\Program Files\Wireshark\tshark.exe' -D
```

Start a capture on the loopback interface number (replace `1` with the correct number):

```powershell
& 'C:\Program Files\Wireshark\tshark.exe' -i 1 -f "tcp port 8080" -w member03-http-history.pcapng
```

Read it back with the required display filter:

```powershell
& 'C:\Program Files\Wireshark\tshark.exe' -r member03-http-history.pcapng -Y "tcp.port == 8080 && http" -V
```

Extract a compact table:

```powershell
& 'C:\Program Files\Wireshark\tshark.exe' -r member03-http-history.pcapng -Y "tcp.port == 8080 && http" -T fields -e frame.number -e ip.src -e ip.dst -e http.request.method -e http.request.uri -e http.response.code -e http.content_type
```

Stop the capture with **Ctrl + C**.

---

## 14. Manual execution checklist

- [ ] Wireshark installed.
- [ ] Npcap installed with loopback support.
- [ ] Loopback interface selected.
- [ ] Display filter `tcp.port == 8080 && http` applied.
- [ ] `java -cp "out;out-test" http.HistoryDemoServer` running on 8080.
- [ ] `curl.exe` requests sent for 200, 404 and 405.
- [ ] GET request headers inspected.
- [ ] 200 response headers and JSON body inspected.
- [ ] 404 response inspected.
- [ ] 405 response and `Allow: GET` inspected.
- [ ] Capture stopped and saved as `.pcapng`.
- [ ] Screenshots captured without editing.
- [ ] Capture file stored outside the repository unless the team agrees to commit it.

No item above has been marked complete on this machine; the tools were not found on PATH or in their standard install locations.

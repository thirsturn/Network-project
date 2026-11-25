# Screenshot and Evidence Checklist — Member 03

This file lists the evidence you must capture **yourself**. No screenshot in this repository was fabricated or pre-filled.

Create a folder such as:

```text
docs/member-03-http-rest/screenshots/
```

Commit the images only if your team agrees. Otherwise keep them with your submission. Do not edit, crop out error messages, or combine images in a way that changes the evidence.

Use **Win + Shift + S** (Snipping Tool) or **Alt + PrintScreen**, and save PNG files.

---

## 1. Required screenshots

| # | Suggested filename | What must be visible | How to produce it |
|---:|---|---|---|
| 1 | `01-test-results.png` | The full test harness summary `RESULT: 38 passed, 0 failed` and several PASS lines | Run `java -cp "out;out-test" http.HttpHistoryTest` in a large terminal |
| 2 | `02-server-startup.png` | `History REST API started: http://localhost:8080/messages` | Run `java -cp "out;out-test" http.HistoryDemoServer` |
| 3 | `03-curl-200.png` | Command `curl.exe -sS -i --http1.1 ... /messages`, status `200 OK`, `Content-type`, JSON body | Run the command in a second terminal |
| 4 | `04-curl-404.png` | Status `404 Not Found` and the JSON error body | `curl.exe -sS -i --http1.1 http://localhost:8080/unknown` |
| 5 | `05-curl-405.png` | Status `405 Method Not Allowed`, `Allow: GET`, JSON error body | `curl.exe -sS -i --http1.1 -X POST http://localhost:8080/messages` |
| 6 | `06-browser-json.png` | Browser address bar `http://localhost:8080/messages` and the JSON array | Open the URL while the server runs |
| 7 | `07-java-client.png` | `---- Message History ----` and the JSON array | `java -cp out http.HistoryClient` while the server runs |
| 8 | `08-wireshark-filter.png` | Display filter `tcp.port == 8080 && http` and packets `GET /messages` / `HTTP/1.1 200 OK` | Wireshark on the loopback interface, after sending requests |
| 9 | `09-request-headers.png` | Expanded GET request: method, URI, Host, User-Agent, Accept | Click the GET packet in Wireshark |
| 10 | `10-response-headers-json.png` | Expanded 200 response: Content-Type, Content-Length, JSON body | Click the response packet and expand the body |
| 11 | `11-http-404.png` | 404 response and JSON error body in Wireshark | Send `GET /unknown`, click the 404 response |
| 12 | `12-http-405-allow.png` | 405 response, `Allow: GET`, JSON error body | Send `POST /messages`, click the 405 response |
| 13 | `13-port-cleanup.png` | No listener on 8080 after stopping the server (optional) | `Get-NetTCPConnection -LocalPort 8080 -State Listen` after Ctrl+C |

---

## 2. Before taking screenshots

- [ ] Increase the terminal font size (for example 18–20 pt) so text is readable in the report.
- [ ] Use a clean terminal with the commands and outputs visible.
- [ ] Do not hide error output; the 404 and 405 are part of the evidence.
- [ ] Start each capture with the command visible, not only the result.
- [ ] For Wireshark, make sure the filter bar is visible and green.
- [ ] For the browser, include the address bar in the screenshot.
- [ ] Capture after a fresh run; do not reuse screenshots from a different date without saying so.

---

## 3. Wireshark screenshot details

### 08 — Filter and packet list

Must show:

- interface: loopback;
- display filter: `tcp.port == 8080 && http`;
- packet list rows containing `GET /messages HTTP/1.1` and `HTTP/1.1 200 OK`.

### 09 — Request headers

Click the GET packet, expand:

```text
Hypertext Transfer Protocol
    GET /messages HTTP/1.1
    Host: localhost:8080
    User-Agent: ...
    Accept: application/json
```

### 10 — Response headers and JSON

Click the 200 response, expand:

```text
HTTP/1.1 200 OK
Content-type: application/json; charset=UTF-8
Content-length: ...
Cache-control: no-store
X-content-type-options: nosniff
```

Then expand the JSON body item (`JavaScript Object Notation` or `Line-based text data`).

### 11 and 12 — 404 and 405

Show the status line, the relevant headers (`Allow: GET` for 405), and the JSON error body.

---

## 4. Evidence integrity statement

If you include a written statement with your submission, use something like:

> "All screenshots were captured by me from a live run on [date] on [machine/OS]. The Wireshark screenshots show traffic captured on the loopback interface with the filter `tcp.port == 8080 && http`. No image was edited to change output."

Do not claim this statement until you have actually captured the images.

---

## 5. Minimum submission set

If time is short, capture at least:

1. `01-test-results.png`
2. `03-curl-200.png`
3. `04-curl-404.png`
4. `05-curl-405.png`
5. `06-browser-json.png`
6. `08-wireshark-filter.png`
7. `10-response-headers-json.png`

The remaining images strengthen the report but are not essential to prove the core behaviour.

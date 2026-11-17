package common;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Represents a chat message exchanged across the network and stored in history.
 */
public class Message {

    private static final DateTimeFormatter FORMATTER = 
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final String sender;
    private final String text;
    private final Instant timestamp;

    public Message(String sender, String text) {
        this(sender, text, Instant.now());
    }

    public Message(String sender, String text, Instant timestamp) {
        this.sender = sender != null ? sender.trim() : "Unknown";
        this.text = text != null ? text.trim() : "";
        this.timestamp = timestamp != null ? timestamp : Instant.now();
    }

    public String getSender() {
        return sender;
    }

    public String getText() {
        return text;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getFormattedTimestamp() {
        return FORMATTER.format(timestamp);
    }

    /**
     * Formats message for TCP broadcast: MSG|sender|text
     */
    public String toProtocolString() {
        return "MSG|" + escapeDelimiter(sender) + "|" + escapeDelimiter(text);
    }

    /**
     * Serializes this message into a JSON object string for the HTTP REST API.
     */
    public String toJson() {
        return String.format(
            "{\"sender\":\"%s\",\"text\":\"%s\",\"timestamp\":\"%s\"}",
            escapeJson(sender),
            escapeJson(text),
            escapeJson(getFormattedTimestamp())
        );
    }

    private String escapeDelimiter(String value) {
        return value.replace("|", "/");
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\b", "\\b")
                    .replace("\f", "\\f")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }

    @Override
    public String toString() {
        return "[" + getFormattedTimestamp() + "] " + sender + ": " + text;
    }
}

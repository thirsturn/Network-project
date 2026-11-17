package common;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Thread-safe in-memory store for chat messages.
 * The TCP Chat Server writes messages to this store.
 * The HTTP REST API reads messages to serve history requests.
 */
public class MessageStore {

    private static final MessageStore INSTANCE = new MessageStore();

    private final List<Message> messages = new CopyOnWriteArrayList<>();

    public static MessageStore getInstance() {
        return INSTANCE;
    }

    /**
     * Adds a message to the store.
     */
    public void addMessage(Message message) {
        if (message != null) {
            messages.add(message);
        }
    }

    /**
     * Adds a message by sender and text.
     */
    public void addMessage(String sender, String text) {
        addMessage(new Message(sender, text));
    }

    /**
     * Returns an unmodifiable list of all stored messages.
     */
    public List<Message> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    /**
     * Returns the most recent N messages.
     */
    public List<Message> getRecentMessages(int limit) {
        int size = messages.size();
        if (size <= limit) {
            return Collections.unmodifiableList(messages);
        }
        return Collections.unmodifiableList(messages.subList(size - limit, size));
    }

    /**
     * Serializes all messages into a JSON array string for the HTTP REST API.
     */
    public String toJsonArray() {
        return "[" + messages.stream()
                .map(Message::toJson)
                .collect(Collectors.joining(",")) + "]";
    }

    /**
     * Clears all stored messages.
     */
    public void clear() {
        messages.clear();
    }
}

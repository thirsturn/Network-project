package common;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe store for active user sessions.
 * Maps session tokens to usernames.
 * The TLS Auth Service writes to it upon successful login.
 * The TCP Chat Server reads from it to authenticate client connections.
 */
public class SessionStore {

    private static final SessionStore INSTANCE = new SessionStore();

    // Map: Token -> Username
    private final Map<String, String> tokenToUserMap = new ConcurrentHashMap<>();

    public static SessionStore getInstance() {
        return INSTANCE;
    }

    /**
     * Creates a new unique session token for the given username and stores it.
     * @param username user identifier
     * @return generated session token
     */
    public String createSession(String username) {
        String token = UUID.randomUUID().toString();
        tokenToUserMap.put(token, username);
        return token;
    }

    /**
     * Stores a pre-existing token and username pair.
     */
    public void storeSession(String token, String username) {
        tokenToUserMap.put(token, username);
    }

    /**
     * Retrieves the username associated with the given session token.
     * @param token session token
     * @return username, or null if token is invalid/expired
     */
    public String getUsername(String token) {
        if (token == null) return null;
        return tokenToUserMap.get(token);
    }

    /**
     * Checks if the given session token is valid.
     */
    public boolean isValidToken(String token) {
        return token != null && tokenToUserMap.containsKey(token);
    }

    /**
     * Invalidates and removes a session by token.
     */
    public void removeSession(String token) {
        if (token != null) {
            tokenToUserMap.remove(token);
        }
    }

    /**
     * Clears all active sessions (e.g., on server reset).
     */
    public void clear() {
        tokenToUserMap.clear();
    }
}

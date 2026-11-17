package common;

/**
 * Global configuration settings for Campus Collaboration Hub.
 * Contains network ports, multicast group addresses, and keystore constants.
 */
public final class Config {

    private Config() {
        // Prevent instantiation
    }

    // Default Host
    public static final String DEFAULT_HOST = "localhost";

    // Ports
    public static final int AUTH_TLS_PORT = 5443;
    public static final int CHAT_TCP_PORT = 5000;
    public static final int STATUS_UDP_PORT = 5001;
    public static final int REST_HTTP_PORT = 8080;
    public static final int ANNOUNCEMENT_MULTICAST_PORT = 5002;

    // Multicast Group
    public static final String MULTICAST_GROUP_ADDRESS = "230.0.0.1";

    // SSL / TLS Keystore Configurations
    public static final String KEYSTORE_PATH = "keystore.jks";
    public static final String KEYSTORE_PASSWORD = "changeit";
}

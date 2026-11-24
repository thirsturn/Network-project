package udp;

import common.Config;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SERVER SIDE of the UDP presence feature (Member 2 - Asanka).
 *
 * Sends "ONLINE|user" and "OFFLINE|user" datagrams to every connected client
 * whenever someone joins or leaves the chat.
 *
 * Why UDP?
 *  - Presence updates are small and frequent. If one is lost it does not matter
 *    much, because the next update corrects the state.
 *  - UDP is connectionless: no handshake, no connection to keep open, so it is
 *    cheap to fire one packet at many clients.
 *
 * How it is used (by the TCP ChatServer, Member 1):
 *   StatusNotifier notifier = new StatusNotifier();
 *   chatServer.setOnUserOnlineListener(notifier::notifyOnline);
 *   chatServer.setOnUserOfflineListener(notifier::notifyOffline);
 */
public class StatusNotifier {

    // One UDP socket is enough to send to all clients.
    // It is bound to port 5001, so every packet leaves from source port 5001
    // and can be found in Wireshark with: udp.port == 5001
    private final DatagramSocket socket;

    // Who should receive notifications: username -> (IP address, UDP port).
    // ConcurrentHashMap because many TCP client threads call us at the same time.
    private final Map<String, InetSocketAddress> clients = new ConcurrentHashMap<>();

    public StatusNotifier() throws SocketException {
        this.socket = new DatagramSocket(Config.STATUS_UDP_PORT);
        System.out.println("[UDP] StatusNotifier ready on port " + Config.STATUS_UDP_PORT);
    }

    /**
     * Called when a user connects. The client told the chat server its UDP port
     * in "AUTH|token|udpPort". Clients run on the same machine for the demo,
     * so we use localhost as the address.
     */
    public void notifyOnline(String username, int udpPort) {
        // The chat server passes -1 when a client has no UDP listener.
        // InetSocketAddress does not accept negative ports, so we use 0 = "no listener".
        int port = Math.max(udpPort, 0);
        notifyOnline(username, new InetSocketAddress(Config.DEFAULT_HOST, port));
    }

    /**
     * Same as above but with the client's real address (use this when clients
     * run on other machines in the LAN).
     */
    public void notifyOnline(String username, InetSocketAddress clientAddress) {
        // 1. Tell everyone ALREADY online that this user has joined.
        broadcast("ONLINE|" + username, username);

        // 2. Tell the new user who is already online, so their list is complete.
        for (String other : clients.keySet()) {
            send("ONLINE|" + other, clientAddress);
        }

        // 3. Remember the new user so they get future notifications.
        //    (A port <= 0 means the client has no UDP listener -> skip it.)
        if (clientAddress.getPort() > 0) {
            clients.put(username, clientAddress);
        }
    }

    /**
     * Called when a user disconnects or sends QUIT.
     * The udpPort parameter is unused, it only exists so the method matches
     * the ChatServer callback signature (username, udpPort).
     */
    public void notifyOffline(String username, int udpPort) {
        notifyOffline(username);
    }

    public void notifyOffline(String username) {
        // Stop sending to this user, then tell everyone else they left.
        clients.remove(username);
        broadcast("OFFLINE|" + username, username);
    }

    /** Sends a text message to every registered client except 'skipUser'. */
    private void broadcast(String text, String skipUser) {
        for (Map.Entry<String, InetSocketAddress> entry : clients.entrySet()) {
            if (!entry.getKey().equals(skipUser)) {
                send(text, entry.getValue());
            }
        }
    }

    /**
     * The core UDP code:
     *  - turn the text into bytes
     *  - wrap the bytes in a DatagramPacket together with the destination
     *  - socket.send() fires it off. There is NO delivery guarantee and NO reply.
     */
    private void send(String text, InetSocketAddress target) {
        if (target.getPort() <= 0) {
            return;
        }
        try {
            byte[] data = text.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(data, data.length, target);
            socket.send(packet);
            System.out.println("[UDP] Sent '" + text + "' to " + target);
        } catch (IOException e) {
            // UDP is "fire and forget": if a send fails we just log it and move on.
            System.out.println("[UDP] Failed to send to " + target + ": " + e.getMessage());
        }
    }

    /** Closes the UDP socket when the server shuts down. */
    public void close() {
        socket.close();
    }

    /**
     * Standalone test (no chat server needed):
     *   1. Run:  java -cp out udp.StatusListener 6001
     *   2. Run:  java -cp out udp.StatusNotifier 6001
     * The listener should print the ONLINE / OFFLINE notifications.
     */
    public static void main(String[] args) throws Exception {
        int listenerPort = args.length > 0 ? Integer.parseInt(args[0]) : 6001;
        StatusNotifier notifier = new StatusNotifier();

        // Pretend "asanka" is the listening client, then other users come and go.
        notifier.notifyOnline("asanka", new InetSocketAddress(InetAddress.getLoopbackAddress(), listenerPort));
        Thread.sleep(1000);
        notifier.notifyOnline("kaveen", -1);   // -1 = this fake user has no listener
        Thread.sleep(1000);
        notifier.notifyOnline("pasindu", -1);
        Thread.sleep(1000);
        notifier.notifyOffline("kaveen");

        notifier.close();
    }
}

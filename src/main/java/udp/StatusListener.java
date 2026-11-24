package udp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

/**
 * CLIENT SIDE of the UDP presence feature (Member 2 - Asanka).
 *
 * Runs in its own background thread inside the ConsoleClient and waits for
 * "ONLINE|user" / "OFFLINE|user" datagrams from the StatusNotifier.
 *
 * How it is used (by ConsoleClient, Member 1):
 *   StatusListener listener = new StatusListener();   // picks a free port
 *   listener.start();                                 // starts the thread
 *   chatClient.connect(token, listener.getPort());    // sends AUTH|token|port
 *   ...
 *   listener.stop();                                  // on /quit
 */
public class StatusListener implements Runnable {

    // Max size of one datagram we expect. "OFFLINE|<username>" is tiny,
    // so 1 KB is more than enough.
    private static final int BUFFER_SIZE = 1024;

    private final DatagramSocket socket;
    private volatile boolean running = true;

    /**
     * Port 0 = "let the operating system choose any free port".
     * This lets several clients run on the same machine without port clashes.
     */
    public StatusListener() throws SocketException {
        this(0);
    }

    /** Listen on a specific port (useful for testing). */
    public StatusListener(int port) throws SocketException {
        this.socket = new DatagramSocket(port);
    }

    /** The port we listen on. The client sends this to the server in AUTH|token|udpPort. */
    public int getPort() {
        return socket.getLocalPort();
    }

    /** Starts listening in a background (daemon) thread so the console stays free for typing. */
    public void start() {
        Thread thread = new Thread(this, "udp-status-listener");
        thread.setDaemon(true); // daemon = does not keep the program alive on exit
        thread.start();
        System.out.println("[UDP] Listening for presence updates on port " + getPort());
    }

    /**
     * The core UDP receive loop:
     *  - create an empty DatagramPacket as a buffer
     *  - socket.receive() BLOCKS until a datagram arrives and fills the packet
     *  - read the bytes back into text and display it
     */
    @Override
    public void run() {
        byte[] buffer = new byte[BUFFER_SIZE];
        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet); // waits here until a packet arrives

                // Only use the bytes actually received (packet.getLength()).
                String text = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                handle(text);
            } catch (IOException e) {
                // stop() closes the socket, which makes receive() throw -> exit loop quietly.
                if (running) {
                    System.out.println("[UDP] Receive error: " + e.getMessage());
                }
            }
        }
    }

    /** Turns a raw "TYPE|user" message into a friendly console line. */
    private void handle(String text) {
        String[] parts = text.split("\\|", 2);
        if (parts.length < 2) {
            System.out.println("[UDP] Unknown message: " + text);
            return;
        }
        switch (parts[0]) {
            case "ONLINE":
                System.out.println("[STATUS] " + parts[1] + " is now online");
                break;
            case "OFFLINE":
                System.out.println("[STATUS] " + parts[1] + " went offline");
                break;
            default:
                System.out.println("[UDP] Unknown message: " + text);
        }
    }

    /** Stops the listener thread by closing the socket. */
    public void stop() {
        running = false;
        socket.close();
    }

    /**
     * Standalone test: java -cp out udp.StatusListener 6001
     * Then run udp.StatusNotifier 6001 in another terminal.
     */
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 6001;
        StatusListener listener = new StatusListener(port);
        listener.run(); // run on the main thread so the program keeps waiting
    }
}

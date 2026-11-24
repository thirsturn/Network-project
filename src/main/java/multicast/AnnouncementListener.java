package multicast;

import common.Config;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

public final class AnnouncementListener implements Runnable, AutoCloseable {
    private static final int RECEIVE_TIMEOUT_MS = 500;

    private final String prompt;
    private volatile boolean closed;
    private MulticastSocket socket;
    private InetSocketAddress membership;

    public AnnouncementListener() {
        this("");
    }

    public AnnouncementListener(String prompt) {
        this.prompt = prompt;
    }

    @Override
    public void run() {
        try {
            MulticastSocket receiver = openSocket();
            if (receiver == null) {
                return;
            }

            byte[] buffer = new byte[AnnouncementSender.MAX_PACKET_BYTES];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            while (!closed && !Thread.currentThread().isInterrupted()) {
                packet.setLength(buffer.length);
                try {
                    receiver.receive(packet);
                } catch (SocketTimeoutException e) {
                    // A timeout lets the loop check for interruption without waiting for traffic.
                    continue;
                }

                String message = new String(packet.getData(), packet.getOffset(),
                        packet.getLength(), StandardCharsets.UTF_8);
                if (!closed && message.startsWith(AnnouncementSender.PREFIX)) {
                    String text = message.substring(AnnouncementSender.PREFIX.length());
                    String newline = System.lineSeparator();
                    System.out.print(newline + "[ANNOUNCEMENT]: " + text + newline + prompt);
                    System.out.flush();
                }
            }
        } catch (IOException e) {
            if (!closed) {
                System.err.println("Announcement listener stopped: " + e.getMessage());
            }
        } finally {
            close();
        }
    }

    private synchronized MulticastSocket openSocket() throws IOException {
        if (closed || Thread.currentThread().isInterrupted()) {
            return null;
        }

        int port = Config.ANNOUNCEMENT_MULTICAST_PORT;
        InetSocketAddress group = new InetSocketAddress(Config.MULTICAST_GROUP_ADDRESS, port);
        socket = new MulticastSocket(null);
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(port));
        socket.setSoTimeout(RECEIVE_TIMEOUT_MS);
        socket.joinGroup(group, null);
        membership = group;
        return socket;
    }

    @Override
    public synchronized void close() {
        closed = true;
        if (socket != null) {
            try {
                if (membership != null) {
                    socket.leaveGroup(membership, null);
                }
            } catch (IOException e) {
                // Closing the socket also releases its multicast membership.
            } finally {
                socket.close();
                socket = null;
                membership = null;
            }
        }
    }
}

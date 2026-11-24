package multicast;

import common.Config;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public final class AnnouncementSender {
    static final String PREFIX = "ANNOUNCE|";
    static final int MAX_PACKET_BYTES = 65_507;

    private AnnouncementSender() {
    }

    public static void main(String[] args) {
        try (DatagramSocket socket = new DatagramSocket();
             Scanner scanner = new Scanner(System.in)) {
            InetAddress group = InetAddress.getByName(Config.MULTICAST_GROUP_ADDRESS);
            int port = Config.ANNOUNCEMENT_MULTICAST_PORT;
            System.out.println("Multicast announcements (type exit or quit to close)");

            while (true) {
                System.out.print("Announcement> ");
                System.out.flush();
                if (!scanner.hasNextLine()) {
                    break;
                }

                String message = scanner.nextLine();
                String command = message.strip();
                if (command.equalsIgnoreCase("exit") || command.equalsIgnoreCase("quit")) {
                    break;
                }

                byte[] payload = (PREFIX + message).getBytes(StandardCharsets.UTF_8);
                if (payload.length > MAX_PACKET_BYTES) {
                    System.err.println("Announcement is too long for one UDP packet.");
                    continue;
                }

                try {
                    socket.send(new DatagramPacket(payload, payload.length, group, port));
                    System.out.println("Announcement sent to " + group.getHostAddress() + ":" + port);
                } catch (IOException e) {
                    System.err.println("Could not send announcement: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Could not start announcement sender: " + e.getMessage());
        }
    }
}

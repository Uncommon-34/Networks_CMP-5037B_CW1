
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Scanner;

public class InputHandshakeSenderThread implements Runnable {

    public void start() {
        Thread thread = new Thread(this);
        thread.start();
    }

    public void run() {
        if (AudioDuplex.IP == null || AudioDuplex.IP.isEmpty()) {
            Scanner scanner = new Scanner(System.in);
            System.out.print("No IP address found. Please enter IP address: ");
            String ip = scanner.nextLine();
            if (ip != null && !ip.isEmpty()) {
                AudioDuplex.IP = ip;
                System.out.println(
                        "IP address set to: " + AudioDuplex.IP + ".\n Starting handshake and Authentication...");
            }

            if (AudioDuplex.KEY == null || AudioDuplex.KEY.isEmpty()) {
                try {
                    // update tomatch client ip
                    InetAddress clientIP = InetAddress.getByName(AudioDuplex.IP);
                    DatagramSocket sending_socket = new DatagramSocket();
                    ByteBuffer buffer = ByteBuffer.allocate(4 + 8);

                    byte[] packetData = buffer.array();
                    DatagramPacket packet = new DatagramPacket(packetData, packetData.length, clientIP,
                            AudioDuplex.PORT);
                    System.out.println("Sending handshake packet");
                    sending_socket.send(packet);

                } catch (Exception e) {
                    System.out.println("ERROR: InputHandshakeSenderThread: Could not initialize socket.");
                    e.printStackTrace();
                    System.exit(0);
                }

            } else {
                System.out.println("Empty key; sender will not start.");
            }
            scanner.close();
        }
    }
}

/*
File: AudioDuplex.java
Author's: CaileyGR, HarryT,
Notes: Packet interleaver implementation and Diffie-Hellman key exchange implementation
*/

//Encryption branch 
import java.util.Scanner;
import java.math.BigInteger;
import java.net.*;
import java.nio.ByteBuffer;

public class AudioDuplex {
    // can be 1, 2, 3, or 4 to select which datagram socket to use
    public static volatile int CHANNEL = 1;
    // controls sender and receiver loops - set to false to stop both threads
    public static volatile boolean RUNNING = true;
    // interleaver depth - values: 2, 3, 4 - 1 technically disables interleaving
    public static volatile int DEPTH = 2;
    // set to false to disable encryption and key exchange
    public static volatile boolean ENCRYPTION = true;
    // flag to indicate handshake packet received and IP/port set
    public static volatile boolean HANDSHAKE_RECEIVED = false;

    // port used for both sending and receiving packets
    public static volatile int SENDER_PORT = 55555;
    public static volatile int RECEIVER_PORT = 55556;

    // destination IP address - use "localhost" if running on the same machine
    public static volatile String SENDER_IP = "";

    // encryption key, must match sender and receiver for audio to be leave as ""
    // to skip encryption
    public static volatile String KEY;

    // Diffie-Hellman parameters
    public static final BigInteger P = BigInteger.probablePrime(256, new java.util.Random(42));
    public static final BigInteger G = BigInteger.valueOf(2);

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        // Prompt for IP if not set
        if (SENDER_IP == null || SENDER_IP.isEmpty()) {
            System.out.print("Enter IP address: ");
            String ip = scanner.nextLine();
            SENDER_IP = ip;
        }

        if (ENCRYPTION) {
            // Start handshake receiver thread first to listen for incoming handshake
            Thread hsReceiver = new Thread(new HandShakeReciverThread());
            hsReceiver.start();
            // Wait for handshake to be received and IP/port set
            while (!HANDSHAKE_RECEIVED) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            // Prompt for IP/port only if not set by received handshake
            if (SENDER_IP == null || SENDER_IP.isEmpty()) {
                System.out.print("Enter IP address: ");
                String ip = scanner.nextLine();
                SENDER_IP = ip;
            }
            if (SENDER_PORT == 0) {
                System.out.print("Enter port: ");
                int port = scanner.nextInt();
                SENDER_PORT = port;
                RECEIVER_PORT = port;
            }
            // Start handshake sender thread
            Thread hsSender = new Thread(new HandShakeSenderThread());
            hsSender.start();
            // Wait for handshake to complete
            try {
                hsSender.join();
                hsReceiver.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } else {
            // Prompt for IP/port if encryption disabled
            if (SENDER_IP == null || SENDER_IP.isEmpty()) {
                System.out.print("Enter IP address: ");
                String ip = scanner.nextLine();
                SENDER_IP = ip;
            }
            if (SENDER_PORT == 0) {
                System.out.print("Enter port: ");
                int port = scanner.nextInt();
                SENDER_PORT = port;
                RECEIVER_PORT = port;
            }
        }

        // Start exit listener thread
        new Thread(() -> {
            Scanner s = new Scanner(System.in);
            while (RUNNING) {
                String input = s.nextLine();
                if ("exit".equals(input)) {
                    // Send exit packet to other client
                    try {
                        DatagramSocket socket = new DatagramSocket();
                        ByteBuffer buffer = ByteBuffer.allocate(4 + 8 + 512);
                        buffer.putInt(-2);
                        buffer.putLong(System.currentTimeMillis());
                        byte[] packetData = buffer.array();
                        DatagramPacket packet = new DatagramPacket(packetData, packetData.length,
                                InetAddress.getByName(SENDER_IP), SENDER_PORT);
                        socket.send(packet);
                        socket.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    RUNNING = false;
                    break;
                }
            }
        }).start();
    }
}
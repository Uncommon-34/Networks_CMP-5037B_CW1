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
    public static volatile int DEPTH = 1;
    // set to false to disable encryption and key exchange
    public static volatile boolean ENCRYPTION = false;

    // port used for both sending and receiving packets
    public static volatile int SENDER_PORT = 55555;
    public static volatile int RECEIVER_PORT = 55555;

    // destination IP address - use "localhost" if running on the same machine
    public static volatile String SENDER_IP = "localhost";

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
        // Prompt for port if not set
        if (SENDER_PORT == 0 || RECEIVER_PORT == 0) {
            System.out.print("Enter port: ");
            int port = scanner.nextInt();
            SENDER_PORT = port;
            RECEIVER_PORT = port;
        }

        if (ENCRYPTION) {
            // Start handshake threads
            Thread hsSender = new Thread(new HandShakeSenderThread());
            Thread hsReceiver = new Thread(new HandShakeReciverThread());
            hsSender.start();
            hsReceiver.start();
            // Wait for handshake to complete
            try {
                hsSender.join();
                hsReceiver.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // Start audio threads
        AudioReceiverThread receiver = new AudioReceiverThread();
        AudioSenderThread sender = new AudioSenderThread();
        receiver.start();
        sender.start();

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
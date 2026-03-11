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
    // set to false to disable decryption
    public static volatile boolean DECRYPTION = true;

    // port used for both sending and receiving packets
    public static volatile int PORT = 5555;

    // destination IP address - use "localhost" if running on the same machine
    public static volatile String SENDER_IP = "";

    // flag to indicate handshake received
    public static volatile boolean HANDSHAKE_RECEIVED = false;

    // encryption key, must match sender and receiver for audio to be leave as ""
    // to skip encryption
    public static volatile String KEY = "2147483647";

    // Diffie-Hellman parameters
    public static final BigInteger P = BigInteger.probablePrime(256, new java.util.Random(42));
    public static final BigInteger G = BigInteger.valueOf(2);

    public static void main(String[] args) {
        if (ENCRYPTION) {
            // Start handshake receiver thread first to listen for incoming handshake
            Thread hsReceiver = new Thread(new HandShakeReciverThread());
            hsReceiver.start();

            // Start handshake sender thread
            Thread hsSender = new Thread(new HandShakeSenderThread());
            hsSender.start();

            while (!HANDSHAKE_RECEIVED) {
                // Wait for handshake to complete before starting audio threads
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            hsSender.interrupt();
            hsReceiver.interrupt();

            AudioReceiverThread receiver = new AudioReceiverThread();
            AudioSenderThread sender = new AudioSenderThread();

            System.out.println("Handshake complete. Starting audio threads with encryption.");
            receiver.start();
            sender.start();
        } else {
            Scanner scanner = new Scanner(System.in);
            if (AudioDuplex.SENDER_IP == null || AudioDuplex.SENDER_IP.isEmpty()) {
                System.out.print("Enter IP address: ");
                String ip = scanner.nextLine();
                AudioDuplex.SENDER_IP = ip;
            }
            // Start audio threads without encryption
            AudioReceiverThread receiver = new AudioReceiverThread();
            AudioSenderThread sender = new AudioSenderThread();

            receiver.start();
            sender.start();
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
                                InetAddress.getByName(SENDER_IP), PORT);
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
/*
File: AudioDuplex.java
Author's: CaileyGR, HarryT,
Notes: Packet interleaver implementation and Diffie-Hellman key exchange implementation
*/

import java.util.Scanner;
import java.math.BigInteger;
import java.net.*;
import java.nio.ByteBuffer;

public class AudioDuplex {

    // 1, 2, 3, or 4 — selects which DatagramSocket implementation to use
    public static volatile int CHANNEL = 1;

    // Set to false to stop both sender and receiver loops
    public static volatile boolean RUNNING = true;

    // Interleaver depth — 2, 3, or 4. Higher = more burst loss resilience, more latency
    public static volatile int DEPTH = 2;

    // Set to false to send audio unencrypted
    public static volatile boolean ENCRYPTION = true;

    public static volatile boolean DECRYPTION = false;

    // Port for audio packets
    public static volatile int A_PORT = 5555;

    // Port for handshake packets
    public static volatile int H_PORT = 4444;

    // Destination IP — set at runtime via handshake or manual input
    public static volatile String SENDER_IP = "";

    // Set to true once Diffie-Hellman handshake completes on both sides
    public static volatile boolean HANDSHAKE_RECEIVED = false;

    // Shared encryption key derived from Diffie-Hellman — set automatically by handshake threads
    public static volatile String KEY = "";

    // Diffie-Hellman parameters
    // P is a 512-bit safe prime — large enough to make discrete logarithm computationally infeasible
    public static final BigInteger P = new BigInteger(
            "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD1" +
                    "29024E088A67CC74020BBEA63B139B22514A08798E3404DD" +
                    "EF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245" +
                    "E485B576625E7EC6F44C42E9A63A3620FFFFFFFFFFFFFFFF",
            16);
    // G is the generator — standard value of 2 used in Diffie-Hellman
    public static final BigInteger G = BigInteger.valueOf(2);

    public static void main(String[] args) {
        if (ENCRYPTION || DECRYPTION) {
            // Start handshake receiver first so it is listening before sender transmits
            Thread hsReceiver = new Thread(new HandShakeReciverThread());
            hsReceiver.start();

            // Start handshake sender — prompts for IP if not already set
            Thread hsSender = new Thread(new HandShakeSenderThread());
            hsSender.start();

            // Block until Diffie-Hellman completes and KEY is set on both sides
            while (!HANDSHAKE_RECEIVED) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            hsSender.interrupt();
            hsReceiver.interrupt();

            System.out.println("Handshake complete. Starting audio threads with encryption.");
            System.out.println("Shared secret computed: " + AudioDuplex.KEY);

            AudioReceiverThread receiver = new AudioReceiverThread();
            AudioSenderThread sender = new AudioSenderThread();

            receiver.start();
            sender.start();

        } else {
            // No encryption — just prompt for IP and start audio threads directly
            Scanner scanner = new Scanner(System.in);
            if (AudioDuplex.SENDER_IP == null || AudioDuplex.SENDER_IP.isEmpty()) {
                System.out.print("Enter IP address: ");
                AudioDuplex.SENDER_IP = scanner.nextLine();
            }

            AudioReceiverThread receiver = new AudioReceiverThread();
            AudioSenderThread sender = new AudioSenderThread();

            receiver.start();
            sender.start();
        }

        // Exit listener — type "exit" in console to cleanly end the call on both machines
        new Thread(() -> {
            Scanner s = new Scanner(System.in);
            while (RUNNING) {
                String input = s.nextLine();
                if ("exit".equals(input)) {
                    // Send exit packet to other machine so it also stops cleanly
                    try {
                        DatagramSocket socket = new DatagramSocket();
                        ByteBuffer buffer = ByteBuffer.allocate(4 + 8 + 512);
                        buffer.putInt(-2); // reserved sequence number — signals exit
                        buffer.putLong(System.currentTimeMillis());
                        byte[] packetData = buffer.array();
                        DatagramPacket packet = new DatagramPacket(packetData, packetData.length,
                                InetAddress.getByName(SENDER_IP), A_PORT);
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
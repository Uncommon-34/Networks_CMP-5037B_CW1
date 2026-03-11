/*
File: HandShakeSenderThread.java
Author: CaileyGR, HarryT,
Notes: Diffier-Hellman key exchange implementation
*/

import java.net.*;
import java.nio.ByteBuffer;
import java.math.BigInteger;
import java.util.Random;
import java.util.Scanner;

public class HandShakeSenderThread implements Runnable {

    public void start() {
        Thread thread = new Thread(this);
        thread.start();
    }

    public void run() {
        try {
            // Prompt for IP/port only if not set by received handshake
            Scanner scanner = new Scanner(System.in);
            if (AudioDuplex.SENDER_IP == null || AudioDuplex.SENDER_IP.isEmpty()) {
                System.out.print("Enter IP address: ");
                String ip = scanner.nextLine();
                AudioDuplex.SENDER_IP = ip;
            }
            System.out.println(
                    "[HS] thread started, sending handshake packet to " + AudioDuplex.SENDER_IP + ":"
                            + AudioDuplex.H_PORT);
            DatagramSocket socket = new DatagramSocket();
            // Generate 256-bit private key
            BigInteger x = new BigInteger(256, new Random());
            // Compute public key A = G^x mod P
            BigInteger A = AudioDuplex.G.modPow(x, AudioDuplex.P);
            // Send public key in packet
            byte[] data = A.toByteArray();
            ByteBuffer buffer = ByteBuffer.allocate(4 + 8 + 4 + data.length);
            buffer.putInt(-1);
            buffer.putLong(System.currentTimeMillis());
            buffer.putInt(data.length);
            buffer.put(data);
            byte[] packetData = buffer.array();
            DatagramPacket packet = new DatagramPacket(packetData, packetData.length,
                    InetAddress.getByName(AudioDuplex.SENDER_IP), AudioDuplex.H_PORT);
            socket.send(packet);
            System.out.println("[HS] Public key generated and handshake packet sent to " + AudioDuplex.SENDER_IP + ":"
                    + AudioDuplex.H_PORT);
            // Receive other's public key
            byte[] buf = new byte[524];
            DatagramPacket recvPacket = new DatagramPacket(buf, buf.length);
            socket.receive(recvPacket);
            System.out.println(
                    "[HS] Handshake response received from " + recvPacket.getAddress() + ":" + recvPacket.getPort());
            ByteBuffer wrapped = ByteBuffer.wrap(recvPacket.getData());
            int seq = wrapped.getInt();
            if (seq == -1) {
                wrapped.position(12);
                int len = wrapped.getInt();
                byte[] bData = new byte[len];
                wrapped.get(bData);
                BigInteger B = new BigInteger(bData);
                // Compute shared secret K = B^x mod P
                BigInteger K = B.modPow(x, AudioDuplex.P);
                AudioDuplex.KEY = K.mod(BigInteger.valueOf(Integer.MAX_VALUE)).toString();
                AudioDuplex.HANDSHAKE_RECEIVED = true;
            }
            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

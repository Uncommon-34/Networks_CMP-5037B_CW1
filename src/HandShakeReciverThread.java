/*
File: HandShakeReciverThread.java
Author: CaileyGR, HarryT,
Notes: Diffier-Hellman key exchange implementation
*/

import java.net.*;
import java.nio.ByteBuffer;
import java.math.BigInteger;
import java.util.Random;

public class HandShakeReciverThread implements Runnable {

    public void start() {
        Thread thread = new Thread(this);
        thread.start();
    }

    public void run() {
        try {
            DatagramSocket socket = new DatagramSocket(AudioDuplex.RECEIVER_PORT);
            byte[] buf = new byte[524];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            socket.receive(packet);
            System.out.println(
                    "Handshake packet received from " + packet.getAddress() + ":" + packet.getPort());
            ByteBuffer wrapped = ByteBuffer.wrap(packet.getData());
            int seq = wrapped.getInt();
            if (seq == -1) {
                byte[] bData = new byte[512];
                wrapped.position(12);
                wrapped.get(bData, 0, Math.min(bData.length, wrapped.remaining()));
                BigInteger B = new BigInteger(bData);
                // Generate 256-bit private key
                BigInteger y = new BigInteger(256, new Random());
                // Compute public key A = G^y mod P
                BigInteger A = AudioDuplex.G.modPow(y, AudioDuplex.P);
                // Send public key back
                ByteBuffer buffer = ByteBuffer.allocate(4 + 8 + 512);
                buffer.putInt(-1);
                buffer.putLong(System.currentTimeMillis());
                byte[] data = A.toByteArray();
                buffer.put(data);
                byte[] packetData = buffer.array();
                DatagramPacket sendPacket = new DatagramPacket(packetData, packetData.length, packet.getAddress(),
                        packet.getPort());
                socket.send(sendPacket);
                // Compute shared secret K = B^y mod P
                BigInteger K = B.modPow(y, AudioDuplex.P);
                AudioDuplex.KEY = K.mod(BigInteger.valueOf(Integer.MAX_VALUE)).toString();
                System.out.println("Shared secret computed: " + AudioDuplex.KEY);
            }
            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

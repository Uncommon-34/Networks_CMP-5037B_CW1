/*
File: AudioReceiverThread.java
Author: CaileyGR, HarryT,
Notes: Packet interleaver implementation and encryption implementation
*/

import java.net.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import CMPC3M06.AudioPlayer;
import uk.ac.uea.cmp.voip.*;

public class AudioReceiverThread implements Runnable {

    static DatagramSocket receiving_socket;

    public void start() {
        Thread thread = new Thread(this);
        thread.start();
    }

    // Decrypt audio block using XOR with circular bit shifted key.
    // XOR is symmetric so this is identical to encryptBlock on the sender.
    // Returns block unchanged if DECRYPTION is disabled or no key is set.
    private byte[] decryptBlock(byte[] block) {
        if (!AudioDuplex.DECRYPTION || AudioDuplex.KEY == null || AudioDuplex.KEY.isEmpty()) {
            return block;
        }

        int key = Integer.parseInt(AudioDuplex.KEY);

        ByteBuffer cipherText = ByteBuffer.wrap(block);
        ByteBuffer decrypted = ByteBuffer.allocate(block.length);

        int numChunks = block.length / 4;
        for (int j = 0; j < numChunks; j++) {
            int fourByte = cipherText.getInt();
            int shiftAmount = j % 32;
            // Circular left shift — must exactly match sender
            int shiftedKey = (key << shiftAmount) | (key >>> (32 - shiftAmount));
            fourByte = fourByte ^ shiftedKey;
            decrypted.putInt(fourByte);
        }

        return decrypted.array();
    }

    // 
    private void playGroup(byte[][] packets, boolean[] arrived, int depth, AudioPlayer player) {
        boolean anyArrived = false;
        for (boolean b : arrived) {
            if (b) {
                anyArrived = true;
                break;
            }
        }

        // If no packets in the group arrived at all, play silence
        if (!anyArrived) {
            try {
                for (int i = 0; i < depth; i++) {
                    player.playBlock(new byte[512]);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return;
        }

        byte[][] reconstructed = new byte[depth][512];
        byte lastByte1 = 0;
        byte lastByte2 = 0;

        // 
        for (int i = 0; i < 256 * depth; i++) {
            int packetIndex = i % depth;
            int sampleIndex = i / depth;
            int blockOriginal = i / 256;
            int sampleOriginal = i % 256;

            if (arrived[packetIndex]) {
                lastByte1 = packets[packetIndex][sampleIndex * 2];
                lastByte2 = packets[packetIndex][sampleIndex * 2 + 1];
                reconstructed[blockOriginal][sampleOriginal * 2] = lastByte1;
                reconstructed[blockOriginal][sampleOriginal * 2 + 1] = lastByte2;
            } else {
                // 
                reconstructed[blockOriginal][sampleOriginal * 2] = lastByte1;
                reconstructed[blockOriginal][sampleOriginal * 2 + 1] = lastByte2;
            }
        }

        try {
            for (int i = 0; i < depth; i++) {
                player.playBlock(reconstructed[i]); // already decrypted on arrival
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void run() {

        try {
            // Select socket type based on channel
            switch (AudioDuplex.CHANNEL) {
                case 1:
                    receiving_socket = new DatagramSocket(AudioDuplex.A_PORT);
                    break;
                case 2:
                    receiving_socket = new DatagramSocket2(AudioDuplex.A_PORT);
                    break;
                case 3:
                    receiving_socket = new DatagramSocket3(AudioDuplex.A_PORT);
                    break;
                case 4:
                    receiving_socket = new DatagramSocket4(AudioDuplex.A_PORT);
                    break;
                default:
                    receiving_socket = new DatagramSocket(AudioDuplex.A_PORT);
                    break;
            }

            // Timeout scales with depth — each audio block takes ~32ms to play
            receiving_socket.setSoTimeout((AudioDuplex.DEPTH * 32) + 100);

        } catch (SocketException e) {
            System.out.println("ERROR: AudioReceiver: Could not open socket.");
            e.printStackTrace();
            System.exit(0);
        }

        AudioPlayer player = null;
        try {
            player = new AudioPlayer();
        } catch (Exception e) {
            System.out.println("ERROR: AudioReceiver: Could not init player.");
            e.printStackTrace();
            receiving_socket.close();
            System.exit(0);
        }

        // Buffer: 4-byte seq + 8-byte timestamp + 512-byte audio
        byte[] buffer = new byte[524];

        PrintWriter logWriter = null;
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy_HH-mm-ss");
            String timestamp = LocalDateTime.now().format(formatter);
            logWriter = new PrintWriter(new FileWriter("logs/packet-log_" + timestamp + ".txt"));
            logWriter.println("         --- PACKET LOG ---");
            logWriter.println("CHANNEL: DatagramSocket" + AudioDuplex.CHANNEL);
            logWriter.println("INTERLEAVER DEPTH: " + AudioDuplex.DEPTH);
            logWriter.println("TIMESTAMP: " + timestamp);
            logWriter.println("-----------------------------------------");
            logWriter.println(String.format("%-6s %-9s %-10s %-12s", "SEQ NO", "RECEIVED", "DELAY(ms)", "STATUS"));
        } catch (IOException e) {
            System.out.println("ERROR: Could not create log file.");
            e.printStackTrace();
        }

        int expectedSeq = 0;
        int depth = AudioDuplex.DEPTH;
        int expectedGroup = 0;
        int packetsArrivedInGroup = 0;

        byte[][] currentGroupPackets = new byte[depth][512];
        boolean[] arrived = new boolean[depth];

        while (AudioDuplex.RUNNING) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                receiving_socket.receive(packet);
                long receiveTime = System.currentTimeMillis();

                // Unpack packet header
                ByteBuffer wrapped = ByteBuffer.wrap(packet.getData());
                int sequenceNumber = wrapped.getInt();
                long sendTime = wrapped.getLong();
                long delay = receiveTime - sendTime;

                // Check for exit packet
                if (sequenceNumber == -2) {
                    AudioDuplex.RUNNING = false;
                    continue;
                }

                // Extract audio payload from packet
                byte[] encryptedAudio = new byte[512];
                wrapped.get(encryptedAudio);

                // Decrypt immediately on arrival, before storing in group buffer.
                // This ensures concealment in playGroup uses real plaintext sample
                // values, not encrypted bytes — fixes screeching on ch2/3.
                byte[] audioPayload = decryptBlock(encryptedAudio);

                String status;
                if (sequenceNumber == expectedSeq) {
                    status = "OK";
                    expectedSeq++;
                } else if (sequenceNumber > expectedSeq) {
                    status = "OUT_OF_ORDER";
                    expectedSeq = sequenceNumber + 1;
                } else {
                    status = "OUT_OF_ORDER";
                }

                // Calculate which interleave group and index this packet belongs to
                int group = sequenceNumber / depth;
                int index = sequenceNumber % depth;

                if (group > expectedGroup) {
                    // A future group has arrived — force play current group with what we have
                    playGroup(currentGroupPackets, arrived, depth, player);

                    // Reset for new group
                    expectedGroup = group;
                    currentGroupPackets = new byte[depth][512];
                    arrived = new boolean[depth];
                    packetsArrivedInGroup = 0;

                    currentGroupPackets[index] = audioPayload;
                    arrived[index] = true;
                    packetsArrivedInGroup++;

                } else if (group == expectedGroup) {
                    if (!arrived[index]) {
                        currentGroupPackets[index] = audioPayload;
                        arrived[index] = true;
                        packetsArrivedInGroup++;

                        // Play as soon as all packets in the group have arrived
                        if (packetsArrivedInGroup == depth) {
                            playGroup(currentGroupPackets, arrived, depth, player);
                            expectedGroup++;
                            currentGroupPackets = new byte[depth][512];
                            arrived = new boolean[depth];
                            packetsArrivedInGroup = 0;
                        }
                    }
                } else {
                    // Packet belongs to an already-played group — discard it
                    status = "TOO_LATE_DISCARDED";
                }

                logWriter.println(String.format("%-6d %-9d %-10d %-12s",
                        sequenceNumber, 1, delay, status));
                logWriter.flush();

            } catch (SocketTimeoutException e) {
                logWriter.println(String.format("%-6d %-9d %-10d %-12s",
                        expectedSeq, 0, 0, "TIMEOUT"));
                logWriter.flush();
                expectedSeq++;

                if (packetsArrivedInGroup > 0) {
                    // Play partial group with concealment for missing packets
                    playGroup(currentGroupPackets, arrived, depth, player);
                    expectedGroup++;
                    currentGroupPackets = new byte[depth][512];
                    arrived = new boolean[depth];
                    packetsArrivedInGroup = 0;
                } else {
                    // Entire group lost — play silence to maintain timing
                    try {
                        for (int i = 0; i < depth; i++) {
                            player.playBlock(new byte[512]);
                        }
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                    expectedGroup++;
                }

            } catch (IOException e) {
                System.out.println("ERROR: AudioReceiver: IO error.");
                e.printStackTrace();
            }
        }

        logWriter.close();
        receiving_socket.close();
    }
}
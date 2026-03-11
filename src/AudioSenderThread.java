/*
File: AudioSenderThread.java
Author: CaileyGR, HarryT,
Notes: Packet interleaver implementation and encryption implementation
*/

import java.net.*;
import java.io.*;
import java.nio.ByteBuffer;

import CMPC3M06.AudioRecorder;
import uk.ac.uea.cmp.voip.*;

public class AudioSenderThread implements Runnable {

    static DatagramSocket sending_socket;

    public void start() {
        Thread thread = new Thread(this);
        thread.start();
    }

    // Encrypt audio block using XOR with shifted key
    private byte[] encryptBlock(byte[] block) {
        if (AudioDuplex.KEY == null || AudioDuplex.KEY.isEmpty()) {
            return block; // if no key configured skips encryption
        }

        int key = Integer.parseInt(AudioDuplex.KEY);

        ByteBuffer plainText = ByteBuffer.wrap(block);
        ByteBuffer encrypted = ByteBuffer.allocate(block.length);

        int numChunks = block.length / 4;
        for (int j = 0; j < numChunks; j++) {
            int fourByte = plainText.getInt();
            int shiftAmount = j % 32;
            // bits shifted out of the left re-enter on the right
            int shiftedKey = (key << shiftAmount) | (key >>> (32 - shiftAmount));
            fourByte = fourByte ^ shiftedKey;
            encrypted.putInt(fourByte);
        }
        return encrypted.array();
    }

    public void run() {

        InetAddress clientIP = null;
        AudioRecorder recorder = null;

        int sequenceNumber = 0;
        int depth = AudioDuplex.DEPTH;

        try {
            // update tomatch client ip
            clientIP = InetAddress.getByName(AudioDuplex.SENDER_IP);

            // Select socket type based on channel
            switch (AudioDuplex.CHANNEL) {
                case 1:
                    sending_socket = new DatagramSocket();
                    break;
                case 2:
                    sending_socket = new DatagramSocket2();
                    break;
                case 3:
                    sending_socket = new DatagramSocket3();
                    break;
                case 4:
                    sending_socket = new DatagramSocket4();
                    break;
                default:
                    sending_socket = new DatagramSocket();
                    break;
            }
        } catch (Exception e) {
            System.out.println("ERROR: AudioSender: " +
                    "Could not initialize socket.");
            e.printStackTrace();
            System.exit(0);
        }

        try {
            recorder = new AudioRecorder();
        } catch (Exception e) {
            System.out.println("ERROR: AudioSender: " +
                    "Could not initialize microphone.");
            e.printStackTrace();
            System.exit(0);
        }

        while (AudioDuplex.RUNNING) {
            try {
                // Record and encrypt audio blocks
                byte[][] bufferedBlocks = new byte[depth][512];
                for (int i = 0; i < depth; i++) {
                    bufferedBlocks[i] = encryptBlock(recorder.getBlock());
                }

                byte[][] interleavePackets = new byte[depth][512];

                // Interleave the audio samples across packets
                for (int i = 0; i < 256 * depth; i++) {
                    int packetIndex = i % depth;
                    int sampleIndex = i / depth;
                    int blockOriginal = i / 256;
                    int sampleOriginal = i % 256;

                    interleavePackets[packetIndex][sampleIndex * 2] = bufferedBlocks[blockOriginal][sampleOriginal * 2];
                    interleavePackets[packetIndex][sampleIndex * 2
                            + 1] = bufferedBlocks[blockOriginal][sampleOriginal * 2 + 1];
                }

                // Send interleaved packets
                for (int i = 0; i < depth; i++) {
                    ByteBuffer buffer = ByteBuffer.allocate(4 + 8 + 512);
                    buffer.putInt(sequenceNumber);
                    buffer.putLong(System.currentTimeMillis());
                    buffer.put(interleavePackets[i]);

                    byte[] packetData = buffer.array();
                    DatagramPacket packet = new DatagramPacket(packetData,
                            packetData.length, clientIP, AudioDuplex.A_PORT);

                    sending_socket.send(packet);
                    sequenceNumber++;
                }
            } catch (IOException e) {
                System.out.println("ERROR: AudioSender: Network/audio error.");
                e.printStackTrace();
            }
        }
        sending_socket.close();
    }
}
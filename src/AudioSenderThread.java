/*
 * AudioSenderThread.java
 */

import java.net.*;
import java.io.*;
import java.nio.ByteBuffer;

import CMPC3M06.AudioRecorder;
import uk.ac.uea.cmp.voip.*;

public class AudioSenderThread implements Runnable {

    static DatagramSocket sending_socket;

    //----REMEMBER TO CHANGE THIS TO MATCH RECEIVER-------------------------
    private static final String CHANNEL_NAME = "DatagramSocket3";

    public void start() {
        Thread thread = new Thread(this);
        thread.start();
    }

    public void run() {

        int PORT = 55555;
        InetAddress clientIP = null;
        AudioRecorder recorder = null;

        int sequenceNumber = 0;

        try {
            clientIP = InetAddress.getByName("139.222.219.51");


            //----SWITCH CHANNELS HERE-------------------------

            //sending_socket = new DatagramSocket2();
            sending_socket = new DatagramSocket3();
            //sending_socket = new DatagramSocket4();

        } catch (Exception e) {
            System.out.println("ERROR: AudioSender: Could not initialize socket.");
            e.printStackTrace();
            System.exit(0);
        }

        try {
            recorder = new AudioRecorder();
        } catch (Exception e) {
            System.out.println("ERROR: AudioSender: Could not initialize microphone.");
            e.printStackTrace();
            System.exit(0);
        }

        boolean running = true;

        while (running) {
            try {
                byte[] audioBlock = recorder.getBlock();

                // Packet
                ByteBuffer buffer = ByteBuffer.allocate(4 + 8 + audioBlock.length);

                buffer.putInt(sequenceNumber);


                buffer.putLong(System.currentTimeMillis());
                buffer.put(audioBlock);

                byte[] packetData = buffer.array();

                DatagramPacket packet = new DatagramPacket(packetData, packetData.length, clientIP, PORT);

                sending_socket.send(packet);
                sequenceNumber++;

            } catch (IOException e) {
                System.out.println("ERROR: AudioSender: Network/audio error.");
                e.printStackTrace();
            }
        }

        sending_socket.close();
    }
}
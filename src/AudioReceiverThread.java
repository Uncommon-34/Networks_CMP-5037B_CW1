/*
 * AudioReceiverThread.java
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

    //----REMEMBER TO CHANGE THIS TO MATCH RECEIVER-------------------------
    private static final String CHANNEL_NAME = "DatagramSocket3";

    public void start() {
        Thread thread = new Thread(this);
        thread.start();
    }

    public void run() {

        int PORT = 55555;

        try {
            //------------------CHOOSE CHANNELS HERE----------------------------
            //receiving_socket = new DatagramSocket2(PORT);
            receiving_socket = new DatagramSocket3(PORT);
            //receiving_socket = new DatagramSocket4(PORT);


            receiving_socket.setSoTimeout(20); // anything that doesn't arrive in this window is assumed lost

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

        byte[] buffer = new byte[524];

        PrintWriter logWriter = null;
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy_HH-mm-ss");
String timestamp = LocalDateTime.now().format(formatter);
            logWriter = new PrintWriter(new FileWriter("logs/packet-log_" + timestamp +  ".txt"));
            logWriter.println("Packet Log,Channle: ," + CHANNEL_NAME + ",TimeStamp: " + timestamp);
            logWriter.println("Seq,Received,Delay(ms),Status");
        } catch (IOException e) {
            System.out.println("ERROR: Could not create log file.");
            e.printStackTrace();
        }

        // keeps track of what sequence number we're expecting next (anything that doesn't match is out of order)
        int expectedSeq =0;

        boolean running = true;

        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                receiving_socket.receive(packet);


                long receiveTime = System.currentTimeMillis();

                // unpack the header fields in order
                ByteBuffer wrapped = ByteBuffer.wrap(packet.getData());
                int sequenceNumber = wrapped.getInt();
                long sendTime = wrapped.getLong();
                long delay = receiveTime - sendTime;

                byte[] audioBlock = new byte[512];
                wrapped.get(audioBlock);


                String status;
                if (sequenceNumber == expectedSeq){
                    status ="OK";
                    expectedSeq++;
                }else if (sequenceNumber > expectedSeq) {
                    // gap in sequence — packets before this one were delayed or lost
                    status = "OUT_OF_ORDER";
                    expectedSeq = sequenceNumber + 1;
                } else {
                    // where seqNumber < expectedSeq
                    status = "OUT_OF_ORDER";

                }

                logWriter.println(sequenceNumber + ",1 ,"  + delay+","+status);
                logWriter.flush();

                player.playBlock(audioBlock);

            } catch (SocketTimeoutException e) {

                // nothing arrived in 20ms
                logWriter.println(expectedSeq+" , 0 , 0 , TIMEOUT ");
                logWriter.flush();
                expectedSeq++;

                try {
                    player.playBlock(new byte[512]);
                } catch (IOException ioException) {
                    ioException.printStackTrace();
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
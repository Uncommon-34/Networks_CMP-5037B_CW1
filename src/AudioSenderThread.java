import java.net.*;
import java.io.*;
import CMPC3M06.AudioRecorder;
import java.util.Vector;
import java.util.Iterator;

public class AudioSenderThread implements Runnable {

    static DatagramSocket sending_socket;

    // Thread start method
    public void start() {
        Thread thread = new Thread(this);
        thread.start();
    }

    public void run() {
        // Port to send to
        int PORT = 55555;
        // IP ADDRESS to send to
        InetAddress clientIP = null;
        try {
            clientIP = InetAddress.getByName("localhost"); // CHANGE localhost to IP or NAME of client machine
        } catch (UnknownHostException e) {
            System.out.println("ERROR: Could not find client IP");
            e.printStackTrace();
            System.exit(0);
        }
        // Open a socket to send from
        // We dont need to know its port number as we never send anything to it.
        // We need the try and catch block to make sure no errors occur.

        // DatagramSocket sending_socket;
        try {
            sending_socket = new DatagramSocket();
            sending_socket.setSendBufferSize(1024 * 1024);
        } catch (SocketException e) {
            System.out.println("ERROR: Could not open UDP socket to send from.");
            e.printStackTrace();
            System.exit(0);
        }

        // Record audio
        Vector<byte[]> voiceVector = new Vector<byte[]>();
        AudioRecorder recorder = null;
        try {
            recorder = new AudioRecorder();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(0);
        }
        int recordTime = 10;
        System.out.println("Recording Audio...");

        for (int i = 0; i < Math.ceil(recordTime / 0.032); i++) {
            byte[] block = null;
            try {
                block = recorder.getBlock();
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (block != null) {
                voiceVector.add(block);
            }
        }
        System.out.println("Finished Recording.");

        recorder.close();

        // Send audio blocks
        Iterator<byte[]> voiceItr = voiceVector.iterator();
        int sentBlocks = 0;
        while (voiceItr.hasNext()) {
            try {
                // Loads the next audio block into a DatagramPacket and sends it to the client
                byte[] buffer = voiceItr.next();
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, clientIP, PORT);
                sending_socket.send(packet);
                sentBlocks++;
                // Sleep to match playback rate (32ms per block)
                try {
                    Thread.sleep(32);
                } catch (InterruptedException e) {
                    // Ignore
                }
            } catch (IOException e) {
                System.out.println("ERROR: An IO error occured while sending audio block! Block: " + sentBlocks);
                e.printStackTrace();
            }
        }

        // Send END
        try {
            byte[] endBuffer = "END".getBytes();
            DatagramPacket endPacket = new DatagramPacket(endBuffer, endBuffer.length, clientIP, PORT);
            sending_socket.send(endPacket);
        } catch (IOException e) {
            System.out.println("ERROR: An IO error occured while sending END packet!");
            e.printStackTrace();
        }

        // Report how many blocks were sent
        System.out.println("Sent " + sentBlocks + " audio blocks.");

        // Close the socket
        sending_socket.close();
    }
}

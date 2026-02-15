import java.net.*;
import java.io.*;
import CMPC3M06.AudioRecorder;

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
            clientIP = InetAddress.getByName("localhost");
        } catch (UnknownHostException e) {
            System.out.println("ERROR: Could not find client IP");
            e.printStackTrace();
            System.exit(0);
        }
        // Open a socket to send from

        try {
            sending_socket = new DatagramSocket();
            sending_socket.setSendBufferSize(1024 * 1024);
        } catch (SocketException e) {
            System.out.println("ERROR: Could not open UDP socket to send from.");
            e.printStackTrace();
            System.exit(0);
        }

        // Initialize AudioRecorder
        AudioRecorder recorder = null;
        try {
            recorder = new AudioRecorder();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(0);
        }
        System.out.println("Starting real-time audio...");

        int sentBlocks = 0;
        while (AudioDuplex.running) {
            // Get a block of audio data from the recorder and send it as a DatagramPacket
            // to the client
            try {
                byte[] block = recorder.getBlock();
                if (block != null) {
                    DatagramPacket packet = new DatagramPacket(block, block.length, clientIP, PORT);
                    sending_socket.send(packet);
                    sentBlocks++;
                }
            } catch (IOException e) {
                System.out.println("ERROR: An IO error occured while sending audio block!");
                e.printStackTrace();
            }
        }

        recorder.close();

        // Report how many blocks were sent
        System.out.println("Sent " + sentBlocks + " audio blocks.");

        // Close the socket
        sending_socket.close();
    }
}

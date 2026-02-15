import java.net.*;
import java.net.SocketTimeoutException;
import javax.sound.sampled.LineUnavailableException;
import java.io.*;
import CMPC3M06.AudioPlayer;

public class AudioReceiverThread implements Runnable {

    static DatagramSocket receiving_socket;

    public void start() {
        Thread thread = new Thread(this);
        thread.start();
    }

    public void run() {

        // Port to open socket on
        int PORT = 55555;
        // Open a socket to receive from on port PORT
        try {
            receiving_socket = new DatagramSocket(PORT);
            receiving_socket.setReceiveBufferSize(1024 * 1024);
            receiving_socket.setSoTimeout(5000); // 5 second timeout
        } catch (SocketException e) {
            System.out.println("ERROR: Could not open UDP socket to receive from.");
            e.printStackTrace();
            System.exit(0);
        }

        // Initialize AudioPlayer
        AudioPlayer player = null;
        try {
            player = new AudioPlayer();
        } catch (LineUnavailableException e) {
            System.out.println("ERROR: Could not initialize AudioPlayer.");
            e.printStackTrace();
            System.exit(0);
        }

        // Main loop.
        int blockCount = 0;

        while (AudioDuplex.running) {

            try {
                // Receive a DatagramPacket
                byte[] buffer = new byte[512];
                DatagramPacket packet = new DatagramPacket(buffer, 0, 512);

                receiving_socket.receive(packet);

                // Print when starting to play
                if (blockCount == 0) {
                    System.out.println("Playing Audio...");
                }
                // Play the audio block
                try {
                    player.playBlock(buffer);
                    blockCount++;
                } catch (Exception e) {
                    System.out.println("ERROR: Failed to play audio block.");
                    e.printStackTrace();
                }

            } catch (SocketTimeoutException e) {
                System.out.println("No packets received for 5 seconds. Stopping...");
            } catch (IOException e) {
                System.out.println("ERROR: Some random IO error occured!");
                e.printStackTrace();
            }
        }
        // Close the socket
        System.out.println("Playing finished. Played " + blockCount + " blocks.");
        receiving_socket.close();
        // Close player
        player.close();
    }
}

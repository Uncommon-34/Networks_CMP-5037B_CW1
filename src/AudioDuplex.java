import java.io.*;

public class AudioDuplex {

    public static volatile boolean running = true;

    public static void main(String[] args) {

        AudioReceiverThread receiver = new AudioReceiverThread();
        AudioSenderThread sender = new AudioSenderThread();

        receiver.start();
        sender.start();

        // Thread to read input for exit command
        Thread inputThread = new Thread(() -> {
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.equalsIgnoreCase("exit")) {
                        running = false;
                        System.out.println("Exiting...");
                        break;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        inputThread.start();

    }

}

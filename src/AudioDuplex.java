/*
 * AudioDuplex.java
 */

/**
 *
 * @author  abj
 */
public class AudioDuplex {

    public static volatile int CHANNEL = 3;

    public static volatile int PORT = 55555;

    public static volatile String IP = "localhost";

    public static volatile boolean RUNNING = true;

    public static void main (String[] args){

        AudioReceiverThread receiver = new AudioReceiverThread();
        AudioSenderThread sender = new AudioSenderThread();

        receiver.start();
        sender.start();

    }
}
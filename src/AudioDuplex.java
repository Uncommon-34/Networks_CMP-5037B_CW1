/*
File: AudioReceiverThread.java
Author: CaileyGR
Notes: Packet interleaver implementation
*/

public class AudioDuplex {

    // channel selector - values: 1, 2, 3, 4
    public static volatile int CHANNEL = 1;

    // port used for both sending and receiving packets
    public static volatile int PORT = 55555;

    // destination IP address - use "localhost" if running on the same machine
    public static volatile String IP = "localhost";

    // controls sender and receiver loops - set to false to stop both threads
    public static volatile boolean RUNNING = true;

    // interleaver depth - values: 2, 3, 4
    public static volatile int DEPTH = 1;

    // encryption key - must match on both sender and receiver
    public static volatile String KEY = "";

    public static void main(String[] args) {

        AudioReceiverThread receiver = new AudioReceiverThread();
        AudioSenderThread sender = new AudioSenderThread();

        receiver.start();
        sender.start();
    }
}
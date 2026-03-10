/*
File: AudioReceiverThread.java
Author: CaileyGR
Notes: Packet interleaver implementation
*/

//Encryption branch 
public class AudioDuplex {

    public static volatile int CHANNEL = 1; // can be 1, 2, 3, or 4 to select which datagram socket to use

    // port used for both sending and receiving packets
    public static volatile int PORT = 55555;

    // destination IP address - use "localhost" if running on the same machine
    public static volatile String IP = "localhost";

    // controls sender and receiver loops - set to false to stop both threads
    public static volatile boolean RUNNING = true;

    // interleaver depth - values: 2, 3, 4
    public static volatile int DEPTH = 1;

    public static volatile String KEY = "45637286578236578236572365ygwefg72tr6t"; // enchryption key, must match
                                                                                  // sender and receiver for audio to be
    // leave as "" to skip encryption

    public static void main(String[] args) {

        AudioReceiverThread receiver = new AudioReceiverThread();
        AudioSenderThread sender = new AudioSenderThread();

        receiver.start();
        sender.start();
    }
}
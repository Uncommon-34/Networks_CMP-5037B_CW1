/*
 * AudioDuplex.java
 */

/**
 *
 * @author abj
 */

public class AudioDuplex {

    public static volatile int CHANNEL = 2; // can be 1, 2, 3, or 4 to select which datagram socket to use

    public static volatile int PORT = 55555; // change to match port on which client is sending, sends from this port as
                                             // wel as sends from

    public static volatile String IP = "localhost"; // change to match client ip if not running on same machine

    public static volatile boolean RUNNING = true; // change to false to break both sender and receiver loops

    public static volatile int WEAVER = 2; // can be 2, 3, or 4 to select which interweaver to use

    public static volatile String KEY = null; // enchryption key, must match on sender and receiver for audio to be
                                              // correctly encrypted and decrypted

    public static void main(String[] args) {

        AudioReceiverThread receiver = new AudioReceiverThread();
        AudioSenderThread sender = new AudioSenderThread();
        InputHandshakeSenderThread handshakeSender = new InputHandshakeSenderThread();

        // receiver should always be running to listen for incoming audio or a handshake
        // packet
        receiver.start();

        if (KEY == null || KEY.equals("") || IP == null || IP.equals("")) {
            System.out.println("Starting handshake sender...");
            handshakeSender.start();
        } else {
            System.out.println("Starting audio sender...");
            sender.start();
        }
    }
}
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

    public void start() {
        Thread thread = new Thread(this);
        thread.start();
    }


    //XOR encrypts byte array using key from Audio.duplex 
    private byte[] encryptBlock(byte[] block){
        if (AudioDuplex.KEY == null || AudioDuplex.KEY.isEmpty()){
            return block; //if no key configured skips encryption 
        }

        int key = Integer.parseInt(AudioDuplex.KEY);

        ByteBuffer plainText = ByteBuffer.wrap(block);
        ByteBuffer encrypted = ByteBuffer.allocate(block.length);

        for (int j =0; j < block.length / 4; j++){
            int fourByte = plainText.getInt();
            fourByte = fourByte ^ key;
            encrypted.putInt(fourByte);
        }
        return encrypted.array();
    }
    public void run() {

        InetAddress clientIP = null;
        AudioRecorder recorder = null;

        int sequenceNumber = 0;

        try {
            //update tomatch client ip
            clientIP = InetAddress.getByName(AudioDuplex.IP);

            //----SWITCH CHANNELS HERE-------------------------

            switch (AudioDuplex.CHANNEL) {
                case 1:
                    sending_socket = new DatagramSocket();
                    break;
                case 2:
                    sending_socket = new DatagramSocket2();
                    break;
                case 3:
                    sending_socket = new DatagramSocket3();
                    break;
                case 4:
                    sending_socket = new DatagramSocket4();
                    break;
                default:
                    sending_socket = new DatagramSocket();
                    break;
            }
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

        while (AudioDuplex.RUNNING) {
            try {
                byte[] audioBlock = recorder.getBlock();

                byte[] encryptedBlock = encryptBlock(audioBlock);


                // Packet
                ByteBuffer buffer = ByteBuffer.allocate(4 + 8 + audioBlock.length);

                buffer.putInt(sequenceNumber);


                buffer.putLong(System.currentTimeMillis());
                buffer.put(encryptedBlock);

                byte[] packetData = buffer.array();

                DatagramPacket packet = new DatagramPacket(packetData, packetData.length, clientIP, AudioDuplex.PORT);

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
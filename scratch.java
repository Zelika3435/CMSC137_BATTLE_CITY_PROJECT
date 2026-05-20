import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;

public class scratch {
    public static void main(String[] args) throws Exception {
        DatagramSocket socket = new DatagramSocket();
        byte[] payload = new byte[31];
        payload[0] = 1; // version
        payload[1] = 0; // JOIN type (wait, JOIN is 0)
        // sessionId = 0 (4 bytes)
        payload[6] = (byte) -1; // playerId = -1
        // seq = 1 (4 bytes)
        payload[7] = 1; 
        // ack = 0 (4 bytes)
        // serverTick = 0 (8 bytes)
        // name length = 6 ("Player") (2 bytes short)
        payload[23] = 6;
        payload[24] = 0; // wait, Little Endian! 
        payload[25] = 'P'; payload[26] = 'l'; payload[27] = 'a'; payload[28] = 'y'; payload[29] = 'e'; payload[30] = 'r';
        
        DatagramPacket p = new DatagramPacket(payload, payload.length, new InetSocketAddress("127.0.0.1", 9000));
        socket.send(p);
        
        byte[] recvBuf = new byte[4096];
        DatagramPacket recv = new DatagramPacket(recvBuf, recvBuf.length);
        socket.setSoTimeout(2000);
        try {
            socket.receive(recv);
            System.out.println("Received " + recv.getLength() + " bytes");
        } catch (Exception e) {
            System.out.println("Timeout");
        }
    }
}

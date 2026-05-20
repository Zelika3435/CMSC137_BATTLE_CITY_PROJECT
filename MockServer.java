import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class MockServer {
    public static void main(String[] args) throws Exception {
        DatagramSocket socket = new DatagramSocket(9000);
        byte[] buf = new byte[4096];
        System.out.println("MockServer started on 9000");
        while (true) {
            DatagramPacket p = new DatagramPacket(buf, buf.length);
            socket.receive(p);
            System.out.println("Received packet of length " + p.getLength() + " from " + p.getAddress() + ":" + p.getPort());
        }
    }
}

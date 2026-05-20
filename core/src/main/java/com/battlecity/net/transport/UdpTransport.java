package com.battlecity.net.transport;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public final class UdpTransport implements AutoCloseable {
    private final DatagramSocket socket;
    private final BlockingQueue<ReceivedDatagram> inbound = new LinkedBlockingQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread receiverThread;

    public UdpTransport(int port) throws SocketException {
        this.socket = new DatagramSocket(null);
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(port));
    }

    public UdpTransport() throws SocketException {
        this.socket = new DatagramSocket();
    }

    public int localPort() {
        return socket.getLocalPort();
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        receiverThread = new Thread(this::receiveLoop, "udp-receiver");
        receiverThread.setDaemon(true);
        receiverThread.start();
    }

    public void send(byte[] data, InetSocketAddress destination) throws IOException {
        DatagramPacket packet = new DatagramPacket(data, data.length, destination);
        socket.send(packet);
    }

    public ReceivedDatagram poll() {
        return inbound.poll();
    }

    public ReceivedDatagram take() throws InterruptedException {
        return inbound.take();
    }

    private void receiveLoop() {
        byte[] buffer = new byte[4096];
        while (running.get()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                byte[] copy = new byte[packet.getLength()];
                System.arraycopy(packet.getData(), packet.getOffset(), copy, 0, packet.getLength());
                InetSocketAddress source = new InetSocketAddress(packet.getAddress(), packet.getPort());
                inbound.offer(new ReceivedDatagram(copy, source));
            } catch (IOException ex) {
                if (running.get()) {
                    ex.printStackTrace();
                }
            }
        }
    }

    @Override
    public void close() {
        running.set(false);
        socket.close();
        if (receiverThread != null) {
            receiverThread.interrupt();
        }
    }

    public record ReceivedDatagram(byte[] data, InetSocketAddress source) {}
}

package com.motofbt.app;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class OscSender {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private DatagramSocket socket;

    OscSender() {
        try {
            socket = new DatagramSocket();
        } catch (Exception ignored) {
        }
    }

    void send(String host, int port, String address, float x, float y, float z) {
        if (host == null || host.isBlank() || socket == null) return;
        byte[] data = OscPacket.threeFloats(address, x, y, z);
        executor.execute(() -> {
            try {
                InetAddress ip = InetAddress.getByName(host.trim());
                DatagramPacket packet = new DatagramPacket(data, data.length, ip, port);
                socket.send(packet);
            } catch (Exception ignored) {
            }
        });
    }

    void close() {
        executor.shutdownNow();
        if (socket != null) socket.close();
    }
}

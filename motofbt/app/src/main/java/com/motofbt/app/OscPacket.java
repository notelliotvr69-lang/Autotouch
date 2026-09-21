package com.motofbt.app;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

final class OscPacket {
    private OscPacket() {}

    static byte[] threeFloats(String address, float a, float b, float c) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            writeOscString(out, address);
            writeOscString(out, ",fff");
            writeFloat(out, a);
            writeFloat(out, b);
            writeFloat(out, c);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void writeOscString(ByteArrayOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.write(bytes);
        out.write(0);
        while ((out.size() & 3) != 0) out.write(0);
    }

    private static void writeFloat(ByteArrayOutputStream out, float value) throws IOException {
        byte[] bytes = ByteBuffer.allocate(4)
                .order(ByteOrder.BIG_ENDIAN)
                .putFloat(value)
                .array();
        out.write(bytes);
    }
}

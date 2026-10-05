package com.motofbt.app

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

class OscSender(private val host: String = "255.255.255.255", private val port: Int = 9000) {
    private val socket = DatagramSocket().apply { broadcast = true }
    private val address = InetAddress.getByName(host)
    private fun pad4(bytes: ByteArray): ByteArray { val n=(bytes.size+3)/4*4; return bytes.copyOf(n) }
    private fun stringBytes(s: String) = pad4(s.toByteArray(Charsets.UTF_8) + byteArrayOf(0))
    private fun message(path: String, args: FloatArray): ByteArray {
        val a=stringBytes(path); val tags=stringBytes(","+"f".repeat(args.size))
        val bb=ByteBuffer.allocate(a.size+tags.size+4*args.size).order(ByteOrder.BIG_ENDIAN)
        bb.put(a).put(tags); args.forEach(bb::putFloat); return bb.array()
    }
    fun send(path: String, vararg values: Float) { val b=message(path, values); socket.send(DatagramPacket(b,b.size,address,port)) }
    fun close() { socket.close() }
}

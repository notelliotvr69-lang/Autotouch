package com.vrflex.quest

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.TreeMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * VRFlex v1 UDP media client. The phone sends 12-byte VFX1 headers followed by
 * H.264 frame fragments (4768) and PCM audio packets (4767). Quest pose telemetry
 * is sent back to the phone on UDP 4766.
 */
object PhoneHostStreamClient {
    private const val MAGIC = 0x56465831
    private const val HEADER = 12
    private val running = AtomicBoolean(false)
    @Volatile private var hostAddress: InetAddress? = null
    @Volatile private var videoSocket: DatagramSocket? = null
    @Volatile private var audioSocket: DatagramSocket? = null
    @Volatile private var poseSocket: DatagramSocket? = null
    @Volatile private var decoder: MediaCodec? = null
    @Volatile private var outputSurface: Surface? = null
    @Volatile private var audioTrack: AudioTrack? = null

    @Synchronized
    fun start(host: String) {
        stop()
        hostAddress = InetAddress.getByName(host.trim())
        running.set(true)
        videoSocket = DatagramSocket(4768).apply { soTimeout = 500 }
        audioSocket = DatagramSocket(4767).apply { soTimeout = 500 }
        poseSocket = DatagramSocket()
        Thread(::receiveVideo, "VRFlex-Video-RX").start()
        Thread(::receiveAudio, "VRFlex-Audio-RX").start()
    }

    @Synchronized
    fun attachSurface(surface: Surface) {
        outputSurface = surface
        try {
            decoder?.stop()
            decoder?.release()
        } catch (_: Exception) {}
        decoder = try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 1280, 720)
            MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
                configure(format, surface, null, 0)
                start()
            }
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    fun detachSurface() {
        outputSurface = null
        try { decoder?.stop() } catch (_: Exception) {}
        try { decoder?.release() } catch (_: Exception) {}
        decoder = null
    }

    fun sendTracking(head: FloatArray, left: FloatArray, right: FloatArray, buttons: Int) {
        val address = hostAddress ?: return
        val socket = poseSocket ?: return
        if (!running.get()) return
        try {
            val packet = JSONObject()
                .put("type", "tracking")
                .put("head", JSONObject().put("x", head[0]).put("y", head[1]).put("z", head[2]))
                .put("left", JSONObject().put("x", left[0]).put("y", left[1]).put("z", left[2]))
                .put("right", JSONObject().put("x", right[0]).put("y", right[1]).put("z", right[2]))
                .put("buttons", buttons)
                .toString().toByteArray(Charsets.UTF_8)
            socket.send(DatagramPacket(packet, packet.size, address, 4766))
        } catch (_: Exception) {}
    }

    private data class FrameParts(val parts: Array<ByteArray?>, var received: Int = 0)

    private fun receiveVideo() {
        val socket = videoSocket ?: return
        val frames = TreeMap<Int, FrameParts>()
        val buffer = ByteArray(1500)
        while (running.get()) {
            try {
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)
                if (packet.length <= HEADER) continue
                val header = ByteBuffer.wrap(packet.data, packet.offset, HEADER).order(ByteOrder.BIG_ENDIAN)
                if (header.int != MAGIC) continue
                val sequence = header.int
                val index = header.short.toInt() and 0xffff
                val count = header.short.toInt() and 0xffff
                if (count !in 1..256 || index !in 0 until count) continue
                val frame = frames.getOrPut(sequence) { FrameParts(arrayOfNulls(count)) }
                if (frame.parts.size != count || frame.parts[index] != null) continue
                frame.parts[index] = packet.data.copyOfRange(packet.offset + HEADER, packet.offset + packet.length)
                frame.received++
                if (frame.received == count) {
                    val size = frame.parts.sumOf { it?.size ?: 0 }
                    val bytes = ByteArray(size)
                    var offset = 0
                    frame.parts.forEach { part ->
                        if (part != null) {
                            System.arraycopy(part, 0, bytes, offset, part.size)
                            offset += part.size
                        }
                    }
                    frames.remove(sequence)
                    decode(bytes, sequence == -1)
                }
                while (frames.size > 8) frames.pollFirstEntry()
            } catch (_: SocketTimeoutException) {
            } catch (_: Exception) {
                if (running.get()) Thread.sleep(10)
            }
        }
    }

    private fun decode(bytes: ByteArray, codecConfig: Boolean) {
        val codec = decoder ?: return
        try {
            val inputIndex = codec.dequeueInputBuffer(0)
            if (inputIndex >= 0) {
                val input = codec.getInputBuffer(inputIndex) ?: return
                input.clear()
                if (bytes.size <= input.remaining()) {
                    input.put(bytes)
                    codec.queueInputBuffer(inputIndex, 0, bytes.size, System.nanoTime() / 1000L, if (codecConfig) MediaCodec.BUFFER_FLAG_CODEC_CONFIG else 0)
                } else {
                    codec.queueInputBuffer(inputIndex, 0, 0, System.nanoTime() / 1000L, 0)
                }
            }
            val info = MediaCodec.BufferInfo()
            var outputIndex = codec.dequeueOutputBuffer(info, 0)
            while (outputIndex >= 0) {
                codec.releaseOutputBuffer(outputIndex, true)
                outputIndex = codec.dequeueOutputBuffer(info, 0)
            }
        } catch (_: Exception) {}
    }

    private fun receiveAudio() {
        val socket = audioSocket ?: return
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build())
                .setAudioFormat(AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(48000)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build())
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(19200)
                .build()
                .also { it.play(); audioTrack = it }
        } catch (_: Exception) { null } ?: return

        val buffer = ByteArray(1500)
        while (running.get()) {
            try {
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)
                if (packet.length <= HEADER) continue
                val header = ByteBuffer.wrap(packet.data, packet.offset, HEADER).order(ByteOrder.BIG_ENDIAN)
                if (header.int != MAGIC) continue
                val pcmLength = packet.length - HEADER
                track.write(packet.data, packet.offset + HEADER, pcmLength, AudioTrack.WRITE_BLOCKING)
            } catch (_: SocketTimeoutException) {
            } catch (_: Exception) {
                if (running.get()) Thread.sleep(10)
            }
        }
        try { track.pause(); track.flush(); track.release() } catch (_: Exception) {}
    }

    @Synchronized
    fun stop() {
        running.set(false)
        try { videoSocket?.close() } catch (_: Exception) {}
        try { audioSocket?.close() } catch (_: Exception) {}
        try { poseSocket?.close() } catch (_: Exception) {}
        videoSocket = null
        audioSocket = null
        poseSocket = null
        detachSurface()
        try { audioTrack?.stop(); audioTrack?.release() } catch (_: Exception) {}
        audioTrack = null
        hostAddress = null
    }
}

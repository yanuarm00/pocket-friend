package id.yanuar.bf01.util

import io.github.jaredmdobson.concentus.OpusApplication
import io.github.jaredmdobson.concentus.OpusDecoder
import io.github.jaredmdobson.concentus.OpusEncoder
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Opus mono 16 kHz, frame 60 ms (sama dengan hello firmware xiaozhi). Pakai Concentus (Java murni, jalan di minSdk 26). */
object Audio {
    const val RATE = 16000
    const val FRAME_MS = 60
    const val FRAME = RATE * FRAME_MS / 1000

    class Decoder { private val d = OpusDecoder(RATE, 1); private val buf = ShortArray(RATE * 120 / 1000)
        fun decode(pkt: ByteArray): ShortArray { val n = d.decode(pkt, 0, pkt.size, buf, 0, buf.size, false); return buf.copyOf(n) } }

    fun encode(pcm: ShortArray): List<ByteArray> {
        val e = OpusEncoder(RATE, 1, OpusApplication.OPUS_APPLICATION_VOIP).apply { bitrate = 24000 }
        val out = ArrayList<ByteArray>(); val tmp = ByteArray(1500)
        var i = 0
        while (i < pcm.size) {
            val frame = ShortArray(FRAME); System.arraycopy(pcm, i, frame, 0, minOf(FRAME, pcm.size - i))
            val n = e.encode(frame, 0, FRAME, tmp, 0, tmp.size); out.add(tmp.copyOf(n)); i += FRAME
        }
        return out
    }

    fun bytesToPcm(b: ByteArray, off: Int = 0): ShortArray {
        val sb = ByteBuffer.wrap(b, off, b.size - off).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        return ShortArray(sb.remaining()).also { sb.get(it) }
    }

    fun wav(pcm: ShortArray, rate: Int = RATE): ByteArray {
        val data = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN).apply { asShortBuffer().put(pcm) }.array()
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray()).putInt(36 + data.size).put("WAVE".toByteArray()).put("fmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(data.size)
        return ByteArrayOutputStream().apply { write(h.array()); write(data) }.toByteArray()
    }

    /** Baca WAV PCM16 mono sederhana -> (pcm, rate). */
    fun parseWav(b: ByteArray): Pair<ShortArray, Int> {
        val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
        var p = 12; var rate = RATE; var ch = 1
        while (p + 8 <= b.size) {
            val id = String(b, p, 4); val len = bb.getInt(p + 4)
            if (id == "fmt ") { ch = bb.getShort(p + 10).toInt(); rate = bb.getInt(p + 12) }
            // Panjang data 0/rusak (rekaman terputus): pakai sisa file.
            if (id == "data") { val end = if (len <= 0 || p + 8 + len > b.size) b.size else p + 8 + len
                var s = bytesToPcm(b.copyOfRange(p + 8, end))
                if (ch == 2) s = ShortArray(s.size / 2) { s[it * 2] }
                return s to rate }
            if (len < 0) break
            p += 8 + len + (len and 1)
        }
        return ShortArray(0) to rate
    }

    fun resample(pcm: ShortArray, from: Int, to: Int = RATE): ShortArray {
        if (from == to || pcm.isEmpty()) return pcm
        val n = (pcm.size.toLong() * to / from).toInt()
        return ShortArray(n) { i -> val x = i.toDouble() * from / to; val a = x.toInt().coerceAtMost(pcm.size - 1)
            val b = (a + 1).coerceAtMost(pcm.size - 1); (pcm[a] + (pcm[b] - pcm[a]) * (x - a)).toInt().toShort() }
    }
}

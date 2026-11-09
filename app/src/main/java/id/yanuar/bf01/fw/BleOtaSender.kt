package id.yanuar.bf01.fw

import id.yanuar.bf01.ble.BfProtocol
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Pengirim firmware lewat BLE (BF v0.3.9+). Logika murni supaya bisa dites tanpa radio.
 *  - Paket [offset u32][isi] berurutan, maksimal [window] byte di depan ack terakhir dari BF.
 *  - BF: ota:ack:<n> tiap 4 KB tertulis, ota:need:<n> kalau paket hilang/terlambat -> mundur ke n.
 *  - Tidak ada kabar [stallMs] -> kirim ulang dari ack terakhir (BF menjawab need kalau ia sudah lebih jauh).
 *  - BLE putus: [send] gagal -> tunggu; setelah tersambung, pemanggil kirim ota:ble lagi -> onReady(offset) -> lanjut.
 */
class BleOtaSender(
    private val data: ByteArray,
    private val chunk: Int,
    private val window: Int = 8 * 1024,
    private val stallMs: Long = 4000,
    private val send: suspend (ByteArray) -> Boolean,
) {
    val size = data.size.toLong()
    @Volatile var acked = 0L; private set
    private var next = 0L
    private val lock = Any()
    private val wake = Channel<Unit>(Channel.CONFLATED)
    @Volatile var aborted = false; private set

    fun onReady(offset: Long) = synchronized(lock) { next = offset; if (offset > acked) acked = offset }.also { wake.trySend(Unit) }
    fun onAck(n: Long) = synchronized(lock) { if (n > acked) acked = n; if (next < acked) next = acked }.also { wake.trySend(Unit) }
    fun onNeed(n: Long) = synchronized(lock) { next = n; if (n > acked) acked = n }.also { wake.trySend(Unit) }
    fun abort() { aborted = true; wake.trySend(Unit) }

    /** Kembali true kalau BF sudah mengakui semua byte. [progress] 0..100. */
    suspend fun run(progress: (Int) -> Unit = {}): Boolean {
        var lastPct = -1
        while (!aborted) {
            val (a, n) = synchronized(lock) { acked to next }
            val pct = (a * 100 / size).toInt()
            if (pct != lastPct) { lastPct = pct; progress(pct) }
            if (a >= size) return true
            if (n < size && n - a < window) {
                val len = minOf(chunk.toLong(), size - n).toInt()
                if (send(BfProtocol.otaPacket(n, data, n.toInt(), len))) {
                    synchronized(lock) { if (next == n) next = n + len }
                } else { withTimeoutOrNull(500) { wake.receive() } }   // radio sibuk / BLE putus
                continue
            }
            if (withTimeoutOrNull(stallMs) { wake.receive() } == null) synchronized(lock) { next = acked }
        }
        return false
    }

    companion object {
        /** Isi per paket untuk MTU tertentu: MTU - 3 (header ATT) - 4 (offset), maks 512 (batas BF). */
        fun chunkFor(mtu: Int) = (mtu - 3 - 4).coerceIn(16, 512)
        /** Perkiraan waktu (detik) untuk [bytes] pada laju [bytesPerSec]. */
        fun etaSeconds(bytes: Long, bytesPerSec: Double) = if (bytesPerSec <= 0) -1 else (bytes / bytesPerSec).toInt()
    }
}

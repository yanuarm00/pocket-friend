package id.yanuar.bf01.svc

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Mencoba mengisi passkey otomatis saat Android meminta (varian PASSKEY).
 * [BELUM DIUJI] Banyak HP tetap menampilkan dialog sistem; pengguna mengetik 00XXXX dari layar BF.
 */
class PairingReceiver : BroadcastReceiver() {
    @SuppressLint("MissingPermission")
    override fun onReceive(ctx: Context, i: Intent) {
        if (i.action != BluetoothDevice.ACTION_PAIRING_REQUEST) return
        val dev: BluetoothDevice = (if (android.os.Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            else @Suppress("DEPRECATION") i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)) ?: return
        val code = pendingCode ?: return
        runCatching { dev.setPin(code.toByteArray()) }
    }
    companion object { @Volatile var pendingCode: String? = null }
}

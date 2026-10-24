package id.yanuar.bf01.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Koneksi GATT ke BF-01. Semua operasi GATT diantrikan satu per satu (Android hanya boleh satu operasi berjalan).
 * Izin BLUETOOTH_CONNECT/SCAN dicek di UI sebelum service dijalankan.
 */
@SuppressLint("MissingPermission")
class BleManager(private val ctx: Context, private val scope: CoroutineScope) {
    enum class State { OFF, SCANNING, CONNECTING, CONNECTED, READY }

    private val _state = MutableStateFlow(State.OFF)
    val state = _state.asStateFlow()
    private val _info = MutableStateFlow<BfProtocol.Info?>(null)
    val info = _info.asStateFlow()
    /** Hanya untuk screenshot/pratinjau UI. */
    fun preview(st: State, i: BfProtocol.Info?) { _state.value = st; _info.value = i }
    private val _events = MutableSharedFlow<BfProtocol.Event>(extraBufferCapacity = 32)
    val events = _events.asSharedFlow()
    /** true = BF minta HP berbunyi. */
    private val _findPhone = MutableSharedFlow<Boolean>(extraBufferCapacity = 4)
    val findPhone = _findPhone.asSharedFlow()
    /** Dipanggil setiap koneksi siap (setelah discovery + subscribe) untuk sinkron. */
    var onReady: (suspend () -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null

    private val adapter get() = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private var gatt: BluetoothGatt? = null
    private var targetAddress: String? = null
    private var wantConnected = false

    private sealed class Op { abstract val uuid: java.util.UUID
        data class W(override val uuid: java.util.UUID, val data: ByteArray, val done: CompletableDeferred<Boolean>) : Op()
        data class R(override val uuid: java.util.UUID, val done: CompletableDeferred<ByteArray?>) : Op()
    }
    private val ops = Channel<Op>(Channel.UNLIMITED)
    @Volatile private var pending: CompletableDeferred<Any?>? = null

    init { scope.launch { for (op in ops) runOp(op) } }

    fun connect(address: String) { targetAddress = address; wantConnected = true; doConnect() }

    fun disconnect() { wantConnected = false; gatt?.disconnect(); gatt?.close(); gatt = null; _state.value = State.OFF }

    private fun doConnect() {
        val addr = targetAddress ?: return
        val dev = adapter?.getRemoteDevice(addr) ?: return
        _state.value = State.CONNECTING
        gatt?.close()
        // autoConnect=true: Android menyambung sendiri saat BF advertise lagi (BF bangun dari deep sleep).
        gatt = dev.connectGatt(ctx, true, cb, BluetoothDevice.TRANSPORT_LE)
    }

    // ---- Scan untuk pairing ----
    private var scanCb: ScanCallback? = null
    fun scan(onFound: (BluetoothDevice, String) -> Unit) {
        val sc = adapter?.bluetoothLeScanner ?: return
        stopScan()
        _state.value = State.SCANNING
        scanCb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, r: ScanResult) {
                val name = r.scanRecord?.deviceName ?: r.device.name ?: return
                if (name.startsWith(BfProtocol.NAME_PREFIX)) onFound(r.device, name)
            }
        }
        sc.startScan(listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(BfProtocol.SERVICE)).build()),
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCb)
    }
    fun stopScan() { scanCb?.let { adapter?.bluetoothLeScanner?.stopScan(it) }; scanCb = null
        if (_state.value == State.SCANNING) _state.value = State.OFF }

    /** Mulai bonding. Passkey (00XXXX) dimasukkan pengguna di dialog sistem; PairingReceiver mencoba mengisi otomatis. */
    fun bond(address: String) { adapter?.getRemoteDevice(address)?.createBond() }

    // ---- API tulis/baca ----
    suspend fun write(uuid: java.util.UUID, data: ByteArray): Boolean {
        if (_state.value != State.READY) return false
        val d = CompletableDeferred<Boolean>(); ops.send(Op.W(uuid, data, d))
        return withTimeoutOrNull(8000) { d.await() } ?: false
    }
    suspend fun read(uuid: java.util.UUID): ByteArray? {
        if (_state.value != State.READY) return null
        val d = CompletableDeferred<ByteArray?>(); ops.send(Op.R(uuid, d))
        return withTimeoutOrNull(8000) { d.await() }
    }
    suspend fun refreshInfo() { read(BfProtocol.INFO)?.let { runCatching { _info.value = BfProtocol.parseInfo(String(it)) } } }

    private fun ch(uuid: java.util.UUID) = gatt?.getService(BfProtocol.SERVICE)?.getCharacteristic(uuid)

    @Suppress("DEPRECATION")
    private suspend fun runOp(op: Op) {
        val g = gatt; val c = ch(op.uuid)
        if (g == null || c == null) { complete(op, null); return }
        val gate = CompletableDeferred<Any?>()
        pending = gate
        val started = when (op) {
            is Op.W -> if (Build.VERSION.SDK_INT >= 33)
                g.writeCharacteristic(c, op.data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
            else { c.value = op.data; c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT; g.writeCharacteristic(c) }
            is Op.R -> g.readCharacteristic(c)
        }
        val result = if (started) withTimeoutOrNull(8000) { gate.await() } else null
        pending = null
        complete(op, result)
    }
    private fun complete(op: Op, v: Any?) { when (op) {
        is Op.W -> op.done.complete(v == true)
        is Op.R -> op.done.complete(v as? ByteArray) } }
    private fun finishPending(v: Any?) { pending?.complete(v) }

    private val cb = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _state.value = State.CONNECTED
                g.requestMtu(BfProtocol.MTU)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                finishPending(null)
                _state.value = if (wantConnected) State.CONNECTING else State.OFF
                onDisconnected?.invoke()
                // autoConnect GATT tetap menunggu BF advertise; tidak perlu loop scan (hemat baterai HP).
            }
        }
        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) { g.discoverServices() }
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            scope.launch {
                subscribe(g, BfProtocol.EVENT); delay(300); subscribe(g, BfProtocol.FIND); delay(300)
                _state.value = State.READY
                refreshInfo()
                runCatching { onReady?.invoke() }.onFailure { Log.w("BF", "sync gagal", it) }
            }
        }
        @Deprecated("API < 33") override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            @Suppress("DEPRECATION") finishPending(if (status == BluetoothGatt.GATT_SUCCESS) (c.value ?: ByteArray(0)) else null)
        }
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            finishPending(if (status == BluetoothGatt.GATT_SUCCESS) value else null)
        }
        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            finishPending(status == BluetoothGatt.GATT_SUCCESS)
        }
        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {}
        @Deprecated("API < 33") override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION") handleNotify(c.uuid, c.value ?: return)
        }
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) = handleNotify(c.uuid, value)
    }

    private fun handleNotify(uuid: java.util.UUID, v: ByteArray) {
        when (uuid) {
            BfProtocol.EVENT -> _events.tryEmit(BfProtocol.parseEvent(String(v, Charsets.UTF_8)))
            BfProtocol.FIND -> _findPhone.tryEmit(v.isNotEmpty() && v[0].toInt() == 1)
        }
    }

    @Suppress("DEPRECATION")
    private fun subscribe(g: BluetoothGatt, uuid: java.util.UUID) {
        val c = ch(uuid) ?: return
        g.setCharacteristicNotification(c, true)
        val d = c.getDescriptor(BfProtocol.CCCD) ?: return
        if (Build.VERSION.SDK_INT >= 33) g.writeDescriptor(d, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        else { d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE; g.writeDescriptor(d) }
    }
}

package pe.aphid.feature.sensors

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.DataInputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pe.aphid.core.domain.repository.ReadingRepository
import pe.aphid.core.domain.repository.SystemRepository
import pe.aphid.core.domain.sensors.AphidGattProfile
import pe.aphid.core.domain.sensors.AphidMqttTopics
import pe.aphid.core.domain.sensors.MqttCodec
import pe.aphid.core.domain.sensors.SensorMeasurement
import pe.aphid.core.model.Reading
import pe.aphid.core.model.ReadingOrigin
import timber.log.Timber

data class SensorState(
    val scanning: Boolean = false,
    val devices: List<Pair<String, String>> = emptyList(),
    val bleConnected: String? = null,
    val mqttConnected: Boolean = false,
    val last: SensorMeasurement? = null,
    val lastMillis: Long? = null,
    val message: String? = null,
)

/**
 * Conexiones con nodos ESP32: BLE GATT (perfil de [AphidGattProfile]) y MQTT opcional por Wi-Fi.
 * Cada medición se guarda como lectura con origen SENSOR; el latido lo vigila AlertsWorker.
 */
@Singleton
class SensorConnections @Inject constructor(
    @ApplicationContext private val context: Context,
    private val readings: ReadingRepository,
    private val systems: SystemRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(SensorState())
    val state: StateFlow<SensorState> = _state.asStateFlow()
    var targetSystemId: Long? = null
    private var gatt: BluetoothGatt? = null
    private var mqttJob: Job? = null

    private val adapter get() = context.getSystemService(BluetoothManager::class.java)?.adapter

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val d = result.device
            val name = runCatching { d.name }.getOrNull() ?: d.address
            _state.update { s -> if (s.devices.any { it.second == d.address }) s else s.copy(devices = s.devices + (name to d.address)) }
        }

        override fun onScanFailed(errorCode: Int) {
            _state.update { it.copy(scanning = false, message = "Error de escaneo BLE ($errorCode)") }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        val scanner = adapter?.bluetoothLeScanner ?: return _state.update { it.copy(message = "Bluetooth no disponible") }
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(UUID.fromString(AphidGattProfile.SERVICE))).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        _state.update { it.copy(scanning = true, devices = emptyList(), message = null) }
        runCatching { scanner.startScan(listOf(filter), settings, scanCallback) }.onFailure { e -> _state.update { it.copy(scanning = false, message = e.message) } }
        scope.launch {
            delay(SCAN_MS)
            stopScan()
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        _state.update { it.copy(scanning = false) }
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        stopScan()
        val device: BluetoothDevice = adapter?.getRemoteDevice(address) ?: return
        gatt?.close()
        gatt = device.connectGatt(context, true, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        _state.update { it.copy(bleConnected = null) }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _state.update { it.copy(bleConnected = g.device.address) }
                g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _state.update { it.copy(bleConnected = null) }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val service = g.getService(UUID.fromString(AphidGattProfile.SERVICE)) ?: return
            listOf(AphidGattProfile.MEASUREMENT, AphidGattProfile.HEARTBEAT).forEach { uuid ->
                service.getCharacteristic(UUID.fromString(uuid))?.let { enableNotify(g, it) }
            }
        }

        @Deprecated("API < 33")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            characteristic.value?.let { handle(characteristic.uuid, it) }
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            handle(characteristic.uuid, value)
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableNotify(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
        g.setCharacteristicNotification(c, true)
        val cccd = c.getDescriptor(UUID.fromString(AphidGattProfile.CCCD)) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            @Suppress("DEPRECATION")
            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            g.writeDescriptor(cccd)
        }
    }

    private fun handle(uuid: UUID, value: ByteArray) {
        when (uuid.toString()) {
            AphidGattProfile.MEASUREMENT -> runCatching { AphidGattProfile.decodeMeasurement(value) }.onSuccess(::store)
            AphidGattProfile.HEARTBEAT -> store(SensorMeasurement(null, null, null, null, AphidGattProfile.decodeHeartbeat(value)))
        }
    }

    /** Guarda la medición como lectura SENSOR. Un latido sin valores también se guarda (registra presencia). */
    private fun store(m: SensorMeasurement) {
        val now = System.currentTimeMillis()
        _state.update { s -> s.copy(last = if (m.ph != null || m.ecMsCm != null) m else s.last, lastMillis = now) }
        val systemId = targetSystemId ?: return
        scope.launch {
            val volume = m.levelPct?.let { pct -> systems.get(systemId)?.volumeL?.let { it * pct / 100.0 } }
            readings.add(
                Reading(
                    systemId = systemId, timestampMillis = now, ph = m.ph, ec = m.ecMsCm, solutionTempC = m.solutionTempC,
                    volumeRemainingL = volume, origin = ReadingOrigin.SENSOR,
                ),
            )
        }
    }

    /** MQTT 3.1.1 mínimo (QoS 0, sin TLS) para un broker en la red local. */
    fun connectMqtt(host: String, port: Int, node: String) {
        mqttJob?.cancel()
        mqttJob = scope.launch {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                    socket.soTimeout = 0
                    val out = socket.getOutputStream()
                    val input = DataInputStream(socket.getInputStream())
                    out.write(MqttCodec.connect("aphid-" + UUID.randomUUID().toString().take(6)))
                    out.write(MqttCodec.subscribe(1, AphidMqttTopics.reading(node)))
                    out.write(MqttCodec.subscribe(2, AphidMqttTopics.heartbeat(node)))
                    out.flush()
                    _state.update { it.copy(mqttConnected = true, message = null) }
                    val pinger = launch {
                        while (isActive) {
                            delay(PING_MS)
                            runCatching { out.write(MqttCodec.pingReq); out.flush() }
                        }
                    }
                    while (isActive) {
                        val header = input.readUnsignedByte()
                        var mult = 1
                        var len = 0
                        do {
                            val b = input.readUnsignedByte()
                            len += (b and 0x7F) * mult
                            mult *= 128
                        } while (b and 0x80 != 0)
                        val body = ByteArray(len)
                        input.readFully(body)
                        val p = MqttCodec.decode(header, body)
                        if (p is MqttCodec.Packet.Publish) {
                            val text = String(p.payload, Charsets.UTF_8)
                            if (p.topic.endsWith("/lectura")) runCatching { AphidMqttTopics.parseReading(text) }.onSuccess(::store)
                            if (p.topic.endsWith("/latido")) store(SensorMeasurement(null, null, null, null, text.trim().toLongOrNull()))
                        }
                    }
                    pinger.cancel()
                }
            } catch (e: Exception) {
                Timber.w(e, "MQTT")
                _state.update { it.copy(mqttConnected = false, message = "MQTT: ${e.message}") }
            }
        }
    }

    fun disconnectMqtt() {
        mqttJob?.cancel()
        _state.update { it.copy(mqttConnected = false) }
    }

    private companion object {
        const val SCAN_MS = 10_000L
        const val CONNECT_TIMEOUT_MS = 5_000
        const val PING_MS = 30_000L
    }
}

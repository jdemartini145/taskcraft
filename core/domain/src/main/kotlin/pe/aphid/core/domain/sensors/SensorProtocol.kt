package pe.aphid.core.domain.sensors

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/** Medición recibida de un nodo de sensores (ESP32). Valores null = sin dato. */
data class SensorMeasurement(
    val ph: Double?,
    val ecMsCm: Double?,
    val solutionTempC: Double?,
    val levelPct: Double?,
    val uptimeS: Long?,
)

/**
 * Perfil BLE GATT del nodo APhid (ver docs/SENSORES.md).
 * Medición (notify, 12 bytes little-endian):
 * int16 pH×100 · uint16 EC µS/cm · int16 T×100 °C · uint16 nivel %×100 · uint32 uptime s.
 * Marcadores "sin dato": 0x7FFF (int16) y 0xFFFF (uint16).
 */
object AphidGattProfile {
    const val SERVICE = "7a1d0001-8f2c-4b8e-9a3d-5f1e2c3b4a50"
    const val MEASUREMENT = "7a1d0002-8f2c-4b8e-9a3d-5f1e2c3b4a50"
    const val HEARTBEAT = "7a1d0003-8f2c-4b8e-9a3d-5f1e2c3b4a50"
    const val CONFIG = "7a1d0004-8f2c-4b8e-9a3d-5f1e2c3b4a50"
    const val CCCD = "00002902-0000-1000-8000-00805f9b34fb"
    const val PACKET_SIZE = 12
    private const val NO_I16 = 0x7FFF
    private const val NO_U16 = 0xFFFF

    fun decodeMeasurement(bytes: ByteArray): SensorMeasurement {
        require(bytes.size >= PACKET_SIZE) { "Paquete corto: ${bytes.size} bytes" }
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val ph = b.short.toInt()
        val ec = b.short.toInt() and 0xFFFF
        val t = b.short.toInt()
        val lvl = b.short.toInt() and 0xFFFF
        val up = b.int.toLong() and 0xFFFFFFFFL
        return SensorMeasurement(
            ph = ph.takeIf { it != NO_I16 }?.let { it / 100.0 },
            ecMsCm = ec.takeIf { it != NO_U16 }?.let { it / 1000.0 },
            solutionTempC = t.takeIf { it != NO_I16 }?.let { it / 100.0 },
            levelPct = lvl.takeIf { it != NO_U16 }?.let { it / 100.0 },
            uptimeS = up,
        )
    }

    fun encodeMeasurement(m: SensorMeasurement): ByteArray {
        val b = ByteBuffer.allocate(PACKET_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        b.putShort((m.ph?.let { Math.round(it * 100).toInt() } ?: NO_I16).toShort())
        b.putShort((m.ecMsCm?.let { Math.round(it * 1000).toInt() } ?: NO_U16).toShort())
        b.putShort((m.solutionTempC?.let { Math.round(it * 100).toInt() } ?: NO_I16).toShort())
        b.putShort((m.levelPct?.let { Math.round(it * 100).toInt() } ?: NO_U16).toShort())
        b.putInt((m.uptimeS ?: 0L).toInt())
        return b.array()
    }

    fun decodeHeartbeat(bytes: ByteArray): Long = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
}

/** Mensajes MQTT del nodo: `aphid/<nodo>/lectura` (JSON) y `aphid/<nodo>/latido`. */
object AphidMqttTopics {
    fun reading(node: String) = "aphid/$node/lectura"
    fun heartbeat(node: String) = "aphid/$node/latido"

    private val json = Json { ignoreUnknownKeys = true }

    fun parseReading(payload: String): SensorMeasurement {
        val o = json.parseToJsonElement(payload) as? JsonObject ?: error("Se esperaba un objeto JSON")
        fun d(k: String) = (o[k] as? JsonPrimitive)?.doubleOrNull
        return SensorMeasurement(d("ph"), d("ec"), d("t_sol"), d("nivel_pct"), d("uptime")?.toLong())
    }
}

/** Códec mínimo de MQTT 3.1.1 (QoS 0) para un broker local sin TLS. */
object MqttCodec {
    sealed interface Packet {
        data class ConnAck(val returnCode: Int) : Packet
        data class SubAck(val packetId: Int) : Packet
        data class Publish(val topic: String, val payload: ByteArray) : Packet
        data object PingResp : Packet
        data class Other(val type: Int) : Packet
    }

    fun encodeRemainingLength(len: Int): ByteArray {
        val out = ByteArrayOutputStream()
        var x = len
        do {
            var byte = x % 128
            x /= 128
            if (x > 0) byte = byte or 0x80
            out.write(byte)
        } while (x > 0)
        return out.toByteArray()
    }

    private fun utf8(s: String): ByteArray {
        val b = s.toByteArray(Charsets.UTF_8)
        return byteArrayOf((b.size shr 8).toByte(), (b.size and 0xFF).toByte()) + b
    }

    private fun packet(header: Int, body: ByteArray) = byteArrayOf(header.toByte()) + encodeRemainingLength(body.size) + body

    fun connect(clientId: String, keepAliveS: Int = 60): ByteArray {
        val variable = utf8("MQTT") + byteArrayOf(4, 0x02, (keepAliveS shr 8).toByte(), (keepAliveS and 0xFF).toByte())
        return packet(0x10, variable + utf8(clientId))
    }

    fun subscribe(packetId: Int, topic: String): ByteArray =
        packet(0x82, byteArrayOf((packetId shr 8).toByte(), (packetId and 0xFF).toByte()) + utf8(topic) + byteArrayOf(0))

    fun publish(topic: String, payload: ByteArray): ByteArray = packet(0x30, utf8(topic) + payload)

    val pingReq: ByteArray = byteArrayOf(0xC0.toByte(), 0)
    val disconnect: ByteArray = byteArrayOf(0xE0.toByte(), 0)

    /** Decodifica un paquete completo (cabecera fija + cuerpo). */
    fun decode(header: Int, body: ByteArray): Packet = when (header shr 4) {
        2 -> Packet.ConnAck(body.getOrNull(1)?.toInt() ?: -1)
        9 -> Packet.SubAck(((body[0].toInt() and 0xFF) shl 8) or (body[1].toInt() and 0xFF))
        3 -> {
            val qos = (header shr 1) and 0x03
            val len = ((body[0].toInt() and 0xFF) shl 8) or (body[1].toInt() and 0xFF)
            val topic = String(body, 2, len, Charsets.UTF_8)
            val start = 2 + len + if (qos > 0) 2 else 0
            Packet.Publish(topic, body.copyOfRange(start, body.size))
        }
        13 -> Packet.PingResp
        else -> Packet.Other(header shr 4)
    }
}

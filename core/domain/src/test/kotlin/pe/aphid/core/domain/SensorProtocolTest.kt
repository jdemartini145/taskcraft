package pe.aphid.core.domain

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import pe.aphid.core.domain.sensors.AphidGattProfile
import pe.aphid.core.domain.sensors.AphidMqttTopics
import pe.aphid.core.domain.sensors.MqttCodec
import pe.aphid.core.domain.sensors.SensorMeasurement

class SensorProtocolTest {
    @Test
    fun gattRoundTrip() {
        val m = SensorMeasurement(5.85, 1.62, 21.4, 78.5, 3600)
        val back = AphidGattProfile.decodeMeasurement(AphidGattProfile.encodeMeasurement(m))
        assertEquals(5.85, back.ph!!, 1e-9)
        assertEquals(1.62, back.ecMsCm!!, 1e-9)
        assertEquals(21.4, back.solutionTempC!!, 1e-9)
        assertEquals(78.5, back.levelPct!!, 1e-9)
        assertEquals(3600L, back.uptimeS)
        val missing = AphidGattProfile.decodeMeasurement(AphidGattProfile.encodeMeasurement(SensorMeasurement(null, null, 20.0, null, 1)))
        assertNull(missing.ph)
        assertNull(missing.ecMsCm)
        assertNull(missing.levelPct)
    }

    @Test
    fun mqttJson() {
        val m = AphidMqttTopics.parseReading("""{"ph":6.1,"ec":1.4,"t_sol":19.5,"nivel_pct":90}""")
        assertEquals(6.1, m.ph!!, 1e-9)
        assertEquals("aphid/nodo1/lectura", AphidMqttTopics.reading("nodo1"))
    }

    @Test
    fun mqttCodec() {
        assertArrayEquals(byteArrayOf(0x7F), MqttCodec.encodeRemainingLength(127))
        assertArrayEquals(byteArrayOf(0x80.toByte(), 0x01), MqttCodec.encodeRemainingLength(128))
        val connect = MqttCodec.connect("aphid")
        assertEquals(0x10, connect[0].toInt())
        assertEquals(connect.size - 2, connect[1].toInt())
        val pub = MqttCodec.publish("a/b", "hola".toByteArray())
        val decoded = MqttCodec.decode(pub[0].toInt() and 0xFF, pub.copyOfRange(2, pub.size)) as MqttCodec.Packet.Publish
        assertEquals("a/b", decoded.topic)
        assertEquals("hola", String(decoded.payload))
        assertEquals(MqttCodec.Packet.ConnAck(0), MqttCodec.decode(0x20, byteArrayOf(0, 0)))
    }
}

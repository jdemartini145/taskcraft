# Sensores (ESP32)

## Perfil BLE GATT

| Elemento | UUID | Propiedades | Formato |
|---|---|---|---|
| Servicio APhid | `7a1d0001-8f2c-4b8e-9a3d-5f1e2c3b4a50` | — | — |
| Medición | `7a1d0002-8f2c-4b8e-9a3d-5f1e2c3b4a50` | notify, read | 12 bytes little-endian |
| Latido | `7a1d0003-8f2c-4b8e-9a3d-5f1e2c3b4a50` | notify | uint32 uptime (s), cada 5 min |
| Configuración | `7a1d0004-8f2c-4b8e-9a3d-5f1e2c3b4a50` | write | uint16 intervalo de medición (s) |

Medición: `int16 pH×100 · uint16 EC µS/cm · int16 T solución ×100 °C · uint16 nivel %×100 · uint32 uptime`.
"Sin dato": `0x7FFF` (int16) y `0xFFFF` (uint16). El códec está en
`core/domain/.../sensors/SensorProtocol.kt` y tiene pruebas.

## MQTT (opcional, Wi-Fi)

Broker local MQTT 3.1.1, QoS 0, sin TLS.

- `aphid/<nodo>/lectura` → `{"ph":5.9,"ec":1.6,"t_sol":21.3,"nivel_pct":80}`
- `aphid/<nodo>/latido` → uptime en segundos

## Latido y alertas

El nodo envía un latido cada 5 min. Si no llega ninguna lectura de origen sensor durante
15 min, `AlertsWorker` genera "posible corte de energía o bomba".

## Esquema del firmware (Arduino/ESP32)

```cpp
// Pseudocódigo: NimBLE + sondas analógicas calibradas
void loop() {
  int16_t ph = readPh() * 100;  uint16_t ec = readEcUs();
  int16_t t = readTemp() * 100; uint16_t lvl = readLevelPct() * 100;
  uint8_t buf[12]; pack_le(buf, ph, ec, t, lvl, millis() / 1000);
  measurement->setValue(buf, 12); measurement->notify();
  if (millis() - lastBeat > 300000) { heartbeat->setValue(uptime); heartbeat->notify(); }
  delay(intervalMs);
}
```

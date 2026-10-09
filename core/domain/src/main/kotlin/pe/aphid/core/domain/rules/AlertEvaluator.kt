package pe.aphid.core.domain.rules

import pe.aphid.core.model.Alert
import pe.aphid.core.model.AlertKind
import pe.aphid.core.model.AlertSeverity
import pe.aphid.core.model.CareTask
import pe.aphid.core.model.Reading
import pe.aphid.core.model.ReadingOrigin
import pe.aphid.core.model.ValueRange

/** Contexto de un sistema para evaluar alertas sin acceso a la base de datos. */
data class SystemAlertContext(
    val systemId: Long,
    val systemName: String,
    val readings: List<Reading>,
    val phRange: ValueRange,
    val ecRange: ValueRange,
    val hasSensor: Boolean = false,
)

/**
 * Evalúa la bitácora y produce alertas predictivas offline (pensado para WorkManager cada 6 h).
 * La predicción usa regresión lineal sobre las últimas 72 h y proyecta 24 h.
 */
class AlertEvaluator(
    private val solutionTempMaxC: Double = 22.0,
    private val heartbeatTimeoutMinutes: Int = 15,
    private val trendWindowHours: Int = 72,
    private val horizonHours: Int = 24,
) {

    fun evaluate(ctx: SystemAlertContext, nowMillis: Long): List<Alert> {
        val out = mutableListOf<Alert>()
        val sorted = ctx.readings.sortedBy { it.timestampMillis }
        val last = sorted.lastOrNull()

        last?.solutionTempC?.let { t ->
            if (t > solutionTempMaxC) {
                out += alert(
                    ctx, AlertKind.TEMPERATURA_ALTA, AlertSeverity.CRITICA, nowMillis,
                    "Solución a %.1f °C".format(t),
                    "Supera %.0f °C: riesgo de pudrición de raíz por bajo oxígeno disuelto. Enfría el reservorio, sombrea y aumenta la aireación."
                        .format(solutionTempMaxC),
                )
            }
        }
        checkParam(ctx, sorted, nowMillis, "pH", ctx.phRange, { it.ph }, AlertKind.PH_FUERA, AlertKind.PH_TENDENCIA)?.let { out += it }
        checkParam(ctx, sorted, nowMillis, "EC", ctx.ecRange, { it.ec }, AlertKind.EC_FUERA, AlertKind.EC_TENDENCIA)?.let { out += it }

        if (ctx.hasSensor) {
            val lastSensor = sorted.lastOrNull { it.origin == ReadingOrigin.SENSOR }
            val silentMin = lastSensor?.let { (nowMillis - it.timestampMillis) / 60_000 }
            if (silentMin == null || silentMin >= heartbeatTimeoutMinutes) {
                out += alert(
                    ctx, AlertKind.LATIDO_PERDIDO, AlertSeverity.CRITICA, nowMillis,
                    "Sin latido del sensor",
                    "No llega señal hace más de $heartbeatTimeoutMinutes min: posible corte de energía o bomba.",
                )
            }
        }
        return out
    }

    fun dueTaskAlerts(tasks: List<CareTask>, nowMillis: Long): List<Alert> = tasks.filter { !it.done && it.dueMillis <= nowMillis }.map {
        Alert(
            systemId = it.systemId, kind = AlertKind.RECORDATORIO, severity = AlertSeverity.INFO,
            title = it.kind.label, message = it.title, createdMillis = nowMillis,
        )
    }

    private fun checkParam(
        ctx: SystemAlertContext,
        sorted: List<Reading>,
        now: Long,
        label: String,
        range: ValueRange,
        value: (Reading) -> Double?,
        outKind: AlertKind,
        trendKind: AlertKind,
    ): Alert? {
        if (range.isEmpty) return null
        val points = sorted.filter { now - it.timestampMillis <= trendWindowHours * HOUR_MS }
            .mapNotNull { r -> value(r)?.let { r.timestampMillis to it } }
        val current = points.lastOrNull()?.second ?: return null
        if (!range.contains(current)) {
            return alert(
                ctx, outKind, AlertSeverity.ADVERTENCIA, now,
                "$label fuera de rango: %.2f".format(current),
                "El rango del cultivo es ${range.text()}. Corrige en pasos pequeños y vuelve a medir.",
            )
        }
        val predicted = Trend.predict(points, now + horizonHours * HOUR_MS) ?: return null
        if (!range.contains(predicted)) {
            return alert(
                ctx, trendKind, AlertSeverity.ADVERTENCIA, now,
                "$label saldrá de rango en ~$horizonHours h",
                "La tendencia proyecta %.2f (rango %s). Revisa antes de que ocurra.".format(predicted, range.text()),
            )
        }
        return null
    }

    private fun alert(ctx: SystemAlertContext, kind: AlertKind, sev: AlertSeverity, now: Long, title: String, msg: String) =
        Alert(systemId = ctx.systemId, kind = kind, severity = sev, title = "${ctx.systemName}: $title", message = msg, createdMillis = now)

    companion object { const val HOUR_MS = 3_600_000L }
}

internal fun ValueRange.text(): String = when {
    min != null && max != null -> "%.1f–%.1f".format(min, max)
    min != null -> "≥ %.1f".format(min)
    max != null -> "≤ %.1f".format(max)
    else -> "dato pendiente"
}

/** Regresión lineal por mínimos cuadrados. */
object Trend {
    fun slopePerHour(points: List<Pair<Long, Double>>): Double? {
        if (points.size < 3) return null
        val t0 = points.first().first
        val xs = points.map { (it.first - t0) / 3_600_000.0 }
        val ys = points.map { it.second }
        val mx = xs.average()
        val my = ys.average()
        val sxx = xs.sumOf { (it - mx) * (it - mx) }
        if (sxx < 1e-9) return null
        return xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) } / sxx
    }

    fun predict(points: List<Pair<Long, Double>>, atMillis: Long): Double? {
        val slope = slopePerHour(points) ?: return null
        val t0 = points.first().first
        val xs = points.map { (it.first - t0) / 3_600_000.0 }
        val intercept = points.map { it.second }.average() - slope * xs.average()
        return intercept + slope * (atMillis - t0) / 3_600_000.0
    }
}

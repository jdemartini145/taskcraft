package pe.aphid.core.data.export

import pe.aphid.core.model.FormulaResult
import pe.aphid.core.model.Reading
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** CSV RFC 4180 (separador coma, punto decimal) para abrir en hojas de cálculo. */
object CsvExporter {
    private fun esc(v: Any?): String {
        val s = when (v) {
            null -> ""
            is Double -> String.format(Locale.ROOT, "%.4f", v)
            else -> v.toString()
        }
        return if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }

    private fun row(vararg v: Any?) = v.joinToString(",") { esc(it) }

    fun formula(result: FormulaResult): String = buildString {
        appendLine(row("seccion", "tanque", "insumo", "g_por_L_final", "g_en_tanque", "g_para_reservorio", "mL_para_reservorio", "costo_PEN"))
        result.tanks.forEach { t ->
            t.doses.forEach { d ->
                appendLine(row("dosis", t.group.name, d.name, d.gramsPerLiterFinal, d.gramsInStockTank, d.gramsForReservoir, d.mlForReservoir, d.costPen))
            }
        }
        appendLine()
        appendLine(row("seccion", "elemento", "meta_ppm", "agua_ppm", "entregado_ppm", "desvio_pct"))
        result.balance.forEach { b -> appendLine(row("balance", b.element.symbol, b.targetPpm, b.fromWaterPpm, b.deliveredPpm, b.deviationPct)) }
        appendLine()
        appendLine(row("ec_estimada_mScm", result.estimatedEc))
        appendLine(row("ph_objetivo", result.phTarget))
        appendLine(row("costo_por_1000L_PEN", result.costPer1000LPen))
        appendLine(row("fuente", result.targetSource))
        appendLine(row("version_motor", result.engineVersion))
    }

    fun readings(systemName: String, readings: List<Reading>, zone: ZoneId = ZoneId.systemDefault()): String = buildString {
        val fmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME
        appendLine(row("sistema", "fecha_hora", "pH", "EC_mScm", "temp_solucion_C", "temp_aire_C", "HR_pct", "volumen_L", "origen"))
        readings.sortedBy { it.timestampMillis }.forEach { r ->
            val t = Instant.ofEpochMilli(r.timestampMillis).atZone(zone).toLocalDateTime().format(fmt)
            appendLine(row(systemName, t, r.ph, r.ec, r.solutionTempC, r.airTempC, r.relativeHumidity, r.volumeRemainingL, r.origin.name))
        }
    }
}

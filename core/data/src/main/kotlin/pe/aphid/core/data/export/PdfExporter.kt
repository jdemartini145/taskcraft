package pe.aphid.core.data.export

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import pe.aphid.core.model.FormulaResult
import pe.aphid.core.model.Reading
import pe.aphid.core.model.TankGroup

/**
 * Exporta a PDF con `android.graphics.pdf.PdfDocument` (A4, 72 ppp).
 * El PDF de fórmula incluye gramos por tanque, ppm contra meta, EC estimada y fuente.
 */
class PdfExporter(private val locale: Locale = Locale.forLanguageTag("es-PE")) {

    private class Writer(private val doc: PdfDocument) {
        private var pageNo = 0
        private var page: PdfDocument.Page? = null
        private var y = 0f
        val body = Paint().apply { textSize = 10f; isAntiAlias = true }
        val bold = Paint(body).apply { typeface = Typeface.DEFAULT_BOLD }
        val title = Paint(bold).apply { textSize = 16f }

        private fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
            y = MARGIN
        }

        fun line(text: String, paint: Paint = body, x: Float = MARGIN) {
            if (page == null || y > PAGE_H - MARGIN) newPage()
            // Corta líneas largas para que no se salgan de la página.
            val max = ((PAGE_W - x - MARGIN) / (paint.textSize * 0.5f)).toInt().coerceAtLeast(20)
            text.chunked(max).forEach { chunk ->
                if (y > PAGE_H - MARGIN) newPage()
                page!!.canvas.drawText(chunk, x, y, paint)
                y += paint.textSize * 1.5f
            }
        }

        fun columns(values: List<String>, widths: List<Float>, paint: Paint = body) {
            if (page == null || y > PAGE_H - MARGIN) newPage()
            var x = MARGIN
            values.forEachIndexed { i, v ->
                page!!.canvas.drawText(v, x, y, paint)
                x += widths[i]
            }
            y += paint.textSize * 1.5f
        }

        fun gap() { y += 8f }

        fun finish() { page?.let { doc.finishPage(it) } }
    }

    private fun n(v: Double?, d: Int = 2): String {
        if (v == null) return "—"
        val nf = NumberFormat.getNumberInstance(locale)
        nf.minimumFractionDigits = d
        nf.maximumFractionDigits = d
        return nf.format(v)
    }

    fun formula(result: FormulaResult, title: String, out: File): File {
        val doc = PdfDocument()
        val w = Writer(doc)
        w.line("APhid — $title", w.title)
        val req = result.request
        w.line("Volumen del reservorio: ${n(req.reservoirVolumeL, 0)} L · Factor ${req.concentrationFactor.value}x · Tanques de ${n(req.tankVolumeL, 1)} L")
        w.line("Fuente de la meta: ${result.targetSource}")
        w.gap()
        result.tanks.forEach { t ->
            val header = if (t.group == TankGroup.DIRECTO) t.group.label else "${t.group.label}: ${n(t.mlPerLiterReservoir, 1)} mL por litro de reservorio"
            w.line(header, w.bold)
            w.columns(listOf("Insumo", "g en tanque", "g/L final"), listOf(270f, 110f, 100f), w.bold)
            t.doses.forEach { d ->
                w.columns(listOf(d.name, n(d.gramsInStockTank, 1), n(d.gramsPerLiterFinal, 4)), listOf(270f, 110f, 100f))
                d.mlForReservoir?.let { w.line("   ≈ ${n(it, 1)} mL de producto para ${n(result.request.reservoirVolumeL, 0)} L", w.body) }
            }
            if (!t.solubilityOk) w.line("⚠ Se excede la solubilidad en este tanque.", w.bold)
            w.gap()
        }
        w.line("ppm entregados contra meta", w.bold)
        w.columns(listOf("Elemento", "Meta", "Agua", "Entregado", "Desvío %"), listOf(90f, 90f, 90f, 100f, 90f), w.bold)
        result.balance.filter { it.targetPpm != null || it.deliveredPpm > 0.0 }.forEach { b ->
            w.columns(
                listOf(b.element.symbol, b.targetPpm?.let { n(it) } ?: "pendiente", n(b.fromWaterPpm), n(b.deliveredPpm), b.deviationPct?.let { n(it, 1) } ?: "—"),
                listOf(90f, 90f, 90f, 100f, 90f),
            )
        }
        w.gap()
        w.line("EC estimada: ${n(result.estimatedEc)} mS/cm" + rangeText(result.ecRange.min, result.ecRange.max), w.bold)
        w.line("pH objetivo: ${result.phTarget?.let { n(it, 1) } ?: "dato pendiente"}")
        w.line("Costo por 1 000 L: ${result.costPer1000LPen?.let { "S/ " + n(it) } ?: "agrega precios en el catálogo"}")
        if (result.warnings.isNotEmpty()) {
            w.gap()
            w.line("Advertencias", w.bold)
            result.warnings.forEach { w.line("• ${it.message}") }
        }
        w.gap()
        w.line("Motor de fórmulas ${result.engineVersion}. Balance de masa: error ${n(result.massBalanceErrorPct, 4)} %.")
        w.line("Usa guantes y lentes al manipular ácidos. Añade siempre el ácido al agua, nunca al revés.")
        w.finish()
        out.parentFile?.mkdirs()
        FileOutputStream(out).use { doc.writeTo(it) }
        doc.close()
        return out
    }

    fun readings(systemName: String, readings: List<Reading>, out: File, zone: ZoneId = ZoneId.systemDefault()): File {
        val doc = PdfDocument()
        val w = Writer(doc)
        val fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", locale)
        w.line("APhid — Bitácora de $systemName", w.title)
        val widths = listOf(110f, 50f, 60f, 70f, 70f, 70f)
        w.columns(listOf("Fecha", "pH", "EC", "T sol. °C", "T aire °C", "Vol. L"), widths, w.bold)
        readings.sortedBy { it.timestampMillis }.forEach { r ->
            w.columns(
                listOf(
                    Instant.ofEpochMilli(r.timestampMillis).atZone(zone).format(fmt),
                    n(r.ph, 1), n(r.ec), n(r.solutionTempC, 1), n(r.airTempC, 1), n(r.volumeRemainingL, 0),
                ),
                widths,
            )
        }
        w.finish()
        out.parentFile?.mkdirs()
        FileOutputStream(out).use { doc.writeTo(it) }
        doc.close()
        return out
    }

    private fun rangeText(min: Double?, max: Double?): String = when {
        min != null && max != null -> " (rango del cultivo ${n(min, 1)}–${n(max, 1)})"
        min != null -> " (mínimo del cultivo ${n(min, 1)})"
        max != null -> " (máximo del cultivo ${n(max, 1)})"
        else -> " (rango del cultivo: dato pendiente)"
    }

    private companion object {
        const val PAGE_W = 595
        const val PAGE_H = 842
        const val MARGIN = 40f
    }
}

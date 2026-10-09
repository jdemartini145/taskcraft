package pe.aphid.core.domain.rules

import kotlin.math.abs
import pe.aphid.core.model.Reading

/** Causa probable de una deriva de pH/EC, con acción sugerida. */
data class DriftExplanation(val id: String, val cause: String, val action: String)

/**
 * Motor de reglas que explica la deriva entre lecturas. Los umbrales son parámetros del
 * algoritmo (sensibilidad), no valores agronómicos, y se pueden ajustar.
 */
class DriftAnalyzer(
    /** Cambio mínimo de EC (mS/cm) para considerarlo subida/bajada. */
    private val ecDelta: Double = 0.1,
    /** Cambio relativo de volumen (fracción) por debajo del cual se considera estable. */
    private val volumeStableFraction: Double = 0.05,
    /** Subida mínima de pH para considerarla tendencia. */
    private val phRiseDelta: Double = 0.2,
    /** Oscilación diaria de pH que dispara revisión. */
    private val phDailySwing: Double = 1.0,
) {

    /**
     * @param readings lecturas de un sistema (cualquier orden).
     * @param nitrateFraction fracción N-NO₃ / N total de la última fórmula (null si se desconoce).
     */
    fun explain(readings: List<Reading>, nitrateFraction: Double? = null): List<DriftExplanation> {
        val sorted = readings.sortedBy { it.timestampMillis }
        if (sorted.size < 2) return emptyList()
        val out = mutableListOf<DriftExplanation>()
        val first = sorted.first()
        val last = sorted.last()

        val ecs = sorted.mapNotNull { r -> r.ec?.let { r to it } }
        val vols = sorted.mapNotNull { r -> r.volumeRemainingL?.let { r to it } }
        if (ecs.size >= 2 && vols.size >= 2) {
            val dEc = ecs.last().second - ecs.first().second
            val v0 = vols.first().second
            val dVolFrac = if (v0 > 0) (vols.last().second - v0) / v0 else 0.0
            if (dEc >= ecDelta && dVolFrac <= -volumeStableFraction) {
                out += DriftExplanation(
                    "ec_sube_volumen_baja",
                    "La EC sube y el volumen baja: la planta toma más agua que nutrientes.",
                    "Reponer con agua sola.",
                )
            }
            if (dEc <= -ecDelta && abs(dVolFrac) < volumeStableFraction) {
                out += DriftExplanation(
                    "ec_baja_volumen_estable",
                    "La EC baja con volumen estable: la planta consume nutrientes.",
                    "Reponer solución a media dosis.",
                )
            }
        }

        val phs = sorted.mapNotNull { r -> r.ph?.let { r to it } }
        if (phs.size >= 2) {
            val dPh = phs.last().second - phs.first().second
            if (dPh >= phRiseDelta && (nitrateFraction ?: 0.0) >= NITRATE_RICH) {
                out += DriftExplanation(
                    "ph_sube_nitrato",
                    "El pH sube con una fórmula rica en nitrato: es el comportamiento esperado (la raíz libera OH⁻ al absorber NO₃⁻).",
                    "Corregir con ácido en pasos pequeños.",
                )
            }
            // Oscilación: máximo - mínimo dentro de cualquier ventana de 24 h.
            val swing = phs.indices.maxOf { i ->
                val t0 = phs[i].first.timestampMillis
                val window = phs.filter { it.first.timestampMillis in t0..(t0 + DAY_MS) }.map { it.second }
                window.max() - window.min()
            }
            if (swing > phDailySwing) {
                out += DriftExplanation(
                    "ph_oscila",
                    "El pH oscila más de %.1f en un día.".format(phDailySwing),
                    "Revisar alcalinidad del agua, calibración del medidor y volumen.",
                )
            }
        }
        if (out.isEmpty() && last.timestampMillis > first.timestampMillis) {
            return emptyList()
        }
        return out
    }

    companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val NITRATE_RICH = 0.9
    }
}

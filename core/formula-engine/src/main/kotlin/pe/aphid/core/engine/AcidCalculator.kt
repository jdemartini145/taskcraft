package pe.aphid.core.engine

import kotlin.math.max
import kotlin.math.min
import pe.aphid.core.model.Element
import pe.aphid.core.model.EngineOptions
import pe.aphid.core.model.EngineWarning
import pe.aphid.core.model.Fertilizer
import pe.aphid.core.model.NutrientProfile
import pe.aphid.core.model.WarningKind
import pe.aphid.core.model.WaterAnalysis

/**
 * Paso 5: neutralización de bicarbonatos.
 * meq de ácido = (HCO₃ − HCO₃ residual) / 61,02. Prioriza ácido fosfórico hasta cubrir
 * la meta de P y completa con ácido nítrico; el N y P aportados entran al balance.
 */
object AcidCalculator {

    fun requiredMeq(water: WaterAnalysis, options: EngineOptions): Double =
        if (water.hco3 <= options.hco3LimitMgL) {
            0.0
        } else {
            max(0.0, (water.hco3 - options.hco3ResidualMgL) / WaterAnalysis.HCO3_MOLAR_MASS)
        }

    /** Devuelve g/L de producto comercial por id de ácido. */
    fun compute(
        water: WaterAnalysis,
        netTarget: NutrientProfile,
        acids: List<Fertilizer>,
        options: EngineOptions,
        warnings: MutableList<EngineWarning>,
    ): Map<String, Double> {
        val meq = requiredMeq(water, options)
        if (meq <= 0.0) return emptyMap()
        if (acids.isEmpty()) {
            warnings += EngineWarning(
                WarningKind.ACIDO,
                "El agua tiene %.0f mg/L de HCO₃ (límite %.0f). Agrega ácido nítrico o fosfórico para controlar el pH."
                    .format(water.hco3, options.hco3LimitMgL),
            )
            return emptyMap()
        }
        val phosphoric = acids.firstOrNull { it.contains(Element.P) }
        val nitric = acids.firstOrNull { it.contains(Element.N_NO3) }
        val out = mutableMapOf<String, Double>()
        var remaining = meq
        if (phosphoric != null) {
            val pPerMeq = phosphoric.mgPerGram(Element.P) * gramsPerMeq(phosphoric)
            val maxMeqByP = if (pPerMeq > 0) netTarget[Element.P] / pPerMeq else 0.0
            val use = if (nitric == null) remaining else min(remaining, maxMeqByP)
            if (use > 0) {
                out[phosphoric.id] = use * gramsPerMeq(phosphoric)
                remaining -= use
            }
        }
        if (remaining > 1e-9 && nitric != null) {
            out[nitric.id] = remaining * gramsPerMeq(nitric)
            remaining = 0.0
        }
        if (remaining > 1e-9) {
            val other = acids.first { it.id !in out }
            out[other.id] = (out[other.id] ?: 0.0) + remaining * gramsPerMeq(other)
        }
        warnings += EngineWarning(
            WarningKind.ACIDO,
            "Se neutralizan %.2f meq/L de bicarbonato con ácido. Añádelo en pasos pequeños y mide el pH.".format(meq),
        )
        return out
    }

    /** g de producto comercial por meq de H⁺. */
    fun gramsPerMeq(f: Fertilizer): Double {
        val info = requireNotNull(f.acid) { "${f.name} no es un ácido" }
        return info.molarMass / info.equivalentsPerMol / 1000.0 / (f.purityPct / 100.0)
    }
}

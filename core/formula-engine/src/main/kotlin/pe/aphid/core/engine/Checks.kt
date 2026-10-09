package pe.aphid.core.engine

import kotlin.math.abs
import pe.aphid.core.model.ConcentrationFactor
import pe.aphid.core.model.Element
import pe.aphid.core.model.EngineWarning
import pe.aphid.core.model.Fertilizer
import pe.aphid.core.model.FertilizerDose
import pe.aphid.core.model.NutrientProfile
import pe.aphid.core.model.StockTank
import pe.aphid.core.model.TankGroup
import pe.aphid.core.model.WarningKind

/** Paso 8: solubilidad por tanque. */
object SolubilityChecker {

    /**
     * Un tanque es válido si cada insumo queda bajo su solubilidad a 20 °C y la suma de
     * fracciones de saturación no supera 1 (criterio conservador por efecto de ion común).
     */
    fun tankOk(doses: List<FertilizerDose>, byId: Map<String, Fertilizer>, factor: Int): Boolean {
        var sum = 0.0
        for (d in doses) {
            val f = byId.getValue(d.fertilizerId)
            if (f.miscible) continue
            val sol = f.solubilityGPerL ?: continue
            val ratio = d.gramsPerLiterFinal * factor / sol
            if (ratio > 1.0) return false
            sum += ratio
        }
        return sum <= 1.0
    }

    fun check(
        tanks: List<StockTank>,
        byId: Map<String, Fertilizer>,
        current: ConcentrationFactor,
        warnings: MutableList<EngineWarning>,
    ): ConcentrationFactor? {
        tanks.flatMap { it.doses }.map { byId.getValue(it.fertilizerId) }
            .filter { !it.miscible && it.solubilityGPerL == null }
            .forEach {
                warnings += EngineWarning(WarningKind.DATO_PENDIENTE, "Solubilidad de ${it.name}: dato pendiente; no se verificó.")
            }
        val bad = tanks.filter { !it.solubilityOk }
        if (bad.isEmpty()) return null
        val candidates = ConcentrationFactor.entries.filter { it.value < current.value }.sortedByDescending { it.value }
        val fit = candidates.firstOrNull { cf ->
            tanks.filter { it.group != TankGroup.DIRECTO }.all { tankOk(it.doses, byId, cf.value) }
        }
        bad.forEach { t ->
            warnings += EngineWarning(
                WarningKind.SOLUBILIDAD,
                if (fit != null) {
                    "${t.group.label}: se excede la solubilidad a ${current.value}x. Usa ${fit.value}x."
                } else {
                    "${t.group.label}: se excede la solubilidad incluso a 50x. Divide el insumo en dos tanques o usa más agua."
                },
            )
        }
        return fit
    }
}

/**
 * Verifica que los gramos pesados en cada tanque, diluidos al factor, reproducen los ppm
 * reportados (agua + insumos). Devuelve el error relativo máximo en %.
 */
object MassBalance {
    fun recompute(tanks: List<StockTank>, water: NutrientProfile, byId: Map<String, Fertilizer>): NutrientProfile {
        val ppm = Element.entries.associateWith { e ->
            water[e] + tanks.sumOf { t ->
                // Litros de solución final que se preparan con el contenido del tanque.
                val servedLiters = if (t.group == TankGroup.DIRECTO) t.volumeL else t.volumeL * t.factor
                t.doses.sumOf { d -> d.gramsInStockTank / servedLiters * byId.getValue(d.fertilizerId).mgPerGram(e) }
            }
        }
        return NutrientProfile(ppm)
    }

    fun relativeErrorPct(
        tanks: List<StockTank>,
        water: NutrientProfile,
        delivered: NutrientProfile,
        byId: Map<String, Fertilizer>,
    ): Double {
        val re = recompute(tanks, water, byId)
        return Element.entries.filter { delivered[it] > 1e-6 }
            .maxOfOrNull { abs(re[it] - delivered[it]) / delivered[it] * 100.0 } ?: 0.0
    }
}

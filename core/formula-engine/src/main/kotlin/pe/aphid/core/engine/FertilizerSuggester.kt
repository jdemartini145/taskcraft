package pe.aphid.core.engine

import pe.aphid.core.model.Element
import pe.aphid.core.model.EngineOptions
import pe.aphid.core.model.Fertilizer
import pe.aphid.core.model.NutrientProfile
import pe.aphid.core.model.WaterAnalysis

data class Suggestion(val fertilizer: Fertilizer, val covers: List<Element>, val reason: String)

/** Sugiere insumos del catálogo para cubrir los elementos que la selección no aporta. */
object FertilizerSuggester {

    fun suggest(
        catalog: List<Fertilizer>,
        selectedIds: Set<String>,
        targets: NutrientProfile,
        water: WaterAnalysis = WaterAnalysis.PURE,
        options: EngineOptions = EngineOptions(),
    ): List<Suggestion> {
        val selected = catalog.filter { it.id in selectedIds }
        val net = targets.minusFloorZero(water.toProfile())
        val missing = Element.entries
            .filter { e -> net[e] > 0 && selected.none { it.contains(e) } }
            .toMutableList()
        val out = mutableListOf<Suggestion>()
        val candidates = catalog.filter { it.id !in selectedIds && !it.isAcid }
        while (missing.isNotEmpty()) {
            // Insumo que cubre más elementos faltantes y aporta menos elementos no deseados.
            val best = candidates.filter { c -> out.none { it.fertilizer.id == c.id } }
                .map { c -> c to missing.filter { c.contains(it) } }
                .filter { it.second.isNotEmpty() }
                .maxWithOrNull(
                    compareBy<Pair<Fertilizer, List<Element>>> { it.second.size }
                        .thenBy { p -> -p.first.composition.keys.count { e -> net[e] <= 0 } }
                        .thenBy { if (it.first.verified) 1 else 0 },
                ) ?: break
            out += Suggestion(best.first, best.second, "Aporta ${best.second.joinToString { it.symbol }}")
            missing.removeAll(best.second)
        }
        if (AcidCalculator.requiredMeq(water, options) > 0 && selected.none { it.isAcid }) {
            catalog.filter { it.isAcid && it.id !in selectedIds }
                .sortedBy { if (it.contains(Element.N_NO3)) 0 else 1 }
                .firstOrNull()
                ?.let { out += Suggestion(it, emptyList(), "Tu agua es alcalina (HCO₃ alto): neutraliza con ácido") }
        }
        return out
    }
}

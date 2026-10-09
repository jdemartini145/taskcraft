package pe.aphid.core.domain.ai

import java.text.Normalizer
import pe.aphid.core.model.ConcentrationFactor
import pe.aphid.core.model.Crop
import pe.aphid.core.model.FormulaRequest
import pe.aphid.core.model.GrowSystemType
import pe.aphid.core.model.GrowthStage

/**
 * Parser determinista y offline de pedidos en lenguaje natural, p. ej.
 * "Fórmula para fresa en fructificación, 200 L, agua de pozo". Es la primera opción
 * (sin red, sin modelo); los proveedores de IA solo se usan si este no entiende el texto.
 */
class RuleBasedRequestParser(private val crops: List<Crop>) {

    data class Parsed(val request: FormulaRequest, val missing: List<String>)

    fun parse(text: String): Parsed? {
        val t = normalize(text)
        val crop = crops
            .flatMap { c -> listOf(normalize(c.commonName) to c, normalize(c.id).replace('_', ' ') to c) + aliases(c) }
            .sortedByDescending { it.first.length }
            .firstOrNull { (name, _) -> Regex("\\b${Regex.escape(name)}(s|es)?\\b").containsMatchIn(t) }
            ?.second
        val stage = STAGES.entries.firstOrNull { (k, _) -> Regex("\\b$k").containsMatchIn(t) }?.value
        val volume = VOLUME.find(t)?.let { m -> m.groupValues[1].replace(',', '.').toDoubleOrNull()?.let { v -> toLiters(v, m.groupValues[2]) } }
        val factor = FACTOR.find(t)?.groupValues?.get(1)?.toIntOrNull()?.let { f -> ConcentrationFactor.entries.firstOrNull { it.value == f } }
        val system = SYSTEMS.entries.firstOrNull { (k, _) -> Regex("\\b$k\\b").containsMatchIn(t) }?.value
        val water = WATER.entries.firstOrNull { (k, _) -> t.contains(k) }?.value
        if (crop == null && volume == null && stage == null) return null
        val missing = buildList {
            if (crop == null) add("cultivo")
            if (stage == null) add("etapa")
            if (volume == null) add("volumen")
        }
        return Parsed(
            FormulaRequest(
                cropId = crop?.id,
                stage = stage,
                systemType = system,
                reservoirVolumeL = volume ?: 0.0,
                waterHint = water,
                concentrationFactor = factor ?: ConcentrationFactor.X100,
            ),
            missing,
        )
    }

    private fun aliases(c: Crop): List<Pair<String, Crop>> = when (c.id) {
        "culantro" -> listOf("cilantro" to c)
        "palta" -> listOf("aguacate" to c)
        "frijol" -> listOf("frejol" to c, "poroto" to c)
        "pimiento" -> listOf("morron" to c)
        "rabanito" -> listOf("rabano" to c)
        "brocoli" -> listOf("brocoli" to c)
        "cebolla_china" -> listOf("cebollita china" to c, "cebolla de verdeo" to c)
        else -> emptyList()
    }

    companion object {
        fun normalize(s: String): String = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")

        private val STAGES = linkedMapOf(
            "plantula" to GrowthStage.PLANTULA, "germinacion" to GrowthStage.PLANTULA, "almacigo" to GrowthStage.PLANTULA,
            "vegetativ" to GrowthStage.VEGETATIVA, "crecimiento" to GrowthStage.VEGETATIVA,
            "floracion" to GrowthStage.FLORACION, "flor" to GrowthStage.FLORACION,
            "fructificacion" to GrowthStage.FRUCTIFICACION, "fruto" to GrowthStage.FRUCTIFICACION, "produccion" to GrowthStage.FRUCTIFICACION,
        )
        private val VOLUME = Regex("(\\d+(?:[.,]\\d+)?)\\s*(l|lt|lts|litros?|gal|galones?|m3)\\b")
        private val FACTOR = Regex("\\b(50|100|200)\\s*x\\b")
        private val SYSTEMS = linkedMapOf(
            "dwc" to GrowSystemType.DWC, "nft" to GrowSystemType.NFT, "kratky" to GrowSystemType.KRATKY,
            "torre" to GrowSystemType.TORRE_VERTICAL, "goteo" to GrowSystemType.GOTEO,
            "flujo y reflujo" to GrowSystemType.FLUJO_REFLUJO, "raiz flotante" to GrowSystemType.RAIZ_FLOTANTE,
        )
        private val WATER = linkedMapOf(
            "pozo" to "agua de pozo", "lluvia" to "agua de lluvia", "osmosis" to "agua de ósmosis",
            "caño" to "agua de caño", "cano" to "agua de caño", "red" to "agua de red", "dura" to "agua dura",
        )

        private fun toLiters(v: Double, unit: String): Double = when {
            unit.startsWith("gal") -> v * 3.785
            unit == "m3" -> v * 1000
            else -> v
        }
    }
}

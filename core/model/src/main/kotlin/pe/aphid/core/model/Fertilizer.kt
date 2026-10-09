package pe.aphid.core.model

import kotlinx.serialization.Serializable

/** Tanque de solución madre. */
@Serializable
enum class TankGroup(val label: String) {
    A("Tanque A (calcio)"),
    B("Tanque B (sulfatos y fosfatos)"),
    ACIDO("Tanque ácido"),
    /** Insumo que no puede ir en ninguna madre (p. ej. contiene Ca y SO₄): se disuelve directo en el reservorio. */
    DIRECTO("Directo al reservorio"),
}

@Serializable
enum class FertilizerKind { SAL, QUELATO, ACIDO, MEZCLA }

/** Datos ácido-base de un ácido líquido. */
@Serializable
data class AcidInfo(
    /** Masa molar del ácido puro (g/mol). */
    val molarMass: Double,
    /** Equivalentes de H⁺ aprovechables por mol al neutralizar HCO₃⁻ (HNO₃ = 1, H₃PO₄ ≈ 1 a pH 5,5–6). */
    val equivalentsPerMol: Double,
    /** Densidad de la solución comercial (g/mL). */
    val densityGPerMl: Double,
)

@Serializable
data class Fertilizer(
    val id: String,
    val name: String,
    val chemicalFormula: String,
    /** Pureza o concentración comercial en % (p/p). */
    val purityPct: Double = 100.0,
    /** % p/p de cada elemento en el compuesto puro. */
    val composition: Map<Element, Double>,
    /** Solubilidad a 20 °C en g/L; null = dato pendiente. */
    val solubilityGPerL: Double? = null,
    val miscible: Boolean = false,
    val defaultTank: TankGroup,
    val kind: FertilizerKind = FertilizerKind.SAL,
    val acid: AcidInfo? = null,
    val pricePerKgPen: Double? = null,
    val supplier: String = "",
    val editable: Boolean = true,
    val source: String = "",
    val verified: Boolean = true,
) {
    /** mg de elemento aportados por 1 g de insumo comercial disuelto en 1 L. */
    fun mgPerGram(e: Element): Double = (composition[e] ?: 0.0) * 10.0 * purityPct / 100.0

    fun contains(e: Element): Boolean = (composition[e] ?: 0.0) > 0.0

    val isMicro: Boolean
        get() = kind != FertilizerKind.ACIDO && composition.keys.none { it.isMacro && it != Element.S } &&
            composition.keys.any { !it.isMacro }

    val isAcid: Boolean get() = kind == FertilizerKind.ACIDO && acid != null
}

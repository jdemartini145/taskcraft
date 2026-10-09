package pe.aphid.core.model

import kotlinx.serialization.Serializable

/**
 * Elementos nutritivos que maneja el motor. Las concentraciones siempre se expresan
 * en mg/L (ppm) del elemento (N-NO3 y N-NH4 como mg/L de nitrógeno).
 *
 * @property molarMass masa atómica del elemento (g/mol).
 * @property cationCharge carga si el elemento se comporta como catión en solución (para meq/L); 0 si no.
 */
@Serializable
enum class Element(
    val symbol: String,
    val isMacro: Boolean,
    val molarMass: Double,
    val cationCharge: Int,
) {
    N_NO3("N-NO₃", true, 14.007, 0),
    N_NH4("N-NH₄", true, 14.007, 1),
    P("P", true, 30.974, 0),
    K("K", true, 39.098, 1),
    Ca("Ca", true, 40.078, 2),
    Mg("Mg", true, 24.305, 2),
    S("S", true, 32.06, 0),
    Fe("Fe", false, 55.845, 0),
    Mn("Mn", false, 54.938, 0),
    Zn("Zn", false, 65.38, 0),
    Cu("Cu", false, 63.546, 0),
    B("B", false, 10.81, 0),
    Mo("Mo", false, 95.95, 0),
    ;

    /** mg/L → meq/L para cationes. */
    fun toMeq(mgPerL: Double): Double = if (cationCharge == 0) 0.0 else mgPerL * cationCharge / molarMass

    companion object {
        val macros: List<Element> = entries.filter { it.isMacro }
        val micros: List<Element> = entries.filterNot { it.isMacro }
    }
}

/** Perfil de nutrientes en mg/L. Los elementos ausentes valen 0. */
@Serializable
data class NutrientProfile(val ppm: Map<Element, Double> = emptyMap()) {
    operator fun get(e: Element): Double = ppm[e] ?: 0.0

    operator fun plus(other: NutrientProfile): NutrientProfile =
        NutrientProfile(Element.entries.associateWith { this[it] + other[it] }.filterValues { it != 0.0 })

    /** Resta con piso 0 (meta neta = meta - agua). */
    fun minusFloorZero(other: NutrientProfile): NutrientProfile =
        NutrientProfile(Element.entries.associateWith { maxOf(0.0, this[it] - other[it]) }.filterValues { it > 0.0 })

    fun totalN(): Double = this[Element.N_NO3] + this[Element.N_NH4]

    companion object {
        val ZERO = NutrientProfile()
        fun of(vararg pairs: Pair<Element, Double>) = NutrientProfile(pairs.toMap())
    }
}

/** Rango con límites opcionales: un límite nulo significa "sin dato" en ese extremo. */
@Serializable
data class ValueRange(val min: Double? = null, val max: Double? = null) {
    val isEmpty: Boolean get() = min == null && max == null
    fun contains(v: Double): Boolean = (min == null || v >= min) && (max == null || v <= max)
    val midpoint: Double?
        get() = when {
            min != null && max != null -> (min + max) / 2
            else -> min ?: max
        }

    /** Intersección; devuelve null si es vacía (incompatibles). */
    fun intersect(other: ValueRange): ValueRange? {
        val lo = listOfNotNull(min, other.min).maxOrNull()
        val hi = listOfNotNull(max, other.max).minOrNull()
        return if (lo != null && hi != null && lo > hi) null else ValueRange(lo, hi)
    }
}

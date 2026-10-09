package pe.aphid.core.model

import kotlinx.serialization.Serializable

/** Análisis de agua en mg/L de cada ion. */
@Serializable
data class WaterAnalysis(
    val id: Long = 0,
    val name: String,
    val dateEpochDay: Long = 0,
    val ca: Double = 0.0,
    val mg: Double = 0.0,
    val na: Double = 0.0,
    val k: Double = 0.0,
    val cl: Double = 0.0,
    val so4: Double = 0.0,
    val no3: Double = 0.0,
    val hco3: Double = 0.0,
    val ph: Double? = null,
    val ec: Double? = null,
) {
    /** Aporte del agua expresado como elementos (mg/L). */
    fun toProfile(): NutrientProfile = NutrientProfile.of(
        Element.Ca to ca,
        Element.Mg to mg,
        Element.K to k,
        Element.N_NO3 to no3 * N_PER_NO3,
        Element.S to so4 * S_PER_SO4,
    )

    val hco3Meq: Double get() = hco3 / HCO3_MOLAR_MASS
    val naMeq: Double get() = na / 22.990

    companion object {
        const val N_PER_NO3 = 14.007 / 62.004
        const val S_PER_SO4 = 32.06 / 96.06
        const val HCO3_MOLAR_MASS = 61.017

        /** Agua de ósmosis inversa o de lluvia: todos los iones en 0. */
        val PURE = WaterAnalysis(id = -1, name = "Agua de ósmosis/lluvia (0)")
    }
}

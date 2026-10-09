package pe.aphid.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class GrowthStage(val label: String) {
    PLANTULA("Plántula"),
    VEGETATIVA("Vegetativa"),
    FLORACION("Floración"),
    FRUCTIFICACION("Fructificación"),
}

/** Marca usada en datos semilla cuando un valor agronómico no tiene fuente verificable. */
const val TODO_FUENTE = "TODO_FUENTE"

@Serializable
data class Crop(
    val id: String,
    val commonName: String,
    val scientificName: String,
    val family: String,
    /** Días por etapa; null = dato pendiente (TODO_FUENTE). */
    val daysPerStage: Map<GrowthStage, Int?> = emptyMap(),
)

/**
 * Meta nutricional de un cultivo en una etapa. Los ppm nulos son "dato pendiente":
 * el motor no inventa valores y la UI los muestra como pendientes.
 */
@Serializable
data class CropStageTarget(
    val cropId: String,
    val stage: GrowthStage,
    val targetsPpm: Map<Element, Double?> = emptyMap(),
    val ec: ValueRange = ValueRange(),
    val ph: ValueRange = ValueRange(),
    val solutionTempC: ValueRange = ValueRange(),
    val dli: ValueRange = ValueRange(),
    val source: String = TODO_FUENTE,
    /** Receta de referencia que sirve de meta cuando la fuente define la etapa por receta. */
    val recipeId: String? = null,
) {
    /** true si la meta tiene al menos los macronutrientes completos para calcular una fórmula. */
    val hasCompleteMacros: Boolean
        get() = Element.macros.filter { it != Element.N_NH4 }.all { targetsPpm[it] != null }

    val pendingElements: List<Element> get() = Element.entries.filter { targetsPpm[it] == null }

    fun toProfile(): NutrientProfile = NutrientProfile(targetsPpm.filterValues { it != null }.mapValues { it.value!! })
}

/** Receta de referencia (Hoagland, La Molina, comercial). */
@Serializable
data class ReferenceRecipe(
    val id: String,
    val name: String,
    val source: String,
    val description: String,
    val targetsPpm: Map<Element, Double>,
    val verified: Boolean,
    val note: String = "",
) {
    fun toProfile() = NutrientProfile(targetsPpm)
}

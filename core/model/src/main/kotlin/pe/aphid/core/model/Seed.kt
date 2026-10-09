package pe.aphid.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class CropsFile(val version: Int, val crops: List<Crop>, val stageTargets: List<CropStageTarget>)

@Serializable
data class FertilizersFile(val version: Int, val fertilizers: List<Fertilizer>)

@Serializable
data class RecipesFile(val version: Int, val recipes: List<ReferenceRecipe>)

/** Lectura de los JSON semilla (`assets/crops_v2.json`, `fertilizers_v1.json`, `recipes_v1.json`). */
object SeedParser {
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        prettyPrint = false
    }

    fun crops(text: String): CropsFile = json.decodeFromString(CropsFile.serializer(), text)
    fun fertilizers(text: String): FertilizersFile = json.decodeFromString(FertilizersFile.serializer(), text)
    fun recipes(text: String): RecipesFile = json.decodeFromString(RecipesFile.serializer(), text)
}

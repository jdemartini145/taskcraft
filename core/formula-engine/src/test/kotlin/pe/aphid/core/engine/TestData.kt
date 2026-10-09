package pe.aphid.core.engine

import java.io.File
import pe.aphid.core.model.CropsFile
import pe.aphid.core.model.Fertilizer
import pe.aphid.core.model.ReferenceRecipe
import pe.aphid.core.model.SeedParser

object TestData {
    private val dir: File by lazy {
        File(System.getProperty("aphid.seedDir") ?: "../database/src/main/assets")
    }

    val fertilizers: List<Fertilizer> by lazy { SeedParser.fertilizers(File(dir, "fertilizers_v1.json").readText()).fertilizers }
    val recipes: List<ReferenceRecipe> by lazy { SeedParser.recipes(File(dir, "recipes_v1.json").readText()).recipes }
    val crops: CropsFile by lazy { SeedParser.crops(File(dir, "crops_v2.json").readText()) }

    fun fert(id: String): Fertilizer = fertilizers.first { it.id == id }
    fun recipe(id: String): ReferenceRecipe = recipes.first { it.id == id }
    fun ferts(vararg ids: String): List<Fertilizer> = ids.map(::fert)

    /** Sales estándar de 6 insumos (más micronutrientes) usadas por los criterios de aceptación. */
    val standardSix = listOf(
        "nitrato_calcio", "nitrato_potasio", "fosfato_monopotasico", "sulfato_magnesio", "sulfato_potasio", "quelato_fe_edta",
    )
    val micros = listOf("sulfato_manganeso", "sulfato_zinc", "sulfato_cobre", "acido_borico", "molibdato_sodio")
}

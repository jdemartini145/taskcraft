package pe.aphid.core.data.seed

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import pe.aphid.core.data.repository.SettingsDataStore
import pe.aphid.core.database.dao.CropDao
import pe.aphid.core.database.dao.FertilizerDao
import pe.aphid.core.database.toEntity
import pe.aphid.core.model.SeedParser
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Carga los datos semilla desde `assets/` la primera vez y cuando cambia su versión.
 * Cultivos y recetas se actualizan; los insumos solo se insertan si no existen para
 * respetar las ediciones del usuario (precio, pureza, proveedor).
 */
@Singleton
class SeedLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cropDao: CropDao,
    private val fertilizerDao: FertilizerDao,
    private val settings: SettingsDataStore,
) {
    suspend fun ensureSeeded() = withContext(Dispatchers.IO) {
        val cropsText = read(CROPS)
        val fertText = read(FERTILIZERS)
        val recipesText = read(RECIPES)
        val crops = SeedParser.crops(cropsText)
        val ferts = SeedParser.fertilizers(fertText)
        val recipes = SeedParser.recipes(recipesText)
        val versions = "crops=${crops.version};fert=${ferts.version};recipes=${recipes.version}"
        if (settings.seedVersions.first() == versions && cropDao.count() > 0) return@withContext
        cropDao.upsertCrops(crops.crops.map { it.toEntity() })
        cropDao.upsertTargets(crops.stageTargets.map { it.toEntity() })
        cropDao.upsertRecipes(recipes.recipes.map { it.toEntity() })
        fertilizerDao.insertIfAbsent(ferts.fertilizers.map { it.toEntity() })
        settings.setSeedVersions(versions)
        Timber.i("Datos semilla cargados: %s", versions)
    }

    private fun read(name: String): String = context.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }

    companion object {
        const val CROPS = "crops_v2.json"
        const val FERTILIZERS = "fertilizers_v1.json"
        const val RECIPES = "recipes_v1.json"
    }
}

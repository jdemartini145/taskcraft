package pe.aphid.core.data.repository

import androidx.room.withTransaction
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pe.aphid.core.database.AphidDatabase
import pe.aphid.core.database.entity.DiagnosisEntity
import pe.aphid.core.database.entity.FormulaResultEntity
import pe.aphid.core.database.toEntity
import pe.aphid.core.database.toModel
import pe.aphid.core.domain.repository.BackupRepository
import pe.aphid.core.domain.repository.UserSettings
import pe.aphid.core.model.Alert
import pe.aphid.core.model.CareTask
import pe.aphid.core.model.Fertilizer
import pe.aphid.core.model.GrowSystem
import pe.aphid.core.model.PestRecord
import pe.aphid.core.model.Planting
import pe.aphid.core.model.Reading
import pe.aphid.core.model.WaterAnalysis

/** Respaldo completo del usuario. Los datos semilla no se incluyen (se recargan). */
@Serializable
data class BackupFile(
    val format: String = "aphid-backup",
    val version: Int = 1,
    val createdMillis: Long,
    val settings: UserSettings,
    val fertilizers: List<Fertilizer>,
    val water: List<WaterAnalysis>,
    val systems: List<GrowSystem>,
    val plantings: List<Planting>,
    val readings: List<Reading>,
    val tasks: List<CareTask>,
    val alerts: List<Alert>,
    val pests: List<PestRecord>,
    val formulas: List<FormulaRow>,
    val diagnoses: List<DiagnosisRow>,
)

@Serializable
data class FormulaRow(
    val createdMillis: Long,
    val label: String,
    val requestJson: String,
    val resultJson: String,
    val estimatedEc: Double,
    val cost: Double?,
    val engineVersion: String,
)

@Serializable
data class DiagnosisRow(val systemId: Long?, val createdMillis: Long, val provider: String, val outputJson: String)

class BackupRepositoryImpl @Inject constructor(
    private val db: AphidDatabase,
    private val settings: SettingsDataStore,
) : BackupRepository {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    override suspend fun exportJson(): String {
        val file = BackupFile(
            createdMillis = System.currentTimeMillis(),
            settings = settings.settings.first(),
            fertilizers = db.fertilizerDao().all().map { it.toModel() },
            water = db.waterDao().all().map { it.toModel() },
            systems = db.systemDao().all().map { it.toModel() },
            plantings = db.systemDao().allPlantings().map { it.toModel() },
            readings = db.readingDao().all().map { it.toModel() },
            tasks = db.taskDao().all().map { it.toModel() },
            alerts = db.alertDao().all().map { it.toModel() },
            pests = db.pestDao().all().map { it.toModel() },
            formulas = db.formulaDao().all().map { FormulaRow(it.createdMillis, it.label, it.requestJson, it.resultJson, it.estimatedEc, it.costPer1000LPen, it.engineVersion) },
            diagnoses = db.diagnosisDao().all().map { DiagnosisRow(it.systemId, it.createdMillis, it.provider, it.outputJson) },
        )
        return json.encodeToString(BackupFile.serializer(), file)
    }

    override suspend fun importJson(json: String) {
        val file = this.json.decodeFromString(BackupFile.serializer(), json)
        require(file.format == "aphid-backup") { "El archivo no es un respaldo de APhid" }
        db.withTransaction {
            file.fertilizers.forEach { db.fertilizerDao().upsert(it.toEntity()) }
            file.water.forEach { db.waterDao().upsert(it.toEntity()) }
            file.systems.forEach { db.systemDao().upsert(it.toEntity()) }
            file.plantings.forEach { db.systemDao().upsertPlanting(it.toEntity()) }
            db.readingDao().insertAll(file.readings.map { it.toEntity().copy(id = 0) })
            file.tasks.forEach { db.taskDao().upsert(it.toEntity()) }
            file.alerts.forEach { db.alertDao().insert(it.toEntity().copy(id = 0)) }
            file.pests.forEach { db.pestDao().insert(it.toEntity().copy(id = 0)) }
            db.formulaDao().insertAll(
                file.formulas.map { FormulaResultEntity(0, it.createdMillis, it.label, it.requestJson, it.resultJson, it.estimatedEc, it.cost, it.engineVersion) },
            )
            file.diagnoses.forEach { db.diagnosisDao().insert(DiagnosisEntity(0, it.systemId, null, it.createdMillis, it.provider, it.outputJson)) }
        }
        settings.update { file.settings }
    }

    /** "Borrar todos mis datos": vacía la base y las preferencias. */
    override suspend fun deleteAll() {
        withContext(Dispatchers.IO) { db.clearAllTables() }
        settings.clear()
    }
}

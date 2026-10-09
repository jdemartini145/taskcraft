package pe.aphid.core.data.repository

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pe.aphid.core.database.DbJson
import pe.aphid.core.database.dao.AlertDao
import pe.aphid.core.database.dao.CropDao
import pe.aphid.core.database.dao.DiagnosisDao
import pe.aphid.core.database.dao.FertilizerDao
import pe.aphid.core.database.dao.FormulaDao
import pe.aphid.core.database.dao.PestDao
import pe.aphid.core.database.dao.ReadingDao
import pe.aphid.core.database.dao.SystemDao
import pe.aphid.core.database.dao.TaskDao
import pe.aphid.core.database.dao.WaterDao
import pe.aphid.core.database.entity.DiagnosisEntity
import pe.aphid.core.database.entity.FormulaResultEntity
import pe.aphid.core.database.entity.PhotoEntity
import pe.aphid.core.database.toEntity
import pe.aphid.core.database.toModel
import pe.aphid.core.domain.repository.AlertRepository
import pe.aphid.core.domain.repository.CropRepository
import pe.aphid.core.domain.repository.DiagnosisRepository
import pe.aphid.core.domain.repository.FertilizerRepository
import pe.aphid.core.domain.repository.FormulaRepository
import pe.aphid.core.domain.repository.PestRepository
import pe.aphid.core.domain.repository.ReadingRepository
import pe.aphid.core.domain.repository.SystemRepository
import pe.aphid.core.domain.repository.TaskRepository
import pe.aphid.core.domain.repository.WaterRepository
import pe.aphid.core.model.Alert
import pe.aphid.core.model.CareTask
import pe.aphid.core.model.Crop
import pe.aphid.core.model.CropStageTarget
import pe.aphid.core.model.Diagnosis
import pe.aphid.core.model.DiagnosisOutput
import pe.aphid.core.model.Fertilizer
import pe.aphid.core.model.FormulaRequest
import pe.aphid.core.model.FormulaResult
import pe.aphid.core.model.GrowSystem
import pe.aphid.core.model.GrowthStage
import pe.aphid.core.model.PestRecord
import pe.aphid.core.model.Photo
import pe.aphid.core.model.Planting
import pe.aphid.core.model.Reading
import pe.aphid.core.model.ReferenceRecipe
import pe.aphid.core.model.SavedFormula
import pe.aphid.core.model.WaterAnalysis

/** Room devuelve -1 en un upsert que actualiza: en ese caso el id es el del objeto. */
private fun Long.orId(id: Long) = if (this == -1L) id else this

class OfflineCropRepository @Inject constructor(private val dao: CropDao) : CropRepository {
    override fun crops(): Flow<List<Crop>> = dao.observeAll().map { l -> l.map { it.toModel() } }
    override suspend fun crop(id: String): Crop? = dao.get(id)?.toModel()
    override suspend fun stageTarget(cropId: String, stage: GrowthStage): CropStageTarget? = dao.target(cropId, stage)?.toModel()
    override fun stageTargets(cropId: String): Flow<List<CropStageTarget>> = dao.observeTargets(cropId).map { l -> l.map { it.toModel() } }
    override suspend fun recipes(): List<ReferenceRecipe> = dao.recipes().map { it.toModel() }
    override suspend fun recipe(id: String): ReferenceRecipe? = dao.recipe(id)?.toModel()
}

class OfflineFertilizerRepository @Inject constructor(private val dao: FertilizerDao) : FertilizerRepository {
    override fun fertilizers(): Flow<List<Fertilizer>> = dao.observeAll().map { l -> l.map { it.toModel() } }
    override suspend fun all(): List<Fertilizer> = dao.all().map { it.toModel() }
    override suspend fun upsert(fertilizer: Fertilizer) = dao.upsert(fertilizer.toEntity())
    override suspend fun delete(id: String) = dao.delete(id)
}

class OfflineWaterRepository @Inject constructor(private val dao: WaterDao) : WaterRepository {
    override fun analyses(): Flow<List<WaterAnalysis>> = dao.observeAll().map { l -> l.map { it.toModel() } }
    override suspend fun get(id: Long): WaterAnalysis? = dao.get(id)?.toModel()
    override suspend fun upsert(analysis: WaterAnalysis): Long = dao.upsert(analysis.toEntity()).orId(analysis.id)
    override suspend fun delete(id: Long) = dao.delete(id)
}

class OfflineSystemRepository @Inject constructor(private val dao: SystemDao) : SystemRepository {
    override fun systems(): Flow<List<GrowSystem>> = dao.observeAll().map { l -> l.map { it.toModel() } }
    override suspend fun get(id: Long): GrowSystem? = dao.get(id)?.toModel()
    override suspend fun upsert(system: GrowSystem): Long = dao.upsert(system.toEntity()).orId(system.id)
    override suspend fun delete(id: Long) = dao.delete(id)
    override fun plantings(systemId: Long): Flow<List<Planting>> = dao.observePlantings(systemId).map { l -> l.map { it.toModel() } }
    override suspend fun plantingsNow(systemId: Long): List<Planting> = dao.plantings(systemId).map { it.toModel() }
    override suspend fun upsertPlanting(planting: Planting): Long = dao.upsertPlanting(planting.toEntity()).orId(planting.id)
    override suspend fun deletePlanting(id: Long) = dao.deletePlanting(id)
}

class OfflineReadingRepository @Inject constructor(private val dao: ReadingDao) : ReadingRepository {
    override fun readings(systemId: Long): Flow<List<Reading>> = dao.observe(systemId).map { l -> l.map { it.toModel() } }
    override fun latest(): Flow<Reading?> = dao.observeLatest().map { it?.toModel() }
    override suspend fun since(systemId: Long, fromMillis: Long): List<Reading> = dao.since(systemId, fromMillis).map { it.toModel() }
    override suspend fun add(reading: Reading): Long = dao.insert(reading.toEntity())
    override suspend fun delete(id: Long) = dao.delete(id)
}

class OfflineFormulaRepository @Inject constructor(private val dao: FormulaDao) : FormulaRepository {
    private fun FormulaResultEntity.toModel() = SavedFormula(id, createdMillis, label, DbJson.decodeFromString(FormulaResult.serializer(), resultJson))

    override fun saved(): Flow<List<SavedFormula>> = dao.observeAll().map { l -> l.mapNotNull { runCatching { it.toModel() }.getOrNull() } }
    override suspend fun get(id: Long): SavedFormula? = dao.get(id)?.let { runCatching { it.toModel() }.getOrNull() }
    override suspend fun latest(): SavedFormula? = dao.latest()?.let { runCatching { it.toModel() }.getOrNull() }
    override suspend fun save(formula: SavedFormula): Long = dao.insert(
        FormulaResultEntity(
            createdMillis = formula.createdMillis,
            label = formula.label,
            requestJson = DbJson.encodeToString(FormulaRequest.serializer(), formula.result.request),
            resultJson = DbJson.encodeToString(FormulaResult.serializer(), formula.result),
            estimatedEc = formula.result.estimatedEc,
            costPer1000LPen = formula.result.costPer1000LPen,
            engineVersion = formula.result.engineVersion,
        ),
    )
    override suspend fun delete(id: Long) = dao.delete(id)
}

class OfflineTaskRepository @Inject constructor(private val dao: TaskDao) : TaskRepository {
    override fun tasks(): Flow<List<CareTask>> = dao.observeAll().map { l -> l.map { it.toModel() } }
    override fun nextTask(): Flow<CareTask?> = dao.observeNext().map { it?.toModel() }
    override suspend fun dueBefore(millis: Long): List<CareTask> = dao.dueBefore(millis).map { it.toModel() }
    override suspend fun upsert(task: CareTask): Long = dao.upsert(task.toEntity()).orId(task.id)

    /** Completa la tarea; si es periódica crea la siguiente. */
    override suspend fun complete(id: Long, nowMillis: Long) {
        val t = dao.get(id)?.toModel() ?: return
        dao.upsert(t.copy(done = true).toEntity())
        t.repeatDays?.let { d -> dao.upsert(t.copy(id = 0, done = false, dueMillis = nowMillis + d * DAY_MS).toEntity()) }
    }
    override suspend fun delete(id: Long) = dao.delete(id)

    private companion object { const val DAY_MS = 86_400_000L }
}

class OfflineAlertRepository @Inject constructor(private val dao: AlertDao) : AlertRepository {
    override fun alerts(): Flow<List<Alert>> = dao.observeAll().map { l -> l.map { it.toModel() } }
    override suspend fun recent(sinceMillis: Long): List<Alert> = dao.since(sinceMillis).map { it.toModel() }
    override suspend fun add(alert: Alert): Long = dao.insert(alert.toEntity())
    override suspend fun markRead(id: Long) = dao.markRead(id)
    override suspend fun clear() = dao.clear()
}

class OfflineDiagnosisRepository @Inject constructor(private val dao: DiagnosisDao) : DiagnosisRepository {
    override fun diagnoses(): Flow<List<Diagnosis>> = dao.observeAll().map { l ->
        l.mapNotNull { e ->
            runCatching {
                Diagnosis(e.id, e.systemId, e.photoId, e.createdMillis, e.provider, DbJson.decodeFromString(DiagnosisOutput.serializer(), e.outputJson))
            }.getOrNull()
        }
    }
    override suspend fun save(diagnosis: Diagnosis): Long = dao.insert(
        DiagnosisEntity(
            systemId = diagnosis.systemId, photoId = diagnosis.photoId, createdMillis = diagnosis.createdMillis,
            provider = diagnosis.provider, outputJson = DbJson.encodeToString(DiagnosisOutput.serializer(), diagnosis.output),
        ),
    )
    override suspend fun savePhoto(photo: Photo): Long = dao.insertPhoto(PhotoEntity(0, photo.systemId, photo.uri, photo.takenMillis, photo.note))
}

class OfflinePestRepository @Inject constructor(private val dao: PestDao) : PestRepository {
    override fun records(): Flow<List<PestRecord>> = dao.observeAll().map { l -> l.map { it.toModel() } }
    override suspend fun add(record: PestRecord): Long = dao.insert(record.toEntity())
    override suspend fun delete(id: Long) = dao.delete(id)
}

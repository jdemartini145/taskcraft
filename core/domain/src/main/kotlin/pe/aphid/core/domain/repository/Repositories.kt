package pe.aphid.core.domain.repository

import kotlinx.coroutines.flow.Flow
import pe.aphid.core.model.Alert
import pe.aphid.core.model.CareTask
import pe.aphid.core.model.Crop
import pe.aphid.core.model.CropStageTarget
import pe.aphid.core.model.Diagnosis
import pe.aphid.core.model.Fertilizer
import pe.aphid.core.model.GrowSystem
import pe.aphid.core.model.GrowthStage
import pe.aphid.core.model.PestRecord
import pe.aphid.core.model.Photo
import pe.aphid.core.model.Planting
import pe.aphid.core.model.Reading
import pe.aphid.core.model.ReferenceRecipe
import pe.aphid.core.model.SavedFormula
import pe.aphid.core.model.WaterAnalysis

interface CropRepository {
    fun crops(): Flow<List<Crop>>
    suspend fun crop(id: String): Crop?
    suspend fun stageTarget(cropId: String, stage: GrowthStage): CropStageTarget?
    fun stageTargets(cropId: String): Flow<List<CropStageTarget>>
    suspend fun recipes(): List<ReferenceRecipe>
    suspend fun recipe(id: String): ReferenceRecipe?
}

interface FertilizerRepository {
    fun fertilizers(): Flow<List<Fertilizer>>
    suspend fun all(): List<Fertilizer>
    suspend fun upsert(fertilizer: Fertilizer)
    suspend fun delete(id: String)
}

interface WaterRepository {
    fun analyses(): Flow<List<WaterAnalysis>>
    suspend fun get(id: Long): WaterAnalysis?
    suspend fun upsert(analysis: WaterAnalysis): Long
    suspend fun delete(id: Long)
}

interface SystemRepository {
    fun systems(): Flow<List<GrowSystem>>
    suspend fun get(id: Long): GrowSystem?
    suspend fun upsert(system: GrowSystem): Long
    suspend fun delete(id: Long)
    fun plantings(systemId: Long): Flow<List<Planting>>
    suspend fun plantingsNow(systemId: Long): List<Planting>
    suspend fun upsertPlanting(planting: Planting): Long
    suspend fun deletePlanting(id: Long)
}

interface ReadingRepository {
    fun readings(systemId: Long): Flow<List<Reading>>
    fun latest(): Flow<Reading?>
    suspend fun since(systemId: Long, fromMillis: Long): List<Reading>
    suspend fun add(reading: Reading): Long
    suspend fun delete(id: Long)
}

interface FormulaRepository {
    fun saved(): Flow<List<SavedFormula>>
    suspend fun get(id: Long): SavedFormula?
    suspend fun latest(): SavedFormula?
    suspend fun save(formula: SavedFormula): Long
    suspend fun delete(id: Long)
}

interface TaskRepository {
    fun tasks(): Flow<List<CareTask>>
    fun nextTask(): Flow<CareTask?>
    suspend fun dueBefore(millis: Long): List<CareTask>
    suspend fun upsert(task: CareTask): Long
    suspend fun complete(id: Long, nowMillis: Long)
    suspend fun delete(id: Long)
}

interface AlertRepository {
    fun alerts(): Flow<List<Alert>>
    suspend fun recent(sinceMillis: Long): List<Alert>
    suspend fun add(alert: Alert): Long
    suspend fun markRead(id: Long)
    suspend fun clear()
}

interface DiagnosisRepository {
    fun diagnoses(): Flow<List<Diagnosis>>
    suspend fun save(diagnosis: Diagnosis): Long
    suspend fun savePhoto(photo: Photo): Long
}

interface PestRepository {
    fun records(): Flow<List<PestRecord>>
    suspend fun add(record: PestRecord): Long
    suspend fun delete(id: Long)
}

/** Respaldo manual: exporta/importa toda la base como JSON. */
interface BackupRepository {
    suspend fun exportJson(): String
    suspend fun importJson(json: String)
    suspend fun deleteAll()
}

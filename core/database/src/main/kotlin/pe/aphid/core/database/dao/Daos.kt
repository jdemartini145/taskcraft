package pe.aphid.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import pe.aphid.core.database.entity.AlertEntity
import pe.aphid.core.database.entity.CropEntity
import pe.aphid.core.database.entity.CropStageTargetEntity
import pe.aphid.core.database.entity.DiagnosisEntity
import pe.aphid.core.database.entity.FertilizerEntity
import pe.aphid.core.database.entity.FormulaResultEntity
import pe.aphid.core.database.entity.GrowSystemEntity
import pe.aphid.core.database.entity.PestRecordEntity
import pe.aphid.core.database.entity.PhotoEntity
import pe.aphid.core.database.entity.PlantingEntity
import pe.aphid.core.database.entity.ReadingEntity
import pe.aphid.core.database.entity.RecipeEntity
import pe.aphid.core.database.entity.TaskEntity
import pe.aphid.core.database.entity.WaterAnalysisEntity
import pe.aphid.core.model.GrowthStage

@Dao
interface CropDao {
    @Query("SELECT * FROM crop ORDER BY commonName")
    fun observeAll(): Flow<List<CropEntity>>

    @Query("SELECT * FROM crop ORDER BY commonName")
    suspend fun all(): List<CropEntity>

    @Query("SELECT * FROM crop WHERE id = :id")
    suspend fun get(id: String): CropEntity?

    @Query("SELECT COUNT(*) FROM crop")
    suspend fun count(): Int

    @Upsert suspend fun upsertCrops(crops: List<CropEntity>)

    @Upsert suspend fun upsertTargets(targets: List<CropStageTargetEntity>)

    @Query("SELECT * FROM crop_stage_target WHERE cropId = :cropId AND stage = :stage")
    suspend fun target(cropId: String, stage: GrowthStage): CropStageTargetEntity?

    @Query("SELECT * FROM crop_stage_target WHERE cropId = :cropId")
    fun observeTargets(cropId: String): Flow<List<CropStageTargetEntity>>

    @Query("SELECT * FROM crop_stage_target")
    suspend fun allTargets(): List<CropStageTargetEntity>

    @Upsert suspend fun upsertRecipes(recipes: List<RecipeEntity>)

    @Query("SELECT * FROM reference_recipe ORDER BY name")
    suspend fun recipes(): List<RecipeEntity>

    @Query("SELECT * FROM reference_recipe WHERE id = :id")
    suspend fun recipe(id: String): RecipeEntity?
}

@Dao
interface FertilizerDao {
    @Query("SELECT * FROM fertilizer ORDER BY name")
    fun observeAll(): Flow<List<FertilizerEntity>>

    @Query("SELECT * FROM fertilizer ORDER BY name")
    suspend fun all(): List<FertilizerEntity>

    @Upsert suspend fun upsert(f: FertilizerEntity)

    /** Inserta semillas sin pisar las ediciones del usuario. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(list: List<FertilizerEntity>)

    @Query("DELETE FROM fertilizer WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface WaterDao {
    @Query("SELECT * FROM water_analysis ORDER BY dateEpochDay DESC")
    fun observeAll(): Flow<List<WaterAnalysisEntity>>

    @Query("SELECT * FROM water_analysis")
    suspend fun all(): List<WaterAnalysisEntity>

    @Query("SELECT * FROM water_analysis WHERE id = :id")
    suspend fun get(id: Long): WaterAnalysisEntity?

    @Upsert suspend fun upsert(w: WaterAnalysisEntity): Long

    @Query("DELETE FROM water_analysis WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface SystemDao {
    @Query("SELECT * FROM grow_system ORDER BY name")
    fun observeAll(): Flow<List<GrowSystemEntity>>

    @Query("SELECT * FROM grow_system")
    suspend fun all(): List<GrowSystemEntity>

    @Query("SELECT * FROM grow_system WHERE id = :id")
    suspend fun get(id: Long): GrowSystemEntity?

    @Upsert suspend fun upsert(s: GrowSystemEntity): Long

    @Query("DELETE FROM grow_system WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM planting WHERE systemId = :systemId ORDER BY sowingEpochDay DESC")
    fun observePlantings(systemId: Long): Flow<List<PlantingEntity>>

    @Query("SELECT * FROM planting WHERE systemId = :systemId")
    suspend fun plantings(systemId: Long): List<PlantingEntity>

    @Query("SELECT * FROM planting")
    suspend fun allPlantings(): List<PlantingEntity>

    @Upsert suspend fun upsertPlanting(p: PlantingEntity): Long

    @Query("DELETE FROM planting WHERE id = :id")
    suspend fun deletePlanting(id: Long)
}

@Dao
interface ReadingDao {
    @Query("SELECT * FROM reading WHERE systemId = :systemId ORDER BY timestampMillis DESC")
    fun observe(systemId: Long): Flow<List<ReadingEntity>>

    @Query("SELECT * FROM reading ORDER BY timestampMillis DESC LIMIT 1")
    fun observeLatest(): Flow<ReadingEntity?>

    @Query("SELECT * FROM reading WHERE systemId = :systemId AND timestampMillis >= :from ORDER BY timestampMillis")
    suspend fun since(systemId: Long, from: Long): List<ReadingEntity>

    @Query("SELECT * FROM reading")
    suspend fun all(): List<ReadingEntity>

    @Insert suspend fun insert(r: ReadingEntity): Long

    @Insert suspend fun insertAll(r: List<ReadingEntity>)

    @Query("DELETE FROM reading WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface FormulaDao {
    @Query("SELECT * FROM formula_result ORDER BY createdMillis DESC")
    fun observeAll(): Flow<List<FormulaResultEntity>>

    @Query("SELECT * FROM formula_result")
    suspend fun all(): List<FormulaResultEntity>

    @Query("SELECT * FROM formula_result WHERE id = :id")
    suspend fun get(id: Long): FormulaResultEntity?

    @Query("SELECT * FROM formula_result ORDER BY createdMillis DESC LIMIT 1")
    suspend fun latest(): FormulaResultEntity?

    @Insert suspend fun insert(f: FormulaResultEntity): Long

    @Insert suspend fun insertAll(f: List<FormulaResultEntity>)

    @Query("DELETE FROM formula_result WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM care_task ORDER BY done, dueMillis")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM care_task WHERE done = 0 ORDER BY dueMillis LIMIT 1")
    fun observeNext(): Flow<TaskEntity?>

    @Query("SELECT * FROM care_task WHERE done = 0 AND dueMillis <= :millis")
    suspend fun dueBefore(millis: Long): List<TaskEntity>

    @Query("SELECT * FROM care_task WHERE id = :id")
    suspend fun get(id: Long): TaskEntity?

    @Query("SELECT * FROM care_task")
    suspend fun all(): List<TaskEntity>

    @Upsert suspend fun upsert(t: TaskEntity): Long

    @Query("DELETE FROM care_task WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface AlertDao {
    @Query("SELECT * FROM alert ORDER BY createdMillis DESC LIMIT 200")
    fun observeAll(): Flow<List<AlertEntity>>

    @Query("SELECT * FROM alert WHERE createdMillis >= :since")
    suspend fun since(since: Long): List<AlertEntity>

    @Query("SELECT * FROM alert")
    suspend fun all(): List<AlertEntity>

    @Insert suspend fun insert(a: AlertEntity): Long

    @Query("UPDATE alert SET read = 1 WHERE id = :id")
    suspend fun markRead(id: Long)

    @Query("DELETE FROM alert")
    suspend fun clear()
}

@Dao
interface DiagnosisDao {
    @Query("SELECT * FROM diagnosis ORDER BY createdMillis DESC")
    fun observeAll(): Flow<List<DiagnosisEntity>>

    @Query("SELECT * FROM diagnosis")
    suspend fun all(): List<DiagnosisEntity>

    @Insert suspend fun insert(d: DiagnosisEntity): Long

    @Insert suspend fun insertPhoto(p: PhotoEntity): Long

    @Query("SELECT * FROM photo")
    suspend fun photos(): List<PhotoEntity>
}

@Dao
interface PestDao {
    @Query("SELECT * FROM pest_record ORDER BY observedMillis DESC")
    fun observeAll(): Flow<List<PestRecordEntity>>

    @Query("SELECT * FROM pest_record")
    suspend fun all(): List<PestRecordEntity>

    @Insert suspend fun insert(r: PestRecordEntity): Long

    @Query("DELETE FROM pest_record WHERE id = :id")
    suspend fun delete(id: Long)
}

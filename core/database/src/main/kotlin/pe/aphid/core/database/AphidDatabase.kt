package pe.aphid.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
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

/** Base local: fuente de verdad (offline-first). */
@Database(
    entities = [
        CropEntity::class, CropStageTargetEntity::class, RecipeEntity::class, FertilizerEntity::class,
        WaterAnalysisEntity::class, GrowSystemEntity::class, PlantingEntity::class, ReadingEntity::class,
        FormulaResultEntity::class, TaskEntity::class, AlertEntity::class, PhotoEntity::class,
        DiagnosisEntity::class, PestRecordEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AphidDatabase : RoomDatabase() {
    abstract fun cropDao(): CropDao
    abstract fun fertilizerDao(): FertilizerDao
    abstract fun waterDao(): WaterDao
    abstract fun systemDao(): SystemDao
    abstract fun readingDao(): ReadingDao
    abstract fun formulaDao(): FormulaDao
    abstract fun taskDao(): TaskDao
    abstract fun alertDao(): AlertDao
    abstract fun diagnosisDao(): DiagnosisDao
    abstract fun pestDao(): PestDao

    companion object {
        const val NAME = "aphid.db"
    }
}

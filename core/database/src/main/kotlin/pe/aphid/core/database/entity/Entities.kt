package pe.aphid.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import pe.aphid.core.model.AcidInfo
import pe.aphid.core.model.AlertKind
import pe.aphid.core.model.AlertSeverity
import pe.aphid.core.model.Element
import pe.aphid.core.model.FertilizerKind
import pe.aphid.core.model.GrowSystemType
import pe.aphid.core.model.GrowthStage
import pe.aphid.core.model.ReadingOrigin
import pe.aphid.core.model.TankGroup
import pe.aphid.core.model.TaskKind
import pe.aphid.core.model.ValueRange

@Entity(tableName = "crop")
data class CropEntity(
    @PrimaryKey val id: String,
    val commonName: String,
    val scientificName: String,
    val family: String,
    val daysPerStage: Map<GrowthStage, Int?>,
)

@Entity(
    tableName = "crop_stage_target",
    primaryKeys = ["cropId", "stage"],
    foreignKeys = [ForeignKey(entity = CropEntity::class, parentColumns = ["id"], childColumns = ["cropId"], onDelete = ForeignKey.CASCADE)],
)
data class CropStageTargetEntity(
    val cropId: String,
    val stage: GrowthStage,
    /** Metas en JSON (`{"Ca":150.0,"Fe":null}`); null = dato pendiente. */
    val targetsJson: String,
    val ec: ValueRange,
    val ph: ValueRange,
    val solutionTempC: ValueRange,
    val dli: ValueRange,
    val source: String,
    val recipeId: String?,
)

@Entity(tableName = "reference_recipe")
data class RecipeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val source: String,
    val description: String,
    val targetsPpm: Map<Element, Double>,
    val verified: Boolean,
    val note: String,
)

@Entity(tableName = "fertilizer")
data class FertilizerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val chemicalFormula: String,
    val purityPct: Double,
    val composition: Map<Element, Double>,
    val solubilityGPerL: Double?,
    val miscible: Boolean,
    val defaultTank: TankGroup,
    val kind: FertilizerKind,
    val acid: AcidInfo?,
    val pricePerKgPen: Double?,
    val supplier: String,
    val editable: Boolean,
    val source: String,
    val verified: Boolean,
)

@Entity(tableName = "water_analysis")
data class WaterAnalysisEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val dateEpochDay: Long,
    val ca: Double,
    val mg: Double,
    val na: Double,
    val k: Double,
    val cl: Double,
    val so4: Double,
    val no3: Double,
    val hco3: Double,
    val ph: Double?,
    val ec: Double?,
)

@Entity(tableName = "grow_system")
data class GrowSystemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: GrowSystemType,
    val volumeL: Double,
    val location: String,
    val photoUri: String?,
)

@Entity(
    tableName = "planting",
    foreignKeys = [ForeignKey(entity = GrowSystemEntity::class, parentColumns = ["id"], childColumns = ["systemId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("systemId")],
)
data class PlantingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val systemId: Long,
    val cropId: String,
    val sowingEpochDay: Long,
    val currentStage: GrowthStage,
    val estimatedHarvestEpochDay: Long?,
)

@Entity(
    tableName = "reading",
    foreignKeys = [ForeignKey(entity = GrowSystemEntity::class, parentColumns = ["id"], childColumns = ["systemId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["systemId", "timestampMillis"])],
)
data class ReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val systemId: Long,
    val timestampMillis: Long,
    val ph: Double?,
    val ec: Double?,
    val solutionTempC: Double?,
    val airTempC: Double?,
    val relativeHumidity: Double?,
    val volumeRemainingL: Double?,
    val origin: ReadingOrigin,
)

/** Resultado de fórmula. El objeto completo se guarda en JSON; algunos campos se indexan. */
@Entity(tableName = "formula_result")
data class FormulaResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdMillis: Long,
    val label: String,
    val requestJson: String,
    val resultJson: String,
    val estimatedEc: Double,
    val costPer1000LPen: Double?,
    val engineVersion: String,
)

@Entity(tableName = "care_task")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val systemId: Long?,
    val kind: TaskKind,
    val title: String,
    val dueMillis: Long,
    val repeatDays: Int?,
    val done: Boolean,
)

@Entity(tableName = "alert", indices = [Index("createdMillis")])
data class AlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val systemId: Long?,
    val kind: AlertKind,
    val severity: AlertSeverity,
    val title: String,
    val message: String,
    val createdMillis: Long,
    val read: Boolean,
)

@Entity(tableName = "photo")
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val systemId: Long?,
    val uri: String,
    val takenMillis: Long,
    val note: String,
)

@Entity(tableName = "diagnosis")
data class DiagnosisEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val systemId: Long?,
    val photoId: Long?,
    val createdMillis: Long,
    val provider: String,
    val outputJson: String,
)

@Entity(tableName = "pest_record")
data class PestRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val systemId: Long?,
    val pestId: String,
    val observedMillis: Long,
    val countPerLeaf: Double?,
    val note: String,
    val nextReviewMillis: Long?,
)

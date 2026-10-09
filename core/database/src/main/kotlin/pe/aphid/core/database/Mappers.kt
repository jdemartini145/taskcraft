package pe.aphid.core.database

import pe.aphid.core.database.entity.AlertEntity
import pe.aphid.core.database.entity.CropEntity
import pe.aphid.core.database.entity.CropStageTargetEntity
import pe.aphid.core.database.entity.FertilizerEntity
import pe.aphid.core.database.entity.GrowSystemEntity
import pe.aphid.core.database.entity.PestRecordEntity
import pe.aphid.core.database.entity.PlantingEntity
import pe.aphid.core.database.entity.ReadingEntity
import pe.aphid.core.database.entity.RecipeEntity
import pe.aphid.core.database.entity.TaskEntity
import pe.aphid.core.database.entity.WaterAnalysisEntity
import pe.aphid.core.model.Alert
import pe.aphid.core.model.CareTask
import pe.aphid.core.model.Crop
import pe.aphid.core.model.CropStageTarget
import pe.aphid.core.model.Fertilizer
import pe.aphid.core.model.GrowSystem
import pe.aphid.core.model.PestRecord
import pe.aphid.core.model.Planting
import pe.aphid.core.model.Reading
import pe.aphid.core.model.ReferenceRecipe
import pe.aphid.core.model.WaterAnalysis

fun CropEntity.toModel() = Crop(id, commonName, scientificName, family, daysPerStage)
fun Crop.toEntity() = CropEntity(id, commonName, scientificName, family, daysPerStage)

fun CropStageTargetEntity.toModel() = CropStageTarget(cropId, stage, decodeTargets(targetsJson), ec, ph, solutionTempC, dli, source, recipeId)
fun CropStageTarget.toEntity() = CropStageTargetEntity(cropId, stage, encodeTargets(targetsPpm), ec, ph, solutionTempC, dli, source, recipeId)

fun RecipeEntity.toModel() = ReferenceRecipe(id, name, source, description, targetsPpm, verified, note)
fun ReferenceRecipe.toEntity() = RecipeEntity(id, name, source, description, targetsPpm, verified, note)

fun FertilizerEntity.toModel() = Fertilizer(
    id = id, name = name, chemicalFormula = chemicalFormula, purityPct = purityPct, composition = composition,
    solubilityGPerL = solubilityGPerL, miscible = miscible, defaultTank = defaultTank, kind = kind, acid = acid,
    pricePerKgPen = pricePerKgPen, supplier = supplier, editable = editable, source = source, verified = verified,
)

fun Fertilizer.toEntity() = FertilizerEntity(
    id, name, chemicalFormula, purityPct, composition, solubilityGPerL, miscible, defaultTank, kind, acid,
    pricePerKgPen, supplier, editable, source, verified,
)

fun WaterAnalysisEntity.toModel() = WaterAnalysis(id, name, dateEpochDay, ca, mg, na, k, cl, so4, no3, hco3, ph, ec)
fun WaterAnalysis.toEntity() = WaterAnalysisEntity(id, name, dateEpochDay, ca, mg, na, k, cl, so4, no3, hco3, ph, ec)

fun GrowSystemEntity.toModel() = GrowSystem(id, name, type, volumeL, location, photoUri)
fun GrowSystem.toEntity() = GrowSystemEntity(id, name, type, volumeL, location, photoUri)

fun PlantingEntity.toModel() = Planting(id, systemId, cropId, sowingEpochDay, currentStage, estimatedHarvestEpochDay)
fun Planting.toEntity() = PlantingEntity(id, systemId, cropId, sowingEpochDay, currentStage, estimatedHarvestEpochDay)

fun ReadingEntity.toModel() = Reading(id, systemId, timestampMillis, ph, ec, solutionTempC, airTempC, relativeHumidity, volumeRemainingL, origin)
fun Reading.toEntity() = ReadingEntity(id, systemId, timestampMillis, ph, ec, solutionTempC, airTempC, relativeHumidity, volumeRemainingL, origin)

fun TaskEntity.toModel() = CareTask(id, systemId, kind, title, dueMillis, repeatDays, done)
fun CareTask.toEntity() = TaskEntity(id, systemId, kind, title, dueMillis, repeatDays, done)

fun AlertEntity.toModel() = Alert(id, systemId, kind, severity, title, message, createdMillis, read)
fun Alert.toEntity() = AlertEntity(id, systemId, kind, severity, title, message, createdMillis, read)

fun PestRecordEntity.toModel() = PestRecord(id, systemId, pestId, observedMillis, countPerLeaf, note, nextReviewMillis)
fun PestRecord.toEntity() = PestRecordEntity(id, systemId, pestId, observedMillis, countPerLeaf, note, nextReviewMillis)

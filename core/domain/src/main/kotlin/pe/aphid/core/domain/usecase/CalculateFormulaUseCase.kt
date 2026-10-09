package pe.aphid.core.domain.usecase

import javax.inject.Inject
import kotlinx.coroutines.flow.first
import pe.aphid.core.domain.repository.CropRepository
import pe.aphid.core.domain.repository.FertilizerRepository
import pe.aphid.core.domain.repository.SettingsRepository
import pe.aphid.core.domain.repository.WaterRepository
import pe.aphid.core.engine.EngineInput
import pe.aphid.core.engine.FertilizerSuggester
import pe.aphid.core.engine.FormulaEngine
import pe.aphid.core.engine.Suggestion
import pe.aphid.core.model.EngineOptions
import pe.aphid.core.model.FormulaRequest
import pe.aphid.core.model.FormulaResult
import pe.aphid.core.model.NutrientProfile
import pe.aphid.core.model.ValueRange
import pe.aphid.core.model.WaterAnalysis

/** Meta resuelta para una solicitud. */
data class ResolvedTarget(
    val profile: NutrientProfile,
    val source: String,
    val ecRange: ValueRange,
    val phRange: ValueRange,
    val pendingNote: String? = null,
)

sealed interface FormulaOutcome {
    data class Success(val result: FormulaResult) : FormulaOutcome
    /** El cultivo/etapa no tiene meta con fuente: se debe elegir una receta de referencia. */
    data class TargetPending(val message: String) : FormulaOutcome
    data class Invalid(val message: String) : FormulaOutcome
}

class ResolveTargetUseCase @Inject constructor(private val crops: CropRepository) {
    suspend operator fun invoke(request: FormulaRequest): ResolvedTarget? {
        val cropId = request.cropId
        val st = request.stage
        val stage = if (cropId != null && st != null) crops.stageTarget(cropId, st) else null
        val ec = stage?.ec ?: ValueRange()
        val ph = stage?.ph ?: ValueRange()
        request.referenceRecipeId?.let { id ->
            val r = crops.recipe(id) ?: return null
            return ResolvedTarget(r.toProfile(), r.source, ec, ph, if (r.verified) null else r.note)
        }
        if (stage == null) return null
        if (stage.hasCompleteMacros) return ResolvedTarget(stage.toProfile(), stage.source, ec, ph)
        stage.recipeId?.let { id ->
            val r = crops.recipe(id) ?: return null
            return ResolvedTarget(r.toProfile(), stage.source + " — " + r.source, ec, ph, if (r.verified) null else r.note)
        }
        return null
    }
}

class CalculateFormulaUseCase @Inject constructor(
    private val resolveTarget: ResolveTargetUseCase,
    private val fertilizers: FertilizerRepository,
    private val water: WaterRepository,
    private val settings: SettingsRepository,
) {
    private val engine = FormulaEngine()

    suspend operator fun invoke(request: FormulaRequest): FormulaOutcome {
        if (request.reservoirVolumeL <= 0) return FormulaOutcome.Invalid("Ingresa un volumen de reservorio mayor que 0.")
        if (request.tankVolumeL <= 0) return FormulaOutcome.Invalid("Ingresa un volumen de tanque mayor que 0.")
        if (request.fertilizerIds.isEmpty()) return FormulaOutcome.Invalid("Marca al menos un insumo.")
        val target = resolveTarget(request)
            ?: return FormulaOutcome.TargetPending(TARGET_PENDING)
        val s = settings.settings.first()
        val all = fertilizers.all().associateBy { it.id }
        val selected = request.fertilizerIds.mapNotNull { all[it] }
        val w = request.waterAnalysisId?.let { water.get(it) } ?: WaterAnalysis.PURE
        val result = engine.calculate(
            EngineInput(
                request = request,
                targets = target.profile,
                targetSource = target.source,
                ecRange = target.ecRange,
                phRange = target.phRange,
                water = w,
                fertilizers = selected,
                options = EngineOptions(hco3LimitMgL = s.hco3LimitMgL, hco3ResidualMgL = s.hco3ResidualMgL),
            ),
        )
        return FormulaOutcome.Success(result)
    }

    companion object {
        const val TARGET_PENDING =
            "La meta nutricional de este cultivo y etapa es un dato pendiente. Elige una receta de referencia (Hoagland o La Molina)."
    }
}

class SuggestFertilizersUseCase @Inject constructor(
    private val resolveTarget: ResolveTargetUseCase,
    private val fertilizers: FertilizerRepository,
    private val water: WaterRepository,
) {
    suspend operator fun invoke(request: FormulaRequest): List<Suggestion> {
        val target = resolveTarget(request) ?: return emptyList()
        val w = request.waterAnalysisId?.let { water.get(it) } ?: WaterAnalysis.PURE
        return FertilizerSuggester.suggest(fertilizers.all(), request.fertilizerIds.toSet(), target.profile, w)
    }
}

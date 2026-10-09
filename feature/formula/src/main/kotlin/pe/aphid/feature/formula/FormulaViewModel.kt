package pe.aphid.feature.formula

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pe.aphid.core.ai.AiOutcome
import pe.aphid.core.ai.AiRouter
import pe.aphid.core.data.export.CsvExporter
import pe.aphid.core.data.export.PdfExporter
import pe.aphid.core.data.export.ShareHelper
import pe.aphid.core.domain.repository.CropRepository
import pe.aphid.core.domain.repository.FertilizerRepository
import pe.aphid.core.domain.repository.FormulaRepository
import pe.aphid.core.domain.repository.SettingsRepository
import pe.aphid.core.domain.repository.SystemRepository
import pe.aphid.core.domain.repository.WaterRepository
import pe.aphid.core.domain.usecase.CalculateFormulaUseCase
import pe.aphid.core.domain.usecase.FormulaOutcome
import pe.aphid.core.domain.usecase.ResolveTargetUseCase
import pe.aphid.core.domain.usecase.SuggestFertilizersUseCase
import pe.aphid.core.engine.Suggestion
import pe.aphid.core.model.ConcentrationFactor
import pe.aphid.core.model.Crop
import pe.aphid.core.model.Fertilizer
import pe.aphid.core.model.FormulaRequest
import pe.aphid.core.model.FormulaResult
import pe.aphid.core.model.GrowSystem
import pe.aphid.core.model.GrowthStage
import pe.aphid.core.model.ReferenceRecipe
import pe.aphid.core.model.SavedFormula
import pe.aphid.core.model.WaterAnalysis
import java.io.File
import javax.inject.Inject

data class TargetInfo(val source: String, val pendingNote: String?, val available: Boolean)

data class FormulaUiState(
    val step: Int = 1,
    val crops: List<Crop> = emptyList(),
    val cropId: String? = null,
    val stage: GrowthStage = GrowthStage.VEGETATIVA,
    val recipes: List<ReferenceRecipe> = emptyList(),
    val recipeId: String? = null,
    val target: TargetInfo? = null,
    val systems: List<GrowSystem> = emptyList(),
    val systemId: Long? = null,
    val volume: String = "100",
    val waters: List<WaterAnalysis> = emptyList(),
    val waterId: Long? = null,
    val fertilizers: List<Fertilizer> = emptyList(),
    val selected: Set<String> = emptySet(),
    val suggestions: List<Suggestion> = emptyList(),
    val factor: ConcentrationFactor = ConcentrationFactor.X100,
    val tankVolume: String = "10",
    val nlText: String = "",
    val nlBusy: Boolean = false,
    val nlMessage: String? = null,
    val calculating: Boolean = false,
    val error: String? = null,
    val result: FormulaResult? = null,
    val explanation: String? = null,
    val explaining: Boolean = false,
    val savedId: Long? = null,
) {
    val volumeValue: Double? get() = volume.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
    val tankValue: Double? get() = tankVolume.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
    val canCalculate: Boolean get() = volumeValue != null && tankValue != null && selected.isNotEmpty() && (cropId != null || recipeId != null)

    fun toRequest() = FormulaRequest(
        cropId = cropId,
        stage = if (cropId != null) stage else null,
        referenceRecipeId = recipeId,
        systemType = systems.firstOrNull { it.id == systemId }?.type,
        reservoirVolumeL = volumeValue ?: 0.0,
        waterAnalysisId = waterId,
        fertilizerIds = selected.toList().sorted(),
        concentrationFactor = factor,
        tankVolumeL = tankValue ?: 0.0,
    )
}

@HiltViewModel
class FormulaViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val cropRepo: CropRepository,
    systemRepo: SystemRepository,
    waterRepo: WaterRepository,
    fertRepo: FertilizerRepository,
    private val formulas: FormulaRepository,
    private val settings: SettingsRepository,
    private val calculate: CalculateFormulaUseCase,
    private val suggest: SuggestFertilizersUseCase,
    private val resolveTarget: ResolveTargetUseCase,
    private val ai: AiRouter,
) : ViewModel() {
    private val _state = MutableStateFlow(FormulaUiState())
    val state: StateFlow<FormulaUiState> = _state.asStateFlow()
    private var suggestJob: Job? = null

    init {
        viewModelScope.launch {
            combine(cropRepo.crops(), systemRepo.systems(), waterRepo.analyses(), fertRepo.fertilizers()) { c, s, w, f ->
                Quad(c, s, w, f)
            }.collect { (c, s, w, f) ->
                _state.update { st ->
                    val firstLoad = st.fertilizers.isEmpty() && f.isNotEmpty()
                    st.copy(
                        crops = c,
                        systems = s,
                        waters = w,
                        fertilizers = f,
                        selected = if (firstLoad) f.filter { it.id in DEFAULT_SELECTION }.map { it.id }.toSet() else st.selected,
                        cropId = st.cropId ?: c.firstOrNull { it.id == "lechuga" }?.id,
                    )
                }
                refreshTarget()
            }
        }
        viewModelScope.launch { _state.update { it.copy(recipes = cropRepo.recipes()) } }
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    fun update(transform: (FormulaUiState) -> FormulaUiState) {
        _state.update(transform)
        refreshTarget()
    }

    fun selectSystem(system: GrowSystem?) = update { it.copy(systemId = system?.id, volume = system?.volumeL?.let { v -> trimNumber(v) } ?: it.volume) }

    fun toggle(id: String) = update { it.copy(selected = if (id in it.selected) it.selected - id else it.selected + id) }

    private fun refreshTarget() {
        suggestJob?.cancel()
        suggestJob = viewModelScope.launch {
            delay(150)
            val req = _state.value.toRequest().copy(reservoirVolumeL = 1.0)
            val target = if (req.cropId == null && req.referenceRecipeId == null) null else resolveTarget(req)
            val info = when {
                target != null -> TargetInfo(target.source, target.pendingNote, true)
                req.cropId != null -> TargetInfo("", null, false)
                else -> null
            }
            val sugg = if (target != null) suggest(req) else emptyList()
            _state.update { it.copy(target = info, suggestions = sugg) }
        }
    }

    fun interpret() {
        val text = _state.value.nlText.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(nlBusy = true, nlMessage = null) }
            val allowCloud = settings.settings.first().let { it.cloudAiConsent && it.proActive }
            when (val out = ai.parseRequest(text, _state.value.crops, allowCloud)) {
                is AiOutcome.Ok -> {
                    val r = out.value
                    val water = r.waterHint?.let { hint ->
                        val key = hint.substringAfterLast(' ').lowercase()
                        _state.value.waters.firstOrNull { it.name.lowercase().contains(key) }
                    }
                    _state.update { st ->
                        st.copy(
                            cropId = r.cropId ?: st.cropId,
                            stage = r.stage ?: st.stage,
                            volume = if (r.reservoirVolumeL > 0) trimNumber(r.reservoirVolumeL) else st.volume,
                            factor = r.concentrationFactor,
                            waterId = water?.id ?: st.waterId,
                            nlBusy = false,
                            nlMessage = buildString {
                                append("✓")
                                if (r.waterHint != null && water == null) append(" ${r.waterHint}: elige o agrega su análisis de agua.")
                            },
                        )
                    }
                    refreshTarget()
                }
                is AiOutcome.Failed -> _state.update { it.copy(nlBusy = false, nlMessage = out.message) }
            }
        }
    }

    fun calculateFormula() {
        val st = _state.value
        if (!st.canCalculate) return
        viewModelScope.launch {
            _state.update { it.copy(calculating = true, error = null) }
            val out = withContext(Dispatchers.Default) { calculate(st.toRequest()) }
            _state.update {
                when (out) {
                    is FormulaOutcome.Success -> it.copy(calculating = false, result = out.result, step = 2, explanation = null, savedId = null)
                    is FormulaOutcome.TargetPending -> it.copy(calculating = false, error = out.message)
                    is FormulaOutcome.Invalid -> it.copy(calculating = false, error = out.message)
                }
            }
        }
    }

    fun backToForm() = _state.update { it.copy(step = 1) }

    fun explain() {
        val r = _state.value.result ?: return
        viewModelScope.launch {
            _state.update { it.copy(explaining = true) }
            val allowCloud = settings.settings.first().let { it.cloudAiConsent && it.proActive }
            val text = when (val out = ai.explain(r, allowCloud)) {
                is AiOutcome.Ok -> out.value
                is AiOutcome.Failed -> out.message
            }
            _state.update { it.copy(explaining = false, explanation = text) }
        }
    }

    fun title(): String {
        val st = _state.value
        val crop = st.crops.firstOrNull { it.id == st.cropId }?.commonName
        val recipe = st.recipes.firstOrNull { it.id == st.recipeId }?.name
        return listOfNotNull(crop, st.stage.label.takeIf { crop != null }, recipe, "${st.volume} L").joinToString(" · ")
    }

    fun save() {
        val r = _state.value.result ?: return
        viewModelScope.launch {
            val id = formulas.save(SavedFormula(createdMillis = System.currentTimeMillis(), label = title(), result = r))
            _state.update { it.copy(savedId = id) }
        }
    }

    suspend fun exportPdf(): File? = _state.value.result?.let { r ->
        withContext(Dispatchers.IO) {
            PdfExporter().formula(r, title(), File(ShareHelper.exportDir(appContext), ShareHelper.safeName("formula_" + title()) + ".pdf"))
        }
    }

    suspend fun exportCsv(): File? = _state.value.result?.let { r ->
        withContext(Dispatchers.IO) {
            File(ShareHelper.exportDir(appContext), ShareHelper.safeName("formula_" + title()) + ".csv").apply { writeText(CsvExporter.formula(r)) }
        }
    }

    companion object {
        val DEFAULT_SELECTION = setOf(
            "nitrato_calcio", "nitrato_potasio", "fosfato_monopotasico", "sulfato_magnesio", "sulfato_potasio",
            "quelato_fe_edta", "acido_borico", "sulfato_manganeso", "sulfato_zinc", "sulfato_cobre", "molibdato_sodio",
        )

        fun trimNumber(v: Double): String = if (v == Math.floor(v)) v.toLong().toString() else v.toString()
    }
}

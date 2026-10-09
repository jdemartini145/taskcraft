package pe.aphid.feature.formula

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pe.aphid.core.ai.AiOutcome
import pe.aphid.core.ai.AiRouter
import pe.aphid.core.data.export.CsvExporter
import pe.aphid.core.data.export.PdfExporter
import pe.aphid.core.data.export.ShareHelper
import pe.aphid.core.domain.repository.FormulaRepository
import pe.aphid.core.domain.repository.SettingsRepository
import pe.aphid.core.domain.repository.WaterRepository
import pe.aphid.core.model.SavedFormula
import pe.aphid.core.model.WaterAnalysis
import java.io.File
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class SavedFormulasViewModel @Inject constructor(private val repo: FormulaRepository) : ViewModel() {
    val saved: StateFlow<List<SavedFormula>> = repo.saved().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun delete(id: Long) = viewModelScope.launch { repo.delete(id) }
}

@HiltViewModel
class SavedFormulaViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val appContext: Context,
    private val repo: FormulaRepository,
    private val settings: SettingsRepository,
    private val ai: AiRouter,
) : ViewModel() {
    private val id = savedStateHandle.toRoute<SavedFormulaRoute>().id
    private val _formula = MutableStateFlow<SavedFormula?>(null)
    val formula: StateFlow<SavedFormula?> = _formula.asStateFlow()
    var explanation by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch { _formula.value = repo.get(id) }
    }

    fun explain() {
        val f = _formula.value ?: return
        viewModelScope.launch {
            val allowCloud = settings.settings.first().let { it.cloudAiConsent && it.proActive }
            explanation = when (val out = ai.explain(f.result, allowCloud)) {
                is AiOutcome.Ok -> out.value
                is AiOutcome.Failed -> out.message
            }
        }
    }

    suspend fun exportPdf(): File? = _formula.value?.let { f ->
        withContext(Dispatchers.IO) { PdfExporter().formula(f.result, f.label, File(ShareHelper.exportDir(appContext), ShareHelper.safeName(f.label) + ".pdf")) }
    }

    suspend fun exportCsv(): File? = _formula.value?.let { f ->
        withContext(Dispatchers.IO) { File(ShareHelper.exportDir(appContext), ShareHelper.safeName(f.label) + ".csv").apply { writeText(CsvExporter.formula(f.result)) } }
    }
}

@HiltViewModel
class WaterViewModel @Inject constructor(private val repo: WaterRepository) : ViewModel() {
    val analyses: StateFlow<List<WaterAnalysis>> = repo.analyses().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun delete(id: Long) = viewModelScope.launch { repo.delete(id) }
}

data class WaterEditState(val id: Long = 0, val name: String = "", val values: Map<String, String> = emptyMap(), val saved: Boolean = false)

@HiltViewModel
class WaterEditViewModel @Inject constructor(savedStateHandle: SavedStateHandle, private val repo: WaterRepository) : ViewModel() {
    private val id = savedStateHandle.toRoute<WaterEditRoute>().id
    private val _state = MutableStateFlow(WaterEditState(id = id))
    val state: StateFlow<WaterEditState> = _state.asStateFlow()

    init {
        if (id != 0L) {
            viewModelScope.launch {
                repo.get(id)?.let { w ->
                    fun s(v: Double?) = v?.toString() ?: ""
                    _state.value = WaterEditState(
                        id, w.name,
                        mapOf(
                            "Ca" to s(w.ca), "Mg" to s(w.mg), "Na" to s(w.na), "K" to s(w.k), "Cl" to s(w.cl),
                            "SO₄" to s(w.so4), "NO₃" to s(w.no3), "HCO₃" to s(w.hco3), "pH" to s(w.ph), "EC" to s(w.ec),
                        ),
                    )
                }
            }
        }
    }

    fun update(transform: (WaterEditState) -> WaterEditState) = _state.update(transform)

    fun save() {
        val st = _state.value
        fun d(k: String) = st.values[k]?.replace(',', '.')?.toDoubleOrNull()
        viewModelScope.launch {
            repo.upsert(
                WaterAnalysis(
                    id = st.id, name = st.name.trim(), dateEpochDay = LocalDate.now().toEpochDay(),
                    ca = d("Ca") ?: 0.0, mg = d("Mg") ?: 0.0, na = d("Na") ?: 0.0, k = d("K") ?: 0.0, cl = d("Cl") ?: 0.0,
                    so4 = d("SO₄") ?: 0.0, no3 = d("NO₃") ?: 0.0, hco3 = d("HCO₃") ?: 0.0, ph = d("pH"), ec = d("EC"),
                ),
            )
            _state.update { it.copy(saved = true) }
        }
    }

    companion object {
        val FIELDS = listOf("Ca", "Mg", "Na", "K", "Cl", "SO₄", "NO₃", "HCO₃", "pH", "EC")
    }
}

package pe.aphid.feature.log

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pe.aphid.core.data.export.CsvExporter
import pe.aphid.core.data.export.PdfExporter
import pe.aphid.core.data.export.ShareHelper
import pe.aphid.core.domain.repository.CropRepository
import pe.aphid.core.domain.repository.FormulaRepository
import pe.aphid.core.domain.repository.ReadingRepository
import pe.aphid.core.domain.repository.SystemRepository
import pe.aphid.core.domain.rules.CompatibilityChecker
import pe.aphid.core.domain.rules.DriftAnalyzer
import pe.aphid.core.domain.rules.DriftExplanation
import pe.aphid.core.model.Element
import pe.aphid.core.model.GrowSystem
import pe.aphid.core.model.Reading
import pe.aphid.core.model.ValueRange

/** Campos del registro rápido (texto para aceptar coma decimal). */
data class QuickEntry(
    val ph: String = "",
    val ec: String = "",
    val solutionTemp: String = "",
    val airTemp: String = "",
    val humidity: String = "",
    val volume: String = "",
) {
    fun parse(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()
    val isEmpty: Boolean get() = listOf(ph, ec, solutionTemp, airTemp, humidity, volume).all { it.isBlank() }
}

data class LogUiState(
    val systems: List<GrowSystem> = emptyList(),
    val systemId: Long? = null,
    val readings: List<Reading> = emptyList(),
    val phRange: ValueRange = ValueRange(),
    val ecRange: ValueRange = ValueRange(),
    val drift: List<DriftExplanation> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LogViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val systems: SystemRepository,
    private val readings: ReadingRepository,
    private val crops: CropRepository,
    private val formulas: FormulaRepository,
) : ViewModel() {
    private val selected = MutableStateFlow<Long?>(null)
    val entry = MutableStateFlow(QuickEntry())
    val savedTick = MutableStateFlow(0)

    val state: StateFlow<LogUiState> = combine(systems.systems(), selected) { list, sel -> list to (sel ?: list.firstOrNull()?.id) }
        .flatMapLatest { (list, id) ->
            if (id == null) {
                flowOf(LogUiState(systems = list))
            } else {
                readings.readings(id).map { rs -> buildState(rs, list, id) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LogUiState())

    private suspend fun buildState(rs: List<Reading>, list: List<GrowSystem>, id: Long): LogUiState {
        val targets = systems.plantingsNow(id).mapNotNull { p ->
            crops.stageTarget(p.cropId, p.currentStage)?.let { p.cropId to it }
        }
        val compat = CompatibilityChecker.check(targets)
        val recent = rs.filter { it.timestampMillis >= System.currentTimeMillis() - DRIFT_WINDOW_MS }
        val latest = formulas.latest()?.result
        val nitrateFraction = latest?.balance?.let { b ->
            val no3 = b.firstOrNull { it.element == Element.N_NO3 }?.deliveredPpm ?: 0.0
            val nh4 = b.firstOrNull { it.element == Element.N_NH4 }?.deliveredPpm ?: 0.0
            if (no3 + nh4 > 0) no3 / (no3 + nh4) else null
        }
        return LogUiState(
            systems = list,
            systemId = id,
            readings = rs,
            phRange = compat.phRange ?: ValueRange(),
            ecRange = compat.ecRange ?: ValueRange(),
            drift = DriftAnalyzer().explain(recent, nitrateFraction),
        )
    }

    fun select(id: Long) {
        selected.value = id
    }

    fun updateEntry(transform: (QuickEntry) -> QuickEntry) = entry.update(transform)

    /** Registro rápido (< 10 s): un toque en Guardar con los campos que tengas. */
    fun save() {
        val id = state.value.systemId ?: return
        val e = entry.value
        if (e.isEmpty) return
        viewModelScope.launch {
            readings.add(
                Reading(
                    systemId = id,
                    timestampMillis = System.currentTimeMillis(),
                    ph = e.parse(e.ph),
                    ec = e.parse(e.ec),
                    solutionTempC = e.parse(e.solutionTemp),
                    airTempC = e.parse(e.airTemp),
                    relativeHumidity = e.parse(e.humidity),
                    volumeRemainingL = e.parse(e.volume),
                ),
            )
            entry.value = QuickEntry()
            savedTick.update { it + 1 }
        }
    }

    fun delete(r: Reading) = viewModelScope.launch { readings.delete(r.id) }

    private fun systemName() = state.value.systems.firstOrNull { it.id == state.value.systemId }?.name ?: "sistema"

    suspend fun exportCsv(): File = withContext(Dispatchers.IO) {
        File(ShareHelper.exportDir(appContext), ShareHelper.safeName("bitacora_" + systemName()) + ".csv")
            .apply { writeText(CsvExporter.readings(systemName(), state.value.readings)) }
    }

    suspend fun exportPdf(): File = withContext(Dispatchers.IO) {
        PdfExporter().readings(systemName(), state.value.readings, File(ShareHelper.exportDir(appContext), ShareHelper.safeName("bitacora_" + systemName()) + ".pdf"))
    }

    companion object {
        const val DRIFT_WINDOW_MS = 72L * 3_600_000
    }
}

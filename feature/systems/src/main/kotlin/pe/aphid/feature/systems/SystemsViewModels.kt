package pe.aphid.feature.systems

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.aphid.core.domain.repository.CropRepository
import pe.aphid.core.domain.repository.SystemRepository
import pe.aphid.core.domain.rules.CompatibilityChecker
import pe.aphid.core.domain.rules.CompatibilityReport
import pe.aphid.core.domain.usecase.StageCalendar
import pe.aphid.core.model.Crop
import pe.aphid.core.model.GrowSystem
import pe.aphid.core.model.GrowSystemType
import pe.aphid.core.model.GrowthStage
import pe.aphid.core.model.Planting

@HiltViewModel
class SystemsViewModel @Inject constructor(repo: SystemRepository) : ViewModel() {
    val systems: StateFlow<List<GrowSystem>?> = repo.systems().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

data class PlantingRow(val planting: Planting, val crop: Crop?, val harvestEpochDay: Long?)

data class SystemDetailUiState(
    val system: GrowSystem? = null,
    val plantings: List<PlantingRow> = emptyList(),
    val crops: List<Crop> = emptyList(),
    val compatibility: CompatibilityReport? = null,
)

@HiltViewModel
class SystemDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val systems: SystemRepository,
    private val crops: CropRepository,
) : ViewModel() {
    private val id = savedStateHandle.toRoute<SystemDetailRoute>().id
    private val system = MutableStateFlow<GrowSystem?>(null)

    val uiState: StateFlow<SystemDetailUiState> = combine(
        system,
        systems.plantings(id),
        crops.crops(),
    ) { s, plantings, cropList -> buildState(s, plantings, cropList) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SystemDetailUiState())

    init {
        viewModelScope.launch { system.value = systems.get(id) }
    }

    private suspend fun buildState(s: GrowSystem?, plantings: List<Planting>, cropList: List<Crop>): SystemDetailUiState {
        val byId = cropList.associateBy { it.id }
        val rows = plantings.map { p ->
            val c = byId[p.cropId]
            PlantingRow(p, c, p.estimatedHarvestEpochDay ?: c?.let { StageCalendar.harvestEpochDay(it, p.sowingEpochDay) })
        }
        val targets = plantings.mapNotNull { p -> crops.stageTarget(p.cropId, p.currentStage)?.let { (byId[p.cropId]?.commonName ?: p.cropId) to it } }
        return SystemDetailUiState(s, rows, cropList, if (targets.size >= 2) CompatibilityChecker.check(targets) else null)
    }

    fun addPlanting(cropId: String, stage: GrowthStage, harvestEpochDay: Long?) = viewModelScope.launch {
        systems.upsertPlanting(
            Planting(systemId = id, cropId = cropId, sowingEpochDay = LocalDate.now().toEpochDay(), currentStage = stage, estimatedHarvestEpochDay = harvestEpochDay),
        )
    }

    fun setStage(p: Planting, stage: GrowthStage) = viewModelScope.launch { systems.upsertPlanting(p.copy(currentStage = stage)) }

    fun deletePlanting(p: Planting) = viewModelScope.launch { systems.deletePlanting(p.id) }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        systems.delete(id)
        onDone()
    }
}

data class SystemEditUiState(
    val id: Long = 0,
    val name: String = "",
    val type: GrowSystemType = GrowSystemType.DWC,
    val volume: String = "",
    val location: String = "",
    val photoUri: String? = null,
    val saved: Boolean = false,
) {
    val volumeValue: Double? get() = volume.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
    val canSave: Boolean get() = name.isNotBlank() && volumeValue != null
}

@HiltViewModel
class SystemEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val systems: SystemRepository,
) : ViewModel() {
    private val id = savedStateHandle.toRoute<SystemEditRoute>().id
    private val _state = MutableStateFlow(SystemEditUiState(id = id))
    val state: StateFlow<SystemEditUiState> = _state.asStateFlow()

    init {
        if (id != 0L) {
            viewModelScope.launch {
                systems.get(id)?.let { s ->
                    _state.value = SystemEditUiState(s.id, s.name, s.type, s.volumeL.toString(), s.location, s.photoUri)
                }
            }
        }
    }

    fun update(transform: (SystemEditUiState) -> SystemEditUiState) = _state.update(transform)

    fun save() {
        val s = _state.value
        val vol = s.volumeValue ?: return
        viewModelScope.launch {
            systems.upsert(GrowSystem(s.id, s.name.trim(), s.type, vol, s.location.trim(), s.photoUri))
            _state.update { it.copy(saved = true) }
        }
    }
}

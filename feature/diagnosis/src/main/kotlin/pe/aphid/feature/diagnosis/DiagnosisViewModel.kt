package pe.aphid.feature.diagnosis

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.aphid.core.ai.AiOutcome
import pe.aphid.core.ai.AiRouter
import pe.aphid.core.ai.DiagnosisHint
import pe.aphid.core.domain.repository.DiagnosisRepository
import pe.aphid.core.domain.repository.SettingsRepository
import pe.aphid.core.domain.repository.TaskRepository
import pe.aphid.core.model.CareTask
import pe.aphid.core.model.Diagnosis
import pe.aphid.core.model.DiagnosisOutput
import pe.aphid.core.model.Photo
import pe.aphid.core.model.TaskKind

data class CaptureUiState(
    val photo: File? = null,
    val notes: String = "",
    val analyzing: Boolean = false,
    val output: DiagnosisOutput? = null,
    val provider: String? = null,
    val error: String? = null,
    val askCloudConsent: Boolean = false,
    val cloudAllowed: Boolean = false,
    val reminderSet: Boolean = false,
)

@HiltViewModel
class DiagnosisViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val ai: AiRouter,
    private val repo: DiagnosisRepository,
    private val tasks: TaskRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    val history: StateFlow<List<Diagnosis>> = repo.diagnoses().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _capture = MutableStateFlow(CaptureUiState())
    val capture: StateFlow<CaptureUiState> = _capture.asStateFlow()

    init {
        viewModelScope.launch {
            val s = settings.settings.first()
            _capture.update { it.copy(cloudAllowed = s.proActive) }
        }
    }

    fun newPhotoFile(): File = File(File(appContext.filesDir, "photos").apply { mkdirs() }, "diag_${System.currentTimeMillis()}.jpg")

    fun onPhoto(file: File) = _capture.update { CaptureUiState(photo = file, cloudAllowed = it.cloudAllowed) }

    fun setNotes(n: String) = _capture.update { it.copy(notes = n) }

    fun reset() = _capture.update { CaptureUiState(cloudAllowed = it.cloudAllowed) }

    /** Analiza en el teléfono (niveles 1 y 2). */
    fun analyzeLocal() = analyze(cloud = false)

    /** Pide confirmación explícita antes de enviar ESTA foto a la nube. */
    fun requestCloud() = _capture.update { it.copy(askCloudConsent = true) }

    fun cloudConsent(granted: Boolean) {
        _capture.update { it.copy(askCloudConsent = false) }
        if (granted) analyze(cloud = true)
    }

    private fun analyze(cloud: Boolean) {
        val photo = _capture.value.photo ?: return
        viewModelScope.launch {
            _capture.update { it.copy(analyzing = true, error = null) }
            when (val out = ai.diagnose(photo, DiagnosisHint(userNotes = _capture.value.notes), cloudConsentForThisPhoto = cloud)) {
                is AiOutcome.Ok -> {
                    val now = System.currentTimeMillis()
                    val photoId = repo.savePhoto(Photo(systemId = null, uri = photo.absolutePath, takenMillis = now, note = _capture.value.notes))
                    repo.save(Diagnosis(systemId = null, photoId = photoId, createdMillis = now, provider = out.providerId, output = out.value))
                    _capture.update { it.copy(analyzing = false, output = out.value, provider = out.providerId) }
                }
                is AiOutcome.Failed -> _capture.update { it.copy(analyzing = false, error = out.message) }
            }
        }
    }

    /** Recordatorio para volver a revisar en `reconsultar_en_dias`. */
    fun scheduleRecheck() {
        val out = _capture.value.output ?: return
        viewModelScope.launch {
            tasks.upsert(
                CareTask(
                    systemId = null, kind = TaskKind.INSPECCION_RAICES, title = "Revisar: ${out.problema}",
                    dueMillis = System.currentTimeMillis() + out.reconsultar_en_dias * DAY_MS,
                ),
            )
            _capture.update { it.copy(reminderSet = true) }
        }
    }

    private companion object { const val DAY_MS = 86_400_000L }
}

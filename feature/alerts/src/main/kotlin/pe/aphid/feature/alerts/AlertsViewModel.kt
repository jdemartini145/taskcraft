package pe.aphid.feature.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pe.aphid.core.domain.repository.AlertRepository
import pe.aphid.core.domain.repository.SystemRepository
import pe.aphid.core.domain.repository.TaskRepository
import pe.aphid.core.domain.usecase.EvaluateAlertsUseCase
import pe.aphid.core.model.Alert
import pe.aphid.core.model.CareTask
import pe.aphid.core.model.GrowSystem
import pe.aphid.core.model.TaskKind
import pe.aphid.core.notifications.Notifier

@HiltViewModel
class AlertsViewModel @Inject constructor(
    private val alerts: AlertRepository,
    private val tasks: TaskRepository,
    systems: SystemRepository,
    private val evaluate: EvaluateAlertsUseCase,
    private val notifier: Notifier,
) : ViewModel() {
    val alertList: StateFlow<List<Alert>> = alerts.alerts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val taskList: StateFlow<List<CareTask>> = tasks.tasks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val systemList: StateFlow<List<GrowSystem>> = systems.systems().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val checking = MutableStateFlow(false)

    fun canNotify() = notifier.canNotify()

    fun checkNow() = viewModelScope.launch {
        checking.value = true
        notifier.notify(evaluate(System.currentTimeMillis()))
        checking.value = false
    }

    fun markRead(a: Alert) = viewModelScope.launch { alerts.markRead(a.id) }

    fun clearAlerts() = viewModelScope.launch { alerts.clear() }

    fun addTask(kind: TaskKind, title: String, systemId: Long?, inDays: Int, repeatDays: Int?) = viewModelScope.launch {
        tasks.upsert(
            CareTask(
                systemId = systemId,
                kind = kind,
                title = title.ifBlank { kind.label },
                dueMillis = System.currentTimeMillis() + inDays * DAY_MS,
                repeatDays = repeatDays?.takeIf { it > 0 },
            ),
        )
    }

    fun complete(t: CareTask) = viewModelScope.launch { tasks.complete(t.id, System.currentTimeMillis()) }

    fun delete(t: CareTask) = viewModelScope.launch { tasks.delete(t.id) }

    private companion object { const val DAY_MS = 86_400_000L }
}

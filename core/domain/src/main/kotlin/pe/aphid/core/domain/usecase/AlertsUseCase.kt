package pe.aphid.core.domain.usecase

import javax.inject.Inject
import kotlinx.coroutines.flow.first
import pe.aphid.core.domain.repository.AlertRepository
import pe.aphid.core.domain.repository.CropRepository
import pe.aphid.core.domain.repository.ReadingRepository
import pe.aphid.core.domain.repository.SettingsRepository
import pe.aphid.core.domain.repository.SystemRepository
import pe.aphid.core.domain.repository.TaskRepository
import pe.aphid.core.domain.rules.AlertEvaluator
import pe.aphid.core.domain.rules.CompatibilityChecker
import pe.aphid.core.domain.rules.SystemAlertContext
import pe.aphid.core.model.Alert
import pe.aphid.core.model.ReadingOrigin

/**
 * Evalúa todos los sistemas, guarda las alertas nuevas (sin repetir la misma alerta del mismo
 * sistema en la ventana de deduplicación) y devuelve las nuevas para notificar.
 */
class EvaluateAlertsUseCase @Inject constructor(
    private val systems: SystemRepository,
    private val readings: ReadingRepository,
    private val crops: CropRepository,
    private val alerts: AlertRepository,
    private val tasks: TaskRepository,
    private val settings: SettingsRepository,
) {
    suspend operator fun invoke(nowMillis: Long): List<Alert> {
        val s = settings.settings.first()
        if (!s.alertsEnabled) return emptyList()
        val evaluator = AlertEvaluator(solutionTempMaxC = s.solutionTempMaxC, heartbeatTimeoutMinutes = s.sensorTimeoutMinutes)
        val produced = mutableListOf<Alert>()
        for (sys in systems.systems().first()) {
            val rs = readings.since(sys.id, nowMillis - WINDOW_MS)
            val targets = systems.plantingsNow(sys.id).mapNotNull { p ->
                crops.stageTarget(p.cropId, p.currentStage)?.let { (crops.crop(p.cropId)?.commonName ?: p.cropId) to it }
            }
            val compat = CompatibilityChecker.check(targets)
            produced += evaluator.evaluate(
                SystemAlertContext(
                    systemId = sys.id,
                    systemName = sys.name,
                    readings = rs,
                    phRange = compat.phRange ?: pe.aphid.core.model.ValueRange(),
                    ecRange = compat.ecRange ?: pe.aphid.core.model.ValueRange(),
                    hasSensor = rs.any { it.origin == ReadingOrigin.SENSOR },
                ),
                nowMillis,
            )
        }
        produced += evaluator.dueTaskAlerts(tasks.dueBefore(nowMillis), nowMillis)
        val recent = alerts.recent(nowMillis - DEDUP_MS)
        val fresh = produced.filter { a -> recent.none { it.kind == a.kind && it.systemId == a.systemId && it.title == a.title } }
        fresh.forEach { alerts.add(it) }
        return fresh
    }

    companion object {
        const val WINDOW_MS = 72L * 3_600_000
        const val DEDUP_MS = 6L * 3_600_000
    }
}

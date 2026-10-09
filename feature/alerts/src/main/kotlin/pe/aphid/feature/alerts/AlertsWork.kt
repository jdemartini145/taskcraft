package pe.aphid.feature.alerts

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import pe.aphid.core.domain.usecase.EvaluateAlertsUseCase
import pe.aphid.core.notifications.Notifier
import timber.log.Timber

/**
 * Evalúa la bitácora offline y notifica alertas nuevas: temperatura de solución, pH/EC fuera
 * de rango o con tendencia a salir en 24 h, recordatorios vencidos y latido de sensores.
 */
@HiltWorker
class AlertsWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val evaluate: EvaluateAlertsUseCase,
    private val notifier: Notifier,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val fresh = evaluate(System.currentTimeMillis())
        notifier.notify(fresh)
        Result.success()
    } catch (e: Exception) {
        Timber.e(e, "Error evaluando alertas")
        Result.retry()
    }
}

object AlertsScheduler {
    const val PERIODIC = "aphid_alerts_6h"
    const val HEARTBEAT = "aphid_sensor_heartbeat"

    /** Evaluación cada 6 h (requisito) y, si hay sensores, cada 15 min (mínimo de WorkManager) para el latido. */
    fun schedule(context: Context, sensorsEnabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<AlertsWorker>(6, TimeUnit.HOURS).build(),
        )
        if (sensorsEnabled) {
            wm.enqueueUniquePeriodicWork(
                HEARTBEAT,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<AlertsWorker>(15, TimeUnit.MINUTES).build(),
            )
        } else {
            wm.cancelUniqueWork(HEARTBEAT)
        }
    }
}

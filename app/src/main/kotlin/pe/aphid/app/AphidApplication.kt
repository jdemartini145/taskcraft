package pe.aphid.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import pe.aphid.app.widget.WidgetUpdater
import pe.aphid.core.data.seed.SeedLoader
import pe.aphid.core.domain.repository.SettingsRepository
import pe.aphid.feature.alerts.AlertsScheduler
import timber.log.Timber

@HiltAndroidApp
class AphidApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var seedLoader: SeedLoader

    @Inject lateinit var widgetUpdater: WidgetUpdater

    @Inject lateinit var settings: SettingsRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        appScope.launch {
            runCatching { seedLoader.ensureSeeded() }.onFailure { Timber.e(it, "Error cargando datos semilla") }
        }
        widgetUpdater.start(appScope)
        // Programa las alertas offline (6 h) y, si hay sensores, el latido (15 min).
        settings.settings
            .map { it.sensorsEnabled }
            .distinctUntilChanged()
            .onEach { AlertsScheduler.schedule(this, it) }
            .launchIn(appScope)
    }
}

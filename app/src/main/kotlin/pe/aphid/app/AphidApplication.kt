package pe.aphid.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import pe.aphid.app.widget.WidgetUpdater
import pe.aphid.core.data.seed.SeedLoader
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class AphidApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var seedLoader: SeedLoader

    @Inject lateinit var widgetUpdater: WidgetUpdater

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
        AppStartup.onCreate(this)
    }
}

package pe.aphid.app

import android.app.Application

/** Punto de extensión para tareas de arranque registradas por módulos (alertas, sensores). */
object AppStartup {
    private val hooks = mutableListOf<(Application) -> Unit>()

    fun register(hook: (Application) -> Unit) {
        hooks += hook
    }

    fun onCreate(app: Application) = hooks.forEach { it(app) }
}

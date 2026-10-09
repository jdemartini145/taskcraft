package pe.aphid.core.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import pe.aphid.core.model.Alert
import pe.aphid.core.model.AlertKind
import pe.aphid.core.model.AlertSeverity

interface Notifier {
    fun canNotify(): Boolean
    fun notify(alerts: List<Alert>)
}

@Singleton
class SystemNotifier @Inject constructor(@ApplicationContext private val context: Context) : Notifier {

    override fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    override fun notify(alerts: List<Alert>) {
        if (alerts.isEmpty() || !canNotify()) return
        ensureChannels()
        val nm = NotificationManagerCompat.from(context)
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_ALERTS, true)
        }
        val pi = launch?.let { PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT) }
        alerts.forEach { a ->
            val channel = if (a.kind == AlertKind.RECORDATORIO) CHANNEL_REMINDERS else CHANNEL_ALERTS
            val n = NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_stat_aphid)
                .setContentTitle(a.title)
                .setContentText(a.message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(a.message))
                .setPriority(if (a.severity == AlertSeverity.CRITICA) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .apply { pi?.let { setContentIntent(it) } }
                .build()
            try {
                nm.notify((a.kind.name + a.systemId + a.title).hashCode(), n)
            } catch (_: SecurityException) {
                // El usuario revocó el permiso entre la verificación y el aviso.
            }
        }
    }

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, context.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_HIGH),
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    companion object {
        const val CHANNEL_ALERTS = "alertas"
        const val CHANNEL_REMINDERS = "recordatorios"
        const val EXTRA_OPEN_ALERTS = "open_alerts"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class NotifierModule {
    @Binds abstract fun notifier(impl: SystemNotifier): Notifier
}

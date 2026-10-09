package pe.aphid.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import pe.aphid.app.MainActivity
import pe.aphid.app.R
import pe.aphid.core.domain.repository.ReadingRepository
import pe.aphid.core.domain.repository.TaskRepository
import pe.aphid.core.model.CareTask
import pe.aphid.core.model.Reading
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun readings(): ReadingRepository
    fun tasks(): TaskRepository
}

/** Widget Glance con la última lectura y la próxima tarea. */
class AphidWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val ep = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        val reading = ep.readings().latest().first()
        val task = ep.tasks().nextTask().first()
        provideContent { GlanceTheme { WidgetContent(context, reading, task) } }
    }
}

@Composable
private fun WidgetContent(context: Context, reading: Reading?, task: CareTask?) {
    val df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    Column(
        GlanceModifier.fillMaxSize().background(GlanceTheme.colors.surface).padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Text(context.getString(R.string.widget_label), style = TextStyle(fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurface))
        if (reading == null) {
            Text("—", style = TextStyle(color = GlanceTheme.colors.onSurface))
        } else {
            val ph = reading.ph?.let { "pH %.1f".format(it) } ?: "pH —"
            val ec = reading.ec?.let { "EC %.2f".format(it) } ?: "EC —"
            val t = reading.solutionTempC?.let { "%.1f °C".format(it) } ?: ""
            Text("$ph · $ec $t", style = TextStyle(color = GlanceTheme.colors.onSurface))
            Text(df.format(Date(reading.timestampMillis)), style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant))
        }
        task?.let {
            Text("⏰ ${it.title} · ${df.format(Date(it.dueMillis))}", style = TextStyle(color = GlanceTheme.colors.primary))
        }
    }
}

class AphidWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AphidWidget()
}

/** Refresca el widget cuando cambian la última lectura o la próxima tarea. */
@Singleton
class WidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val readings: ReadingRepository,
    private val tasks: TaskRepository,
) {
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    fun start(scope: CoroutineScope) {
        combine(readings.latest(), tasks.nextTask()) { r, t -> r to t }
            .debounce(1_000)
            .onEach { runCatching { AphidWidget().updateAll(context) } }
            .launchIn(scope)
    }
}

package pe.aphid.feature.alerts

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.aphid.core.designsystem.R
import pe.aphid.core.designsystem.component.AphidScaffold
import pe.aphid.core.designsystem.component.AphidTopBar
import pe.aphid.core.designsystem.component.BannerKind
import pe.aphid.core.designsystem.component.ChoiceField
import pe.aphid.core.designsystem.component.InfoBanner
import pe.aphid.core.designsystem.component.NumberField
import pe.aphid.core.designsystem.component.formatMillis
import pe.aphid.core.designsystem.component.parseDecimal
import pe.aphid.core.designsystem.icon.AphidIcons
import pe.aphid.core.model.AlertSeverity
import pe.aphid.core.model.GrowSystem
import pe.aphid.core.model.TaskKind

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlertsScreen(onBack: () -> Unit, viewModel: AlertsViewModel = hiltViewModel()) {
    val alerts by viewModel.alertList.collectAsStateWithLifecycle()
    val tasks by viewModel.taskList.collectAsStateWithLifecycle()
    val systems by viewModel.systemList.collectAsStateWithLifecycle()
    val checking by viewModel.checking.collectAsStateWithLifecycle()
    var canNotify by remember { mutableStateOf(viewModel.canNotify()) }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { canNotify = it }

    AphidScaffold(topBar = { AphidTopBar(stringResource(R.string.alerts_title), onBack = onBack) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!canNotify) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        InfoBanner(stringResource(R.string.alerts_permission_why), BannerKind.WARNING)
                        Button(onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }) { Text(stringResource(R.string.onb_allow)) }
                    }
                }
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.checkNow() }, enabled = !checking) { Text(stringResource(R.string.alerts_check_now)) }
                    OutlinedButton(onClick = { viewModel.clearAlerts() }) { Text(stringResource(R.string.alerts_clear)) }
                }
            }
            item { Text(stringResource(R.string.alerts_section), style = MaterialTheme.typography.titleMedium) }
            if (alerts.isEmpty()) item { Text(stringResource(R.string.alerts_none)) }
            items(alerts, key = { "a" + it.id }) { a ->
                ListItem(
                    headlineContent = { Text(a.title, fontWeight = if (a.read) FontWeight.Normal else FontWeight.Bold) },
                    supportingContent = { Text("${a.message}\n${formatMillis(a.createdMillis)}") },
                    leadingContent = {
                        Icon(
                            if (a.severity == AlertSeverity.INFO) AphidIcons.Info else AphidIcons.Warning,
                            contentDescription = a.severity.name,
                        )
                    },
                    modifier = Modifier.clickable { viewModel.markRead(a) },
                )
            }
            item {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.tasks_section), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { showAdd = true }) { Text(stringResource(R.string.tasks_add)) }
                }
            }
            items(tasks, key = { "t" + it.id }) { t ->
                ListItem(
                    headlineContent = { Text(t.title) },
                    supportingContent = {
                        Text(
                            formatMillis(t.dueMillis) + (t.repeatDays?.let { " · " + stringResource(R.string.tasks_every, it) } ?: ""),
                        )
                    },
                    leadingContent = { Checkbox(checked = t.done, onCheckedChange = { if (!t.done) viewModel.complete(t) }) },
                    trailingContent = {
                        IconButton(onClick = { viewModel.delete(t) }) { Icon(AphidIcons.Delete, contentDescription = stringResource(R.string.action_delete)) }
                    },
                )
            }
        }
    }
    if (showAdd) {
        AddTaskDialog(systems, onDismiss = { showAdd = false }) { kind, title, systemId, inDays, repeat ->
            viewModel.addTask(kind, title, systemId, inDays, repeat)
            showAdd = false
        }
    }
}

@Composable
private fun AddTaskDialog(systems: List<GrowSystem>, onDismiss: () -> Unit, onAdd: (TaskKind, String, Long?, Int, Int?) -> Unit) {
    var kind by remember { mutableStateOf(TaskKind.CAMBIO_SOLUCION) }
    var title by remember { mutableStateOf("") }
    var system by remember { mutableStateOf<GrowSystem?>(null) }
    var inDays by remember { mutableStateOf("1") }
    var repeat by remember { mutableStateOf("") }
    val kinds = taskKindLabels()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tasks_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceField(stringResource(R.string.tasks_kind), TaskKind.entries, kind, { kinds.getValue(it) }, { kind = it })
                OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.tasks_title)) }, singleLine = true)
                if (systems.isNotEmpty()) ChoiceField(stringResource(R.string.nav_systems), systems, system, { it.name }, { system = it })
                NumberField(inDays, { inDays = it }, stringResource(R.string.tasks_in_days), suffix = stringResource(R.string.unit_days))
                NumberField(repeat, { repeat = it }, stringResource(R.string.tasks_repeat), suffix = stringResource(R.string.unit_days))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onAdd(kind, title, system?.id, parseDecimal(inDays)?.toInt() ?: 0, parseDecimal(repeat)?.toInt())
            }) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun taskKindLabels(): Map<TaskKind, String> = mapOf(
    TaskKind.CAMBIO_SOLUCION to stringResource(R.string.task_change_solution),
    TaskKind.LIMPIEZA to stringResource(R.string.task_cleaning),
    TaskKind.CALIBRACION to stringResource(R.string.task_calibration),
    TaskKind.INSPECCION_RAICES to stringResource(R.string.task_roots),
    TaskKind.REVISION_PLAGAS to stringResource(R.string.task_pests),
    TaskKind.OTRA to stringResource(R.string.task_other),
)

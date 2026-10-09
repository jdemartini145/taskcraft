package pe.aphid.feature.systems

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import pe.aphid.core.designsystem.R
import pe.aphid.core.designsystem.component.AphidScaffold
import pe.aphid.core.designsystem.component.AphidTopBar
import pe.aphid.core.designsystem.component.BannerKind
import pe.aphid.core.designsystem.component.ChoiceField
import pe.aphid.core.designsystem.component.EmptyState
import pe.aphid.core.designsystem.component.InfoBanner
import pe.aphid.core.designsystem.component.NumberField
import pe.aphid.core.designsystem.component.SectionCard
import pe.aphid.core.designsystem.component.fmt
import pe.aphid.core.designsystem.component.formatEpochDay
import pe.aphid.core.designsystem.component.millisToEpochDay
import pe.aphid.core.designsystem.component.stageLabels
import pe.aphid.core.designsystem.component.systemTypeLabels
import pe.aphid.core.designsystem.icon.AphidIcons
import pe.aphid.core.model.Crop
import pe.aphid.core.model.GrowSystemType
import pe.aphid.core.model.GrowthStage
import pe.aphid.core.model.ValueRange

@Composable
fun SystemsScreen(onOpen: (Long) -> Unit, onAdd: () -> Unit, viewModel: SystemsViewModel = hiltViewModel()) {
    val systems by viewModel.systems.collectAsStateWithLifecycle()
    val types = systemTypeLabels()
    AphidScaffold(
        topBar = { AphidTopBar(stringResource(R.string.nav_systems)) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(AphidIcons.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.systems_new)) },
            )
        },
    ) { padding ->
        val list = systems
        when {
            list == null -> Unit
            list.isEmpty() -> EmptyState(stringResource(R.string.systems_empty), Modifier.padding(padding))
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(list, key = { it.id }) { s ->
                    ListItem(
                        headlineContent = { Text(s.name) },
                        supportingContent = {
                            Text(
                                stringResource(R.string.systems_summary, types.getValue(s.type), s.volumeL.fmt(0), s.location.ifBlank { "—" }),
                            )
                        },
                        leadingContent = { SystemThumb(s.photoUri) },
                        modifier = Modifier.clickable { onOpen(s.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SystemThumb(uri: String?) {
    if (uri != null) {
        AsyncImage(
            model = uri,
            contentDescription = stringResource(R.string.systems_photo),
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
        )
    } else {
        Icon(AphidIcons.Leaf, contentDescription = null, modifier = Modifier.size(32.dp))
    }
}

@Composable
fun SystemEditScreen(onDone: () -> Unit, viewModel: SystemEditViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val types = systemTypeLabels()
    val context = LocalContext.current
    LaunchedEffect(state.saved) { if (state.saved) onDone() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            viewModel.update { it.copy(photoUri = uri.toString()) }
        }
    }
    AphidScaffold(
        topBar = {
            AphidTopBar(
                stringResource(if (state.id == 0L) R.string.systems_new else R.string.systems_edit),
                onBack = onDone,
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = { v -> viewModel.update { it.copy(name = v) } },
                label = { Text(stringResource(R.string.systems_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            ChoiceField(
                label = stringResource(R.string.systems_type),
                options = GrowSystemType.entries,
                selected = state.type,
                optionLabel = { types.getValue(it) },
                onSelect = { t -> viewModel.update { it.copy(type = t) } },
            )
            NumberField(
                value = state.volume,
                onValueChange = { v -> viewModel.update { it.copy(volume = v) } },
                label = stringResource(R.string.systems_volume),
                suffix = stringResource(R.string.unit_liters),
                isError = state.volume.isNotEmpty() && state.volumeValue == null,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.location,
                onValueChange = { v -> viewModel.update { it.copy(location = v) } },
                label = { Text(stringResource(R.string.systems_location)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SystemThumb(state.photoUri)
                OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Text(stringResource(R.string.systems_pick_photo))
                }
            }
            Button(onClick = viewModel::save, enabled = state.canSave, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}

@Composable
fun SystemDetailScreen(onBack: () -> Unit, onEdit: (Long) -> Unit, viewModel: SystemDetailViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val system = state.system
    val types = systemTypeLabels()
    AphidScaffold(
        topBar = {
            AphidTopBar(system?.name ?: "", onBack = onBack) {
                IconButton(onClick = { system?.let { onEdit(it.id) } }) {
                    Icon(AphidIcons.Edit, contentDescription = stringResource(R.string.action_edit))
                }
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(AphidIcons.Delete, contentDescription = stringResource(R.string.action_delete))
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(AphidIcons.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.systems_add_crop)) },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (system != null) {
                item {
                    SectionCard(types.getValue(system.type)) {
                        Text(stringResource(R.string.systems_summary, types.getValue(system.type), system.volumeL.fmt(0), system.location.ifBlank { "—" }))
                        system.photoUri?.let {
                            AsyncImage(
                                model = it,
                                contentDescription = stringResource(R.string.systems_photo),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(12.dp)),
                            )
                        }
                    }
                }
            }
            state.compatibility?.let { c ->
                item {
                    if (c.compatible) {
                        InfoBanner(
                            stringResource(R.string.systems_compatible, rangeText(c.phRange), rangeText(c.ecRange)),
                            BannerKind.INFO,
                        )
                    } else {
                        InfoBanner(c.issues.joinToString("\n"), BannerKind.ERROR)
                    }
                }
                if (c.pending.isNotEmpty()) item { InfoBanner(c.pending.joinToString("\n"), BannerKind.WARNING) }
            }
            item { Text(stringResource(R.string.systems_crops), style = MaterialTheme.typography.titleMedium) }
            if (state.plantings.isEmpty()) item { Text(stringResource(R.string.systems_no_crops)) }
            items(state.plantings, key = { it.planting.id }) { row ->
                PlantingCard(row, onStage = { viewModel.setStage(row.planting, it) }, onDelete = { viewModel.deletePlanting(row.planting) })
            }
            item { CycleTimerCard() }
        }
    }
    if (showAdd) {
        AddPlantingDialog(
            crops = state.crops,
            onDismiss = { showAdd = false },
            onAdd = { crop, stage, harvest ->
                viewModel.addPlanting(crop.id, stage, harvest)
                showAdd = false
            },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.systems_delete_title)) },
            text = { Text(stringResource(R.string.systems_delete_text)) },
            confirmButton = { TextButton(onClick = { viewModel.delete(onBack) }) { Text(stringResource(R.string.action_delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun rangeText(r: ValueRange?): String = when {
    r == null || r.isEmpty -> stringResource(R.string.pending_data)
    r.min != null && r.max != null -> "${r.min!!.fmt(1)}–${r.max!!.fmt(1)}"
    r.min != null -> "≥ ${r.min!!.fmt(1)}"
    else -> "≤ ${r.max!!.fmt(1)}"
}

@Composable
private fun PlantingCard(row: PlantingRow, onStage: (GrowthStage) -> Unit, onDelete: () -> Unit) {
    val stages = stageLabels()
    SectionCard(row.crop?.commonName ?: row.planting.cropId) {
        Text(stringResource(R.string.systems_sown, formatEpochDay(row.planting.sowingEpochDay)))
        Text(
            row.harvestEpochDay?.let { stringResource(R.string.systems_harvest, formatEpochDay(it)) }
                ?: stringResource(R.string.systems_harvest_pending),
        )
        ChoiceField(
            label = stringResource(R.string.systems_stage),
            options = GrowthStage.entries,
            selected = row.planting.currentStage,
            optionLabel = { stages.getValue(it) },
            onSelect = onStage,
        )
        TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPlantingDialog(crops: List<Crop>, onDismiss: () -> Unit, onAdd: (Crop, GrowthStage, Long?) -> Unit) {
    var crop by remember { mutableStateOf<Crop?>(null) }
    var stage by remember { mutableStateOf(GrowthStage.PLANTULA) }
    var harvest by remember { mutableStateOf<Long?>(null) }
    var showDate by remember { mutableStateOf(false) }
    val stages = stageLabels()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.systems_add_crop)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceField(stringResource(R.string.formula_crop), crops, crop, { it.commonName }, { crop = it })
                ChoiceField(stringResource(R.string.systems_stage), GrowthStage.entries, stage, { stages.getValue(it) }, { stage = it })
                OutlinedButton(onClick = { showDate = true }) {
                    Text(harvest?.let { stringResource(R.string.systems_harvest, formatEpochDay(it)) } ?: stringResource(R.string.systems_set_harvest))
                }
                Text(stringResource(R.string.systems_harvest_hint), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { crop?.let { onAdd(it, stage, harvest) } }, enabled = crop != null) { Text(stringResource(R.string.action_add)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
    if (showDate) {
        val dateState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    harvest = dateState.selectedDateMillis?.let(::millisToEpochDay)
                    showDate = false
                }) { Text(stringResource(R.string.action_accept)) }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state = dateState) }
    }
}

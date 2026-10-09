package pe.aphid.feature.formula

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pe.aphid.core.data.export.ShareHelper
import pe.aphid.core.designsystem.R
import pe.aphid.core.designsystem.component.AphidScaffold
import pe.aphid.core.designsystem.component.AphidTopBar
import pe.aphid.core.designsystem.component.EmptyState
import pe.aphid.core.designsystem.component.NumberField
import pe.aphid.core.designsystem.component.fmt
import pe.aphid.core.designsystem.component.formatMillis
import pe.aphid.core.designsystem.icon.AphidIcons

@Composable
fun SavedFormulasScreen(onBack: () -> Unit, onOpen: (Long) -> Unit, viewModel: SavedFormulasViewModel = hiltViewModel()) {
    val list by viewModel.saved.collectAsStateWithLifecycle()
    AphidScaffold(topBar = { AphidTopBar(stringResource(R.string.formula_saved), onBack = onBack) }) { padding ->
        if (list.isEmpty()) {
            EmptyState(stringResource(R.string.formula_saved_empty), Modifier.padding(padding))
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(list, key = { it.id }) { f ->
                    ListItem(
                        headlineContent = { Text(f.label) },
                        supportingContent = { Text("${formatMillis(f.createdMillis)} · EC ${f.result.estimatedEc.fmt(2)}") },
                        trailingContent = {
                            IconButton(onClick = { viewModel.delete(f.id) }) {
                                Icon(AphidIcons.Delete, contentDescription = stringResource(R.string.action_delete))
                            }
                        },
                        modifier = Modifier.clickable { onOpen(f.id) },
                    )
                }
            }
        }
    }
}

@Composable
fun SavedFormulaScreen(onBack: () -> Unit, viewModel: SavedFormulaViewModel = hiltViewModel()) {
    val saved by viewModel.formula.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shareTitle = stringResource(R.string.action_share)
    AphidScaffold(topBar = { AphidTopBar(saved?.label ?: "", onBack = onBack) }) { padding ->
        saved?.let { f ->
            FormulaResultContent(
                result = f.result,
                title = f.label,
                explanation = viewModel.explanation,
                explaining = false,
                saved = true,
                onExplain = viewModel::explain,
                onSave = null,
                onPdf = { scope.launch { viewModel.exportPdf()?.let { ShareHelper.share(context, it, ShareHelper.MIME_PDF, shareTitle) } } },
                onCsv = { scope.launch { viewModel.exportCsv()?.let { ShareHelper.share(context, it, ShareHelper.MIME_CSV, shareTitle) } } },
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
fun WaterListScreen(onBack: () -> Unit, onEdit: (Long) -> Unit, viewModel: WaterViewModel = hiltViewModel()) {
    val list by viewModel.analyses.collectAsStateWithLifecycle()
    AphidScaffold(
        topBar = { AphidTopBar(stringResource(R.string.water_title), onBack = onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onEdit(0L) },
                icon = { Icon(AphidIcons.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.water_new)) },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item { Text(stringResource(R.string.water_hint), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium) }
            items(list, key = { it.id }) { w ->
                ListItem(
                    headlineContent = { Text(w.name) },
                    supportingContent = { Text("Ca ${w.ca.fmt(0)} · Mg ${w.mg.fmt(0)} · HCO₃ ${w.hco3.fmt(0)} mg/L") },
                    trailingContent = {
                        IconButton(onClick = { viewModel.delete(w.id) }) {
                            Icon(AphidIcons.Delete, contentDescription = stringResource(R.string.action_delete))
                        }
                    },
                    modifier = Modifier.clickable { onEdit(w.id) },
                )
            }
        }
    }
}

@Composable
fun WaterEditScreen(onDone: () -> Unit, viewModel: WaterEditViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onDone() }
    AphidScaffold(topBar = { AphidTopBar(stringResource(R.string.water_title), onBack = onDone) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = { v -> viewModel.update { it.copy(name = v) } },
                label = { Text(stringResource(R.string.water_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            WaterEditViewModel.FIELDS.forEach { field ->
                NumberField(
                    value = state.values[field] ?: "",
                    onValueChange = { v -> viewModel.update { it.copy(values = it.values + (field to v)) } },
                    label = field,
                    suffix = if (field == "pH") null else if (field == "EC") "mS/cm" else "mg/L",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Button(onClick = viewModel::save, enabled = state.name.isNotBlank(), modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}

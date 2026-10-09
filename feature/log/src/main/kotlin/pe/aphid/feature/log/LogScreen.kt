package pe.aphid.feature.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pe.aphid.core.data.export.ShareHelper
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
import pe.aphid.core.designsystem.component.formatMillis
import pe.aphid.core.designsystem.icon.AphidIcons
import pe.aphid.core.model.Reading
import pe.aphid.core.model.ValueRange

private enum class Series { PH, EC, TEMP }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LogScreen(onOpenCalculators: () -> Unit, viewModel: LogViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val entry by viewModel.entry.collectAsStateWithLifecycle()
    val tick by viewModel.savedTick.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val savedText = stringResource(R.string.saved_ok)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shareTitle = stringResource(R.string.action_share)
    var series by rememberSaveable { mutableStateOf(Series.PH) }
    LaunchedEffect(tick) { if (tick > 0) snackbar.showSnackbar(savedText) }

    AphidScaffold(
        topBar = {
            AphidTopBar(stringResource(R.string.nav_log)) {
                IconButton(onClick = onOpenCalculators) { Icon(AphidIcons.Calculator, contentDescription = stringResource(R.string.calc_title)) }
            }
        },
        snackbarHostState = snackbar,
    ) { padding ->
        if (state.systems.isEmpty()) {
            EmptyState(stringResource(R.string.log_no_systems), Modifier.padding(padding))
            return@AphidScaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ChoiceField(
                    stringResource(R.string.nav_systems),
                    state.systems,
                    state.systems.firstOrNull { it.id == state.systemId },
                    { it.name },
                    { viewModel.select(it.id) },
                )
            }
            item {
                SectionCard(stringResource(R.string.log_quick_entry)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField(entry.ph, { v -> viewModel.updateEntry { it.copy(ph = v) } }, stringResource(R.string.label_ph), Modifier.weight(1f))
                        NumberField(entry.ec, { v -> viewModel.updateEntry { it.copy(ec = v) } }, stringResource(R.string.label_ec), Modifier.weight(1f), suffix = "mS/cm")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField(
                            entry.solutionTemp, { v -> viewModel.updateEntry { it.copy(solutionTemp = v) } }, stringResource(R.string.log_temp_solution),
                            Modifier.weight(1f), suffix = "°C",
                        )
                        NumberField(
                            entry.airTemp, { v -> viewModel.updateEntry { it.copy(airTemp = v) } }, stringResource(R.string.log_temp_air),
                            Modifier.weight(1f), suffix = "°C",
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField(
                            entry.humidity, { v -> viewModel.updateEntry { it.copy(humidity = v) } }, stringResource(R.string.log_humidity),
                            Modifier.weight(1f), suffix = "%",
                        )
                        NumberField(
                            entry.volume, { v -> viewModel.updateEntry { it.copy(volume = v) } }, stringResource(R.string.log_volume), Modifier.weight(1f),
                            suffix = "L", imeAction = ImeAction.Done,
                        )
                    }
                    Button(onClick = viewModel::save, enabled = !entry.isEmpty, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Text(stringResource(R.string.action_save))
                    }
                }
            }
            if (state.drift.isNotEmpty()) {
                item {
                    SectionCard(stringResource(R.string.log_drift_title)) {
                        state.drift.forEach { d ->
                            Text(d.cause, style = MaterialTheme.typography.bodyMedium)
                            Text("→ ${d.action}", style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
            item {
                SectionCard(stringResource(R.string.log_chart_title)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Series.entries.forEach { s ->
                            FilterChip(
                                selected = series == s,
                                onClick = { series = s },
                                label = {
                                    Text(
                                        when (s) {
                                            Series.PH -> stringResource(R.string.label_ph)
                                            Series.EC -> stringResource(R.string.label_ec)
                                            Series.TEMP -> stringResource(R.string.log_temp_solution)
                                        },
                                    )
                                },
                                modifier = Modifier.heightIn(min = 48.dp),
                            )
                        }
                    }
                    val selector: (Reading) -> Double? = when (series) {
                        Series.PH -> { r -> r.ph }
                        Series.EC -> { r -> r.ec }
                        Series.TEMP -> { r -> r.solutionTempC }
                    }
                    val band = when (series) {
                        Series.PH -> state.phRange
                        Series.EC -> state.ecRange
                        Series.TEMP -> ValueRange()
                    }
                    val points = remember(state.readings, series) { chartPoints(state.readings, selector) }
                    if (points.size < 2) {
                        Text(stringResource(R.string.log_chart_empty))
                    } else {
                        RangeChart(points, band, stringResource(R.string.log_chart_cd))
                        Text(stringResource(R.string.log_chart_axis), style = MaterialTheme.typography.bodySmall)
                    }
                    if (band.isEmpty && series != Series.TEMP) InfoBanner(stringResource(R.string.log_band_pending), BannerKind.INFO)
                }
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { scope.launch { ShareHelper.share(context, viewModel.exportCsv(), ShareHelper.MIME_CSV, shareTitle) } }) {
                        Text(stringResource(R.string.action_export_csv))
                    }
                    OutlinedButton(onClick = { scope.launch { ShareHelper.share(context, viewModel.exportPdf(), ShareHelper.MIME_PDF, shareTitle) } }) {
                        Text(stringResource(R.string.action_export_pdf))
                    }
                }
            }
            items(state.readings.take(MAX_ROWS), key = { it.id }) { r ->
                ListItem(
                    headlineContent = { Text(readingLine(r)) },
                    supportingContent = { Text(formatMillis(r.timestampMillis)) },
                    trailingContent = {
                        IconButton(onClick = { viewModel.delete(r) }) {
                            Icon(AphidIcons.Delete, contentDescription = stringResource(R.string.action_delete))
                        }
                    },
                )
            }
        }
    }
}

private const val MAX_ROWS = 100

private fun chartPoints(readings: List<Reading>, selector: (Reading) -> Double?): List<Pair<Double, Double>> {
    val sorted = readings.sortedBy { it.timestampMillis }
    val t0 = sorted.firstOrNull()?.timestampMillis ?: return emptyList()
    return sorted.mapNotNull { r -> selector(r)?.let { (r.timestampMillis - t0) / 3_600_000.0 to it } }
}

private fun readingLine(r: Reading): String = listOfNotNull(
    r.ph?.let { "pH ${it.fmt(1)}" },
    r.ec?.let { "EC ${it.fmt(2)}" },
    r.solutionTempC?.let { "${it.fmt(1)} °C" },
    r.volumeRemainingL?.let { "${it.fmt(0)} L" },
).joinToString(" · ").ifEmpty { "—" }

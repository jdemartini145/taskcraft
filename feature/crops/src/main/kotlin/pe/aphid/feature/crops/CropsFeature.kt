package pe.aphid.feature.crops

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import pe.aphid.core.designsystem.R
import pe.aphid.core.designsystem.component.AphidScaffold
import pe.aphid.core.designsystem.component.AphidTopBar
import pe.aphid.core.designsystem.component.BannerKind
import pe.aphid.core.designsystem.component.ChoiceField
import pe.aphid.core.designsystem.component.InfoBanner
import pe.aphid.core.designsystem.component.LabeledValue
import pe.aphid.core.designsystem.component.SectionCard
import pe.aphid.core.designsystem.component.fmt
import pe.aphid.core.designsystem.component.stageLabels
import pe.aphid.core.designsystem.icon.AphidIcons
import pe.aphid.core.domain.repository.CropRepository
import pe.aphid.core.domain.rules.CompatibilityChecker
import pe.aphid.core.domain.rules.CompatibilityReport
import pe.aphid.core.model.Crop
import pe.aphid.core.model.CropStageTarget
import pe.aphid.core.model.Element
import pe.aphid.core.model.GrowthStage
import pe.aphid.core.model.TODO_FUENTE
import pe.aphid.core.model.ValueRange

@Serializable data object CropsRoute

@Serializable data class CropDetailRoute(val id: String)

@Serializable data object CompatibilityRoute

fun NavGraphBuilder.cropsGraph(navController: NavController) {
    composable<CropsRoute> {
        CropsScreen(
            onBack = navController::popBackStack,
            onOpen = { navController.navigate(CropDetailRoute(it)) },
            onCompatibility = { navController.navigate(CompatibilityRoute) },
        )
    }
    composable<CropDetailRoute> { CropDetailScreen(onBack = navController::popBackStack) }
    composable<CompatibilityRoute> { CompatibilityScreen(onBack = navController::popBackStack) }
}

@HiltViewModel
class CropsViewModel @Inject constructor(private val repo: CropRepository) : ViewModel() {
    val crops: StateFlow<List<Crop>> = repo.crops().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _report = MutableStateFlow<CompatibilityReport?>(null)
    val report: StateFlow<CompatibilityReport?> = _report.asStateFlow()

    fun check(selection: List<Pair<Crop, GrowthStage>>) = viewModelScope.launch {
        val targets = selection.mapNotNull { (c, s) -> repo.stageTarget(c.id, s)?.let { c.commonName to it } }
        _report.value = CompatibilityChecker.check(targets)
    }
}

@HiltViewModel
class CropDetailViewModel @Inject constructor(savedStateHandle: SavedStateHandle, repo: CropRepository) : ViewModel() {
    private val id = savedStateHandle.toRoute<CropDetailRoute>().id
    private val _crop = MutableStateFlow<Crop?>(null)
    val crop: StateFlow<Crop?> = _crop.asStateFlow()
    val targets: StateFlow<List<CropStageTarget>> = repo.stageTargets(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { _crop.value = repo.crop(id) }
    }
}

@Composable
fun CropsScreen(onBack: () -> Unit, onOpen: (String) -> Unit, onCompatibility: () -> Unit, viewModel: CropsViewModel = hiltViewModel()) {
    val crops by viewModel.crops.collectAsStateWithLifecycle()
    AphidScaffold(
        topBar = {
            AphidTopBar(stringResource(R.string.crops_title), onBack = onBack) {
                IconButton(onClick = onCompatibility) { Icon(AphidIcons.Check, contentDescription = stringResource(R.string.crops_compat)) }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(crops, key = { it.id }) { c ->
                ListItem(
                    headlineContent = { Text(c.commonName) },
                    supportingContent = { Text("${c.scientificName} · ${c.family}") },
                    modifier = Modifier.clickable { onOpen(c.id) },
                )
            }
        }
    }
}

@Composable
fun CropDetailScreen(onBack: () -> Unit, viewModel: CropDetailViewModel = hiltViewModel()) {
    val crop by viewModel.crop.collectAsStateWithLifecycle()
    val targets by viewModel.targets.collectAsStateWithLifecycle()
    val stages = stageLabels()
    val pending = stringResource(R.string.pending_data)
    AphidScaffold(topBar = { AphidTopBar(crop?.commonName ?: "", onBack = onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            crop?.let { Text("${it.scientificName} · ${it.family}", style = MaterialTheme.typography.bodyMedium) }
            InfoBanner(stringResource(R.string.crops_no_invent), BannerKind.INFO)
            GrowthStage.entries.forEach { st ->
                val t = targets.firstOrNull { it.stage == st }
                SectionCard(stages.getValue(st)) {
                    val days = crop?.daysPerStage?.get(st)
                    LabeledValue(stringResource(R.string.crops_days), days?.toString() ?: pending)
                    LabeledValue("EC (mS/cm)", rangeText(t?.ec, pending))
                    LabeledValue("pH", rangeText(t?.ph, pending))
                    LabeledValue(stringResource(R.string.log_temp_solution), rangeText(t?.solutionTempC, pending))
                    val macros = Element.entries.filter { t?.targetsPpm?.get(it) != null }
                    val rid = t?.recipeId
                    if (macros.isEmpty()) {
                        LabeledValue(stringResource(R.string.crops_ppm), if (rid != null) stringResource(R.string.crops_by_recipe, rid) else pending)
                    } else {
                        Text(macros.joinToString(" · ") { e -> "${e.symbol} ${t!!.targetsPpm[e]!!.fmt(if (e.isMacro) 0 else 2)}" })
                    }
                    Text(
                        stringResource(R.string.formula_source, t?.source?.takeIf { it != TODO_FUENTE } ?: pending),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

private fun rangeText(r: ValueRange?, pending: String): String = when {
    r == null || r.isEmpty -> pending
    r.min != null && r.max != null -> "${r.min!!.fmt(1)}–${r.max!!.fmt(1)}"
    r.min != null -> "≥ ${r.min!!.fmt(1)}"
    else -> "≤ ${r.max!!.fmt(1)}"
}

@Composable
fun CompatibilityScreen(onBack: () -> Unit, viewModel: CropsViewModel = hiltViewModel()) {
    val crops by viewModel.crops.collectAsStateWithLifecycle()
    val report by viewModel.report.collectAsStateWithLifecycle()
    val stages = stageLabels()
    val selection = remember { mutableStateListOf<Pair<Crop, GrowthStage>>() }
    var crop by remember { mutableStateOf<Crop?>(null) }
    var stage by remember { mutableStateOf(GrowthStage.VEGETATIVA) }
    val pending = stringResource(R.string.pending_data)
    AphidScaffold(topBar = { AphidTopBar(stringResource(R.string.crops_compat), onBack = onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.crops_compat_hint))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceField(stringResource(R.string.formula_crop), crops, crop, { it.commonName }, { crop = it }, Modifier.weight(1f))
                ChoiceField(stringResource(R.string.systems_stage), GrowthStage.entries, stage, { stages.getValue(it) }, { stage = it }, Modifier.weight(1f))
            }
            Button(onClick = { crop?.let { selection.add(it to stage) } }, enabled = crop != null) { Text(stringResource(R.string.action_add)) }
            selection.forEachIndexed { i, (c, s) ->
                Row(Modifier.fillMaxWidth()) {
                    Text("${c.commonName} · ${stages.getValue(s)}", Modifier.weight(1f))
                    IconButton(onClick = { selection.removeAt(i) }) { Icon(AphidIcons.Close, contentDescription = stringResource(R.string.action_delete)) }
                }
            }
            Button(onClick = { viewModel.check(selection.toList()) }, enabled = selection.size >= 2) { Text(stringResource(R.string.crops_check)) }
            report?.let { r ->
                if (r.compatible) {
                    InfoBanner(stringResource(R.string.systems_compatible, rangeText(r.phRange, pending), rangeText(r.ecRange, pending)), BannerKind.INFO)
                } else {
                    InfoBanner(r.issues.joinToString("\n"), BannerKind.ERROR)
                }
                if (r.pending.isNotEmpty()) InfoBanner(r.pending.joinToString("\n"), BannerKind.WARNING)
            }
        }
    }
}

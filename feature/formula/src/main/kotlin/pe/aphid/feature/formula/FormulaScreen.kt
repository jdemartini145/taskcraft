package pe.aphid.feature.formula

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import pe.aphid.core.designsystem.component.InfoBanner
import pe.aphid.core.designsystem.component.NumberField
import pe.aphid.core.designsystem.component.SectionCard
import pe.aphid.core.designsystem.component.stageLabels
import pe.aphid.core.designsystem.icon.AphidIcons
import pe.aphid.core.model.ConcentrationFactor
import pe.aphid.core.model.GrowthStage
import pe.aphid.core.model.WaterAnalysis

/** Asistente de fórmula en 2 pantallas: datos (1) y resultado (2). */
@Composable
fun FormulaScreen(onAddWater: () -> Unit, onOpenSaved: () -> Unit, viewModel: FormulaViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    BackHandler(enabled = state.step == 2) { viewModel.backToForm() }
    val shareTitle = stringResource(R.string.action_share)
    AphidScaffold(
        topBar = {
            AphidTopBar(
                title = stringResource(if (state.step == 1) R.string.formula_title_step1 else R.string.formula_title_step2),
                onBack = if (state.step == 2) viewModel::backToForm else null,
            ) {
                IconButton(onClick = onOpenSaved) { Icon(AphidIcons.List, contentDescription = stringResource(R.string.formula_saved)) }
            }
        },
    ) { padding ->
        val result = state.result
        if (state.step == 2 && result != null) {
            FormulaResultContent(
                result = result,
                title = viewModel.title(),
                explanation = state.explanation,
                explaining = state.explaining,
                saved = state.savedId != null,
                onExplain = viewModel::explain,
                onSave = viewModel::save,
                onPdf = { scope.launch { viewModel.exportPdf()?.let { ShareHelper.share(context, it, ShareHelper.MIME_PDF, shareTitle) } } },
                onCsv = { scope.launch { viewModel.exportCsv()?.let { ShareHelper.share(context, it, ShareHelper.MIME_CSV, shareTitle) } } },
                modifier = Modifier.padding(padding),
            )
        } else {
            FormulaForm(state, viewModel, onAddWater, Modifier.padding(padding))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormulaForm(state: FormulaUiState, vm: FormulaViewModel, onAddWater: () -> Unit, modifier: Modifier) {
    val stages = stageLabels()
    val pureWater = stringResource(R.string.formula_water_pure)
    Column(
        modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard(stringResource(R.string.formula_nl_title)) {
            OutlinedTextField(
                value = state.nlText,
                onValueChange = { t -> vm.update { it.copy(nlText = t) } },
                label = { Text(stringResource(R.string.formula_nl_hint)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = vm::interpret, enabled = !state.nlBusy && state.nlText.isNotBlank()) {
                    Icon(AphidIcons.Sparkle, contentDescription = null)
                    Text(stringResource(R.string.formula_nl_button), Modifier.padding(start = 8.dp))
                }
                if (state.nlBusy) CircularProgressIndicator()
            }
            state.nlMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }

        SectionCard(stringResource(R.string.formula_section_crop)) {
            ChoiceField(stringResource(R.string.formula_crop), state.crops, state.crops.firstOrNull { it.id == state.cropId }, { it.commonName }, { c ->
                vm.update { it.copy(cropId = c.id) }
            })
            ChoiceField(stringResource(R.string.systems_stage), GrowthStage.entries, state.stage, { stages.getValue(it) }, { s ->
                vm.update { it.copy(stage = s) }
            })
            val target = state.target
            when {
                target == null -> Unit
                target.available && state.recipeId == null -> Text(stringResource(R.string.formula_source, target.source), style = MaterialTheme.typography.bodySmall)
                !target.available && state.recipeId == null -> InfoBanner(stringResource(R.string.formula_target_pending), BannerKind.WARNING)
                else -> Unit
            }
            target?.pendingNote?.let { InfoBanner(it, BannerKind.WARNING) }
            ChoiceField(
                label = stringResource(R.string.formula_recipe),
                options = listOf(null) + state.recipes,
                selected = state.recipes.firstOrNull { it.id == state.recipeId },
                optionLabel = { it?.name ?: "—" },
                onSelect = { r -> vm.update { it.copy(recipeId = r?.id) } },
                placeholder = stringResource(R.string.formula_recipe_none),
            )
        }

        SectionCard(stringResource(R.string.formula_section_system)) {
            if (state.systems.isNotEmpty()) {
                ChoiceField(stringResource(R.string.nav_systems), state.systems, state.systems.firstOrNull { it.id == state.systemId }, { it.name }, { s -> vm.selectSystem(s) })
            }
            NumberField(
                value = state.volume,
                onValueChange = { v -> vm.update { it.copy(volume = v) } },
                label = stringResource(R.string.systems_volume),
                suffix = stringResource(R.string.unit_liters),
                isError = state.volumeValue == null,
                modifier = Modifier.fillMaxWidth(),
            )
            ChoiceField<WaterAnalysis?>(
                label = stringResource(R.string.formula_water),
                options = listOf(null) + state.waters,
                selected = state.waters.firstOrNull { it.id == state.waterId },
                optionLabel = { it?.name ?: pureWater },
                onSelect = { w -> vm.update { it.copy(waterId = w?.id) } },
                placeholder = pureWater,
            )
            TextButton(onClick = onAddWater) { Text(stringResource(R.string.formula_add_water)) }
        }

        SectionCard(stringResource(R.string.formula_section_inputs)) {
            Text(stringResource(R.string.formula_inputs_hint), style = MaterialTheme.typography.bodySmall)
            state.fertilizers.forEach { f ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(value = f.id in state.selected, role = Role.Checkbox, onValueChange = { vm.toggle(f.id) }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = f.id in state.selected, onCheckedChange = null)
                    Column(Modifier.padding(start = 8.dp)) {
                        Text(f.name)
                        Text(f.chemicalFormula, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (state.suggestions.isNotEmpty()) {
                InfoBanner(stringResource(R.string.formula_suggestions), BannerKind.WARNING)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.suggestions.forEach { s ->
                        AssistChip(
                            onClick = { vm.toggle(s.fertilizer.id) },
                            label = { Text("+ ${s.fertilizer.name} (${s.reason})") },
                        )
                    }
                }
            }
        }

        SectionCard(stringResource(R.string.formula_section_stock)) {
            Text(stringResource(R.string.formula_factor))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ConcentrationFactor.entries.forEach { f ->
                    FilterChip(
                        selected = state.factor == f,
                        onClick = { vm.update { it.copy(factor = f) } },
                        label = { Text("${f.value}x") },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
            NumberField(
                value = state.tankVolume,
                onValueChange = { v -> vm.update { it.copy(tankVolume = v) } },
                label = stringResource(R.string.formula_tank_volume),
                suffix = stringResource(R.string.unit_liters),
                isError = state.tankValue == null,
                imeAction = ImeAction.Done,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        state.error?.let { InfoBanner(it, BannerKind.ERROR) }
        Button(
            onClick = vm::calculateFormula,
            enabled = state.canCalculate && !state.calculating,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            if (state.calculating) CircularProgressIndicator() else Text(stringResource(R.string.formula_calculate))
        }
    }
}

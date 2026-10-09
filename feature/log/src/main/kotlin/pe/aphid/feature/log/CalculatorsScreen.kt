package pe.aphid.feature.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import pe.aphid.core.designsystem.R
import pe.aphid.core.designsystem.component.AphidScaffold
import pe.aphid.core.designsystem.component.AphidTopBar
import pe.aphid.core.designsystem.component.ChoiceField
import pe.aphid.core.designsystem.component.LabeledValue
import pe.aphid.core.designsystem.component.NumberField
import pe.aphid.core.designsystem.component.SectionCard
import pe.aphid.core.designsystem.component.fmt
import pe.aphid.core.designsystem.component.parseDecimal
import pe.aphid.core.domain.calc.Calculators
import pe.aphid.core.domain.repository.FertilizerRepository
import pe.aphid.core.domain.repository.TdsScale
import pe.aphid.core.domain.repository.WaterRepository
import pe.aphid.core.model.Fertilizer
import pe.aphid.core.model.WaterAnalysis

@HiltViewModel
class CalculatorsViewModel @Inject constructor(water: WaterRepository, fertilizers: FertilizerRepository) : ViewModel() {
    val waters: StateFlow<List<WaterAnalysis>> = water.analyses().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val acids: StateFlow<List<Fertilizer>> = fertilizers.fertilizers().map { l -> l.filter { it.isAcid } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun CalculatorsScreen(onBack: () -> Unit, viewModel: CalculatorsViewModel = hiltViewModel()) {
    val waters by viewModel.waters.collectAsStateWithLifecycle()
    val acids by viewModel.acids.collectAsStateWithLifecycle()
    AphidScaffold(topBar = { AphidTopBar(stringResource(R.string.calc_title), onBack = onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EcPpmCard()
            PhByTestCard()
            AcidCard(waters, acids)
            ReplenishCard()
        }
    }
}

@Composable
private fun EcPpmCard() {
    var ec by rememberSaveable { mutableStateOf("") }
    var ppm by rememberSaveable { mutableStateOf("") }
    var scale by rememberSaveable { mutableStateOf(TdsScale.PPM_500) }
    SectionCard(stringResource(R.string.calc_ecppm)) {
        NumberField(ec, { ec = it }, stringResource(R.string.label_ec), suffix = "mS/cm")
        parseDecimal(ec)?.let { v ->
            LabeledValue("ppm (500)", Calculators.ecToPpm(v, TdsScale.PPM_500).fmt(0))
            LabeledValue("ppm (700)", Calculators.ecToPpm(v, TdsScale.PPM_700).fmt(0))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(ppm, { ppm = it }, "ppm", Modifier.weight(1f))
            ChoiceField(stringResource(R.string.calc_scale), TdsScale.entries, scale, { "${it.factor}" }, { scale = it }, Modifier.weight(1f))
        }
        parseDecimal(ppm)?.let { v -> LabeledValue(stringResource(R.string.label_ec), "${Calculators.ppmToEc(v, scale).fmt(2)} mS/cm") }
    }
}

@Composable
private fun PhByTestCard() {
    var volume by rememberSaveable { mutableStateOf("") }
    var delta by rememberSaveable { mutableStateOf("") }
    var testMl by rememberSaveable { mutableStateOf("") }
    var testL by rememberSaveable { mutableStateOf("") }
    var testDelta by rememberSaveable { mutableStateOf("") }
    SectionCard(stringResource(R.string.calc_ph)) {
        Text(stringResource(R.string.calc_ph_hint), style = MaterialTheme.typography.bodySmall)
        NumberField(volume, { volume = it }, stringResource(R.string.systems_volume), suffix = "L")
        NumberField(delta, { delta = it }, stringResource(R.string.calc_ph_delta))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(testMl, { testMl = it }, stringResource(R.string.calc_test_ml), Modifier.weight(1f), suffix = "mL")
            NumberField(testL, { testL = it }, stringResource(R.string.calc_test_l), Modifier.weight(1f), suffix = "L")
            NumberField(testDelta, { testDelta = it }, stringResource(R.string.calc_test_delta), Modifier.weight(1f))
        }
        val v = parseDecimal(volume)
        val d = parseDecimal(delta)
        val tm = parseDecimal(testMl)
        val tl = parseDecimal(testL)
        val td = parseDecimal(testDelta)
        if (v != null && d != null && tm != null && tl != null && td != null && tl > 0 && td > 0) {
            val dose = Calculators.phCorrectionByTest(v, d, tm, tl, td)
            LabeledValue(stringResource(R.string.calc_total), "${dose.totalMl.fmt(1)} mL")
            LabeledValue(stringResource(R.string.calc_first_step), "${dose.firstStepMl.fmt(1)} mL")
        }
    }
}

@Composable
private fun AcidCard(waters: List<WaterAnalysis>, acids: List<Fertilizer>) {
    var water by rememberSaveable { mutableStateOf<Long?>(null) }
    var acid by rememberSaveable { mutableStateOf<String?>(null) }
    var volume by rememberSaveable { mutableStateOf("") }
    SectionCard(stringResource(R.string.calc_acid)) {
        ChoiceField(stringResource(R.string.formula_water), waters, waters.firstOrNull { it.id == water }, { it.name }, { water = it.id })
        ChoiceField(stringResource(R.string.calc_acid_product), acids, acids.firstOrNull { it.id == acid }, { it.name }, { acid = it.id })
        NumberField(volume, { volume = it }, stringResource(R.string.systems_volume), suffix = "L")
        val w = waters.firstOrNull { it.id == water }
        val a = acids.firstOrNull { it.id == acid }
        val v = parseDecimal(volume)
        if (w != null && a != null && v != null) {
            val dose = Calculators.acidForAlkalinity(w, a, v)
            LabeledValue(stringResource(R.string.calc_total), "${dose.totalMl.fmt(1)} mL")
            LabeledValue(stringResource(R.string.calc_first_step), "${dose.firstStepMl.fmt(1)} mL")
        }
    }
}

@Composable
private fun ReplenishCard() {
    var full by rememberSaveable { mutableStateOf("") }
    var remaining by rememberSaveable { mutableStateOf("") }
    var current by rememberSaveable { mutableStateOf("") }
    var target by rememberSaveable { mutableStateOf("") }
    SectionCard(stringResource(R.string.calc_replenish)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(full, { full = it }, stringResource(R.string.calc_full), Modifier.weight(1f), suffix = "L")
            NumberField(remaining, { remaining = it }, stringResource(R.string.calc_remaining), Modifier.weight(1f), suffix = "L")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(current, { current = it }, stringResource(R.string.calc_ec_now), Modifier.weight(1f))
            NumberField(target, { target = it }, stringResource(R.string.calc_ec_target), Modifier.weight(1f))
        }
        val f = parseDecimal(full)
        val r = parseDecimal(remaining)
        val c = parseDecimal(current)
        val t = parseDecimal(target)
        if (f != null && r != null && c != null && t != null && f > 0 && r in 0.0..f && t > 0) {
            Text(Calculators.replenish(f, r, c, t, fullStrengthEc = t).advice, style = MaterialTheme.typography.titleSmall)
        }
    }
}

package pe.aphid.feature.shopping

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import pe.aphid.core.designsystem.R
import pe.aphid.core.designsystem.component.AphidScaffold
import pe.aphid.core.designsystem.component.AphidTopBar
import pe.aphid.core.designsystem.component.BannerKind
import pe.aphid.core.designsystem.component.ChoiceField
import pe.aphid.core.designsystem.component.InfoBanner
import pe.aphid.core.designsystem.component.LabeledValue
import pe.aphid.core.designsystem.component.NumberField
import pe.aphid.core.designsystem.component.SectionCard
import pe.aphid.core.designsystem.component.fmt
import pe.aphid.core.designsystem.component.parseDecimal
import pe.aphid.core.designsystem.icon.AphidIcons
import pe.aphid.core.domain.repository.FertilizerRepository
import pe.aphid.core.domain.repository.FormulaRepository
import pe.aphid.core.model.Element
import pe.aphid.core.model.Fertilizer
import pe.aphid.core.model.FertilizerKind
import pe.aphid.core.model.SavedFormula
import pe.aphid.core.model.TankGroup

@Serializable data object ShoppingRoute

@Serializable data object CatalogRoute

@Serializable data class FertilizerEditRoute(val id: String = "")

fun NavGraphBuilder.shoppingGraph(navController: NavController) {
    composable<ShoppingRoute> { ShoppingScreen(onBack = navController::popBackStack, onCatalog = { navController.navigate(CatalogRoute) }) }
    composable<CatalogRoute> { CatalogScreen(onBack = navController::popBackStack, onEdit = { navController.navigate(FertilizerEditRoute(it)) }) }
    composable<FertilizerEditRoute> { FertilizerEditScreen(onDone = navController::popBackStack) }
}

@HiltViewModel
class ShoppingViewModel @Inject constructor(formulas: FormulaRepository, fertilizers: FertilizerRepository) : ViewModel() {
    val saved: StateFlow<List<SavedFormula>> = formulas.saved().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val catalog: StateFlow<List<Fertilizer>> = fertilizers.fertilizers().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun ShoppingScreen(onBack: () -> Unit, onCatalog: () -> Unit, viewModel: ShoppingViewModel = hiltViewModel()) {
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var litersPerHarvest by rememberSaveable { mutableStateOf("") }
    var batches by rememberSaveable { mutableStateOf("1") }
    val formula = saved.firstOrNull { it.id == selectedId } ?: saved.firstOrNull()
    val prices = catalog.associate { it.id to it.pricePerKgPen }
    AphidScaffold(
        topBar = {
            AphidTopBar(stringResource(R.string.shop_title), onBack = onBack) {
                IconButton(onClick = onCatalog) { Icon(AphidIcons.Edit, contentDescription = stringResource(R.string.shop_catalog)) }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (formula == null) {
                InfoBanner(stringResource(R.string.shop_no_formula), BannerKind.INFO)
                Button(onClick = onCatalog) { Text(stringResource(R.string.shop_catalog)) }
                return@Column
            }
            ChoiceField(stringResource(R.string.formula_saved), saved, formula, { it.label }, { selectedId = it.id })
            NumberField(batches, { batches = it }, stringResource(R.string.shop_batches))
            val n = parseDecimal(batches)?.coerceAtLeast(0.0) ?: 1.0
            SectionCard(stringResource(R.string.shop_list)) {
                var total = 0.0
                var missingPrice = false
                formula.result.shoppingList.forEach { item ->
                    val grams = item.grams * n
                    val price = prices[item.fertilizerId]
                    val cost = price?.let { it * grams / 1000.0 }
                    if (cost == null) missingPrice = true else total += cost
                    LabeledValue(item.name, "${grams.fmt(1)} g · " + (cost?.let { "S/ ${it.fmt(2)}" } ?: "—"))
                }
                LabeledValue(stringResource(R.string.shop_total), "S/ ${total.fmt(2)}")
                if (missingPrice) InfoBanner(stringResource(R.string.shop_missing_price), BannerKind.WARNING)
            }
            // Costo por litro con precios actuales del catálogo.
            val perLiter = formula.result.doses.takeIf { d -> d.all { prices[it.fertilizerId] != null } }
                ?.sumOf { it.gramsPerLiterFinal * (prices[it.fertilizerId] ?: 0.0) / 1000.0 }
            SectionCard(stringResource(R.string.shop_costs)) {
                LabeledValue(stringResource(R.string.shop_cost_liter), perLiter?.let { "S/ ${it.fmt(4)}" } ?: stringResource(R.string.formula_cost_pending))
                LabeledValue(stringResource(R.string.formula_cost), perLiter?.let { "S/ ${(it * 1000).fmt(2)}" } ?: "—")
                NumberField(litersPerHarvest, { litersPerHarvest = it }, stringResource(R.string.shop_liters_harvest), suffix = "L")
                val l = parseDecimal(litersPerHarvest)
                if (l != null && perLiter != null) LabeledValue(stringResource(R.string.shop_cost_harvest), "S/ ${(l * perLiter).fmt(2)}")
            }
        }
    }
}

@Composable
fun CatalogScreen(onBack: () -> Unit, onEdit: (String) -> Unit, viewModel: ShoppingViewModel = hiltViewModel()) {
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    AphidScaffold(
        topBar = { AphidTopBar(stringResource(R.string.shop_catalog), onBack = onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { onEdit("") }, icon = { Icon(AphidIcons.Add, null) }, text = { Text(stringResource(R.string.shop_new_input)) })
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(catalog, key = { it.id }) { f ->
                ListItem(
                    headlineContent = { Text(f.name) },
                    supportingContent = {
                        Text(
                            listOf(
                                f.chemicalFormula,
                                f.pricePerKgPen?.let { "S/ ${it.fmt(2)}/kg" } ?: stringResource(R.string.shop_no_price),
                                f.supplier.ifBlank { null },
                                if (!f.verified) stringResource(R.string.shop_verify_label) else null,
                            ).filterNotNull().joinToString(" · "),
                        )
                    },
                    modifier = Modifier.clickable { onEdit(f.id) },
                )
            }
        }
    }
}

data class FertEditState(
    val original: Fertilizer? = null,
    val name: String = "",
    val formula: String = "",
    val purity: String = "100",
    val price: String = "",
    val supplier: String = "",
    val solubility: String = "",
    val tank: TankGroup = TankGroup.B,
    val composition: Map<Element, String> = emptyMap(),
    val saved: Boolean = false,
)

@HiltViewModel
class FertilizerEditViewModel @Inject constructor(savedStateHandle: SavedStateHandle, private val repo: FertilizerRepository) : ViewModel() {
    private val id = savedStateHandle.toRoute<FertilizerEditRoute>().id
    private val _state = MutableStateFlow(FertEditState())
    val state: StateFlow<FertEditState> = _state.asStateFlow()

    init {
        if (id.isNotEmpty()) {
            viewModelScope.launch {
                repo.all().firstOrNull { it.id == id }?.let { f ->
                    _state.value = FertEditState(
                        original = f, name = f.name, formula = f.chemicalFormula, purity = f.purityPct.toString(),
                        price = f.pricePerKgPen?.toString() ?: "", supplier = f.supplier, solubility = f.solubilityGPerL?.toString() ?: "",
                        tank = f.defaultTank, composition = f.composition.mapValues { it.value.toString() },
                    )
                }
            }
        }
    }

    fun update(t: (FertEditState) -> FertEditState) = _state.update(t)

    fun save() {
        val s = _state.value
        fun d(v: String) = v.trim().replace(',', '.').toDoubleOrNull()
        val composition = s.composition.mapNotNull { (e, v) -> d(v)?.takeIf { it > 0 }?.let { e to it } }.toMap()
        val base = s.original ?: Fertilizer(
            id = "custom_" + UUID.randomUUID().toString().take(8),
            name = "", chemicalFormula = "", composition = emptyMap(), defaultTank = s.tank, kind = FertilizerKind.SAL,
            source = "Ingresado por el usuario", verified = false,
        )
        viewModelScope.launch {
            repo.upsert(
                base.copy(
                    name = s.name.trim(), chemicalFormula = s.formula.trim(), purityPct = d(s.purity) ?: 100.0,
                    pricePerKgPen = d(s.price), supplier = s.supplier.trim(), solubilityGPerL = d(s.solubility),
                    defaultTank = s.tank, composition = composition,
                ),
            )
            _state.update { it.copy(saved = true) }
        }
    }
}

@Composable
fun FertilizerEditScreen(onDone: () -> Unit, viewModel: FertilizerEditViewModel = hiltViewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(s.saved) { if (s.saved) onDone() }
    AphidScaffold(topBar = { AphidTopBar(s.name.ifBlank { stringResource(R.string.shop_new_input) }, onBack = onDone) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(s.name, { v -> viewModel.update { it.copy(name = v) } }, label = { Text(stringResource(R.string.systems_name)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                s.formula,
                { v -> viewModel.update { it.copy(formula = v) } },
                label = { Text(stringResource(R.string.shop_formula)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(s.price, { v -> viewModel.update { it.copy(price = v) } }, stringResource(R.string.shop_price), Modifier.weight(1f), suffix = "S/ kg")
                NumberField(s.purity, { v -> viewModel.update { it.copy(purity = v) } }, stringResource(R.string.shop_purity), Modifier.weight(1f), suffix = "%")
            }
            OutlinedTextField(
                s.supplier,
                { v -> viewModel.update { it.copy(supplier = v) } },
                label = { Text(stringResource(R.string.shop_supplier)) },
                modifier = Modifier.fillMaxWidth(),
            )
            NumberField(s.solubility, { v -> viewModel.update { it.copy(solubility = v) } }, stringResource(R.string.shop_solubility), suffix = "g/L")
            ChoiceField(stringResource(R.string.shop_tank), listOf(TankGroup.A, TankGroup.B), s.tank, { it.label }, { t -> viewModel.update { it.copy(tank = t) } })
            Text(stringResource(R.string.shop_composition), style = MaterialTheme.typography.titleSmall)
            Element.entries.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { e ->
                        NumberField(
                            s.composition[e] ?: "",
                            { v -> viewModel.update { it.copy(composition = it.composition + (e to v)) } },
                            e.symbol,
                            Modifier.weight(1f),
                            suffix = "%",
                        )
                    }
                }
            }
            s.original?.source?.takeIf { it.isNotBlank() }?.let { Text(stringResource(R.string.formula_source, it), style = MaterialTheme.typography.bodySmall) }
            Button(onClick = viewModel::save, enabled = s.name.isNotBlank(), modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.action_save)) }
        }
    }
}

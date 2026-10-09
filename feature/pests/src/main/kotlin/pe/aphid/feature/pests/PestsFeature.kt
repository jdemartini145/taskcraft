package pe.aphid.feature.pests

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import pe.aphid.core.designsystem.R
import pe.aphid.core.designsystem.component.AphidScaffold
import pe.aphid.core.designsystem.component.AphidTopBar
import pe.aphid.core.designsystem.component.NumberField
import pe.aphid.core.designsystem.component.SectionCard
import pe.aphid.core.designsystem.component.formatMillis
import pe.aphid.core.designsystem.component.parseDecimal
import pe.aphid.core.designsystem.icon.AphidIcons
import pe.aphid.core.domain.pests.PestCatalog
import pe.aphid.core.domain.pests.PestSheet
import pe.aphid.core.domain.repository.PestRepository
import pe.aphid.core.domain.repository.TaskRepository
import pe.aphid.core.model.CareTask
import pe.aphid.core.model.PestRecord
import pe.aphid.core.model.TODO_FUENTE
import pe.aphid.core.model.TaskKind

@Serializable data object PestsRoute

@Serializable data class PestDetailRoute(val id: String)

fun NavGraphBuilder.pestsGraph(navController: NavController) {
    composable<PestsRoute> { PestsScreen(onBack = navController::popBackStack, onOpen = { navController.navigate(PestDetailRoute(it)) }) }
    composable<PestDetailRoute> { PestDetailScreen(onBack = navController::popBackStack) }
}

@HiltViewModel
class PestsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val records: PestRepository,
    private val tasks: TaskRepository,
) : ViewModel() {
    private val pestId: String? = runCatching { savedStateHandle.toRoute<PestDetailRoute>().id }.getOrNull()
    val sheet: PestSheet? = pestId?.let(PestCatalog::byId)
    val history: StateFlow<List<PestRecord>> = records.records()
        .map { l -> if (pestId == null) l else l.filter { it.pestId == pestId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val saved = MutableStateFlow(false)

    /** Registra la observación y programa el recordatorio de revisión de la ficha. */
    fun record(count: Double?, note: String) {
        val s = sheet ?: return
        viewModelScope.launch {
            val next = System.currentTimeMillis() + s.reviewEveryDays * DAY_MS
            records.add(PestRecord(systemId = null, pestId = s.id, observedMillis = System.currentTimeMillis(), countPerLeaf = count, note = note, nextReviewMillis = next))
            tasks.upsert(CareTask(systemId = null, kind = TaskKind.REVISION_PLAGAS, title = "Revisar ${s.name}", dueMillis = next, repeatDays = s.reviewEveryDays))
            saved.value = true
        }
    }

    private companion object { const val DAY_MS = 86_400_000L }
}

@Composable
fun PestsScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    AphidScaffold(topBar = { AphidTopBar(stringResource(R.string.pests_title), onBack = onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item { Text(stringResource(R.string.pests_intro), Modifier.padding(16.dp)) }
            items(PestCatalog.sheets, key = { it.id }) { s ->
                ListItem(
                    headlineContent = { Text(s.name) },
                    supportingContent = { Text(s.scientificName) },
                    leadingContent = { Icon(AphidIcons.Bug, contentDescription = null) },
                    modifier = Modifier.clickable { onOpen(s.id) },
                )
            }
        }
    }
}

@Composable
fun PestDetailScreen(onBack: () -> Unit, viewModel: PestsViewModel = hiltViewModel()) {
    val sheet = viewModel.sheet
    val history by viewModel.history.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    var count by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    AphidScaffold(topBar = { AphidTopBar(sheet?.name ?: "", onBack = onBack) }) { padding ->
        if (sheet == null) return@AphidScaffold
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(sheet.scientificName, style = MaterialTheme.typography.bodyMedium)
            SectionCard(stringResource(R.string.pests_identification)) { Text(sheet.identification) }
            SectionCard(stringResource(R.string.pests_threshold)) {
                Text(if (sheet.threshold == TODO_FUENTE) stringResource(R.string.pending_data) else sheet.threshold)
            }
            SectionCard(stringResource(R.string.pests_physical)) { sheet.physicalControl.forEach { Text("• $it") } }
            SectionCard(stringResource(R.string.pests_biological)) { sheet.biologicalControl.forEach { Text("• $it") } }
            SectionCard(stringResource(R.string.pests_record)) {
                NumberField(count, { count = it }, stringResource(R.string.pests_count))
                OutlinedTextField(note, { note = it }, label = { Text(stringResource(R.string.tasks_title)) })
                Button(onClick = { viewModel.record(parseDecimal(count), note) }, enabled = !saved) {
                    Text(stringResource(if (saved) R.string.saved_ok else R.string.pests_save_and_remind, sheet.reviewEveryDays))
                }
            }
            if (history.isNotEmpty()) {
                SectionCard(stringResource(R.string.pests_history)) {
                    history.forEach { r -> Text("${formatMillis(r.observedMillis)} · ${r.countPerLeaf ?: "—"} · ${r.note}") }
                }
            }
            OutlinedButton(onClick = onBack) { Text(stringResource(R.string.action_close)) }
        }
    }
}

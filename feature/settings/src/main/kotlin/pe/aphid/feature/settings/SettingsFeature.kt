package pe.aphid.feature.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import pe.aphid.core.designsystem.R
import pe.aphid.core.designsystem.component.AphidScaffold
import pe.aphid.core.designsystem.component.AphidTopBar
import pe.aphid.core.designsystem.component.BannerKind
import pe.aphid.core.designsystem.component.ChoiceField
import pe.aphid.core.designsystem.component.InfoBanner
import pe.aphid.core.designsystem.component.NumberField
import pe.aphid.core.designsystem.component.SectionCard
import pe.aphid.core.designsystem.component.parseDecimal
import pe.aphid.core.domain.repository.BackupRepository
import pe.aphid.core.domain.repository.SettingsRepository
import pe.aphid.core.domain.repository.TdsScale
import pe.aphid.core.domain.repository.UserSettings

@Serializable data object SettingsRoute

@Serializable data object PrivacyRoute

@Serializable data object ProRoute

fun NavGraphBuilder.settingsGraph(navController: NavController) {
    composable<SettingsRoute> {
        SettingsScreen(
            onBack = navController::popBackStack,
            onPrivacy = { navController.navigate(PrivacyRoute) },
            onPro = { navController.navigate(ProRoute) },
        )
    }
    composable<PrivacyRoute> { PrivacyScreen(onBack = navController::popBackStack) }
    composable<ProRoute> { ProScreen(onBack = navController::popBackStack) }
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val settings: SettingsRepository,
    private val backup: BackupRepository,
    val billing: BillingManager,
) : ViewModel() {
    val state: StateFlow<UserSettings?> = settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val message = MutableStateFlow<String?>(null)

    fun update(t: (UserSettings) -> UserSettings) = viewModelScope.launch { settings.update(t) }

    fun exportTo(uri: Uri) = viewModelScope.launch {
        runCatching {
            val json = backup.exportJson()
            withContext(Dispatchers.IO) { appContext.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } }
        }.onSuccess { message.value = "OK" }.onFailure { message.value = it.message }
    }

    fun importFrom(uri: Uri) = viewModelScope.launch {
        runCatching {
            val json = withContext(Dispatchers.IO) { appContext.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } } ?: error("Archivo vacío")
            backup.importJson(json)
        }.onSuccess { message.value = "OK" }.onFailure { message.value = it.message }
    }

    fun deleteAll(onDone: () -> Unit) = viewModelScope.launch {
        backup.deleteAll()
        withContext(Dispatchers.IO) {
            appContext.filesDir.resolve("photos").deleteRecursively()
            appContext.cacheDir.resolve("exports").deleteRecursively()
        }
        onDone()
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, supporting: String? = null) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label)
            supporting?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onPrivacy: () -> Unit, onPro: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(viewModel::exportTo) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::importFrom) }
    AphidScaffold(topBar = { AphidTopBar(stringResource(R.string.settings_title), onBack = onBack) }) { padding ->
        val st = s ?: return@AphidScaffold
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(stringResource(R.string.settings_alerts)) {
                ToggleRow(stringResource(R.string.settings_alerts_enabled), st.alertsEnabled, { v -> viewModel.update { it.copy(alertsEnabled = v) } })
                var temp by rememberSaveable(st.solutionTempMaxC) { mutableStateOf(st.solutionTempMaxC.toString()) }
                NumberField(temp, { v ->
                    temp = v
                    parseDecimal(v)?.let { d -> viewModel.update { it.copy(solutionTempMaxC = d) } }
                }, stringResource(R.string.settings_temp_max), suffix = "°C")
                ToggleRow(stringResource(R.string.settings_sensors), st.sensorsEnabled, { v -> viewModel.update { it.copy(sensorsEnabled = v) } })
            }
            SectionCard(stringResource(R.string.settings_units)) {
                ChoiceField(stringResource(R.string.calc_scale), TdsScale.entries, st.tdsScale, { "ppm ${it.factor}" }, { t -> viewModel.update { it.copy(tdsScale = t) } })
                var limit by rememberSaveable(st.hco3LimitMgL) { mutableStateOf(st.hco3LimitMgL.toString()) }
                NumberField(limit, { v ->
                    limit = v
                    parseDecimal(v)?.let { d -> viewModel.update { it.copy(hco3LimitMgL = d) } }
                }, stringResource(R.string.settings_hco3_limit), suffix = "mg/L")
                var residual by rememberSaveable(st.hco3ResidualMgL) { mutableStateOf(st.hco3ResidualMgL.toString()) }
                NumberField(residual, { v ->
                    residual = v
                    parseDecimal(v)?.let { d -> viewModel.update { it.copy(hco3ResidualMgL = d) } }
                }, stringResource(R.string.settings_hco3_residual), suffix = "mg/L")
            }
            SectionCard(stringResource(R.string.settings_privacy)) {
                ToggleRow(
                    stringResource(R.string.settings_cloud_consent), st.cloudAiConsent, { v -> viewModel.update { it.copy(cloudAiConsent = v) } },
                    stringResource(R.string.settings_cloud_consent_hint),
                )
                ToggleRow(
                    stringResource(R.string.settings_telemetry), st.telemetryEnabled, { v -> viewModel.update { it.copy(telemetryEnabled = v) } },
                    stringResource(R.string.settings_telemetry_hint),
                )
                ToggleRow(
                    stringResource(R.string.settings_sync), st.cloudSyncEnabled && st.proActive, { v -> viewModel.update { it.copy(cloudSyncEnabled = v) } },
                    stringResource(R.string.settings_sync_hint),
                )
                TextButton(onClick = onPrivacy) { Text(stringResource(R.string.privacy_title)) }
            }
            SectionCard(stringResource(R.string.settings_backup)) {
                Text(stringResource(R.string.settings_backup_hint), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { exportLauncher.launch("aphid_respaldo.json") }) { Text(stringResource(R.string.settings_export)) }
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json")) }) { Text(stringResource(R.string.settings_import)) }
                }
                message?.let { Text(it) }
            }
            SectionCard(stringResource(R.string.pro_title)) {
                Text(stringResource(if (st.proActive) R.string.pro_active else R.string.pro_summary))
                TextButton(onClick = onPro) { Text(stringResource(R.string.pro_more)) }
            }
            Button(
                onClick = { confirmDelete = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.settings_delete_all)) }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.settings_delete_all)) },
            text = { Text(stringResource(R.string.settings_delete_text)) },
            confirmButton = { TextButton(onClick = { viewModel.deleteAll { confirmDelete = false } }) { Text(stringResource(R.string.action_delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    AphidScaffold(topBar = { AphidTopBar(stringResource(R.string.privacy_title), onBack = onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            listOf(
                R.string.privacy_p1, R.string.privacy_p2, R.string.privacy_p3, R.string.privacy_p4, R.string.privacy_p5, R.string.privacy_p6,
            ).forEach { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun ProScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val pro by viewModel.billing.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { viewModel.billing.connect() }
    AphidScaffold(topBar = { AphidTopBar(stringResource(R.string.pro_title), onBack = onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            InfoBanner(stringResource(R.string.pro_free_core), BannerKind.INFO)
            Text(stringResource(R.string.pro_includes))
            if (pro.active) {
                Text(stringResource(R.string.pro_active), style = MaterialTheme.typography.titleMedium)
            } else {
                Button(onClick = { scope.launch { context.findActivity()?.let(viewModel.billing::purchase) } }) {
                    Text(pro.price?.let { stringResource(R.string.pro_buy_price, it) } ?: stringResource(R.string.pro_buy))
                }
            }
            pro.message?.let { InfoBanner(it, BannerKind.WARNING) }
        }
    }
}

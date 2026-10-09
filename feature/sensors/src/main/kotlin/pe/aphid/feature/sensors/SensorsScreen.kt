package pe.aphid.feature.sensors

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
import pe.aphid.core.designsystem.component.NumberField
import pe.aphid.core.designsystem.component.SectionCard
import pe.aphid.core.designsystem.component.fmt
import pe.aphid.core.designsystem.component.formatMillis
import pe.aphid.core.designsystem.component.parseDecimal
import pe.aphid.core.domain.repository.SettingsRepository
import pe.aphid.core.domain.repository.SystemRepository
import pe.aphid.core.domain.sensors.AphidGattProfile
import pe.aphid.core.model.GrowSystem

@Serializable data object SensorsRoute

fun NavGraphBuilder.sensorsGraph(navController: NavController) {
    composable<SensorsRoute> { SensorsScreen(onBack = navController::popBackStack) }
}

@HiltViewModel
class SensorsViewModel @Inject constructor(
    val connections: SensorConnections,
    systems: SystemRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    val systemList: StateFlow<List<GrowSystem>> = systems.systems().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun enableHeartbeatWatch() = viewModelScope.launch { settings.update { it.copy(sensorsEnabled = true) } }
}

@Composable
fun SensorsScreen(onBack: () -> Unit, viewModel: SensorsViewModel = hiltViewModel()) {
    val state by viewModel.connections.state.collectAsStateWithLifecycle()
    val systems by viewModel.systemList.collectAsStateWithLifecycle()
    var systemId by rememberSaveable { mutableStateOf(viewModel.connections.targetSystemId) }
    var host by rememberSaveable { mutableStateOf("192.168.1.10") }
    var port by rememberSaveable { mutableStateOf("1883") }
    var node by rememberSaveable { mutableStateOf("nodo1") }
    val blePermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.all { it }) viewModel.connections.startScan()
    }
    AphidScaffold(topBar = { AphidTopBar(stringResource(R.string.sensors_title), onBack = onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            InfoBanner(stringResource(R.string.sensors_intro), BannerKind.INFO)
            ChoiceField(stringResource(R.string.sensors_target), systems, systems.firstOrNull { it.id == systemId }, { it.name }, {
                systemId = it.id
                viewModel.connections.targetSystemId = it.id
                viewModel.enableHeartbeatWatch()
            })
            SectionCard(stringResource(R.string.sensors_ble)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { permLauncher.launch(blePermissions) }, enabled = !state.scanning) { Text(stringResource(R.string.sensors_scan)) }
                    if (state.bleConnected != null) OutlinedButton(onClick = viewModel.connections::disconnect) { Text(stringResource(R.string.sensors_disconnect)) }
                }
                state.devices.forEach { (name, address) ->
                    TextButton(onClick = { viewModel.connections.connect(address) }) { Text("$name ($address)") }
                }
                state.bleConnected?.let { Text(stringResource(R.string.sensors_connected, it)) }
            }
            SectionCard(stringResource(R.string.sensors_mqtt)) {
                OutlinedTextField(host, { host = it }, label = { Text(stringResource(R.string.sensors_host)) }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(port, { port = it }, stringResource(R.string.sensors_port), Modifier.weight(1f))
                    OutlinedTextField(node, { node = it }, label = { Text(stringResource(R.string.sensors_node)) }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.connections.connectMqtt(host.trim(), parseDecimal(port)?.toInt() ?: 1883, node.trim()) }) {
                        Text(stringResource(R.string.sensors_connect))
                    }
                    if (state.mqttConnected) OutlinedButton(onClick = viewModel.connections::disconnectMqtt) { Text(stringResource(R.string.sensors_disconnect)) }
                }
                Text(stringResource(R.string.sensors_mqtt_hint), style = MaterialTheme.typography.bodySmall)
            }
            state.message?.let { InfoBanner(it, BannerKind.ERROR) }
            state.last?.let { m ->
                SectionCard(stringResource(R.string.sensors_last)) {
                    LabeledValue("pH", m.ph?.fmt(2) ?: "—")
                    LabeledValue("EC", m.ecMsCm?.let { "${it.fmt(2)} mS/cm" } ?: "—")
                    LabeledValue(stringResource(R.string.log_temp_solution), m.solutionTempC?.let { "${it.fmt(1)} °C" } ?: "—")
                    LabeledValue(stringResource(R.string.sensors_level), m.levelPct?.let { "${it.fmt(0)} %" } ?: "—")
                    state.lastMillis?.let { Text(formatMillis(it), style = MaterialTheme.typography.bodySmall) }
                }
            }
            SectionCard(stringResource(R.string.sensors_profile)) {
                SelectionContainer {
                    Text(
                        """
                        Servicio:  ${AphidGattProfile.SERVICE}
                        Medición:  ${AphidGattProfile.MEASUREMENT} (notify, 12 B LE)
                          int16 pH×100 · uint16 EC µS/cm · int16 T×100 °C
                          uint16 nivel %×100 · uint32 uptime s
                        Latido:    ${AphidGattProfile.HEARTBEAT} (notify, uint32, cada 5 min)
                        Config:    ${AphidGattProfile.CONFIG} (write, uint16 intervalo s)
                        Sin dato:  0x7FFF (int16) / 0xFFFF (uint16)
                        """.trimIndent(),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(stringResource(R.string.sensors_heartbeat_hint), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

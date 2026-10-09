package pe.aphid.feature.systems

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import pe.aphid.core.designsystem.R
import pe.aphid.core.designsystem.component.NumberField
import pe.aphid.core.designsystem.component.SectionCard
import pe.aphid.core.designsystem.component.parseDecimal

/**
 * Temporizador de ciclo (encendido/apagado de bomba o flujo y reflujo), paridad con iOS.
 * Funciona mientras la pantalla está abierta; los recordatorios largos usan Alertas.
 */
@Composable
fun CycleTimerCard() {
    var onMin by rememberSaveable { mutableStateOf("15") }
    var offMin by rememberSaveable { mutableStateOf("45") }
    var running by rememberSaveable { mutableStateOf(false) }
    var pumpOn by rememberSaveable { mutableStateOf(true) }
    var remaining by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(running, pumpOn) {
        if (!running) return@LaunchedEffect
        val minutes = parseDecimal(if (pumpOn) onMin else offMin) ?: return@LaunchedEffect
        if (remaining <= 0) remaining = (minutes * 60).toInt()
        while (remaining > 0) {
            delay(1_000)
            remaining--
        }
        pumpOn = !pumpOn
    }
    SectionCard(stringResource(R.string.timer_title)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(onMin, { onMin = it }, stringResource(R.string.timer_on), Modifier.weight(1f), suffix = stringResource(R.string.unit_minutes))
            NumberField(offMin, { offMin = it }, stringResource(R.string.timer_off), Modifier.weight(1f), suffix = stringResource(R.string.unit_minutes))
        }
        if (running) {
            Text(
                stringResource(
                    if (pumpOn) R.string.timer_state_on else R.string.timer_state_off,
                    "%02d:%02d".format(remaining / 60, remaining % 60),
                ),
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                running = !running
                remaining = 0
                pumpOn = true
            }) { Text(stringResource(if (running) R.string.action_stop else R.string.action_start)) }
        }
    }
}

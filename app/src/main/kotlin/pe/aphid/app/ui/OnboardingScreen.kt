package pe.aphid.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import pe.aphid.core.designsystem.R
import pe.aphid.core.designsystem.icon.AphidIcons

/** Onboarding de 3 pasos: offline, privacidad, notificaciones (con explicación previa al permiso). */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onFinish() }
    val pages = listOf(
        Triple(AphidIcons.Drop, R.string.onb1_title, R.string.onb1_text),
        Triple(AphidIcons.Lock, R.string.onb2_title, R.string.onb2_text),
        Triple(AphidIcons.Bell, R.string.onb3_title, R.string.onb3_text),
    )
    val (icon, title, text) = pages[step]
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.onb_step, step + 1, pages.size), style = MaterialTheme.typography.labelLarge)
        Icon(icon, contentDescription = null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text(stringResource(text), style = MaterialTheme.typography.bodyLarge)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (step == pages.lastIndex) {
                TextButton(onClick = onFinish) { Text(stringResource(R.string.onb_later)) }
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            onFinish()
                        }
                    },
                    modifier = Modifier.height(48.dp),
                ) { Text(stringResource(R.string.onb_allow)) }
            } else {
                TextButton(onClick = onFinish) { Text(stringResource(R.string.onb_skip)) }
                Button(onClick = { step++ }, modifier = Modifier.height(48.dp)) { Text(stringResource(R.string.action_next)) }
            }
        }
    }
}

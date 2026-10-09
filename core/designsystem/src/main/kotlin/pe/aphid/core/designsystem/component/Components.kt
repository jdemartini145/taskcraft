package pe.aphid.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pe.aphid.core.designsystem.R
import pe.aphid.core.designsystem.icon.AphidIcons
import pe.aphid.core.designsystem.theme.WarningAmber
import java.text.NumberFormat
import java.util.Locale

/** Locale de formato numérico: español de Perú por defecto (coma decimal). */
val AphidLocale: Locale = Locale.forLanguageTag("es-PE")

/** Formatea un número con [decimals] decimales en el locale del usuario. */
fun Double.fmt(decimals: Int = 2, locale: Locale = Locale.getDefault()): String {
    val nf = NumberFormat.getNumberInstance(locale)
    nf.minimumFractionDigits = decimals
    nf.maximumFractionDigits = decimals
    return nf.format(this)
}

/** Interpreta "1,5" o "1.5" como 1.5. Devuelve null si no es un número. */
fun parseDecimal(text: String): Double? = text.trim().replace(" ", "").replace(',', '.').toDoubleOrNull()

/** Campo numérico con teclado decimal. Acepta coma o punto. */
@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { new -> if (new.all { it.isDigit() || it == ',' || it == '.' || it == '-' }) onValueChange(new) },
        label = { Text(label) },
        modifier = modifier.heightIn(min = 56.dp),
        singleLine = true,
        isError = isError,
        suffix = suffix?.let { { Text(it) } },
        supportingText = supportingText?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction),
    )
}

@Composable
fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            content()
        }
    }
}

enum class BannerKind { INFO, WARNING, ERROR }

@Composable
fun InfoBanner(text: String, kind: BannerKind = BannerKind.INFO, modifier: Modifier = Modifier) {
    val (container, content) = when (kind) {
        BannerKind.INFO -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        BannerKind.WARNING -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        BannerKind.ERROR -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = container, contentColor = content)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                if (kind == BannerKind.INFO) AphidIcons.Info else AphidIcons.Warning,
                contentDescription = stringResource(if (kind == BannerKind.INFO) R.string.cd_info else R.string.cd_warning),
                tint = if (kind == BannerKind.WARNING) WarningAmber else Color.Unspecified,
                modifier = Modifier.size(24.dp),
            )
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Marca visible para valores sin fuente (TODO_FUENTE). */
@Composable
fun PendingDataChip(modifier: Modifier = Modifier) {
    AssistChip(onClick = {}, label = { Text(stringResource(R.string.pending_data)) }, modifier = modifier)
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(24.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AphidTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(AphidIcons.Back, contentDescription = stringResource(R.string.cd_back)) }
            }
        },
        actions = { actions() },
    )
}

/** Fila etiqueta–valor accesible (se lee como una unidad). */
@Composable
fun LabeledValue(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().heightIn(min = 32.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

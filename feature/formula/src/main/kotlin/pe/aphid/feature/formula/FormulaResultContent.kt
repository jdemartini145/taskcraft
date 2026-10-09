package pe.aphid.feature.formula

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pe.aphid.core.designsystem.R
import pe.aphid.core.designsystem.component.BannerKind
import pe.aphid.core.designsystem.component.InfoBanner
import pe.aphid.core.designsystem.component.LabeledValue
import pe.aphid.core.designsystem.component.SectionCard
import pe.aphid.core.designsystem.component.fmt
import pe.aphid.core.designsystem.theme.WarningAmber
import pe.aphid.core.model.FormulaResult
import pe.aphid.core.model.TankGroup
import pe.aphid.core.model.ValueRange
import pe.aphid.core.model.WarningKind
import kotlin.math.abs

/** Pantalla 2 del asistente: gramos por tanque, ppm contra meta, EC, costo y acciones. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FormulaResultContent(
    result: FormulaResult,
    title: String,
    explanation: String?,
    explaining: Boolean,
    saved: Boolean,
    onExplain: () -> Unit,
    onSave: (() -> Unit)?,
    onPdf: () -> Unit,
    onCsv: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vol = result.request.reservoirVolumeL
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard(title) {
            LabeledValue(stringResource(R.string.formula_ec_estimated), "${result.estimatedEc.fmt(2)} mS/cm")
            LabeledValue(stringResource(R.string.formula_ec_range), rangeLabel(result.ecRange))
            LabeledValue(stringResource(R.string.formula_ph_target), result.phTarget?.fmt(1) ?: stringResource(R.string.pending_data))
            LabeledValue(
                stringResource(R.string.formula_cost),
                result.costPer1000LPen?.let { "S/ ${it.fmt(2)}" } ?: stringResource(R.string.formula_cost_pending),
            )
            Text(stringResource(R.string.formula_source, result.targetSource), style = MaterialTheme.typography.bodySmall)
        }

        result.tanks.forEach { tank ->
            val header = when (tank.group) {
                TankGroup.DIRECTO -> stringResource(R.string.formula_tank_direct)
                TankGroup.A -> stringResource(R.string.formula_tank_a)
                TankGroup.B -> stringResource(R.string.formula_tank_b)
                TankGroup.ACIDO -> stringResource(R.string.formula_tank_acid)
            }
            SectionCard(header) {
                if (tank.group != TankGroup.DIRECTO) {
                    Text(
                        stringResource(
                            R.string.formula_tank_dose,
                            tank.volumeL.fmt(1),
                            tank.factor,
                            tank.mlPerLiterReservoir.fmt(1),
                            tank.mlForReservoir.fmt(0),
                            vol.fmt(0),
                        ),
                    )
                }
                tank.doses.forEach { d ->
                    Row(
                        Modifier.fillMaxWidth().clearAndSetSemantics {
                            contentDescription = "${d.name}: ${d.gramsInStockTank.fmt(1)} g"
                        },
                    ) {
                        Text(d.name, Modifier.weight(1f))
                        Text("${d.gramsInStockTank.fmt(1)} g", fontWeight = FontWeight.Bold)
                    }
                    d.mlForReservoir?.let { ml ->
                        Text(stringResource(R.string.formula_acid_ml, ml.fmt(1), vol.fmt(0)), style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (!tank.solubilityOk) InfoBanner(stringResource(R.string.formula_solubility_exceeded), BannerKind.ERROR)
            }
        }

        SectionCard(stringResource(R.string.formula_balance)) {
            Row(Modifier.fillMaxWidth()) {
                listOf(R.string.formula_col_element, R.string.formula_col_target, R.string.formula_col_delivered, R.string.formula_col_dev).forEach {
                    Text(stringResource(it), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.End)
                }
            }
            HorizontalDivider()
            result.balance.filter { it.targetPpm != null || it.deliveredPpm > 0.0 }.forEach { b ->
                val dev = b.deviationPct
                val color = if (dev != null && abs(dev) > DEVIATION_WARN) WarningAmber else MaterialTheme.colorScheme.onSurface
                Row(Modifier.fillMaxWidth()) {
                    Text(b.element.symbol, Modifier.weight(1f), textAlign = TextAlign.End)
                    Text(b.targetPpm?.fmt(decimalsFor(b.targetPpm!!)) ?: "—", Modifier.weight(1f), textAlign = TextAlign.End)
                    Text(b.deliveredPpm.fmt(decimalsFor(b.deliveredPpm)), Modifier.weight(1f), textAlign = TextAlign.End)
                    Text(dev?.let { "${it.fmt(1)} %" } ?: "—", Modifier.weight(1f), textAlign = TextAlign.End, color = color)
                }
            }
            Text(stringResource(R.string.formula_mass_balance, result.massBalanceErrorPct.fmt(4)), style = MaterialTheme.typography.bodySmall)
        }

        result.warnings.forEach { w ->
            InfoBanner(w.message, if (w.kind == WarningKind.DATO_PENDIENTE || w.kind == WarningKind.ACIDO) BannerKind.WARNING else BannerKind.ERROR)
        }

        SectionCard(stringResource(R.string.formula_explain_title)) {
            if (explanation != null) Text(explanation)
            OutlinedButton(onClick = onExplain, enabled = !explaining) {
                if (explaining) CircularProgressIndicator() else Text(stringResource(R.string.formula_explain))
            }
            Text(stringResource(R.string.formula_ai_never_grams), style = MaterialTheme.typography.bodySmall)
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onSave != null) {
                Button(onClick = onSave, enabled = !saved) { Text(stringResource(if (saved) R.string.saved_ok else R.string.action_save)) }
            }
            OutlinedButton(onClick = onPdf) { Text(stringResource(R.string.action_export_pdf)) }
            OutlinedButton(onClick = onCsv) { Text(stringResource(R.string.action_export_csv)) }
        }
        Text(stringResource(R.string.formula_safety), style = MaterialTheme.typography.bodySmall)
    }
}

private const val DEVIATION_WARN = 10.0

private fun decimalsFor(v: Double) = if (v >= 10) 0 else if (v >= 1) 1 else 2

@Composable
private fun rangeLabel(r: ValueRange): String = when {
    r.min != null && r.max != null -> "${r.min!!.fmt(1)}–${r.max!!.fmt(1)}"
    r.min != null -> "≥ ${r.min!!.fmt(1)}"
    r.max != null -> "≤ ${r.max!!.fmt(1)}"
    else -> stringResource(R.string.pending_data)
}

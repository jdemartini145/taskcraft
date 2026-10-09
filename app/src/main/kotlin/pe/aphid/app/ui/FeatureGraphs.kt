package pe.aphid.app.ui

import androidx.compose.material3.Text
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

// Temporal: se reemplaza por las rutas de :feature:log y :feature:diagnosis.
@Serializable data object LogTabRoute

@Serializable data object DiagnosisTabRoute

fun NavGraphBuilder.featureGraphs(@Suppress("UNUSED_PARAMETER") navController: NavController) {
    composable<LogTabRoute> { Text("Bitácora") }
    composable<DiagnosisTabRoute> { Text("Diagnóstico") }
}

/** Entradas de la pestaña "Más". */
fun moreEntries(): List<MoreEntry> = listOf(
    MoreEntry(pe.aphid.core.designsystem.R.string.water_title, { pe.aphid.core.designsystem.icon.AphidIcons.Drop }, pe.aphid.feature.formula.WaterListRoute),
    MoreEntry(pe.aphid.core.designsystem.R.string.formula_saved, { pe.aphid.core.designsystem.icon.AphidIcons.Document }, pe.aphid.feature.formula.SavedFormulasRoute),
)

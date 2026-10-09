package pe.aphid.app.ui

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import pe.aphid.core.designsystem.R
import pe.aphid.core.designsystem.icon.AphidIcons
import pe.aphid.feature.alerts.AlertsRoute
import pe.aphid.feature.alerts.alertsGraph
import pe.aphid.feature.crops.CropsRoute
import pe.aphid.feature.crops.cropsGraph
import pe.aphid.feature.diagnosis.diagnosisGraph
import pe.aphid.feature.formula.SavedFormulasRoute
import pe.aphid.feature.formula.WaterListRoute
import pe.aphid.feature.log.CalculatorsRoute
import pe.aphid.feature.log.logGraph
import pe.aphid.feature.pests.PestsRoute
import pe.aphid.feature.pests.pestsGraph
import pe.aphid.feature.sensors.SensorsRoute
import pe.aphid.feature.sensors.sensorsGraph
import pe.aphid.feature.settings.SettingsRoute
import pe.aphid.feature.settings.settingsGraph
import pe.aphid.feature.shopping.ShoppingRoute
import pe.aphid.feature.shopping.shoppingGraph

/** Grafos de navegación de los módulos de funcionalidad (además de Sistemas y Fórmula). */
fun NavGraphBuilder.featureGraphs(navController: NavController) {
    logGraph(navController)
    diagnosisGraph(navController)
    alertsGraph(navController)
    cropsGraph(navController)
    pestsGraph(navController)
    shoppingGraph(navController)
    settingsGraph(navController)
    sensorsGraph(navController)
}

/** Entradas de la pestaña "Más". */
fun moreEntries(): List<MoreEntry> = listOf(
    MoreEntry(R.string.more_alerts, { AphidIcons.Bell }, AlertsRoute),
    MoreEntry(R.string.more_crops, { AphidIcons.Leaf }, CropsRoute),
    MoreEntry(R.string.water_title, { AphidIcons.Drop }, WaterListRoute),
    MoreEntry(R.string.formula_saved, { AphidIcons.Document }, SavedFormulasRoute),
    MoreEntry(R.string.more_calculators, { AphidIcons.Calculator }, CalculatorsRoute),
    MoreEntry(R.string.more_shopping, { AphidIcons.Cart }, ShoppingRoute),
    MoreEntry(R.string.more_pests, { AphidIcons.Bug }, PestsRoute),
    MoreEntry(R.string.more_sensors, { AphidIcons.Sensor }, SensorsRoute),
    MoreEntry(R.string.more_settings, { AphidIcons.Settings }, SettingsRoute),
)

package pe.aphid.feature.alerts

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable data object AlertsRoute

fun NavGraphBuilder.alertsGraph(navController: NavController) {
    composable<AlertsRoute> { AlertsScreen(onBack = navController::popBackStack) }
}

package pe.aphid.feature.log

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable data object LogRoute

@Serializable data object CalculatorsRoute

fun NavGraphBuilder.logGraph(navController: NavController) {
    composable<LogRoute> { LogScreen(onOpenCalculators = { navController.navigate(CalculatorsRoute) }) }
    composable<CalculatorsRoute> { CalculatorsScreen(onBack = navController::popBackStack) }
}

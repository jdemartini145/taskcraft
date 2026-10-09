package pe.aphid.feature.diagnosis

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable data object DiagnosisRoute

@Serializable data object CaptureRoute

fun NavGraphBuilder.diagnosisGraph(navController: NavController) {
    composable<DiagnosisRoute> { DiagnosisScreen(onCapture = { navController.navigate(CaptureRoute) }) }
    composable<CaptureRoute> { CaptureScreen(onBack = navController::popBackStack) }
}

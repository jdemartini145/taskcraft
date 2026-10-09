package pe.aphid.feature.formula

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable data object FormulaRoute

@Serializable data object SavedFormulasRoute

@Serializable data class SavedFormulaRoute(val id: Long)

@Serializable data object WaterListRoute

@Serializable data class WaterEditRoute(val id: Long = 0L)

fun NavGraphBuilder.formulaGraph(navController: NavController) {
    composable<FormulaRoute> {
        FormulaScreen(
            onAddWater = { navController.navigate(WaterEditRoute()) },
            onOpenSaved = { navController.navigate(SavedFormulasRoute) },
        )
    }
    composable<SavedFormulasRoute> {
        SavedFormulasScreen(onBack = navController::popBackStack, onOpen = { navController.navigate(SavedFormulaRoute(it)) })
    }
    composable<SavedFormulaRoute> { SavedFormulaScreen(onBack = navController::popBackStack) }
    composable<WaterListRoute> {
        WaterListScreen(onBack = navController::popBackStack, onEdit = { navController.navigate(WaterEditRoute(it)) })
    }
    composable<WaterEditRoute> { WaterEditScreen(onDone = navController::popBackStack) }
}

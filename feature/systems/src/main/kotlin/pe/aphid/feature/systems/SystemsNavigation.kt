package pe.aphid.feature.systems

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable data object SystemsRoute

@Serializable data class SystemDetailRoute(val id: Long)

@Serializable data class SystemEditRoute(val id: Long = 0L)

fun NavGraphBuilder.systemsGraph(navController: NavController) {
    composable<SystemsRoute> {
        SystemsScreen(
            onOpen = { navController.navigate(SystemDetailRoute(it)) },
            onAdd = { navController.navigate(SystemEditRoute()) },
        )
    }
    composable<SystemDetailRoute> {
        SystemDetailScreen(
            onBack = navController::popBackStack,
            onEdit = { navController.navigate(SystemEditRoute(it)) },
        )
    }
    composable<SystemEditRoute> {
        SystemEditScreen(onDone = navController::popBackStack)
    }
}

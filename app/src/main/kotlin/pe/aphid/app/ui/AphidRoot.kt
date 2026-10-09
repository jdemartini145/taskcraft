package pe.aphid.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.serialization.Serializable
import pe.aphid.core.designsystem.R
import pe.aphid.core.designsystem.icon.AphidIcons
import pe.aphid.core.designsystem.theme.AphidTheme
import pe.aphid.feature.formula.FormulaRoute
import pe.aphid.feature.formula.formulaGraph
import pe.aphid.feature.systems.SystemsRoute
import pe.aphid.feature.systems.systemsGraph
import kotlin.reflect.KClass

@Serializable data object MoreRoute

/** Pestañas de la navegación inferior. */
enum class TopLevel(val route: Any, val routeClass: KClass<*>, val label: Int, val icon: () -> ImageVector) {
    SYSTEMS(SystemsRoute, SystemsRoute::class, R.string.nav_systems, { AphidIcons.Leaf }),
    FORMULA(FormulaRoute, FormulaRoute::class, R.string.nav_formula, { AphidIcons.Flask }),
    LOG(LogTabRoute, LogTabRoute::class, R.string.nav_log, { AphidIcons.List }),
    DIAGNOSIS(DiagnosisTabRoute, DiagnosisTabRoute::class, R.string.nav_diagnosis, { AphidIcons.Camera }),
    MORE(MoreRoute, MoreRoute::class, R.string.nav_more, { AphidIcons.More }),
}

@Composable
fun AphidRoot(viewModel: MainViewModel = hiltViewModel()) {
    val settings by viewModel.userSettings.collectAsStateWithLifecycle()
    AphidTheme {
        Surface(Modifier.fillMaxSize()) {
            val s = settings ?: return@Surface
            if (!s.onboardingDone) {
                OnboardingScreen(onFinish = viewModel::finishOnboarding)
            } else {
                AphidApp()
            }
        }
    }
}

@Composable
fun AphidApp(navController: NavHostController = rememberNavController()) {
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            TopLevel.entries.forEach { tab ->
                val selected = destination?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true
                item(
                    selected = selected,
                    onClick = {
                        navController.navigate(tab.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(tab.icon(), contentDescription = null) },
                    label = { Text(stringResource(tab.label)) },
                )
            }
        },
    ) {
        NavHost(navController = navController, startDestination = SystemsRoute) {
            systemsGraph(navController)
            formulaGraph(navController)
            featureGraphs(navController)
            composable<MoreRoute> { MoreScreen(onNavigate = { navController.navigate(it) }) }
        }
    }
}

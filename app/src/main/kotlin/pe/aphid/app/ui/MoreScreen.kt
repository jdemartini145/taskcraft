package pe.aphid.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import pe.aphid.core.designsystem.R
import pe.aphid.core.designsystem.component.AphidScaffold
import pe.aphid.core.designsystem.component.AphidTopBar

data class MoreEntry(val label: Int, val icon: () -> ImageVector, val route: Any)

@Composable
fun MoreScreen(onNavigate: (Any) -> Unit) {
    AphidScaffold(topBar = { AphidTopBar(stringResource(R.string.nav_more)) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(moreEntries()) { e ->
                ListItem(
                    headlineContent = { Text(stringResource(e.label)) },
                    leadingContent = { Icon(e.icon(), contentDescription = null) },
                    modifier = Modifier.clickable { onNavigate(e.route) },
                )
            }
        }
    }
}

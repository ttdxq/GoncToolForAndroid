package cyou.ttdxq.gonctool.android.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cyou.ttdxq.gonctool.android.R

enum class MainPage {
    HOME,
    SETTINGS,
    LOGS,
}

@Composable
fun AppNavigationMenuButton(
    onMenuClick: () -> Unit,
) {
    IconButton(onClick = onMenuClick) {
        Icon(
            imageVector = Icons.Filled.Menu,
            contentDescription = stringResource(R.string.menu_content_description)
        )
    }
}

@Composable
fun AppDrawerContent(
    currentPage: MainPage,
    onNavigate: (MainPage) -> Unit,
) {
    ModalDrawerSheet {
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 28.dp, top = 20.dp, bottom = 20.dp)
        )
        Spacer(Modifier.height(4.dp))
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.menu_home)) },
            selected = currentPage == MainPage.HOME,
            onClick = { onNavigate(MainPage.HOME) },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.menu_settings)) },
            selected = currentPage == MainPage.SETTINGS,
            onClick = { onNavigate(MainPage.SETTINGS) },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.menu_logs)) },
            selected = currentPage == MainPage.LOGS,
            onClick = { onNavigate(MainPage.LOGS) },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
    }
}

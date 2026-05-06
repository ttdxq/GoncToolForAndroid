package cyou.ttdxq.gonctool.android.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cyou.ttdxq.gonctool.android.R

private val AppsGridIcon: ImageVector
    get() {
        if (_appsGridIcon != null) {
            return _appsGridIcon!!
        }

        _appsGridIcon = ImageVector.Builder(
            name = "AppsGridIcon",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(4f, 4f)
                lineTo(10f, 4f)
                lineTo(10f, 10f)
                lineTo(4f, 10f)
                close()

                moveTo(14f, 4f)
                lineTo(20f, 4f)
                lineTo(20f, 10f)
                lineTo(14f, 10f)
                close()

                moveTo(4f, 14f)
                lineTo(10f, 14f)
                lineTo(10f, 20f)
                lineTo(4f, 20f)
                close()

                moveTo(14f, 14f)
                lineTo(20f, 14f)
                lineTo(20f, 20f)
                lineTo(14f, 20f)
                close()
            }
        }.build()

        return _appsGridIcon!!
    }

private var _appsGridIcon: ImageVector? = null

enum class MainPage {
    HOME,
    SETTINGS,
    APP_SELECTOR,
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
            icon = { Icon(Icons.Filled.Home, contentDescription = null) },
            label = { Text(stringResource(R.string.menu_home)) },
            selected = currentPage == MainPage.HOME,
            onClick = { onNavigate(MainPage.HOME) },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
        NavigationDrawerItem(
            icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
            label = { Text(stringResource(R.string.menu_settings)) },
            selected = currentPage == MainPage.SETTINGS,
            onClick = { onNavigate(MainPage.SETTINGS) },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
        NavigationDrawerItem(
            icon = { Icon(AppsGridIcon, contentDescription = null) },
            label = { Text(stringResource(R.string.menu_app_selector)) },
            selected = currentPage == MainPage.APP_SELECTOR,
            onClick = { onNavigate(MainPage.APP_SELECTOR) },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
        NavigationDrawerItem(
            icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
            label = { Text(stringResource(R.string.menu_logs)) },
            selected = currentPage == MainPage.LOGS,
            onClick = { onNavigate(MainPage.LOGS) },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
    }
}

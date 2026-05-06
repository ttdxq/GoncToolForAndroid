package cyou.ttdxq.gonctool.android.ui

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.google.gson.Gson
import cyou.ttdxq.gonctool.android.R
import cyou.ttdxq.gonctool.android.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SplitTunnelMode(
    val value: String,
    @StringRes val labelResId: Int,
) {
    ALL("all", R.string.split_tunnel_mode_all),
    ALLOW("allow", R.string.split_tunnel_mode_allow),
    DENY("deny", R.string.split_tunnel_mode_deny);

    companion object {
        fun fromValue(value: String): SplitTunnelMode {
            return values().firstOrNull { it.value == value } ?: ALL
        }
    }
}

private data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
)

private object AppSelectorScreenCache {
    private const val ICON_SIZE_PX = 96

    @Volatile
    private var installedApps: List<InstalledAppInfo>? = null

    private val iconCache = LinkedHashMap<String, ImageBitmap>()

    fun getInstalledApps(packageManager: PackageManager): List<InstalledAppInfo> {
        installedApps?.let { return it }

        return synchronized(this) {
            installedApps ?: loadInstalledUserApps(packageManager).also { installedApps = it }
        }
    }

    fun getInstalledAppsOrEmpty(): List<InstalledAppInfo> {
        return installedApps.orEmpty()
    }

    fun getCachedIcon(packageName: String): ImageBitmap? {
        return synchronized(iconCache) {
            iconCache[packageName]
        }
    }

    fun getOrLoadIcon(
        packageManager: PackageManager,
        packageName: String,
    ): ImageBitmap? {
        getCachedIcon(packageName)?.let { return it }

        val loadedIcon = runCatching {
            packageManager.getApplicationIcon(packageName)
                .toBitmap(width = ICON_SIZE_PX, height = ICON_SIZE_PX)
                .asImageBitmap()
        }.getOrNull()

        if (loadedIcon != null) {
            synchronized(iconCache) {
                iconCache.putIfAbsent(packageName, loadedIcon)
                return iconCache[packageName]
            }
        }

        return null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectorScreen(
    settingsStore: SettingsStore,
    onMenuClick: () -> Unit,
    shouldLoadApps: Boolean = true,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val packageManager = context.packageManager
    val scope = rememberCoroutineScope()
    val gson = remember { Gson() }
    val splitTunnelMode by settingsStore.splitTunnelMode.collectAsState(initial = SplitTunnelMode.ALL.value)
    val splitTunnelAppsJson by settingsStore.splitTunnelApps.collectAsState(initial = "[]")
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val selectedPackages = remember(splitTunnelAppsJson) {
        runCatching {
            gson.fromJson(splitTunnelAppsJson, Array<String>::class.java)
                ?.filter { it.isNotBlank() }
                ?.toSet()
                ?: emptySet()
        }.getOrDefault(emptySet())
    }
    val installedApps by produceState(
        initialValue = if (shouldLoadApps) AppSelectorScreenCache.getInstalledAppsOrEmpty() else emptyList(),
        packageManager,
        shouldLoadApps,
    ) {
        if (!shouldLoadApps) {
            value = emptyList()
            return@produceState
        }

        value = withContext(Dispatchers.IO) {
            AppSelectorScreenCache.getInstalledApps(packageManager)
        }
    }
    val filteredApps = remember(installedApps, searchQuery) {
        val keyword = searchQuery.trim()
        if (keyword.isBlank()) {
            installedApps
        } else {
            installedApps.filter { app ->
                app.appName.contains(keyword, ignoreCase = true) ||
                    app.packageName.contains(keyword, ignoreCase = true)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_selector_title)) },
                navigationIcon = {
                    AppNavigationMenuButton(
                        onMenuClick = onMenuClick,
                    )
                },
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SplitTunnelMode.values().forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                scope.launch {
                                    settingsStore.setSplitTunnelMode(mode.value)
                                }
                            }
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = splitTunnelMode == mode.value,
                            onClick = {
                                scope.launch {
                                    settingsStore.setSplitTunnelMode(mode.value)
                                }
                            }
                        )
                        Text(
                            text = stringResource(mode.labelResId),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text(stringResource(R.string.split_tunnel_search_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            if (!shouldLoadApps) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.app_selector_not_needed_in_all_mode),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (filteredApps.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_apps_found),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = filteredApps,
                        key = { app -> app.packageName }
                    ) { app ->
                        val isSelected = selectedPackages.contains(app.packageName)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    val updatedPackages = selectedPackages.toMutableSet().apply {
                                        if (isSelected) {
                                            remove(app.packageName)
                                        } else {
                                            add(app.packageName)
                                        }
                                    }
                                    scope.launch {
                                        settingsStore.setSplitTunnelApps(gson.toJson(updatedPackages.sorted()))
                                    }
                                }
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AppIcon(
                                packageManager = packageManager,
                                packageName = app.packageName,
                                appName = app.appName,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.appName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = app.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    val updatedPackages = selectedPackages.toMutableSet().apply {
                                        if (checked) {
                                            add(app.packageName)
                                        } else {
                                            remove(app.packageName)
                                        }
                                    }
                                    scope.launch {
                                        settingsStore.setSplitTunnelApps(gson.toJson(updatedPackages.sorted()))
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppIcon(
    packageManager: PackageManager,
    packageName: String,
    appName: String,
) {
    val icon by produceState(
        initialValue = AppSelectorScreenCache.getCachedIcon(packageName),
        packageManager,
        packageName,
    ) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                AppSelectorScreenCache.getOrLoadIcon(packageManager, packageName)
            }
        }
    }

    val iconBitmap = icon

    if (iconBitmap != null) {
        androidx.compose.foundation.Image(
            bitmap = iconBitmap,
            contentDescription = null,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
        )
    } else {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = appName.trim().take(1),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Suppress("DEPRECATION")
private fun loadInstalledUserApps(packageManager: PackageManager): List<InstalledAppInfo> {
    return packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        .asSequence()
        .filter { app -> app.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
        .map { app ->
            InstalledAppInfo(
                packageName = app.packageName,
                appName = app.loadLabel(packageManager)?.toString().orEmpty().ifBlank { app.packageName },
            )
        }
        .sortedWith(
            compareBy<InstalledAppInfo> { it.appName.lowercase() }
                .thenBy { it.packageName.lowercase() }
        )
        .toList()
}

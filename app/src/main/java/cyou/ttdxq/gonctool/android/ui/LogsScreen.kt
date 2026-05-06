package cyou.ttdxq.gonctool.android.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import cyou.ttdxq.gonctool.android.R
import cyou.ttdxq.gonctool.android.data.LogRepository

private val logLevelPattern = Regex("""^\[\d{2}:\d{2}:\d{2}] (\w+)\b""")

private enum class LogFilterLevel(
    val labelResId: Int,
    val rawLevel: String?,
) {
    ALL(R.string.log_filter_all, null),
    DEBUG(R.string.log_filter_debug, "DEBUG"),
    INFO(R.string.log_filter_info, "INFO"),
    WARN(R.string.log_filter_warn, "WARN"),
    ERROR(R.string.log_filter_error, "ERROR"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    onMenuClick: () -> Unit,
) {
    val logs by LogRepository.logs.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    var selectedLevel by remember { mutableStateOf(LogFilterLevel.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    val filteredLogs = remember(logs, selectedLevel, searchQuery) {
        val keyword = searchQuery.trim()
        logs.filter { log ->
            val matchesLevel = selectedLevel.rawLevel == null || extractLogLevel(log) == selectedLevel.rawLevel
            val matchesKeyword = keyword.isBlank() || log.contains(keyword, ignoreCase = true)
            matchesLevel && matchesKeyword
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.logs_page_title)) },
                navigationIcon = {
                    AppNavigationMenuButton(
                        onMenuClick = onMenuClick,
                    )
                },
                actions = {
                    TextButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(logs.joinToString(separator = "\n")))
                        },
                        enabled = logs.isNotEmpty(),
                    ) {
                        Text(stringResource(R.string.copy_all_logs))
                    }
                    TextButton(
                        onClick = { LogRepository.clear() },
                        enabled = logs.isNotEmpty(),
                    ) {
                        Text(stringResource(R.string.clear_logs))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LogFilterLevel.values().forEach { level ->
                        FilterChip(
                            selected = selectedLevel == level,
                            onClick = { selectedLevel = level },
                            label = { Text(stringResource(level.labelResId)) }
                        )
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.log_search_hint)) },
                    singleLine = true
                )
            }

            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (logs.isEmpty()) {
                            stringResource(R.string.no_logs)
                        } else {
                            stringResource(R.string.no_matching_logs)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(16.dp)
                ) {
                    items(
                        items = filteredLogs,
                        key = { log -> log }
                    ) { log ->
                        Text(
                            text = log,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun extractLogLevel(log: String): String? {
    return logLevelPattern.find(log)?.groupValues?.getOrNull(1)?.uppercase()
}

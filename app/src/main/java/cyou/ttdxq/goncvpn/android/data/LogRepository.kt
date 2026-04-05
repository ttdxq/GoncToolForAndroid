package cyou.ttdxq.goncvpn.android.data

import cyou.ttdxq.goncvpn.android.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR
}

object LogRepository {
    private const val MAX_LOGS = 200
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val jsonLevelRegex = Regex("\"level\"\\s*:\\s*\"(debug|info|warn|warning|error)\"", RegexOption.IGNORE_CASE)

    fun log(tag: String, message: String, level: LogLevel = inferLogLevel(message)) {
        val timestamp = dateFormat.format(Date())
        val entry = "[$timestamp] ${level.name} $tag: $message"
        _logs.value = listOf(entry) + _logs.value.take(MAX_LOGS - 1)

        if (isDebugBuild()) {
            when (level) {
                LogLevel.DEBUG -> android.util.Log.d(tag, message)
                LogLevel.INFO -> android.util.Log.i(tag, message)
                LogLevel.WARN -> android.util.Log.w(tag, message)
                LogLevel.ERROR -> android.util.Log.e(tag, message)
            }
        }
    }

    fun debug(tag: String, message: String) = log(tag, message, LogLevel.DEBUG)

    fun info(tag: String, message: String) = log(tag, message, LogLevel.INFO)

    fun warn(tag: String, message: String) = log(tag, message, LogLevel.WARN)

    fun error(tag: String, message: String) = log(tag, message, LogLevel.ERROR)

    fun error(tag: String, message: String, throwable: Throwable) {
        log(tag, "$message: ${throwable.message}", LogLevel.ERROR)
        if (isDebugBuild()) {
            android.util.Log.e(tag, message, throwable)
        }
    }

    fun clear() {
        _logs.value = emptyList()
    }

    private fun inferLogLevel(message: String): LogLevel {
        val jsonLevel = jsonLevelRegex.find(message)?.groupValues?.getOrNull(1)?.lowercase(Locale.ROOT)
        if (jsonLevel != null) {
            return when (jsonLevel) {
                "debug" -> LogLevel.DEBUG
                "info" -> LogLevel.INFO
                "warn", "warning" -> LogLevel.WARN
                "error" -> LogLevel.ERROR
                else -> LogLevel.INFO
            }
        }

        val lowered = message.lowercase(Locale.ROOT)
        return when {
            "panic" in lowered || "fatal" in lowered || "failed" in lowered || "error" in lowered || "exception" in lowered -> LogLevel.ERROR
            "warn" in lowered || "timeout" in lowered || "retry" in lowered || "refused" in lowered -> LogLevel.WARN
            else -> LogLevel.INFO
        }
    }

    private fun isDebugBuild(): Boolean {
        return BuildConfig.DEBUG
    }
}

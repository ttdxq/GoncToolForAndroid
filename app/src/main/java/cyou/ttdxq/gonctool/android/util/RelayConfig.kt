package cyou.ttdxq.gonctool.android.util

import java.net.IDN
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

enum class RelayMode(val value: String) {
    FALLBACK("fallback"),
    FORCE("force");

    companion object {
        fun fromValue(value: String): RelayMode = entries.firstOrNull { it.value == value } ?: FALLBACK
    }
}

data class RelayConfig(
    val enabled: Boolean,
    val mode: RelayMode,
    val host: String,
    val port: Int,
    val tlsEnabled: Boolean,
    val psk: String,
    val authEnabled: Boolean,
    val username: String,
    val password: String,
)

enum class RelayConfigError {
    HOST_REQUIRED,
    HOST_INVALID,
    PORT_INVALID,
    PSK_REQUIRED,
    CREDENTIALS_REQUIRED,
}

fun validateRelayConfig(config: RelayConfig): RelayConfigError? {
    if (!config.enabled) return null
    if (config.host.isBlank()) return RelayConfigError.HOST_REQUIRED
    if (normalizedRelayHost(config.host) == null) return RelayConfigError.HOST_INVALID
    if (config.port !in 1..65535) return RelayConfigError.PORT_INVALID
    if (config.tlsEnabled && config.psk.isBlank()) return RelayConfigError.PSK_REQUIRED
    if (config.authEnabled && (config.username.isBlank() || config.password.isBlank())) {
        return RelayConfigError.CREDENTIALS_REQUIRED
    }
    return null
}

fun buildRelayExtraArgs(config: RelayConfig): String {
    require(validateRelayConfig(config) == null) { "Invalid Relay configuration" }
    if (!config.enabled) return ""
    val flag = if (config.mode == RelayMode.FORCE) "-x" else "-x2"
    return "$flag ${buildRelayProxyUrl(config)}"
}

fun buildRelayProxyUrl(config: RelayConfig): String {
    val host = requireNotNull(normalizedRelayHost(config.host)) { "Invalid Relay host" }
    val scheme = if (config.tlsEnabled) "socks5s" else "socks5"
    val userInfo = if (config.authEnabled) {
        "${encodeUrlComponent(config.username)}:${encodeUrlComponent(config.password)}@"
    } else {
        ""
    }
    val authorityHost = if (host.contains(':')) "[$host]" else host
    val query = if (config.tlsEnabled) "?psk=${encodeUrlComponent(config.psk)}" else ""
    return "$scheme://$userInfo$authorityHost:${config.port}$query"
}

private fun normalizedRelayHost(rawHost: String): String? {
    val trimmed = rawHost.trim().removePrefix("[").removeSuffix("]")
    if (trimmed.isBlank() || trimmed.any { it.isWhitespace() }) return null
    return runCatching {
        val asciiHost = if (trimmed.contains(':')) trimmed else IDN.toASCII(trimmed)
        val uriHost = if (asciiHost.contains(':')) "[$asciiHost]" else asciiHost
        require(!URI("socks5://$uriHost:1080").host.isNullOrBlank())
        asciiHost
    }.getOrNull()
}

private fun encodeUrlComponent(value: String): String {
    return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
}

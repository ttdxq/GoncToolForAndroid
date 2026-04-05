package cyou.ttdxq.gonctool.android.util

import java.net.InetAddress

fun normalizeIpAddressInput(address: String): String? {
    val normalized = address.trim().removePrefix("[").removeSuffix("]")
    if (normalized.isBlank()) return null

    return runCatching { InetAddress.getByName(normalized).hostAddress }
        .getOrNull()
}

fun isValidIpAddressInput(address: String): Boolean {
    return normalizeIpAddressInput(address) != null
}

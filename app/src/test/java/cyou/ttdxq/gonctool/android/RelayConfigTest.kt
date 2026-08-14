package cyou.ttdxq.gonctool.android

import cyou.ttdxq.gonctool.android.util.RelayConfig
import cyou.ttdxq.gonctool.android.util.RelayConfigError
import cyou.ttdxq.gonctool.android.util.RelayMode
import cyou.ttdxq.gonctool.android.util.buildRelayExtraArgs
import cyou.ttdxq.gonctool.android.util.validateRelayConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RelayConfigTest {
    @Test
    fun buildsEncodedFallbackRelayUrl() {
        val config = relayConfig(
            tlsEnabled = true,
            psk = "relay key&value",
            authEnabled = true,
            username = "user name",
            password = "p@ss:word",
        )

        assertEquals(
            "-x2 socks5s://user%20name:p%40ss%3Aword@relay.example.com:1080?psk=relay%20key%26value",
            buildRelayExtraArgs(config),
        )
    }

    @Test
    fun buildsForceRelayUrlForIpv6() {
        val config = relayConfig(
            mode = RelayMode.FORCE,
            host = "[2001:db8::1]",
            port = 2080,
        )

        assertEquals("-x socks5://[2001:db8::1]:2080", buildRelayExtraArgs(config))
    }

    @Test
    fun validatesRequiredSecurityFields() {
        assertEquals(
            RelayConfigError.PSK_REQUIRED,
            validateRelayConfig(relayConfig(tlsEnabled = true)),
        )
        assertEquals(
            RelayConfigError.CREDENTIALS_REQUIRED,
            validateRelayConfig(relayConfig(authEnabled = true, username = "user")),
        )
        assertNull(validateRelayConfig(relayConfig()))
    }

    private fun relayConfig(
        enabled: Boolean = true,
        mode: RelayMode = RelayMode.FALLBACK,
        host: String = "relay.example.com",
        port: Int = 1080,
        tlsEnabled: Boolean = false,
        psk: String = "",
        authEnabled: Boolean = false,
        username: String = "",
        password: String = "",
    ) = RelayConfig(
        enabled = enabled,
        mode = mode,
        host = host,
        port = port,
        tlsEnabled = tlsEnabled,
        psk = psk,
        authEnabled = authEnabled,
        username = username,
        password = password,
    )
}

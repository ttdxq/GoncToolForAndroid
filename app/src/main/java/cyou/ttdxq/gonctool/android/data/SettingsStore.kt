package cyou.ttdxq.gonctool.android.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    companion object {
        val KEY_P2P_SECRET = stringPreferencesKey("p2p_secret")
        val KEY_ROUTE_CIDRS = stringPreferencesKey("route_cidrs")
        val KEY_SPLIT_TUNNEL_MODE = stringPreferencesKey("split_tunnel_mode")
        val KEY_SPLIT_TUNNEL_APPS = stringPreferencesKey("split_tunnel_apps")
        val KEY_USE_CUSTOM_DNS = booleanPreferencesKey("use_custom_dns")
        val KEY_CUSTOM_DNS_ADDRESS = stringPreferencesKey("custom_dns_address")
        val KEY_DNS_THROUGH_TUNNEL = booleanPreferencesKey("dns_through_tunnel")
        val KEY_LINK_GONC_DNS = booleanPreferencesKey("link_gonc_dns")
        val KEY_CUSTOM_STUN_SERVERS = stringPreferencesKey("custom_stun_servers")
        val KEY_CUSTOM_MQTT_SERVERS = stringPreferencesKey("custom_mqtt_servers")
        val KEY_EXPERT_MODE_ENABLED = booleanPreferencesKey("expert_mode_enabled")
        val KEY_EXPERT_MODE_RAW_ARGS = stringPreferencesKey("expert_mode_raw_args")
        val KEY_KCP_ENABLED = booleanPreferencesKey("kcp_enabled")
    }

    val p2pSecret: Flow<String> = context.dataStore.data
        .map { preferences -> preferences[KEY_P2P_SECRET] ?: "" }

    val routeCidrs: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[KEY_ROUTE_CIDRS] ?: "10.0.0.0/8\n172.16.0.0/12\n192.168.0.0/16"
        }

    val splitTunnelMode: Flow<String> = context.dataStore.data
        .map { preferences -> preferences[KEY_SPLIT_TUNNEL_MODE] ?: "all" }

    val splitTunnelApps: Flow<String> = context.dataStore.data
        .map { preferences -> preferences[KEY_SPLIT_TUNNEL_APPS] ?: "[]" }

    val useCustomDns: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[KEY_USE_CUSTOM_DNS] ?: false }

    val customDnsAddress: Flow<String> = context.dataStore.data
        .map { preferences -> preferences[KEY_CUSTOM_DNS_ADDRESS] ?: "" }

    val dnsThroughTunnel: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[KEY_DNS_THROUGH_TUNNEL] ?: true }

    val linkGoncDns: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[KEY_LINK_GONC_DNS] ?: false }

    val customStunServers: Flow<String> = context.dataStore.data
        .map { preferences -> preferences[KEY_CUSTOM_STUN_SERVERS] ?: "" }

    val customMqttServers: Flow<String> = context.dataStore.data
        .map { preferences -> preferences[KEY_CUSTOM_MQTT_SERVERS] ?: "" }

    val expertModeEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[KEY_EXPERT_MODE_ENABLED] ?: false }

    val expertModeRawArgs: Flow<String> = context.dataStore.data
        .map { preferences -> preferences[KEY_EXPERT_MODE_RAW_ARGS] ?: "" }

    val kcpEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[KEY_KCP_ENABLED] ?: false }

    suspend fun setP2pSecret(secret: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_P2P_SECRET] = secret
        }
    }

    suspend fun setRouteCidrs(cidrs: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ROUTE_CIDRS] = cidrs
        }
    }

    suspend fun setSplitTunnelMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SPLIT_TUNNEL_MODE] = mode
        }
    }

    suspend fun setSplitTunnelApps(apps: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SPLIT_TUNNEL_APPS] = apps
        }
    }

    suspend fun setUseCustomDns(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_USE_CUSTOM_DNS] = enabled
        }
    }

    suspend fun setCustomDnsAddress(address: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_CUSTOM_DNS_ADDRESS] = address
        }
    }

    suspend fun setDnsThroughTunnel(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_DNS_THROUGH_TUNNEL] = enabled
        }
    }

    suspend fun setLinkGoncDns(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_LINK_GONC_DNS] = enabled
        }
    }

    suspend fun setCustomStunServers(servers: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_CUSTOM_STUN_SERVERS] = servers
        }
    }

    suspend fun setCustomMqttServers(servers: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_CUSTOM_MQTT_SERVERS] = servers
        }
    }

    suspend fun setExpertModeEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_EXPERT_MODE_ENABLED] = enabled
        }
    }

    suspend fun setExpertModeRawArgs(args: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_EXPERT_MODE_RAW_ARGS] = args
        }
    }

    suspend fun setKcpEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_KCP_ENABLED] = enabled
        }
    }
}

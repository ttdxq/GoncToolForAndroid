package cyou.ttdxq.goncvpn.android.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * VPN连接状态枚举
 */
enum class VpnStatus {
    DISCONNECTED,   // 已断开
    CONNECTING,     // 连接中
    STOPPING,
    CONNECTED,      // 已连接
    ERROR           // 错误
}

/**
 * VPN状态管理器（单例）
 */
object VpnState {
    private val _status = MutableStateFlow(VpnStatus.DISCONNECTED)
    private val _errorMessage = MutableStateFlow<String?>(null)
    
    val status: StateFlow<VpnStatus> = _status.asStateFlow()
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    /**
     * 更新状态
     */
    fun setStatus(newStatus: VpnStatus) {
        _status.value = newStatus
        // 状态切换时清除错误信息
        if (newStatus != VpnStatus.ERROR) {
            _errorMessage.value = null
        }
    }
    
    /**
     * 设置错误信息
     */
    fun setError(message: String) {
        _errorMessage.value = message
        _status.value = VpnStatus.ERROR
    }
    
    /**
     * 清除错误信息
     */
    fun clearError() {
        _errorMessage.value = null
    }
}

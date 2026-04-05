package cyou.ttdxq.gonctool.android.util

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * CIDR验证结果
 */
sealed class CidrValidationResult {
    data object Valid : CidrValidationResult()
    data class Invalid(val error: String) : CidrValidationResult()
}

/**
 * CIDR验证工具类
 */
object CidrValidator {
    
    /**
     * 验证单条CIDR
     */
    fun validateCidr(cidr: String): CidrValidationResult {
        val trimmed = cidr.trim()
        
        if (trimmed.isEmpty()) {
            return CidrValidationResult.Invalid("CIDR不能为空")
        }
        
        val parts = trimmed.split("/")
        if (parts.size != 2) {
            return CidrValidationResult.Invalid("CIDR格式错误，应为 '地址/前缀长度'")
        }
        
        val address = parts[0].trim()
        val prefixLengthStr = parts[1].trim()
        
        try {
            val ipAddress = InetAddress.getByName(address)
            val prefixLength = prefixLengthStr.toInt()
            
            when (ipAddress) {
                is Inet4Address -> {
                    if (prefixLength < 0 || prefixLength > 32) {
                        return CidrValidationResult.Invalid("IPv4前缀长度必须在0-32之间")
                    }
                }
                is Inet6Address -> {
                    if (prefixLength < 0 || prefixLength > 128) {
                        return CidrValidationResult.Invalid("IPv6前缀长度必须在0-128之间")
                    }
                }
            }
            
            return CidrValidationResult.Valid
            
        } catch (e: NumberFormatException) {
            return CidrValidationResult.Invalid("前缀长度必须是整数")
        } catch (e: Exception) {
            return CidrValidationResult.Invalid("无效的IP地址: ${e.message}")
        }
    }
    
    /**
     * 验证多行CIDR
     */
    fun validateCidrs(cidrs: String): CidrValidationResult {
        if (cidrs.isBlank()) {
            return CidrValidationResult.Invalid("CIDR列表不能为空")
        }
        
        val lines = cidrs.lines().filter { it.trim().isNotEmpty() }
        if (lines.isEmpty()) {
            return CidrValidationResult.Invalid("CIDR列表不能为空")
        }
        
        for ((index, line) in lines.withIndex()) {
            val result = validateCidr(line)
            if (result is CidrValidationResult.Invalid) {
                return CidrValidationResult.Invalid("第${index + 1}行: ${result.error}")
            }
        }
        
        return CidrValidationResult.Valid
    }
    
    /**
     * 解析CIDR为地址和前缀长度
     */
    fun parseCidr(cidr: String): Pair<String, Int>? {
        val result = validateCidr(cidr)
        if (result is CidrValidationResult.Invalid) return null
        
        val parts = cidr.trim().split("/")
        val address = parts[0].trim()
        val prefixLength = parts[1].trim().toInt()
        
        return Pair(address, prefixLength)
    }
    
    /**
     * 判断是否为IPv4 CIDR
     */
    fun isIPv4Cidr(cidr: String): Boolean {
        val parsed = parseCidr(cidr) ?: return false
        return try {
            val address = InetAddress.getByName(parsed.first)
            address is Inet4Address
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 判断是否为IPv6 CIDR
     */
    fun isIPv6Cidr(cidr: String): Boolean {
        val parsed = parseCidr(cidr) ?: return false
        return try {
            val address = InetAddress.getByName(parsed.first)
            address is Inet6Address
        } catch (e: Exception) {
            false
        }
    }
}

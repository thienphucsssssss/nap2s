package vn.twosmc.nap2s.security

import vn.twosmc.nap2s.config.SecureConfigManager
import vn.twosmc.nap2s.database.ConfigDAO
import vn.twosmc.nap2s.utils.Utils.Companion.info
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Audit Log — ghi lại mọi thao tác nhạy cảm của admin.
 * Lưu vào database (ConfigDAO) để admin không thể xóa bằng tay.
 *
 * Các thao tác ghi log:
 * - setkey / deletekey (thay đổi config bảo mật)
 * - napthucong (nạp thủ công)
 * - cleardata (xóa dữ liệu player)
 * - reload (reload config)
 */
object AuditLog {

    private const val DB_PREFIX = "audit_"
    private const val SECURE_DB_PREFIX = "secure_$DB_PREFIX"
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    /**
     * Ghi 1 dòng audit log vào database.
     * Key = audit_{timestamp}_{random}
     * Value = "[time] action by sender: details"
     */
    fun log(action: String, sender: String, details: String = "") {
        val timestamp = System.currentTimeMillis()
        val timeStr = formatter.format(Instant.ofEpochMilli(timestamp))
        val key = "$DB_PREFIX${timestamp}_${(Math.random() * 1000).toInt()}"
        val value = "[$timeStr] $action bởi $sender${if (details.isNotBlank()) ": $details" else ""}"

        try {
            SecureConfigManager.setSecure(key, value)
        } catch (e: Exception) {
            info("Không thể ghi audit log: ${e.message}")
        }
    }

    /**
     * Đọc audit log gần nhất (tối đa N dòng).
     */
    fun getRecent(limit: Int = 20): List<String> {
        return try {
            ConfigDAO.getInstance().getByPrefix(SECURE_DB_PREFIX)
                .take(limit)
                .mapNotNull { (key, _) -> SecureConfigManager.getSecure(key.removePrefix("secure_")) }
        } catch (e: Exception) {
            listOf("Lỗi đọc audit log: ${e.message}")
        }
    }
}

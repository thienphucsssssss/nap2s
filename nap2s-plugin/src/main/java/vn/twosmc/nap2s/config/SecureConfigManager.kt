package vn.twosmc.nap2s.config

import vn.twosmc.nap2s.Nap2S
import vn.twosmc.nap2s.database.ConfigDAO
import vn.twosmc.nap2s.utils.Utils.Companion.info
import vn.twosmc.nap2s.utils.Utils.Companion.warning
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Quản lý config nhạy cảm (API key, bank info) lưu trên Database.
 * Tránh staff đọc được file config trên server.
 *
 * Flow:
 * 1. Admin dùng /nap2s setkey → lưu encrypted vào DB
 * 2. Khi plugin load, đọc từ DB → decrypt → ghi đè lên config in-memory
 * 3. File config vẫn giữ nhưng DB luôn ưu tiên hơn
 */
object SecureConfigManager {

    // Prefix cho key trong ConfigDAO
    private const val DB_PREFIX = "secure_"

    // Key dùng để encrypt — dựa trên server name (không cần hoàn hảo, chỉ để
    // staff không đọc raw text trong DB)
    private fun getEncryptionKey(): ByteArray {
        val serverName = Nap2S.instance.config.server
        // Pad/truncate tới 16 bytes cho AES-128
        val padded = (serverName + "Nap2SSecretPad!!").toByteArray(Charsets.UTF_8)
        return padded.copyOf(16)
    }

    /**
     * Encrypt và lưu giá trị vào database
     */
    fun setSecure(key: String, value: String) {
        val encrypted = encrypt(value)
        ConfigDAO.getInstance().set("$DB_PREFIX$key", encrypted)
        info("Đã lưu config bảo mật: $key (encrypted)")
    }

    /**
     * Đọc giá trị từ database và decrypt
     * @return giá trị đã decrypt, hoặc null nếu không có
     */
    fun getSecure(key: String): String? {
        val encrypted = ConfigDAO.getInstance().get("$DB_PREFIX$key") ?: return null
        return try {
            decrypt(encrypted)
        } catch (e: Exception) {
            e.warning("Không thể decrypt config bảo mật: $key")
            null
        }
    }

    /**
     * Xóa config bảo mật khỏi DB
     */
    fun deleteSecure(key: String) {
        ConfigDAO.getInstance().delete("$DB_PREFIX$key")
    }

    /**
     * Đọc config: nếu có trong DB (bảo mật) thì dùng DB, không thì fallback file config
     */
    fun getOrFallback(key: String, fileValue: String): String {
        return getSecure(key)?.takeIf { it.isNotBlank() } ?: fileValue
    }

    /**
     * Đọc credential per-provider từ DB, fallback file config.
     * Key DB = "provider.<providerId>.<field>" (VD: "provider.provider-1.partner-key")
     *
     * @param providerId ID của provider (VD: "provider-1")
     * @param field Tên field (VD: "partner-key", "partner-id", "api-key", "api-secret")
     * @param fileValue Giá trị fallback từ file config
     */
    fun getProviderKey(providerId: String, field: String, fileValue: String): String {
        val dbKey = "provider.$providerId.$field"
        return getSecure(dbKey)?.takeIf { it.isNotBlank() } ?: fileValue
    }

    /**
     * Lưu credential per-provider vào DB (encrypted)
     */
    fun setProviderKey(providerId: String, field: String, value: String) {
        val dbKey = "provider.$providerId.$field"
        setSecure(dbKey, value)
    }

    /**
     * Xóa credential per-provider khỏi DB
     */
    fun deleteProviderKey(providerId: String, field: String) {
        val dbKey = "provider.$providerId.$field"
        deleteSecure(dbKey)
    }

    // === Danh sách các key được hỗ trợ lưu bảo mật ===

    /** Các key hiện đã được plugin dùng thật ở runtime */
    val SUPPORTED_KEYS = mapOf(
        // Banking
        "sepay-api-token" to "SePay API Token",
        "sepay-secret-key" to "SePay Webhook Secret Key",
        "bank-account-number" to "Số tài khoản ngân hàng",
        "bank-account-holder" to "Tên chủ tài khoản",
        "payos-client-id" to "PayOS Client ID",
        "payos-api-key" to "PayOS API Key",
        "payos-checksum-key" to "PayOS Checksum Key",
    )

    /** Các field provider thẻ hỗ trợ setkey */
    val PROVIDER_FIELDS = listOf(
        "partner-id",
        "partner-key",
        "api-key",
        "api-secret",
        "api-url",
    )

    // === Encrypt / Decrypt bằng AES ===

    private fun encrypt(plainText: String): String {
        val key = SecretKeySpec(getEncryptionKey(), "AES")
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(encrypted)
    }

    private fun decrypt(encryptedText: String): String {
        val key = SecretKeySpec(getEncryptionKey(), "AES")
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, key)
        val decrypted = cipher.doFinal(Base64.getDecoder().decode(encryptedText))
        return String(decrypted, Charsets.UTF_8)
    }
}

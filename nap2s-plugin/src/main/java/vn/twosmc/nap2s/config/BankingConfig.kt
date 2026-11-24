package vn.twosmc.nap2s.config

import vn.twosmc.nap2s.card.CardPrice

class BankingConfig : FileConfig("banking") {
    val enabled = config.getBoolean("enabled", false)
    val command = config.getString("command", "napatm")!!.trim()
    val providerId = config.getString("provider-id", "sepay")!!.trim()

    // Web server nhỏ đi kèm plugin để mở trang QR và nhận webhook.
    val webserverEnabled = config.getBoolean("webserver-enabled", true)
    val webserverPort = config.getInt("webserver-port", 8585)
    val webserverPublicUrl = config.getString("webserver-public-url", "")!!.trim().removeSuffix("/")

    // === SePay credentials (ưu tiên DB > file config) ===
    val sepayApiToken = SecureConfigManager.getOrFallback(
        "sepay-api-token", config.getString("sepay-api-token", "")!!.trim()
    )
    val sepaySecretKey = SecureConfigManager.getOrFallback(
        "sepay-secret-key", config.getString("sepay-secret-key", "")!!.trim()
    )
    val payosClientId = SecureConfigManager.getOrFallback(
        "payos-client-id", config.getString("payos-client-id", "")!!.trim()
    )
    val payosApiKey = SecureConfigManager.getOrFallback(
        "payos-api-key", config.getString("payos-api-key", "")!!.trim()
    )
    val payosChecksumKey = SecureConfigManager.getOrFallback(
        "payos-checksum-key", config.getString("payos-checksum-key", "")!!.trim()
    )

    // === Thông tin ngân hàng (ưu tiên DB > file config) ===
    val bankCode = config.getString("bank-code", "")!!.trim()
    val bankName = config.getString("bank-name", "")!!.trim()
    val bankAccountNumber = SecureConfigManager.getOrFallback(
        "bank-account-number", config.getString("bank-account-number", "")!!.trim()
    )
    val bankAccountHolder = SecureConfigManager.getOrFallback(
        "bank-account-holder", config.getString("bank-account-holder", "")!!.trim()
    )

    // Thiết lập trải nghiệm.
    val pollSeconds = config.getLong("poll-seconds", 10L)
    val cooldownSeconds = config.getLong("cooldown-seconds", 15L)
    val expiresMinutes = config.getInt("expires-minutes", 10)
    // Chỉ cho phép 1 đơn chờ / player. Multi-pending chưa hỗ trợ đầy đủ
    // (countdown, status, cancel, QR map đều giả định 1 đơn/player).
    val allowMultiplePending = false
    val allowedAmounts = config.getIntegerList("allowed-amounts")
        .map { it.toInt() }
        .takeIf { it.isNotEmpty() }
        ?: CardPrice.entries.map { it.value }

    val rewardBonusPercent = config.getInt("reward-bonus-percent", 20)

    val allowCustomAmount = config.getBoolean("allow-custom-amount", true)
    val minCustomAmount = config.getInt("min-custom-amount", 1000)
    val maxCustomAmount = config.getInt("max-custom-amount", 10000000)

    fun isValidCustomAmount(amount: Int): Boolean {
        if (!allowCustomAmount) return false
        if (amount < minCustomAmount || amount > maxCustomAmount) return false
        return amount % 1000 == 0
    }
}

package vn.twosmc.nap2s.banking

import vn.twosmc.nap2s.config.BankingConfig
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Hỗ trợ tạo dữ liệu QR ngay trong plugin.
 */
object SepayClient {

    /**
     * Tạo đường dẫn QR theo chuẩn VietQR.
     */
    fun generateVietQrUrl(config: BankingConfig, amount: Int, transferContent: String): String {
        val bankCode = config.bankCode
        val accountNo = config.bankAccountNumber
        val accountName = java.net.URLEncoder.encode(config.bankAccountHolder, StandardCharsets.UTF_8.name())
        val addInfo = java.net.URLEncoder.encode(transferContent, StandardCharsets.UTF_8.name())

        return "https://img.vietqr.io/image/$bankCode-$accountNo-qr_only.png" +
            "?amount=$amount&addInfo=$addInfo&accountName=$accountName"
    }

    /**
     * Tạo nội dung chuyển khoản riêng cho từng đơn.
     */
    fun generateTransferContent(orderId: String): String {
        return "NAP2S $orderId"
    }

    /**
     * Tạo mã đơn ngắn gọn.
     */
    fun generateOrderId(): String {
        val uuid = UUID.randomUUID().toString().replace("-", "").uppercase()
        return uuid.take(8)
    }

    /**
     * Tạo đơn mới ngay trong plugin.
     */
    fun createLocalOrder(config: BankingConfig, amount: Int): LocalOrderResult {
        val orderId = generateOrderId()
        val transferContent = generateTransferContent(orderId)
        val qrImageUrl = generateVietQrUrl(config, amount, transferContent)
        val expiresAt = System.currentTimeMillis() + (config.expiresMinutes * 60 * 1000L)
        val checkoutUrl = if (config.webserverPublicUrl.isNotBlank()) {
            "${config.webserverPublicUrl}/orders/$orderId"
        } else ""

        return LocalOrderResult(
            orderId = orderId,
            amount = amount,
            transferContent = transferContent,
            qrImageUrl = qrImageUrl,
            checkoutUrl = checkoutUrl,
            bankName = config.bankName,
            bankAccountNumber = config.bankAccountNumber,
            bankAccountHolder = config.bankAccountHolder,
            expiresAt = expiresAt
        )
    }

    data class LocalOrderResult(
        val orderId: String,
        val amount: Int,
        val transferContent: String,
        val qrImageUrl: String,
        val checkoutUrl: String,
        val bankName: String,
        val bankAccountNumber: String,
        val bankAccountHolder: String,
        val expiresAt: Long
    )
}

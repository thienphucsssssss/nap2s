package vn.twosmc.nap2s.banking

import com.google.gson.Gson
import com.google.gson.JsonParser
import vn.twosmc.nap2s.config.BankingConfig
import vn.twosmc.nap2s.config.SecureConfigManager
import vn.twosmc.nap2s.utils.Utils.Companion.info
import vn.twosmc.nap2s.utils.Utils.Companion.warning
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Client PayOS — tạo payment link và verify webhook.
 *
 * PayOS API:
 * - Base URL: https://api-merchant.payos.vn
 * - Create: POST /v2/payment-requests
 * - Headers: x-client-id, x-api-key
 * - Webhook: HMAC_SHA256 signature verify bằng checksum key
 */
object PayOSClient {

    private const val API_BASE = "https://api-merchant.payos.vn"

    /**
     * Tạo payment link qua PayOS API.
     * Trả về PayOS checkout URL + QR URL.
     */
    fun createPaymentLink(config: BankingConfig, amount: Int, orderId: String): PayOSOrderResult {
        val clientId = SecureConfigManager.getOrFallback(
            "payos-client-id", config.payosClientId
        )
        val apiKey = SecureConfigManager.getOrFallback(
            "payos-api-key", config.payosApiKey
        )
        val checksumKey = SecureConfigManager.getOrFallback(
            "payos-checksum-key", config.payosChecksumKey
        )

        // OrderCode: dùng epoch-based + random suffix thay vì hashCode() (32-bit, dễ collision)
        // PayOS yêu cầu int64 dương, unique. Format: last 10 digits of epoch millis + 4 random digits
        val epochPart = System.currentTimeMillis() % 10_000_000_000L
        val randomPart = (1000..9999).random()
        val orderCode = epochPart * 10_000 + randomPart
        val description = orderId.take(8)
        val returnUrl = if (config.webserverPublicUrl.isNotBlank()) {
            "${config.webserverPublicUrl}/orders/$orderId"
        } else "https://payos.vn"
        val cancelUrl = returnUrl

        // Tạo signature: HMAC_SHA256 "amount={amount}&cancelUrl={cancelUrl}&description={description}&orderCode={orderCode}&returnUrl={returnUrl}"
        val dataStr = "amount=$amount&cancelUrl=$cancelUrl&description=$description&orderCode=$orderCode&returnUrl=$returnUrl"
        val signature = hmacSHA256(checksumKey, dataStr)

        val body = mapOf(
            "orderCode" to orderCode,
            "amount" to amount,
            "description" to description,
            "buyerName" to "Nap2S Player",
            "returnUrl" to returnUrl,
            "cancelUrl" to cancelUrl,
            "signature" to signature
        )

        val conn = URL("$API_BASE/v2/payment-requests").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("x-client-id", clientId)
        conn.setRequestProperty("x-api-key", apiKey)
        conn.doOutput = true

        val jsonBody = Gson().toJson(body)
        OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { it.write(jsonBody) }

        val responseCode = conn.responseCode
        val responseBody = (if (responseCode in 200..299) conn.inputStream else conn.errorStream)
            .bufferedReader().readText()

        if (responseCode !in 200..299) {
            throw RuntimeException("PayOS API lỗi ($responseCode): $responseBody")
        }

        val json = JsonParser.parseString(responseBody).asJsonObject
        val code = json.get("code")?.asString ?: ""
        if (code != "00") {
            val desc = json.get("desc")?.asString ?: "Không rõ"
            throw RuntimeException("PayOS API trả lỗi: $desc")
        }

        val data = json.getAsJsonObject("data")
        val checkoutUrl = data.get("checkoutUrl")?.asString ?: ""
        val qrCode = data.get("qrCode")?.asString ?: ""
        val paymentLinkId = data.get("paymentLinkId")?.asString ?: ""
        val accountNumber = data.get("accountNumber")?.asString ?: ""
        val accountName = data.get("accountName")?.asString ?: ""
        val bin = data.get("bin")?.asString ?: ""

        info("PayOS: tạo payment link thành công — orderCode=$orderCode, paymentLinkId=$paymentLinkId")

        val expiresAt = System.currentTimeMillis() + (config.expiresMinutes * 60 * 1000L)

        return PayOSOrderResult(
            orderId = orderId,
            orderCode = orderCode,
            amount = amount,
            transferContent = description,
            checkoutUrl = checkoutUrl,
            qrImageUrl = buildQrRenderUrl(qrCode),
            paymentLinkId = paymentLinkId,
            bankName = if (bin.isNotBlank()) "PayOS ($bin)" else "PayOS",
            bankAccountNumber = accountNumber,
            bankAccountHolder = accountName,
            expiresAt = expiresAt
        )
    }

    /**
     * Xác nhận webhook URL với PayOS để hệ thống gửi IPN về đúng endpoint của plugin.
     */
    fun confirmWebhook(config: BankingConfig): Boolean {
        val clientId = SecureConfigManager.getOrFallback(
            "payos-client-id", config.payosClientId
        )
        val apiKey = SecureConfigManager.getOrFallback(
            "payos-api-key", config.payosApiKey
        )
        val webhookUrl = config.webserverPublicUrl.trim().removeSuffix("/")
            .takeIf { it.isNotBlank() }
            ?.plus("/nap2s/ipn/payos")
            ?: throw IllegalArgumentException("webserver-public-url đang trống, không thể xác nhận webhook PayOS")

        val conn = URL("$API_BASE/confirm-webhook").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("x-client-id", clientId)
        conn.setRequestProperty("x-api-key", apiKey)
        conn.doOutput = true

        val jsonBody = Gson().toJson(mapOf("webhookUrl" to webhookUrl))
        OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { it.write(jsonBody) }

        val responseCode = conn.responseCode
        val responseBody = (if (responseCode in 200..299) conn.inputStream else conn.errorStream)
            .bufferedReader().readText()

        if (responseCode !in 200..299) {
            throw RuntimeException("PayOS confirm-webhook lỗi ($responseCode): $responseBody")
        }

        val json = JsonParser.parseString(responseBody).asJsonObject
        val code = json.get("code")?.asString ?: ""
        if (code != "00") {
            val desc = json.get("desc")?.asString ?: "Khong ro"
            throw RuntimeException("PayOS confirm-webhook that bai: $desc")
        }

        info("PayOS: xac nhan webhook thanh cong -> $webhookUrl")
        return true
    }

    /**
     * Verify webhook signature từ PayOS.
     * PayOS gửi webhook data + signature, verify bằng HMAC_SHA256 với checksum key.
     */
    fun verifyWebhookSignature(data: String, signature: String, checksumKey: String): Boolean {
        val computed = hmacSHA256(checksumKey, data)
        return computed.equals(signature, ignoreCase = true)
    }

    /**
     * HMAC SHA256
     */
    private fun hmacSHA256(key: String, data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        val hash = mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun buildQrRenderUrl(qrCode: String): String {
        if (qrCode.isBlank()) return ""
        val encoded = URLEncoder.encode(qrCode, StandardCharsets.UTF_8.name())
        return "https://quickchart.io/qr?size=320&text=$encoded"
    }

    data class PayOSOrderResult(
        val orderId: String,
        val orderCode: Long,
        val amount: Int,
        val transferContent: String,
        val checkoutUrl: String,
        val qrImageUrl: String,
        val paymentLinkId: String,
        val bankName: String,
        val bankAccountNumber: String,
        val bankAccountHolder: String,
        val expiresAt: Long
    )
}

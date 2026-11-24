package vn.twosmc.nap2s.webserver

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import fi.iki.elonen.NanoHTTPD
import vn.twosmc.nap2s.Nap2S
import vn.twosmc.nap2s.banking.BankingService
import vn.twosmc.nap2s.banking.PayOSClient
import vn.twosmc.nap2s.config.SecureConfigManager
import vn.twosmc.nap2s.database.BankingOrderDAO
import vn.twosmc.nap2s.utils.Utils.Companion.info
import vn.twosmc.nap2s.utils.Utils.Companion.warning
import java.nio.charset.StandardCharsets

/**
 * Xử lý webhook IPN từ PayOS.
 * PayOS gửi JSON chứa data + signature (HMAC_SHA256).
 * Verify signature -> tìm order -> đánh dấu PAID -> phát xu.
 */
class PayOSWebhookHandler {

    fun handle(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val body = readRequestBody(session)

        if (body.isBlank()) {
            return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "application/json",
                """{"success":false,"message":"Empty body"}"""
            )
        }

        return try {
            val json = JsonParser.parseString(body).asJsonObject
            info("Nhận webhook PayOS: $body")

            val code = json.get("code")?.asString ?: ""
            val signature = json.get("signature")?.asString ?: ""
            val data = json.getAsJsonObject("data") ?: run {
                return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.BAD_REQUEST,
                    "application/json",
                    """{"success":false,"message":"Missing data"}"""
                )
            }

            val config = Nap2S.instance.banking
            val checksumKey = SecureConfigManager.getOrFallback(
                "payos-checksum-key", config.payosChecksumKey
            )

            if (checksumKey.isNotBlank()) {
                val sortedData = buildSignatureData(data)
                if (!PayOSClient.verifyWebhookSignature(sortedData, signature, checksumKey)) {
                    warning("PayOS webhook: signature không hợp lệ!")
                    warning("PayOS webhook payload verify string: $sortedData")
                    return NanoHTTPD.newFixedLengthResponse(
                        NanoHTTPD.Response.Status.FORBIDDEN,
                        "application/json",
                        """{"success":false,"message":"Invalid signature"}"""
                    )
                }
            }

            val orderCode = data.get("orderCode")?.asLong ?: 0L
            val amount = data.get("amount")?.asLong ?: 0L
            val description = data.get("description")?.asString ?: ""
            val paymentLinkId = data.get("paymentLinkId")?.asString ?: ""

            if (code != "00") {
                info("PayOS webhook: code=$code, bỏ qua (chưa thành công)")
                return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.OK,
                    "application/json",
                    """{"success":true,"message":"Ignored non-success code"}"""
                )
            }

            val dao = BankingOrderDAO.getInstance()
            val pendingOrders = dao.getPendingOrders(Nap2S.instance.config.server)
            val matchedOrder = pendingOrders.find { order ->
                description.equals(order.transferContent, ignoreCase = true)
                    || description.equals(order.orderId, ignoreCase = true)
            }

            if (matchedOrder == null) {
                info("PayOS webhook: không tìm thấy order khớp — orderCode=$orderCode, description='$description'")
                return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.OK,
                    "application/json",
                    """{"success":true,"message":"No matching order found"}"""
                )
            }

            if (amount != matchedOrder.amount.toLong()) {
                info("PayOS webhook: số tiền không khớp — nhận $amount, cần ${matchedOrder.amount}")
                dao.updatePendingStatus(matchedOrder.orderId, "AMOUNT_MISMATCH", body)
                return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.OK,
                    "application/json",
                    """{"success":true,"message":"Amount mismatch"}"""
                )
            }

            val transactionId = paymentLinkId.ifBlank { matchedOrder.orderId }
            BankingService.handleWebhookPaid(matchedOrder, transactionId, body)

            info("PayOS webhook: order ${matchedOrder.orderId} thanh toán thành công!")

            NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK,
                "application/json",
                """{"success":true,"message":"Order paid"}"""
            )
        } catch (e: Exception) {
            e.warning("Lỗi xử lý webhook PayOS")
            NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.INTERNAL_ERROR,
                "application/json",
                """{"success":false,"message":"${e.message}"}"""
            )
        }
    }

    private fun readRequestBody(session: NanoHTTPD.IHTTPSession): String {
        val contentLength = session.headers["content-length"]?.toIntOrNull()
        return if (contentLength != null && contentLength > 0) {
            String(session.inputStream.readNBytes(contentLength), StandardCharsets.UTF_8)
        } else {
            session.inputStream.bufferedReader(StandardCharsets.UTF_8).readText()
        }
    }

    private fun buildSignatureData(data: com.google.gson.JsonObject): String {
        return data.entrySet()
            .sortedBy { it.key }
            .joinToString("&") { "${it.key}=${toSignatureValue(it.value)}" }
    }

    private fun toSignatureValue(value: JsonElement): String {
        if (value.isJsonNull) return ""
        if (value.isJsonPrimitive) {
            val primitive = value.asJsonPrimitive
            return if (primitive.isString) primitive.asString else primitive.toString()
        }
        return value.toString()
    }
}

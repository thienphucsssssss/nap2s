package vn.twosmc.nap2s.webserver

import com.google.gson.JsonParser
import fi.iki.elonen.NanoHTTPD
import vn.twosmc.nap2s.Nap2S
import vn.twosmc.nap2s.banking.BankingService
import vn.twosmc.nap2s.database.BankingOrderDAO
import vn.twosmc.nap2s.utils.Utils.Companion.info
import vn.twosmc.nap2s.utils.Utils.Companion.warning

/**
 * Xử lý webhook IPN từ SePay khi có giao dịch chuyển khoản vào.
 * Parse JSON body → tìm order khớp transfer_content → đánh dấu PAID → phát xu.
 */
class WebhookHandler {

    fun handle(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        // Đọc request body
        val bodyMap = HashMap<String, String>()
        session.parseBody(bodyMap)
        val body = bodyMap["postData"] ?: ""

        if (body.isBlank()) {
            return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "application/json",
                """{"success":false,"message":"Empty body"}"""
            )
        }

        // Verify secret key nếu có cấu hình
        val config = Nap2S.instance.banking
        val secretKey = config.sepaySecretKey
        if (secretKey.isNotBlank()) {
            val headerKey = session.headers["x-secret-key"]
                ?: session.headers["x-nap2s-ipn-key"]
                ?: ""
            if (headerKey != secretKey) {
                return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.FORBIDDEN,
                    "application/json",
                    """{"success":false,"message":"Invalid secret key"}"""
                )
            }
        }

        return try {
            val json = JsonParser.parseString(body).asJsonObject
            info("Nhận webhook SePay: $body")

            // SePay gửi thông tin giao dịch
            val transferAmount = json.get("transferAmount")?.asLong ?: 0L
            val content = json.get("content")?.asString ?: ""
            val transferType = json.get("transferType")?.asString ?: ""

            // Chỉ xử lý tiền VÀO
            if (transferType == "out") {
                return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.OK,
                    "application/json",
                    """{"success":true,"message":"Ignored outbound transfer"}"""
                )
            }

            // Tìm order khớp với nội dung chuyển khoản
            val dao = BankingOrderDAO.getInstance()
            val pendingOrders = dao.getPendingOrders(Nap2S.instance.config.server)
            val matchedOrder = pendingOrders.find { order ->
                content.uppercase().contains(order.transferContent.uppercase())
            }

            if (matchedOrder == null) {
                info("Webhook SePay: không tìm thấy order khớp nội dung '$content'")
                return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.OK,
                    "application/json",
                    """{"success":true,"message":"No matching order found"}"""
                )
            }

            // Kiểm tra số tiền
            if (transferAmount < matchedOrder.amount) {
                info("Webhook SePay: số tiền không khớp — nhận $transferAmount, cần ${matchedOrder.amount}")
                dao.updatePendingStatus(matchedOrder.orderId, "AMOUNT_MISMATCH", body)
                return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.OK,
                    "application/json",
                    """{"success":true,"message":"Amount mismatch"}"""
                )
            }

            // Xử lý thanh toán thành công
            val transactionId = json.get("referenceCode")?.asString
                ?: json.get("id")?.asString
                ?: matchedOrder.orderId
            BankingService.handleWebhookPaid(matchedOrder, transactionId, body)

            info("Webhook SePay: order ${matchedOrder.orderId} thanh toán thành công!")

            NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK,
                "application/json",
                """{"success":true,"message":"Order paid"}"""
            )
        } catch (e: Exception) {
            e.warning("Lỗi xử lý webhook SePay")
            NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.INTERNAL_ERROR,
                "application/json",
                """{"success":false,"message":"${e.message}"}"""
            )
        }
    }
}

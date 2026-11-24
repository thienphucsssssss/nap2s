package vn.twosmc.nap2s.utils

import vn.twosmc.nap2s.Nap2S
import vn.twosmc.nap2s.utils.Utils.Companion.info
import vn.twosmc.nap2s.utils.Utils.Companion.warning

/**
 * Kiểm tra config khi plugin khởi chạy.
 * Log rõ ràng lỗi thiếu/sai để admin biết phải sửa gì.
 */
object ConfigValidator {

    /**
     * Chạy tất cả kiểm tra. Gọi sau khi load config xong.
     * Trả true nếu không có lỗi nghiêm trọng.
     */
    fun validate(): Boolean {
        var ok = true
        val config = Nap2S.instance.config

        // Kiểm tra server name
        if (config.server.isBlank()) {
            warning("[Config] 'server' đang trống trong config.yml. Hãy đặt tên server (VD: 'survival').")
            ok = false
        }

        // Kiểm tra point-unit
        if (config.pointUnit.isBlank()) {
            warning("[Config] 'point-unit' đang trống. Mặc định sẽ dùng 'point'.")
        }

        // Kiểm tra donate-amounts
        val amounts = config.amounts
        if (amounts.values.all { it == 0 }) {
            warning("[Config] Tất cả mệnh giá trong 'donate-amounts' đều = 0. Người chơi sẽ không nhận được xu.")
            ok = false
        }

        // Kiểm tra database engine + network mode
        val engine = config.config.getString("database.engine", "h2")!!.lowercase()
        val networkMode = config.config.getBoolean("network-mode", false)
        if (networkMode && engine == "h2") {
            warning("[Config] network-mode bật nhưng đang dùng H2. Cần đổi sang mysql hoặc mariadb.")
        }

        // Banking config check
        try {
            val banking = Nap2S.instance.banking
            val bankingEnabled = banking.config.getBoolean("enabled", false)
            if (bankingEnabled) {
                val providerId = banking.config.getString("provider-id", "")
                if (providerId.isNullOrBlank()) {
                    warning("[Banking] 'enabled' = true nhưng 'provider-id' trống. Nạp ATM/QR sẽ không hoạt động.")
                }

                val webUrl = banking.config.getString("webserver-public-url", "")
                if (webUrl.isNullOrBlank()) {
                    warning("[Banking] 'webserver-public-url' trống. Webhook IPN sẽ không nhận được callback.")
                }

                // Check SePay credentials
                if (providerId.equals("sepay", ignoreCase = true)) {
                    if (banking.bankAccountNumber.isBlank()) {
                        warning("[Banking/SePay] 'bank-account-number' trống. QR code sẽ không chứa STK nhận tiền.")
                    }
                    if (banking.bankAccountHolder.isBlank()) {
                        warning("[Banking/SePay] 'bank-account-holder' trống. Trang thanh toán sẽ thiếu tên chủ TK.")
                    }
                    if (banking.bankCode.isBlank()) {
                        warning("[Banking/SePay] 'bank-code' trống. Không thể tạo QR VietQR.")
                    }
                }

                // Check PayOS credentials
                if (providerId.equals("payos", ignoreCase = true)) {
                    if (banking.payosClientId.isBlank()) {
                        warning("[Banking/PayOS] 'payos-client-id' trống. Không thể gọi PayOS API.")
                        ok = false
                    }
                    if (banking.payosApiKey.isBlank()) {
                        warning("[Banking/PayOS] 'payos-api-key' trống. Không thể gọi PayOS API.")
                        ok = false
                    }
                    if (banking.payosChecksumKey.isBlank()) {
                        warning("[Banking/PayOS] 'payos-checksum-key' trống. Không thể verify webhook.")
                        ok = false
                    }
                }
            }
        } catch (_: Exception) {
            // Banking config chưa init — bỏ qua
        }

        if (ok) {
            info("[Config] Kiểm tra cấu hình: OK")
        } else {
            warning("[Config] Có lỗi cấu hình — xem log phía trên để biết chi tiết.")
        }

        return ok
    }
}

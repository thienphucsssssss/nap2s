package vn.twosmc.nap2s.webserver

import fi.iki.elonen.NanoHTTPD
import vn.twosmc.nap2s.Nap2S
import vn.twosmc.nap2s.database.BankingOrderDAO

/**
 * Serve trang thanh toán QR cho người chơi mở trên trình duyệt.
 * Hiển thị QR code, thông tin chuyển khoản, countdown và tự làm mới trạng thái.
 */
class PaymentPageHandler {

    fun handle(orderId: String): NanoHTTPD.Response {
        val order = BankingOrderDAO.getInstance().findByOrderId(orderId)
            ?: return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.NOT_FOUND,
                "text/html; charset=utf-8",
                "<h1>Không tìm thấy đơn hàng</h1>"
            )

        val config = Nap2S.instance.banking
        val status = order.status.uppercase()
        val isPaid = status == "PAID"
        val isExpired = status in listOf("EXPIRED", "CANCELLED", "FAILED", "AMOUNT_MISMATCH")
        val isPayOS = order.gateway.equals("payos", true)

        // Ưu tiên snapshot từ đơn, fallback config (cho đơn cũ chưa có snapshot)
        val displayBankName = order.bankName.ifBlank { if (isPayOS) "PayOS" else config.bankName }
        val displayBankAccNum = order.bankAccountNumber.ifBlank { if (isPayOS) "" else config.bankAccountNumber }
        val displayBankAccHolder = order.bankAccountHolder.ifBlank { if (isPayOS) "" else config.bankAccountHolder }

        val accountSection = if (!isPayOS) {
            """
    <div class="info-row">
        <span class="info-label">Ngân hàng</span>
        <span class="info-value">${displayBankName}</span>
    </div>
    <div class="info-row">
        <span class="info-label">Số tài khoản</span>
        <span class="info-value">
            ${displayBankAccNum}
            <button class="copy-btn" onclick="copy('${displayBankAccNum}')">Copy</button>
        </span>
    </div>
    <div class="info-row">
        <span class="info-label">Chủ tài khoản</span>
        <span class="info-value">${displayBankAccHolder}</span>
    </div>
            """.trimIndent()
        } else {
            """
    <div class="info-row">
        <span class="info-label">Cổng thanh toán</span>
        <span class="info-value">PayOS</span>
    </div>
    <div class="info-row">
        <span class="info-label">Mã đơn</span>
        <span class="info-value">${order.orderId}</span>
    </div>
            """.trimIndent()
        }

        val html = """
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Nạp tiền - ${order.orderId}</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            font-family: 'Segoe UI', system-ui, sans-serif;
            background: linear-gradient(135deg, #0f0c29, #302b63, #24243e);
            color: #fff; min-height: 100vh;
            display: flex; justify-content: center; align-items: center;
            padding: 20px;
        }
        .card {
            background: rgba(255,255,255,0.08);
            backdrop-filter: blur(20px);
            border-radius: 20px; padding: 30px;
            max-width: 420px; width: 100%;
            box-shadow: 0 8px 32px rgba(0,0,0,0.3);
            border: 1px solid rgba(255,255,255,0.1);
        }
        .header { text-align: center; margin-bottom: 20px; }
        .header h1 { font-size: 1.4em; color: #ffd700; }
        .header .subtitle { color: #aaa; font-size: 0.85em; margin-top: 5px; }
        .qr-container { text-align: center; margin: 15px 0; }
        .qr-container img { width: 200px; height: 200px; border-radius: 10px; background: #fff; padding: 8px; }
        .info-row {
            display: flex; justify-content: space-between; align-items: center;
            padding: 10px 0; border-bottom: 1px solid rgba(255,255,255,0.1);
        }
        .info-label { color: #aaa; font-size: 0.85em; }
        .info-value { font-weight: 600; color: #fff; font-size: 0.95em; }
        .info-value.highlight { color: #ffd700; font-size: 1.1em; }
        .copy-btn {
            background: rgba(255,215,0,0.2); border: 1px solid #ffd700;
            color: #ffd700; padding: 4px 10px; border-radius: 6px;
            cursor: pointer; font-size: 0.8em; margin-left: 8px;
        }
        .copy-btn:hover { background: rgba(255,215,0,0.4); }
        .countdown {
            text-align: center; margin-top: 15px; padding: 12px;
            background: rgba(255,215,0,0.1); border-radius: 10px;
            font-size: 1.2em; color: #ffd700;
        }
        .status-paid {
            text-align: center; padding: 15px; margin-top: 15px;
            background: rgba(0,255,0,0.15); border-radius: 10px;
            color: #4ade80; font-size: 1.1em; font-weight: 700;
        }
        .status-expired {
            text-align: center; padding: 15px; margin-top: 15px;
            background: rgba(255,0,0,0.15); border-radius: 10px;
            color: #f87171; font-size: 1.1em;
        }
        .warning {
            text-align: center; margin-top: 10px; padding: 8px;
            background: rgba(255,50,50,0.15); border-radius: 8px;
            color: #ff6b6b; font-size: 0.85em;
        }
    </style>
</head>
<body>
<div class="card">
    <div class="header">
        <h1>⚡ Nạp Tiền Nap2S</h1>
        <div class="subtitle">Mã đơn: ${order.orderId}</div>
    </div>

    ${if (!isPaid && !isExpired && order.qrImageUrl.isNotBlank()) """
    <div class="qr-container">
        <img src="${order.qrImageUrl}" alt="QR Code" />
    </div>
    """ else ""}

$accountSection
    <div class="info-row">
        <span class="info-label">Số tiền</span>
        <span class="info-value highlight">${"%,d".format(order.amount)} VNĐ</span>
    </div>
    <div class="info-row">
        <span class="info-label">Nội dung CK</span>
        <span class="info-value highlight">
            ${order.transferContent}
            <button class="copy-btn" onclick="copy('${order.transferContent}')">Copy</button>
        </span>
    </div>

    <div class="warning">⚠️ QUAN TRỌNG: Nhập đúng nội dung chuyển khoản!</div>

    ${if (isPaid) """
    <div class="status-paid">✅ Đã thanh toán thành công!</div>
    """ else if (isExpired) """
    <div class="status-expired">❌ Đơn hàng đã $status</div>
    """ else """
    <div class="countdown" id="countdown">⏳ Đang tính...</div>
    <script>
        var expiresAt = ${order.expiresAt};
        function updateCountdown() {
            var remain = expiresAt - Date.now();
            if (remain <= 0) {
                document.getElementById('countdown').textContent = '⏰ Hết hạn';
                location.reload();
                return;
            }
            var m = Math.floor(remain / 60000);
            var s = Math.floor((remain % 60000) / 1000);
            document.getElementById('countdown').textContent = '⏳ ' + m + ':' + String(s).padStart(2, '0');
        }
        setInterval(updateCountdown, 1000);
        updateCountdown();
        setInterval(function() { location.reload(); }, 5000);
    </script>
    """}
</div>
<script>
function copy(text) {
    navigator.clipboard.writeText(text).then(function() {
        alert('Đã copy: ' + text);
    });
}
</script>
</body>
</html>
        """.trimIndent()

        return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.OK,
            "text/html; charset=utf-8",
            html
        )
    }
}

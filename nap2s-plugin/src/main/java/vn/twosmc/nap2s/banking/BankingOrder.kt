package vn.twosmc.nap2s.banking

data class BankingOrder(
    val id: Int,
    val orderId: String,
    val uuid: String,
    val playerName: String,
    val amount: Int,
    val pointAmount: Int,
    val status: String,
    val checkoutUrl: String,
    val qrImageUrl: String,
    val transferContent: String,
    val transactionId: String,
    val gateway: String,
    val rawResponse: String,
    val server: String,
    val expiresAt: Long,
    val paidAt: Long,
    val rewarded: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    // Snapshot thông tin tài khoản nhận tiền tại thời điểm tạo đơn
    val bankName: String = "",
    val bankAccountNumber: String = "",
    val bankAccountHolder: String = ""
) {
    fun isPending() = status.equals("PENDING", true)
}

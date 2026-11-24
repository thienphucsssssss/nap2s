package vn.twosmc.nap2s.card

class CardWaiting(
    val id: Int,
    val uuid: String,
    val seri: String,
    val pin: String = "",
    val type: String,
    val price: Int,
    val transactionId: String,
    val providerId: String = ""
) {
    var isSuccess: Boolean = false
    var message: String? = null

    /**
     * Chuyển đổi CardWaiting thành Card, throw lỗi rõ ràng nếu data không hợp lệ.
     */
    fun toCard(): Card {
        val cardPrice = CardPrice[price]
            ?: throw IllegalStateException("Mệnh giá $price không hợp lệ (waiting id: $id)")
        val cardType = CardType[type]
            ?: throw IllegalStateException("Loại thẻ '$type' không hợp lệ (waiting id: $id)")
        return Card(seri, pin, cardPrice, cardType).apply {
            logId = id
            providerId = this@CardWaiting.providerId
        }
    }
}

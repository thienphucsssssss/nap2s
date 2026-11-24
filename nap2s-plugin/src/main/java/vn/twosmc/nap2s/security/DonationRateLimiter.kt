package vn.twosmc.nap2s.security

import vn.twosmc.nap2s.Nap2S
import vn.twosmc.nap2s.database.ConfigDAO
import vn.twosmc.nap2s.utils.Utils.Companion.info
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Giới hạn số lần/số tiền nạp mỗi ngày per player.
 * Tránh abuse (spam thẻ, chuyển khoản bất thường).
 *
 * Config trong config.yml:
 *   rate-limit:
 *     max-card-per-day: 20          # Số lần nạp thẻ tối đa / ngày
 *     max-banking-per-day: 10       # Số lần nạp ATM/QR tối đa / ngày
 *     max-total-amount-per-day: 50000000  # Tổng tiền tối đa / ngày (50 triệu)
 */
object DonationRateLimiter {

    // In-memory counters — reset khi plugin reload hoặc ngày mới
    private val cardCounters = ConcurrentHashMap<String, Int>()     // uuid_date → count
    private val bankingCounters = ConcurrentHashMap<String, Int>()
    private val amountCounters = ConcurrentHashMap<String, Long>()

    private var maxCardPerDay = 20
    private var maxBankingPerDay = 10
    private var maxTotalAmountPerDay = 50_000_000L

    /**
     * Load cấu hình từ config.yml
     */
    fun init() {
        val config = Nap2S.instance.config.config
        maxCardPerDay = config.getInt("rate-limit.max-card-per-day", 20)
        maxBankingPerDay = config.getInt("rate-limit.max-banking-per-day", 10)
        maxTotalAmountPerDay = config.getLong("rate-limit.max-total-amount-per-day", 50_000_000L)

        // Reset counters khi reload
        cardCounters.clear()
        bankingCounters.clear()
        amountCounters.clear()
    }

    private fun todayKey(uuid: UUID): String {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        return "${uuid}_$today"
    }

    /**
     * Kiểm tra xem player có thể nạp thẻ không.
     * @return null nếu OK, hoặc message lý do bị block
     */
    fun checkCardLimit(uuid: UUID): String? {
        val key = todayKey(uuid)
        val count = cardCounters.getOrDefault(key, 0)
        if (count >= maxCardPerDay) {
            return "§cBạn đã đạt giới hạn nạp thẻ hôm nay ($maxCardPerDay lần). Vui lòng thử lại vào ngày mai."
        }
        return null
    }

    /**
     * Kiểm tra xem player có thể nạp ATM/QR không.
     * @return null nếu OK, hoặc message lý do bị block
     */
    fun checkBankingLimit(uuid: UUID): String? {
        val key = todayKey(uuid)
        val count = bankingCounters.getOrDefault(key, 0)
        if (count >= maxBankingPerDay) {
            return "§cBạn đã đạt giới hạn nạp chuyển khoản hôm nay ($maxBankingPerDay lần). Vui lòng thử lại vào ngày mai."
        }
        return null
    }

    /**
     * Kiểm tra tổng tiền nạp trong ngày.
     * @return null nếu OK, hoặc message lý do bị block
     */
    fun checkAmountLimit(uuid: UUID, amount: Int): String? {
        val key = todayKey(uuid)
        val current = amountCounters.getOrDefault(key, 0L)
        if (current + amount > maxTotalAmountPerDay) {
            val remaining = (maxTotalAmountPerDay - current).coerceAtLeast(0)
            return "§cBạn đã gần đạt giới hạn nạp hôm nay. Còn lại: ${remaining}đ."
        }
        return null
    }

    /**
     * Ghi nhận nạp thẻ thành công (tăng counter).
     */
    fun recordCard(uuid: UUID, amount: Int) {
        val key = todayKey(uuid)
        cardCounters.merge(key, 1, Int::plus)
        amountCounters.merge(key, amount.toLong(), Long::plus)
    }

    /**
     * Ghi nhận nạp ATM/QR thành công (tăng counter).
     */
    fun recordBanking(uuid: UUID, amount: Int) {
        val key = todayKey(uuid)
        bankingCounters.merge(key, 1, Int::plus)
        amountCounters.merge(key, amount.toLong(), Long::plus)
    }
}

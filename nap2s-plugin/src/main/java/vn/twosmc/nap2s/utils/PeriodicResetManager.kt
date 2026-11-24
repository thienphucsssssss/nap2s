package vn.twosmc.nap2s.utils

import vn.twosmc.nap2s.Nap2S
import vn.twosmc.nap2s.database.ConfigDAO
import vn.twosmc.nap2s.database.PlayerDataDAO
import vn.twosmc.nap2s.utils.Utils.Companion.info
import vn.twosmc.nap2s.utils.Utils.Companion.runAsyncTimer
import vn.twosmc.nap2s.utils.Utils.Companion.warning
import org.bukkit.scheduler.BukkitTask
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/**
 * Quản lý reset data mốc nạp theo chu kỳ (ngày/tuần/tháng).
 * Khi bắt đầu chu kỳ mới → xóa data cũ của WEEK/MONTH/DAILY keys.
 * Lưu marker chu kỳ hiện tại trong ConfigDAO để tránh reset trùng.
 */
object PeriodicResetManager {

    private const val KEY_LAST_DAILY = "milestone_last_daily"
    private const val KEY_LAST_WEEKLY = "milestone_last_weekly"
    private const val KEY_LAST_MONTHLY = "milestone_last_monthly"

    private var task: BukkitTask? = null

    /**
     * Khởi động: check reset ngay lập tức, sau đó check mỗi 5 phút
     */
    fun init() {
        stop()
        try {
            checkAndReset()
        } catch (e: Exception) {
            e.warning("Lỗi khi kiểm tra reset mốc nạp lúc khởi động")
        }
        // Check mỗi 5 phút (20 ticks/s * 60s * 5 = 6000 ticks)
        task = runAsyncTimer(20L * 10, 20L * 60 * 5) {
            try {
                checkAndReset()
            } catch (e: Exception) {
                e.warning("Lỗi khi reset mốc nạp theo chu kỳ")
            }
        }
    }

    fun stop() {
        task?.cancel()
        task = null
    }

    /**
     * Kiểm tra và reset data nếu đã sang chu kỳ mới
     */
    private fun checkAndReset() {
        val configDao = ConfigDAO.getInstance()
        val dataDao = PlayerDataDAO.getInstance()
        val today = LocalDate.now()

        // === Reset DAILY ===
        val todayStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE) // "2024-03-24"
        val lastDaily = configDao.get(KEY_LAST_DAILY)
        if (lastDaily != todayStr) {
            info("Mốc nạp ngày: reset data (ngày mới: $todayStr)")
            dataDao.deleteByKeyPattern("_DAILY")
            configDao.set(KEY_LAST_DAILY, todayStr)
        }

        // === Reset WEEKLY (mỗi thứ 2) ===
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekStr = weekStart.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val lastWeekly = configDao.get(KEY_LAST_WEEKLY)
        if (lastWeekly != weekStr) {
            info("Mốc nạp tuần: reset data (tuần mới bắt đầu: $weekStr)")
            dataDao.deleteByKeyPattern("_WEEK")
            configDao.set(KEY_LAST_WEEKLY, weekStr)
        }

        // === Reset MONTHLY (ngày 1 hàng tháng) ===
        val monthStr = today.format(DateTimeFormatter.ofPattern("yyyy-MM"))
        val lastMonthly = configDao.get(KEY_LAST_MONTHLY)
        if (lastMonthly != monthStr) {
            info("Mốc nạp tháng: reset data (tháng mới: $monthStr)")
            dataDao.deleteByKeyPattern("_MONTH")
            configDao.set(KEY_LAST_MONTHLY, monthStr)
        }
    }
}

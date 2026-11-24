package vn.twosmc.nap2s.config

import vn.twosmc.nap2s.TOP_KEY_DONATE_TOTAL
import vn.twosmc.nap2s.database.PlayerDataDAO
import vn.twosmc.nap2s.utils.BukkitBossBar
import vn.twosmc.nap2s.utils.Utils.Companion.format
import vn.twosmc.nap2s.utils.Utils.Companion.info
import vn.twosmc.nap2s.utils.Utils.Companion.runAsyncTimer
import vn.twosmc.nap2s.utils.Utils.Companion.runSync
import vn.twosmc.nap2s.utils.Utils.Companion.warning
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask

class Milestones : FileConfig("mocnap") {

    private var components: List<Component> = emptyList()

    init {
        loadComponents()
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadComponents() {
        components = (config.getList("mocnap") ?: emptyList()).map {
            try {
                it as Map<*, *>
                val type = it["type"]

                if (type !in listOf("all", "week", "month", "daily")) {
                    warning("Loại mốc nạp \"$type\" không hợp lệ. Chỉ chấp nhận all, week, month, daily")
                    return@map null
                }

                Component(it["type"] as String, it["amount"] as Int, it["commands"] as List<String>)
            } catch (e: Exception) {
                e.warning("Có một mốc nạp không hợp lệ, hãy liên hệ developer để được hỗ trợ")
                null
            }
        }.filterNotNull()

        info("Đã nạp ${components.size} mốc nạp.")
    }

    override fun reload() {
        super.reload()
        loadComponents()
    }

    fun getAll() = components.toList()

    class Component(val type: String, val amount: Int, val commands: List<String>, val bossBar: String? = null,
                    val from: Int = 0, barColor: String = "GREEN", barStyle: String = "SEGMENTED_10") {

        var bar: BukkitBossBar? = null
        var barTask: BukkitTask? = null; private set

        init {
            if (bossBar != null) {
                bar = BukkitBossBar("§r", barColor, barStyle).apply {
                    isVisible = false
                    barTask = runAsyncTimer(0, 20 * 5) {
                        val current = PlayerDataDAO.getInstance()
                            .getSumData("${TOP_KEY_DONATE_TOTAL}_${type.uppercase()}")
                        if (current in from until amount) {
                            if (!isVisible) {
                                isVisible = true
                                runSync { Bukkit.getOnlinePlayers().forEach { addPlayer(it) } }
                            }
                            val title = bossBar
                                .replace("%CURRENT%", current.format())
                                .replace("%TARGET%", amount.format())
                            progress = current.toDouble() / amount
                            setTitle(title)
                        } else {
                            if (isVisible) {
                                removeAll()
                                isVisible = false
                            }
                        }
                    }
                }
            }
        }

        private val typeName = when (type) {
            "all" -> "toàn thời gian"
            "week" -> "tuần"
            "month" -> "tháng"
            "daily" -> "ngày"
            else -> throw IllegalArgumentException()
        }

        /**
         * Kiểm tra xem người chơi đã đạt mốc nạp này chưa,
         * nếu đạt thì thực hiện các lệnh trong config
         *
         * @param player người chơi
         * @param current số tiền sau khi nạp
         * @param amount số tiền nạp
         */
        fun check(player: Player, current: Int, amount: Int) {
            if (current >= this.amount && current - amount < this.amount) {
                info("${player.name} đã đạt mốc nạp ${this.amount} ($typeName)")
                runSync {
                    commands.forEach {
                        val command = it.replace("%player%", player.name)
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
                    }
                }
            }
        }

        /**
         * Kiểm tra xem toàn server đã đạt mốc nạp này chưa,
         * nếu đạt thì thực hiện các lệnh trong config
         *
         * @param current số tiền sau khi nạp
         * @param amount số tiền nạp
         */
        fun sumCheck(current: Int, amount: Int) {
            if (current >= this.amount && current - amount < this.amount) {
                runSync {
                    commands.forEach {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), it)
                    }
                    bar?.removeAll()
                    bar?.isVisible = false
                    bar = null
                }
            }
        }
    }

    fun removeBossBars() {
        getAll().forEach {
            it.barTask?.cancel()
            it.bar?.removeAll()
            it.bar?.isVisible = false
            it.bar = null
        }
    }
}

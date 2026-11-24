package vn.twosmc.nap2s.config

import vn.twosmc.nap2s.config.Milestones.Component
import vn.twosmc.nap2s.utils.Utils.Companion.info
import vn.twosmc.nap2s.utils.Utils.Companion.warning
import vn.twosmc.nap2s.platform.gui.color
import org.bukkit.entity.Player

class MilestonesMaster : FileConfig("mocnaptong") {
    private var components: List<Component> = emptyList()

    init {
        loadComponents()
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadComponents() {
        components = (config.getList("mocnaptong") ?: emptyList()).map {
            try {
                it as Map<*, *>
                val type = it["type"]
                val bossBar = (it.getOrDefault("bossbar", null) as String?)?.color()
                val from = it.getOrDefault("from", 0) as Int
                val barColor = it.getOrDefault("bossbar-color", "GREEN") as String
                val barStyle = it.getOrDefault("bossbar-style", "SEGMENTED_10") as String

                if (type !in listOf("all", "week", "month", "daily")) {
                    warning("Loại mốc nạp \"$type\" không hợp lệ. Chỉ chấp nhận all, week, month, daily")
                    return@map null
                }

                Component(it["type"] as String, it["amount"] as Int, it["commands"] as List<String>,
                    bossBar, from, barColor, barStyle)
            } catch (e: Exception) {
                e.warning("Có một mốc nạp không hợp lệ, hãy liên hệ developer để được hỗ trợ")
                null
            }
        }.filterNotNull()

        info("Đã nạp ${components.size} mốc nạp tổng.")
    }

    override fun reload() {
        super.reload()
        loadComponents()
    }

    fun getAll() = components.toList()

    fun onJoin(player: Player) {
        components.forEach {
            it.bar?.addPlayer(player)
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

package vn.twosmc.nap2s

import vn.twosmc.nap2s.database.PlayerDataDAO
import vn.twosmc.nap2s.utils.Utils.Companion.runAsync
import vn.twosmc.nap2s.utils.Utils.Companion.runSync
import org.bukkit.Bukkit

const val TOP_EXPIRE = 1 * 60 * 1000L // 1 phút
const val TOP_COUNT = 100

const val TOP_KEY_DONATE_TOTAL = "DONATE_TOTAL"
const val TOP_KEY_POINT_FROM_CARD = "POINT_FROM_CARD"

class LeaderBoard(
    private val list: List<Pair<String, Int>>,
    private val inititalTime: Long = System.currentTimeMillis()
) {
    fun isExpired() = System.currentTimeMillis() - inititalTime > TOP_EXPIRE

    operator fun get(rank: Int) = list.getOrNull(rank - 1)

    fun size() = list.size

    companion object {
        private val topCache = mutableMapOf<String, LeaderBoard>()

        private fun getFromDB(key: String) = LeaderBoard(PlayerDataDAO.getInstance().getTop(key, TOP_COUNT))

        operator fun get(key: String) = topCache[key].run {
            if (this?.isExpired() != false) {
                if (Bukkit.isPrimaryThread()) {
                    runAsync { getFromDB(key).also { runSync { topCache[key] = it } } }
                    this ?: LeaderBoard(emptyList(), 0L)
                } else {
                    getFromDB(key).also { runSync { topCache[key] = it } }
                }
            } else this
        }
    }
}

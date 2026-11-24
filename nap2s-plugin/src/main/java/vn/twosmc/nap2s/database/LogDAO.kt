package vn.twosmc.nap2s.database

import vn.twosmc.nap2s.Nap2S
import vn.twosmc.nap2s.TopupType
import vn.twosmc.nap2s.card.Card
import vn.twosmc.nap2s.card.CardType
import vn.twosmc.nap2s.card.CardWaiting
import vn.twosmc.nap2s.utils.Utils
import vn.twosmc.nap2s.utils.Utils.Companion.format
import vn.twosmc.nap2s.platform.db.DataAccess
import vn.twosmc.nap2s.platform.db.fetch
import vn.twosmc.nap2s.platform.db.fetchRecords
import vn.twosmc.nap2s.platform.time.minMaxEpochTimestamp
import vn.twosmc.nap2s.platform.time.timeToString
import org.bukkit.entity.Player

abstract class LogDAO : DataAccess() {
    data class RecentHistoryEntry(
        val transactionId: String,
        val playerName: String,
        val type: String,
        val amount: Int,
        val pointsReceived: Int,
        val time: String,
        val server: String
    )

    companion object {
        fun getInstance() = Nap2S.instance.getDAO(LogDAO::class)
    }

    private fun getMain() = Nap2S.instance

    abstract fun insertLogScript(): String
    abstract fun setWaitingScript(): String
    abstract fun getWaitingCardsScript(includeLegacyBlank: Boolean): String
    abstract fun stopWaitingScript(): String
    abstract fun setTransactionIdScript(): String
    abstract fun getHistoryScriptAllPlayerAllTime(): String
    abstract fun getHistoryScriptAllPlayerByMonth(): String
    abstract fun getHistoryScriptByPlayerAllTime(): String
    abstract fun getHistoryScriptByPlayerByMonth(): String
    abstract fun getSumScriptAllPlayerAllTime(): String
    abstract fun getSumScriptAllPlayerByMonth(): String
    abstract fun getSumScriptByPlayerAllTime(): String
    abstract fun getSumScriptByPlayerByMonth(): String
    abstract fun getRevenueStatsByTimeRangeScript(): String
    abstract fun getRevenueStatsAllServersScript(): String
    abstract fun getTransactionDetailsByIdScript(): String
    abstract fun getRecentHistoryByPlayerScript(): String
    abstract fun updatePointReceivedScript(): String

    private fun formatCardType(type: String): String {
        return CardType.entries
            .find { it.name.equals(type, true) || it.alternative.any { alias -> alias.equals(type, true) } }
            ?.displayName
            ?: TopupType.entries.find { it.name.equals(type, true) }?.typeName
            ?: type
    }

    fun insertLog(player: Player, card: Card) = insertLog(
        name = player.name,
        uuid = player.uniqueId.toString(),
        seri = card.seri,
        pin = card.pin,
        type = card.type.name,
        amount = card.price.value,
        providerId = card.providerId
    )

    fun insertLog(
        name: String = "",
        uuid: String,
        seri: String,
        pin: String,
        type: String,
        amount: Int,
        providerId: String = ""
    ) = run {
        insertLogScript().statementWithKey {
            setString(1, name)
            setString(2, uuid)
            setString(3, seri)
            setString(4, pin)
            setString(5, type)
            setInt(6, amount)
            setLong(7, System.currentTimeMillis())
            setString(8, getMain().config.server)
            setString(9, providerId)

            executeUpdate()
            generatedKeys.use {
                if (it.next()) {
                    it.getInt(1).apply {
                        Utils.info("Ghi log thanh cong: UUID: $uuid, ID: $this, provider: $providerId")
                    }
                } else {
                    throw IllegalStateException("Ghi log that bai cho UUID $uuid")
                }
            }
        }
    }

    fun setWaiting(id: Int) {
        setWaitingScript().statement {
            setInt(1, id)
            executeUpdate()
        }
    }

    fun stopWaiting(id: Int, isSuccess: Boolean) {
        stopWaitingScript().statement {
            setInt(1, if (isSuccess) 1 else 0)
            setInt(2, id)
            executeUpdate()
        }
    }

    fun getWaitingCards(
        playerUuids: Array<String>,
        server: String,
        providerId: String,
        includeLegacyBlank: Boolean
    ) = run {
        val uuids = playerUuids.takeIf { it.isNotEmpty() }
            ?.joinToString(", ") { "'$it'" }
            ?: return@run null

        getWaitingCardsScript(includeLegacyBlank)
            .replace("{UUIDS}", uuids)
            .statement {
                setString(1, server)
                setString(2, providerId)

                fetchRecords {
                    CardWaiting(
                        getInt("id"),
                        getString("uuid"),
                        getString("seri"),
                        getString("pin"),
                        getString("type"),
                        getInt("price"),
                        getString("transaction_id"),
                        getString("provider")
                    )
                }
            }
    }

    fun setTransactionId(id: Int, transactionId: String, isSuccess: Boolean) {
        setTransactionIdScript().statement {
            setString(1, transactionId)
            setInt(2, if (isSuccess) 1 else 0)
            setInt(3, id)
            executeUpdate()
        }
    }

    @Suppress("UNUSED_CHANGED_VALUE")
    fun getHistory(playerName: String? = null, yearMonth: String? = null, page: Int) = run {
        val linePerPage = 20
        val script = if (yearMonth == null) {
            if (playerName == null) getHistoryScriptAllPlayerAllTime() else getHistoryScriptByPlayerAllTime()
        } else {
            if (playerName == null) getHistoryScriptAllPlayerByMonth() else getHistoryScriptByPlayerByMonth()
        }
        script.statement {
            var columnIndex = 1
            val offset = (page - 1) * linePerPage
            if (playerName != null) {
                val uuid = PlayerInfoDAO.getInstance().getUUID(playerName) ?: ""
                setString(columnIndex++, uuid)
            }
            if (yearMonth != null) {
                val (min, max) = minMaxEpochTimestamp(yearMonth)
                setLong(columnIndex++, min)
                setLong(columnIndex++, max)
            }
            setInt(columnIndex++, offset)
            setInt(columnIndex++, linePerPage)
            fetchRecords {
                val rowNum = getInt("rownum")
                val name = getString("name")
                val type = formatCardType(getString("type"))
                val price = getInt("price")
                val pointsReceived = getInt("pointsnhan")
                val time = getLong("time").timeToString()
                getMain().language.logOutPut
                    .replace("%ORDER%", rowNum.toString())
                    .replace("%PLAYER%", name)
                    .replace("%CARD_TYPE%", type)
                    .replace("%CARD_PRICE%", price.format())
                    .replace("%POINTS_RECEIVED%", pointsReceived.toString())
                    .replace("%POINT_UNIT%", getMain().config.pointUnit)
                    .replace("%DATE%", time)
            }
        }
    }

    @Suppress("UNUSED_CHANGED_VALUE")
    fun getSum(playerName: String? = null, yearMonth: String? = null) = run {
        val script = if (yearMonth == null) {
            if (playerName == null) getSumScriptAllPlayerAllTime() else getSumScriptByPlayerAllTime()
        } else {
            if (playerName == null) getSumScriptAllPlayerByMonth() else getSumScriptByPlayerByMonth()
        }
        script.statement {
            var columnIndex = 1
            if (playerName != null) {
                val uuid = PlayerInfoDAO.getInstance().getUUID(playerName) ?: ""
                setString(columnIndex++, uuid)
            }
            if (yearMonth != null) {
                val (min, max) = minMaxEpochTimestamp(yearMonth)
                setLong(columnIndex++, min)
                setLong(columnIndex++, max)
            }

            fetch {
                next()
                Pair(getInt(1), getInt(2))
            }
        }
    }

    fun getRevenueStatsByTimeRange(server: String, fromMs: Long, toMs: Long): Pair<Int, Int> =
        getRevenueStatsByTimeRangeScript().statement {
            setString(1, server)
            setLong(2, fromMs)
            setLong(3, toMs)
            fetch {
                next()
                Pair(getInt("count"), getInt("total"))
            }
        }

    /**
     * Thống kê doanh thu thẻ cào cross-server (không filter server).
     * Dùng cho báo cáo network.
     */
    fun getRevenueStatsAllServers(fromMs: Long, toMs: Long): Pair<Int, Int> =
        getRevenueStatsAllServersScript().statement {
            setLong(1, fromMs)
            setLong(2, toMs)
            fetch {
                next()
                Pair(getInt("count"), getInt("total"))
            }
        }

    fun getTransactionDetailsById(id: String): List<List<String>> = run {
        getTransactionDetailsByIdScript().statement {
            setString(1, id)
            fetchRecords {
                val name = getString("name")
                val type = formatCardType(getString("type"))
                val price = getInt("price")
                val time = getLong("time").timeToString()
                val pointsnhan = getInt("pointsnhan")
                val server = getString("server")
                getMain().language.transactionIdDetailsOutPut.map {
                    it.replace("%TRANSACTION_ID%", id)
                        .replace("%PLAYER%", name)
                        .replace("%CARD_TYPE%", type)
                        .replace("%CARD_PRICE%", price.format())
                        .replace("%POINTS_RECEIVED%", pointsnhan.toString())
                        .replace("%POINT_UNIT%", getMain().config.pointUnit)
                        .replace("%SERVER%", server)
                        .replace("%DATE%", time)
                }
            }
        }
    }

    fun getRecentHistoryByPlayer(uuid: String, limit: Int = 20): List<RecentHistoryEntry> = run {
        getRecentHistoryByPlayerScript().statement {
            setString(1, uuid)
            setInt(2, limit)
            fetchRecords {
                RecentHistoryEntry(
                    transactionId = getString("transaction_id") ?: "",
                    playerName = getString("name"),
                    type = formatCardType(getString("type")),
                    amount = getInt("price"),
                    pointsReceived = getInt("pointsnhan"),
                    time = getLong("time").timeToString(),
                    server = getString("server")
                )
            }
        }
    }

    fun updatePointReceived(id: Int, points: Int) {
        updatePointReceivedScript().statement {
            setInt(1, points)
            setInt(2, id)
            executeUpdate()
        }
    }
}

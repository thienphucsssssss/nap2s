package vn.twosmc.nap2s.commands

import vn.twosmc.nap2s.Nap2S
import vn.twosmc.nap2s.database.BankingOrderDAO
import vn.twosmc.nap2s.database.LogDAO
import vn.twosmc.nap2s.network.NetworkManager
import vn.twosmc.nap2s.utils.Utils.Companion.format
import vn.twosmc.nap2s.utils.Utils.Companion.runNotSync
import vn.twosmc.nap2s.utils.Utils.Companion.send
import vn.twosmc.nap2s.platform.command.command
import org.bukkit.command.CommandSender
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Lệnh đối soát doanh thu cho admin.
 *
 * /nap2s report           -> Báo cáo hôm nay
 * /nap2s report today     -> Báo cáo hôm nay
 * /nap2s report week      -> 7 ngày gần nhất
 * /nap2s report month     -> Tháng này
 * /nap2s report yyyy-MM   -> Tháng cụ thể (VD: 2026-03)
 * /nap2s report all       -> Toàn thời gian
 * /nap2s report network [period] -> Cross-server (khi network-mode bật)
 */
class ReportCmd {
    companion object {
        fun create() = command {
            description("Đối soát doanh thu")

            tabComplete {
                val options = mutableListOf("today", "week", "month", "all")
                if (NetworkManager.isEnabled()) options.add("network")
                when (args.size) {
                    0 -> emptyList()
                    1 -> options.filter { it.startsWith(args.last()) }
                    2 -> if (args[0] == "network") {
                        listOf("today", "week", "month", "all").filter { it.startsWith(args.last()) }
                    } else {
                        emptyList()
                    }
                    else -> emptyList()
                }
            }

            action {
                val isNetwork = args.getOrNull(0)?.lowercase() == "network"
                val period = if (isNetwork) args.getOrNull(1) ?: "today" else args.getOrNull(0) ?: "today"

                if (isNetwork && !NetworkManager.isEnabled()) {
                    sender.send("§cNetwork mode chưa bật. Thêm 'network-mode: true' vào config.yml.")
                    return@action
                }

                runNotSync {
                    generateReport(sender, period, isNetwork)
                }
            }
        }

        private fun generateReport(sender: CommandSender, period: String, isNetwork: Boolean = false) {
            val now = LocalDate.now()
            val periodName: String
            val fromMs: Long
            val toMs: Long

            when (period.lowercase()) {
                "today" -> {
                    periodName = "hôm nay (${now.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))})"
                    val todayStart = now.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    fromMs = todayStart
                    toMs = System.currentTimeMillis()
                }

                "week" -> {
                    periodName = "7 ngày gần nhất"
                    val weekAgo = now.minusDays(7).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    fromMs = weekAgo
                    toMs = System.currentTimeMillis()
                }

                "month" -> {
                    val ym = YearMonth.from(now)
                    periodName = "tháng ${ym.monthValue}/${ym.year}"
                    fromMs = monthStartEpoch(ym)
                    toMs = monthEndEpoch(ym)
                }

                "all" -> {
                    periodName = "toàn thời gian"
                    fromMs = 0L
                    toMs = System.currentTimeMillis()
                }

                else -> {
                    if (period.matches(Regex("\\d{4}-\\d{2}"))) {
                        val ym = YearMonth.parse(period)
                        periodName = "tháng $period"
                        fromMs = monthStartEpoch(ym)
                        toMs = monthEndEpoch(ym)
                    } else {
                        sender.send("§cKhông hiểu kỳ báo cáo: $period")
                        sender.send("§7Cách dùng: /nap2s report [today|week|month|all|yyyy-MM]")
                        return
                    }
                }
            }

            val scopeLabel = if (isNetwork) " §d[NETWORK]" else ""
            sender.send("§b§l═══ BÁO CÁO DOANH THU$scopeLabel ═══")
            sender.send("§7Kỳ: §f$periodName")
            if (isNetwork) sender.send("§7Phạm vi: §dtoàn network")
            sender.send("")

            val (cardCount, cardTotal) = if (isNetwork) {
                LogDAO.getInstance().getRevenueStatsAllServers(fromMs, toMs)
            } else {
                LogDAO.getInstance().getRevenueStatsByTimeRange(Nap2S.instance.config.server, fromMs, toMs)
            }

            sender.send("§e§l▸ Thẻ cào:")
            if (cardCount > 0) {
                sender.send("  §7Số giao dịch thành công: §a$cardCount")
                sender.send("  §7Tổng mệnh giá thẻ: §a${cardTotal.format()}đ")
            } else {
                sender.send("  §7Chưa có giao dịch nào")
            }

            val bankingStats = getBankingStats(fromMs, toMs, isNetwork)
            sender.send("")
            sender.send("§e§l▸ ATM/QR (chuyển khoản):")
            if (bankingStats.paidCount > 0) {
                sender.send("  §7Đơn thành công: §a${bankingStats.paidCount}")
                sender.send("  §7Tổng tiền nhận: §a${bankingStats.paidAmount.format()}đ")
                if (bankingStats.pendingCount > 0) {
                    sender.send("  §7Đơn đang chờ: §e${bankingStats.pendingCount}")
                }
                if (bankingStats.expiredCount > 0) {
                    sender.send("  §7Đơn hết hạn: §c${bankingStats.expiredCount}")
                }
            } else {
                sender.send("  §7Chưa có giao dịch nào")
                if (bankingStats.pendingCount > 0) {
                    sender.send("  §7Đơn đang chờ: §e${bankingStats.pendingCount}")
                }
            }

            val totalRevenue = cardTotal + bankingStats.paidAmount
            val totalTxn = cardCount + bankingStats.paidCount
            sender.send("")
            sender.send("§6§l▸ Tổng hợp:")
            sender.send("  §7Tổng giao dịch thành công: §a$totalTxn")
            sender.send("  §7Tổng doanh thu: §a${totalRevenue.format()}đ")

            if (bankingStats.byGateway.isNotEmpty()) {
                sender.send("")
                sender.send("§e§l▸ Chi tiết theo cổng:")
                bankingStats.byGateway.forEach { (gateway, stats) ->
                    sender.send("  §b$gateway: §a${stats.first} đơn §8| §a${stats.second.format()}đ")
                }
            }

            sender.send("")
            sender.send("§b§l═══════════════════════")
        }

        private fun monthStartEpoch(yearMonth: YearMonth): Long {
            return yearMonth.atDay(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }

        private fun monthEndEpoch(yearMonth: YearMonth): Long {
            return yearMonth.plusMonths(1)
                .atDay(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli() - 1
        }

        private fun getBankingStats(fromMs: Long, toMs: Long, isNetwork: Boolean = false): BankingStats {
            val dao = BankingOrderDAO.getInstance()
            val server = Nap2S.instance.config.server

            val allOrders = try {
                if (isNetwork) {
                    dao.getAllOrdersByTimeRangeAllServers(fromMs, toMs)
                } else {
                    dao.getAllOrdersByTimeRange(server, fromMs, toMs)
                }
            } catch (_: Exception) {
                emptyList()
            }

            var paidCount = 0
            var paidAmount = 0
            var pendingCount = 0
            var expiredCount = 0
            val byGateway = mutableMapOf<String, Pair<Int, Int>>()

            for (order in allOrders) {
                when (order.status) {
                    "PAID", "REWARDED" -> {
                        paidCount++
                        paidAmount += order.amount
                        val gateway = order.gateway.ifBlank { "unknown" }
                        val previous = byGateway[gateway] ?: (0 to 0)
                        byGateway[gateway] = (previous.first + 1) to (previous.second + order.amount)
                    }

                    "PENDING" -> {
                        if (order.expiresAt > 0 && order.expiresAt < System.currentTimeMillis()) {
                            expiredCount++
                        } else {
                            pendingCount++
                        }
                    }

                    "EXPIRED" -> expiredCount++
                }
            }

            return BankingStats(paidCount, paidAmount, pendingCount, expiredCount, byGateway)
        }

        data class BankingStats(
            val paidCount: Int,
            val paidAmount: Int,
            val pendingCount: Int,
            val expiredCount: Int,
            val byGateway: Map<String, Pair<Int, Int>>
        )
    }
}

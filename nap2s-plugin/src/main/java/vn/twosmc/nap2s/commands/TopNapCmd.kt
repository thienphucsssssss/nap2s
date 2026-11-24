package vn.twosmc.nap2s.commands

import vn.twosmc.nap2s.Nap2S
import vn.twosmc.nap2s.LeaderBoard
import vn.twosmc.nap2s.TOP_KEY_DONATE_TOTAL
import vn.twosmc.nap2s.utils.Utils.Companion.makePagination
import vn.twosmc.nap2s.utils.Utils.Companion.runNotSync
import vn.twosmc.nap2s.utils.Utils.Companion.send
import vn.twosmc.nap2s.platform.command.command
import org.bukkit.entity.Player
import kotlin.math.ceil

class TopNapCmd { companion object { fun init() {
    command {
        description("Xem top nạp thẻ")

        action { runNotSync {
            if (args.size > 1) {
                sender.sendMessage("§cCách sử dụng: /topnap [số trang]")
                return@runNotSync
            }

            val page = args.getOrNull(0)?.toIntOrNull() ?: 1
            val top = LeaderBoard["${TOP_KEY_DONATE_TOTAL}_ALL"]

            if (top.size() == 0) {
                sender.sendMessage("§cChưa có dữ liệu top nạp thẻ.")
                return@runNotSync
            }

            val maxPage = ceil(top.size() / 20.0).toInt()
            if (page > maxPage) {
                sender.sendMessage("§cKhông tìm thấy trang $page")
                return@runNotSync
            }
            val pagination = makePagination("/topnap", page, maxPage, sender is Player)

            sender.send("§e§lTop nạp thẻ")
            for (i in (page - 1) * 20 until page * 20) {
                val rank = i + 1
                val entry = top[rank] ?: break
                sender.sendMessage("§a${rank}. §b${entry.first} §e${entry.second} VNĐ")
            }
            sender.spigot().sendMessage(*pagination)
        }}

        register(Nap2S.instance, "topnap")
    }
}}}
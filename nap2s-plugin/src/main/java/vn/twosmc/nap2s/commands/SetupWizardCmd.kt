package vn.twosmc.nap2s.commands

import vn.twosmc.nap2s.Nap2S
import vn.twosmc.nap2s.database.ConfigDAO
import vn.twosmc.nap2s.utils.Utils.Companion.runNotSync
import vn.twosmc.nap2s.utils.Utils.Companion.send
import vn.twosmc.nap2s.platform.command.command
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Wizard cài đặt lần đầu — dẫn admin qua từng bước cấu hình.
 *
 * /nap2s setup    → Bắt đầu wizard
 * /nap2s setup cancel → Hủy wizard đang chạy
 *
 * Flow:
 *  1. Chọn cổng nạp thẻ (chargingws-v2 / card2k / ...)
 *  2. Nhập API credentials
 *  3. Chọn banking provider (sepay / payos / bỏ qua)
 *  4. Nhập thông tin ngân hàng
 *  5. Hoàn tất → reload plugin
 */
class SetupWizardCmd {
    companion object {
        private val activeSessions = ConcurrentHashMap<UUID, WizardSession>()
        private var listener: Listener? = null

        fun create() = command {
            description("Trình cài đặt nhanh cho lần đầu sử dụng")

            tabComplete {
                when (args.size) {
                    1 -> listOf("cancel").filter { it.startsWith(args.last()) }
                    else -> emptyList()
                }
            }

            action {
                val player = sender as? Player ?: run {
                    sender.send("§cChỉ dùng được từ trong game.")
                    return@action
                }

                if (args.getOrNull(0)?.lowercase() == "cancel") {
                    val removed = activeSessions.remove(player.uniqueId)
                    if (removed != null) {
                        player.send("§eĐã hủy wizard.")
                    } else {
                        player.send("§7Không có wizard nào đang chạy.")
                    }
                    return@action
                }

                if (activeSessions.containsKey(player.uniqueId)) {
                    player.send("§eWizard đang chạy. Gõ §b/nap2s setup cancel §eđể hủy.")
                    return@action
                }

                startWizard(player)
            }
        }

        private fun startWizard(player: Player) {
            val session = WizardSession(player.uniqueId)
            activeSessions[player.uniqueId] = session
            registerListener()

            player.send("")
            player.send("§b§l╔══════════════════════════╗")
            player.send("§b§l║   §f§lNAP2S — SETUP WIZARD   §b§l║")
            player.send("§b§l╚══════════════════════════╝")
            player.send("")
            player.send("§7Chào mừng bạn đến với Nap2S. Mình sẽ hướng dẫn")
            player.send("§7cấu hình cơ bản qua vài câu hỏi ngắn.")
            player.send("")
            player.send("§7Gõ §c/nap2s setup cancel §7bất kỳ lúc nào để hủy.")
            player.send("")

            askStep(player, session)
        }

        private fun askStep(player: Player, session: WizardSession) {
            when (session.step) {
                0 -> {
                    player.send("§e§l[Bước 1/5] §fTên máy chủ")
                    player.send("§7Dùng để phân biệt giữa các server khi chạy network.")
                    player.send("§7Nhập tên server (VD: §fsurvival§7, §flobby§7):")
                    val current = Nap2S.instance.config.server
                    if (current.isNotBlank()) {
                        player.send("§8Hiện tại: $current — nhấn Enter để giữ nguyên")
                    }
                }
                1 -> {
                    player.send("")
                    player.send("§e§l[Bước 2/5] §fĐơn vị xu")
                    player.send("§7Tên đơn vị thưởng hiện trong game (VD: §fxu§7, §fcoin§7, §fđồng§7):")
                    val current = Nap2S.instance.config.pointUnit
                    player.send("§8Hiện tại: $current")
                }
                2 -> {
                    player.send("")
                    player.send("§e§l[Bước 3/5] §fNạp thẻ cào")
                    player.send("§7Bạn muốn bật nạp thẻ không?")
                    player.send("  §a[có] §7— Sẽ cần cấu hình cổng nạp thẻ trong §fproviders.yml")
                    player.send("  §c[không] §7— Bỏ qua")
                    player.send("§7Nhập §acó §7hoặc §ckhông§7:")
                }
                3 -> {
                    player.send("")
                    player.send("§e§l[Bước 4/5] §fNạp ATM/QR (chuyển khoản)")
                    player.send("§7Bạn muốn bật nạp qua chuyển khoản ngân hàng?")
                    player.send("  §a[có] §7— Dùng SePay hoặc PayOS, QR code in-game")
                    player.send("  §c[không] §7— Bỏ qua")
                    player.send("§7Nhập §acó §7hoặc §ckhông§7:")
                }
                4 -> {
                    if (session.enableBanking) {
                        player.send("")
                        player.send("§e§l[Bước 5/5] §fChọn banking provider")
                        player.send("  §b[sepay] §7— SePay (VietQR, nhiều ngân hàng)")
                        player.send("  §b[payos] §7— PayOS (payment link)")
                        player.send("§7Nhập §bsepay §7hoặc §bpayos§7:")
                    } else {
                        session.step = 99
                        finishWizard(player, session)
                        return
                    }
                }
                99 -> finishWizard(player, session)
            }
        }

        private fun handleInput(player: Player, message: String) {
            val session = activeSessions[player.uniqueId] ?: return
            val input = message.trim()

            when (session.step) {
                0 -> {
                    session.serverName = input.ifBlank { Nap2S.instance.config.server }
                    player.send("§a✓ Server: §f${session.serverName}")
                    session.step = 1
                }
                1 -> {
                    session.pointUnit = input.ifBlank { Nap2S.instance.config.pointUnit }
                    player.send("§a✓ Đơn vị xu: §f${session.pointUnit}")
                    session.step = 2
                }
                2 -> {
                    session.enableCard = input.lowercase() in listOf("có", "co", "yes", "y", "1", "true")
                    player.send("§a✓ Nạp thẻ: ${if (session.enableCard) "§aCó" else "§cKhông"}")
                    session.step = 3
                }
                3 -> {
                    session.enableBanking = input.lowercase() in listOf("có", "co", "yes", "y", "1", "true")
                    player.send("§a✓ Nạp ATM/QR: ${if (session.enableBanking) "§aCó" else "§cKhông"}")
                    session.step = 4
                }
                4 -> {
                    val provider = input.lowercase()
                    if (provider !in listOf("sepay", "payos")) {
                        player.send("§cChỉ nhập §bsepay §choặc §bpayos")
                        return
                    }
                    session.bankingProvider = provider
                    player.send("§a✓ Banking: §f$provider")
                    session.step = 99
                }
                else -> return
            }

            askStep(player, session)
        }

        private fun finishWizard(player: Player, session: WizardSession) {
            activeSessions.remove(player.uniqueId)

            player.send("")
            player.send("§a§l══ HOÀN TẤT SETUP ══")
            player.send("")
            player.send("§7Cấu hình đã được ghi nhận. Đây là các bước bạn cần làm:")
            player.send("")

            // config.yml
            player.send("§e§l▸ Sửa config.yml:")
            player.send("§7  server: §f'${session.serverName}'")
            player.send("§7  point-unit: §f'${session.pointUnit}'")
            player.send("")

            // Nạp thẻ
            if (session.enableCard) {
                player.send("§e§l▸ Nạp thẻ cào:")
                player.send("§7  1. Mở §fplugins/Nap2S/providers/ §7và chọn cổng muốn dùng")
                player.send("§7  2. Sửa §fenabled: true §7trong file provider")
                player.send("§7  3. Nhập API key: §b/nap2s setkey <provider-id> <field> <value>")
            }

            // Banking
            if (session.enableBanking) {
                player.send("§e§l▸ Nạp ATM/QR (${session.bankingProvider}):")
                player.send("§7  1. Mở §fbanking.yml §7và sửa:")
                player.send("§7     enabled: §ftrue")
                player.send("§7     provider-id: §f${session.bankingProvider}")
                if (session.bankingProvider == "sepay") {
                    player.send("§7  2. Set API token: §b/nap2s setkey sepay-api-token <token>")
                    player.send("§7  3. Đăng ký webhook trên SePay → URL: §f<server>/nap2s/ipn")
                } else {
                    player.send("§7  2. Set PayOS keys:")
                    player.send("§7     §b/nap2s setkey payos-client-id <id>")
                    player.send("§7     §b/nap2s setkey payos-api-key <key>")
                    player.send("§7     §b/nap2s setkey payos-checksum-key <key>")
                    player.send("§7  3. Đăng ký webhook trên PayOS → URL: §f<server>/nap2s/ipn/payos")
                }
            }

            player.send("")
            player.send("§7Sau khi sửa xong, gõ §b/nap2s reload §7để áp dụng.")
            player.send("§a§l═══════════════════")

            // Đánh dấu đã chạy wizard
            runNotSync {
                try {
                    ConfigDAO.getInstance().set("setup_completed", "true")
                } catch (_: Exception) { }
            }
        }

        private fun registerListener() {
            if (listener != null) return
            listener = object : Listener {
                @EventHandler
                fun onChat(event: AsyncPlayerChatEvent) {
                    val session = activeSessions[event.player.uniqueId] ?: return
                    event.isCancelled = true
                    handleInput(event.player, event.message)
                }
            }
            Bukkit.getPluginManager().registerEvents(listener!!, Nap2S.instance)
        }

        /**
         * Dọn listener khi plugin disable
         */
        fun cleanup() {
            listener?.let { HandlerList.unregisterAll(it) }
            listener = null
            activeSessions.clear()
        }
    }

    data class WizardSession(
        val playerId: UUID,
        var step: Int = 0,
        var serverName: String = "",
        var pointUnit: String = "xu",
        var enableCard: Boolean = true,
        var enableBanking: Boolean = false,
        var bankingProvider: String = "sepay"
    )
}

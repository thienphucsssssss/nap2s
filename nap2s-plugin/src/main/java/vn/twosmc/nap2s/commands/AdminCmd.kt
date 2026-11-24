package vn.twosmc.nap2s.commands

import vn.twosmc.nap2s.Nap2S
import vn.twosmc.nap2s.Nap2S.Companion.transactional
import vn.twosmc.nap2s.TopupType
import vn.twosmc.nap2s.config.SecureConfigManager
import vn.twosmc.nap2s.providers.CardProvider
import vn.twosmc.nap2s.security.AuditLog
import vn.twosmc.nap2s.database.ConfigDAO
import vn.twosmc.nap2s.database.LogDAO
import vn.twosmc.nap2s.database.PlayerDataDAO
import vn.twosmc.nap2s.database.PlayerInfoDAO
import vn.twosmc.nap2s.gui.AdminDashboardUI
import vn.twosmc.nap2s.utils.Utils.Companion.format
import vn.twosmc.nap2s.utils.Utils.Companion.makePagination
import vn.twosmc.nap2s.utils.Utils.Companion.runNotSync
import vn.twosmc.nap2s.utils.Utils.Companion.send
import vn.twosmc.nap2s.platform.asString
import vn.twosmc.nap2s.platform.chat.ChatListener
import vn.twosmc.nap2s.platform.command.command
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.*
import kotlin.math.ceil

class AdminCmd {
    companion object {
        fun init() = command {
            addSubCommand(reload(), "reload")
            addSubCommand(adminGui(), "quanly", "dashboard", "adminui", "gui")
            addSubCommand(thongbao(), "thongbao")
            addSubCommand(setBankLocation(), "chuyenkhoan")
            addSubCommand(history(), "lichsu", "history")
            addSubCommand(napThuCong(), "napthucong", "manual")
            addSubCommand(traCuuGiaoDich(), "tracuugd", "magiaodich")
            addSubCommand(clearPlayerData(), "cleardata")
            addSubCommand(setSecureKey(), "setkey")
            addSubCommand(viewSecureKeys(), "viewkeys")
            addSubCommand(deleteSecureKey(), "deletekey")
            addSubCommand(ReportCmd.create(), "report", "doanhsthu", "revenue")
            addSubCommand(auditLogCmd(), "auditlog", "audit")
            addSubCommand(SetupWizardCmd.create(), "setup", "wizard")

            action {
                sender.sendMessage("§b§lCác lệnh của plugin Nap2S")
                sendSubCommandsUsage(sender, commandTree)
            }

            register(Nap2S.instance, "nap2s")
        }

        private fun reload() = command {
            description("Reload plugin")

            action {
                Nap2S.instance.reload()
                sender.send("§aĐã reload plugin")
            }
        }

        private fun adminGui() = command {
            description("Mở GUI quản lý cổng nạp và định tuyến thẻ")

            action {
                val player = sender as? Player ?: run {
                    sender.send("Vào game rồi thực hiện lệnh này.")
                    return@action
                }
                AdminDashboardUI(player)
            }
        }

        private fun thongbao() = command {
            description("Thay đổi thông báo trong giao diện nạp thẻ")

            action { runNotSync {
                val config = ConfigDAO.getInstance()

                val message = args.joinToString(" ")
                if (message.isEmpty()) {
                    config.delete("announcement")
                    sender.send("§aĐã xóa thông báo thành công")
                } else {
                    config.set("announcement", args.joinToString(" "))
                    sender.send("§aĐã thay đổi thông báo thành công")
                }
            }}
        }

        private fun setBankLocation() = command {
            description("Đặt vị trí xem hướng dẫn chuyển khoản")

            action { runNotSync {
                val config = ConfigDAO.getInstance()

                val player = sender as? Player ?: run {
                    sender.send("Vào server rồi thực hiện lệnh này.")
                    return@runNotSync
                }
                config.set("banking-location", player.location.asString())
                player.send("Đã đặt vị trí xem hướng dẫn chuyển khoản")
            }}
        }

        private fun history() = command {
            val usage = "[-p <tên người chơi>] [-m <tháng cần tra>] <Số trang>"

            description("Xem lịch sử nạp thẻ")

            tabComplete {
                when(args.size) {
                    0 -> emptyList()
                    1 -> if(args.last().isEmpty()) listOf(usage) else emptyList()
                    else -> when(args.takeLast(2).first()) {
                        "-p" -> Bukkit.getOnlinePlayers().map { it.name }.filter { it.startsWith(args.last()) }
                        "-m" -> run {
                            val value = args.last()
                            val year = LocalDate.now().year
                            (1..12).map { "${it.toString().padStart(2, '0')}/$year" }
                                .filter { month -> month.startsWith(value) || month.startsWith("0$value") }
                        }
                        else -> listOf("<số trang>", "-p", "-m").filter { it.startsWith(args.last()) }
                    }
                }
            }

            action {
                val logDao = LogDAO.getInstance()

                if (args.isEmpty()) {
                    sender.send("Tra cứu lịch sử nạp thẻ của người chơi hoặc toàn server")
                    sender.send("Cách dùng: §b/$commandTree $usage")
                    return@action
                }

                // parse args: [-p <player>] [-m <month>] <page>
                val args = args.toMutableList()
                var playerName: String? = null
                var month: String? = null
                var page = 1
                while (args.isNotEmpty()) {
                    when (val current = args.removeFirst()) {
                        "-p" -> playerName = args.removeFirstArgumentOrNull() ?: run {
                            sender.send("§cBạn phải nhập tên người chơi sau tùy chọn §b-p")
                            return@action
                        }
                        "-m" -> month = args.removeFirstArgumentOrNull() ?: run {
                            sender.send("§cBạn phải nhập tháng sau tùy chọn §b-m")
                            return@action
                        }
                        else -> page = current.toIntOrNull() ?: 1
                    }
                }

                runNotSync {
                    try {
                        val (sum, count) = logDao.getSum(playerName, month)
                        val maxPage = ceil(count / 20.0).toInt()
                        val title = "§aLịch sử nạp thẻ của §b%PLAYER_NAME%"
                            .replace("%PLAYER_NAME%", playerName ?: "toàn server")
                        val total = if (month != null) {
                            "§eTổng nạp tháng $month: §d§l${sum.format()} VNĐ"
                        } else "§eTổng nạp từ trước đến nay: §d§l${sum.format()} VNĐ"
                        val logs = logDao.getHistory(playerName, month, page)

                        val paginationBuilder = StringBuilder("/nap2s history").run {
                            if (playerName != null) append(" -p $playerName")
                            if (month != null) append(" -m $month")
                            toString()
                        }

                        val pagination = makePagination(paginationBuilder, page, maxPage, sender is Player)

                        sender.send(title)
                        sender.sendMessage(total)
                        sender.sendMessage("§f")
                        logs.forEach(sender::sendMessage)
                        sender.spigot().sendMessage(*pagination)
                    } catch (e: DateTimeParseException) {
                        sender.send("§cSai định dạng tháng. Ví dụ định dạng đúng: 01/2024")
                    }
                }
            }
        }

        private fun napThuCong() = command {
            val usage = "<tên người chơi> <số tiền> [-p <số point nhận>] [-d <nội dung nạp>] [-f force offline]"

            description("Nạp tiền thủ công cho người chơi")

            tabComplete {
                when(args.size) {
                    0 -> emptyList()
                    1 -> Bukkit.getOnlinePlayers().map { it.name }.filter { it.startsWith(args.last()) }
                    2 -> if (args.last().isEmpty()) listOf("<số tiền nạp>") else emptyList()
                    else -> when(args.takeLast(2).first()) {
                        "-p" -> if (args.last().isEmpty()) listOf("<số point nhận>") else emptyList()
                        "-d" -> if (args.last().isEmpty()) listOf("<nội dung nạp>") else emptyList()
                        else -> if (args.last().isEmpty()) listOf("-p", "-d", "-f") else emptyList()
                    }
                }
            }

            action {
                val args = args.toMutableList()
                val main = Nap2S.instance
                val cfg = main.config

                var playerName: String? = null
                var amount: Int? = null
                var point: Double? = null
                var content = "THỦ CÔNG"
                var forceOffline = false
                var extraRatePercent = 0
                var plannedExtraName: String? = null

                var index = 0
                while (args.isNotEmpty()) {
                    when (val current = args.removeFirst()) {
                        "-p" -> point = args.removeFirstArgumentOrNull().let { pointArg ->
                            if (pointArg == null) {
                                sender.send("§cBạn phải nhập số point nhận sau tùy chọn §b-p")
                                return@action
                            }
                            pointArg.toDoubleOrNull()?.takeIf { it > 0 } ?: run {
                                sender.send("§cSố point nhận phải là chữ số lớn hơn 0")
                                return@action
                            }
                        }
                        "-d" -> {
                            val sb = StringBuilder()
                            while (args.isNotEmpty()) {
                                if (args.first().matches("-[a-z]".toRegex())) break
                                sb.append(args.removeFirst()).append(" ")
                                content = sb.toString().trim().takeIf { it.length <= 20 } ?: run {
                                    sender.send("§cNội dung không được quá 20 ký tự")
                                    return@action
                                }
                            }
                        }
                        "-f" -> forceOffline = true
                        else -> when(index++) {
                            0 -> playerName = current
                            1 -> amount = current.toIntOrNull() ?: run {
                                sender.send("§cSố tiền phải là số")
                                return@action
                            }
                            else -> {
                                sender.send("§cCách dùng: /$commandTree $usage")
                                return@action
                            }
                        }
                    }
                }

                if (playerName == null || amount == null) {
                    sender.send("§cCách dùng: /$commandTree $usage")
                    return@action
                }

                // Tính số point dựa trên số tiền nạp nếu user không truyền option -p
                if (point == null) {
                    // Giá trị khuyến mãi cũ từ config.yml
                    val legacyExtraRate = if (cfg.extraUntil > System.currentTimeMillis()) cfg.extraRate else 0.0
                    // Giá trị khuyến mãi mới từ PlannedExtras
                    val plannedExtra = main.plannedExtras.getCurrentExtra()

                    // Giá trị khuyến mãi chính thức: Nếu có khuyến mãi planned thì dùng, không thì lấy legacyExtraRate
                    val extraRate = plannedExtra?.rate ?: legacyExtraRate
                    val pointPer1K = cfg.manualBase + cfg.manualExtra + (cfg.manualBase * extraRate)
                    point = (amount / 1000) * pointPer1K

                    // Lấy % khuyến mãi, đồng thời nếu là planned thì lấy tên để gửi thông báo
                    extraRatePercent = (extraRate * 100).toInt()
                    plannedExtraName = plannedExtra?.name
                }

                runNotSync { transactional {
                    try {
                        val infoDao = PlayerInfoDAO.getInstance()
                        val uuidStr = infoDao.getUUID(playerName) ?: run {
                            sender.send("§cNgười chơi $playerName không tồn tại. Có thể họ chưa vào server bao giờ?")
                            return@transactional
                        }

                        val uuid = UUID.fromString(uuidStr)
                        if (Bukkit.getPlayer(uuid)?.isOnline != true && !forceOffline) {
                            sender.send("§cNgười chơi $playerName hiện không online, vì vậy phần thưởng mốc nạp sẽ " +
                                    "không được thực hiện nếu họ đạt mốc.")
                            sender.send("§cHãy thêm tùy chọn §a-f §cvào lệnh nếu bạn vẫn muốn thực hiện nạp thủ công.")
                            return@transactional
                        }

                        main.playerPoints.api.give(UUID.fromString(uuidStr), point.toInt())
                        main.updateLeaderBoard(uuid, amount, point.toInt(), TopupType.MANUAL)
                        val logDao = LogDAO.getInstance()
                        logDao.insertLog(playerName, uuidStr, content, "--", "MANUAL", amount).let {
                            logDao.stopWaiting(it, true)
                            logDao.updatePointReceived(it, point.toInt())
                        }
                        sender.send("§aĐã nạp §d${amount.format()} VNĐ §acho người chơi §b$playerName " +
                                "§avà nhận §b${point.toInt()} §apoint")

                        if (extraRatePercent != 0) {
                            sender.send("§aNgười chơi được khuyến mãi §b$extraRatePercent% §agiá trị nạp.")
                            if (plannedExtraName != null) {
                                sender.send("§aTên chương trình khuyến mãi: §b$plannedExtraName")
                            }
                        }
                    } catch (e: Exception) {
                        sender.send("§cCó lỗi xảy ra: ${e.message}. Hãy xem Console để kiểm tra chi tiết.")
                        throw e
                    }
                }}
            }
        }

        private fun traCuuGiaoDich() = command {
            val usage = "<mã giao dịch>"
            description("Tra cứu thông tin qua mã giao dịch trên cổng gạch thẻ")

            action {
                runNotSync {
                    if (args.isEmpty()) {
                        sender.send("§cCách dùng: /$commandTree $usage")
                        return@runNotSync
                    }

                    val transactionId = args.first()
                    val logDao = LogDAO.getInstance()
                    val transactionDetails = logDao.getTransactionDetailsById(transactionId).firstOrNull()
                    if (transactionDetails == null) {
                        sender.send("§cMã giao dịch $transactionId không tồn tại hoặc giao dịch đã thất bại.")
                        return@runNotSync
                    }
                    transactionDetails.forEach(sender::sendMessage)
                }
            }
        }

        private fun clearPlayerData() = command {
            val usage = "<tên người chơi>"
            description("Xóa toàn bộ dữ liệu của người chơi")

            /**
             * Chức năng chính của command
             */
            fun removePlayerData(sender: CommandSender, playerName: String) {
                val infoDao = PlayerInfoDAO.getInstance()
                val dataDao = PlayerDataDAO.getInstance()
                runNotSync { transactional {
                    try {
                        val uuid = infoDao.getUUID(playerName) ?: run {
                            sender.send("§cNgười chơi $playerName không tồn tại. Có thể họ chưa vào server bao giờ?")
                            return@transactional
                        }

                        val deletedCount = dataDao.deleteAllDataByUuid(uuid)
                        if (deletedCount > 0) {
                            sender.send("§aĐã xóa toàn bộ dữ liệu của người chơi §b$playerName")
                        } else {
                            sender.send("§cNgười chơi §b$playerName§c hiện không có dữ liệu để xóa")
                        }
                    } catch (e: Exception) {
                        sender.send("§cCó lỗi xảy ra: ${e.message}. Hãy xem Console để kiểm tra chi tiết.")
                        throw e
                    }
                }}
            }

            tabComplete {
                when {
                    args.isEmpty() -> emptyList()
                    args.size == 1 -> Bukkit.getOnlinePlayers().map { it.name }
                        .filter { it.lowercase().startsWith(args.last().lowercase()) }
                    else -> emptyList()
                }
            }

            action {
                if (args.isEmpty()) {
                    sender.send("§cCách dùng: /$commandTree $usage")
                    return@action
                }

                val playerName = args.first()
                val player = sender as? Player ?: run {
                    // Nếu lệnh chạy trên console thì bỏ qua confirm
                    removePlayerData(sender, playerName)
                    return@action
                }

                // Nếu gõ lệnh này trong server thì yêu cầu xác nhận
                player.send("§cBạn sắp xóa toàn bộ dữ liệu của người chơi §b$playerName§c.")
                player.send("§eNhập §a§lXACNHAN §eđể xác nhận, hoặc nhập §c§lHUY §eđể hủy bỏ")

                ChatListener(player) {
                    if (message.equals("huy", true)) {
                        player.send("§aĐã hủy bỏ xóa dữ liệu")
                        return@ChatListener
                    }

                    if (!message.equals("XACNHAN", true)) {
                        player.send("§cXác nhận không hợp lệ. Đã hủy bỏ xóa dữ liệu")
                        return@ChatListener
                    }

                    removePlayerData(sender, playerName)
                }
            }
        }

        /**
         * Lưu config nhạy cảm lên database (encrypted).
         * 2 dạng:
         *   /nap2s setkey <banking-key> <value>
         *   /nap2s setkey <provider-id> <field> <value>
         * Staff không thể đọc được key này từ file config.
         */
        private fun setSecureKey() = command {
            val bankingKeys = SecureConfigManager.SUPPORTED_KEYS
            val providerFields = SecureConfigManager.PROVIDER_FIELDS
            val providerIds = CardProvider.getProviders().map { it.id }

            description("Lưu config bảo mật lên database (encrypted)")

            tabComplete {
                when (args.size) {
                    0 -> emptyList()
                    1 -> {
                        val suggestions = bankingKeys.keys.toMutableList()
                        suggestions.addAll(providerIds)
                        suggestions.filter { it.startsWith(args.last()) }
                    }
                    2 -> {
                        if (args[0] in providerIds) {
                            providerFields.filter { it.startsWith(args.last()) }
                        } else if (args.last().isEmpty()) listOf("<giá trị>") else emptyList()
                    }
                    3 -> if (args[0] in providerIds && args.last().isEmpty()) listOf("<giá trị>") else emptyList()
                    else -> emptyList()
                }
            }

            action {
                if (args.isEmpty()) {
                    sender.send("§b§lCách dùng:")
                    sender.send("  §e/$commandTree <key> <giá trị> §8— Banking config")
                    sender.send("  §e/$commandTree <provider-id> <field> <giá trị> §8— Provider thẻ")
                    sender.send("")
                    sender.send("§7Banking keys: §f${bankingKeys.keys.joinToString(", ")}")
                    sender.send("§7Provider fields: §f${providerFields.joinToString(", ")}")
                    if (providerIds.isNotEmpty()) {
                        sender.send("§7Providers đang hoạt động: §f${providerIds.joinToString(", ")}")
                    }
                    return@action
                }

                // Dạng 1: banking key (VD: setkey sepay-api-token abc123)
                if (args[0] in bankingKeys) {
                    if (args.size < 2) {
                        sender.send("§cCách dùng: /$commandTree ${args[0]} <giá trị>")
                        return@action
                    }
                    val key = args[0]
                    val value = args.drop(1).joinToString(" ")
                    runNotSync {
                        SecureConfigManager.setSecure(key, value)
                        AuditLog.log("SETKEY", sender.name, "banking key: $key")
                        sender.send("§aĐã lưu §b$key §abảo mật lên database!")
                        sender.send("§7Xóa giá trị trong file config để tăng bảo mật.")
                        sender.send("§eGõ §b/nap2s reload §eđể áp dụng.")
                    }
                    return@action
                }

                // Dạng 2: provider key (VD: setkey provider-1 partner-key abc123)
                // Kiểm tra nếu user nhầm nhập family thay vì provider ID
                val knownFamilies = listOf("chargingws-v2", "card2k", "gachthe1s", "gamebank", "default")
                if (args[0].lowercase() in knownFamilies) {
                    sender.send("§c'${args[0]}' là tên family (chuẩn API), không phải provider ID.")
                    sender.send("§7Provider ID là tên bạn đặt trong §fproviders.yml §7(mục providers:)")
                    if (providerIds.isNotEmpty()) {
                        sender.send("§7Các provider đang hoạt động: §b${providerIds.joinToString(", ")}")
                    }
                    sender.send("§7VD: §b/nap2s setkey provider-1 partner-key abc123")
                    return@action
                }

                if (args.size < 3) {
                    sender.send("§cCách dùng: /$commandTree <provider-id> <field> <giá trị>")
                    sender.send("§7Provider ID = tên cổng trong §fproviders.yml")
                    sender.send("§7Fields: ${providerFields.joinToString(", ")}")
                    if (providerIds.isNotEmpty()) {
                        sender.send("§7Providers đang hoạt động: §b${providerIds.joinToString(", ")}")
                    }
                    return@action
                }

                val providerId = args[0]
                val field = args[1]
                val value = args.drop(2).joinToString(" ")

                if (field !in providerFields) {
                    sender.send("§cField '$field' không hợp lệ. Chấp nhận: ${providerFields.joinToString(", ")}")
                    return@action
                }

                runNotSync {
                    SecureConfigManager.setProviderKey(providerId, field, value)
                    AuditLog.log("SETKEY", sender.name, "provider key: $providerId/$field")
                    sender.send("§aĐã lưu §b$providerId/$field §abảo mật lên database!")
                    sender.send("§7Xóa giá trị trong providers.yml để tăng bảo mật.")
                    sender.send("§eGõ §b/nap2s reload §eđể áp dụng.")
                }
            }
        }

        /**
         * Xem danh sách config bảo mật đang lưu trong DB
         */
        private fun viewSecureKeys() = command {
            description("Xem danh sách config bảo mật đang lưu trong database")

            action {
                runNotSync {
                    var found = false

                    // Banking keys
                    sender.send("§b§l[Banking] Config bảo mật:")
                    SecureConfigManager.SUPPORTED_KEYS.forEach { (key, desc) ->
                        val value = SecureConfigManager.getSecure(key)
                        if (value != null) {
                            found = true
                            val masked = if (value.length > 4) "****${value.takeLast(4)}" else "****"
                            sender.send("  §a✔ §b$key §8= §7$masked §8($desc)")
                        }
                    }

                    // Provider keys
                    val providerIds = CardProvider.getProviders().map { it.id }
                    if (providerIds.isNotEmpty()) {
                        sender.send("§b§l[Providers] Config bảo mật:")
                        for (pid in providerIds) {
                            var providerHasKeys = false
                            for (field in SecureConfigManager.PROVIDER_FIELDS) {
                                val value = SecureConfigManager.getProviderKey(pid, field, "")
                                if (value.isNotBlank()) {
                                    found = true
                                    providerHasKeys = true
                                    val masked = if (value.length > 4) "****${value.takeLast(4)}" else "****"
                                    sender.send("  §a✔ §b$pid/$field §8= §7$masked")
                                }
                            }
                            if (!providerHasKeys) {
                                sender.send("  §7$pid §8— chưa có key nào")
                            }
                        }
                    }

                    if (!found) {
                        sender.send("§7Chưa có config bảo mật nào. Dùng §b/nap2s setkey §7để thêm.")
                    }
                }
            }
        }

        /**
         * Xóa config bảo mật khỏi database
         */
        private fun deleteSecureKey() = command {
            description("Xóa config bảo mật khỏi database")

            tabComplete {
                when (args.size) {
                    0 -> emptyList()
                    1 -> {
                        val suggestions = SecureConfigManager.SUPPORTED_KEYS.keys.toMutableList()
                        suggestions.addAll(CardProvider.getProviders().map { it.id })
                        suggestions.filter { it.startsWith(args.last()) }
                    }
                    2 -> if (args[0] in CardProvider.getProviders().map { it.id }) {
                        SecureConfigManager.PROVIDER_FIELDS.filter { it.startsWith(args.last()) }
                    } else emptyList()
                    else -> emptyList()
                }
            }

            action {
                if (args.isEmpty()) {
                    sender.send("§b§lCách dùng:")
                    sender.send("  §e/$commandTree <key> §8— Xóa banking key")
                    sender.send("  §e/$commandTree <provider-id> <field> §8— Xóa provider key")
                    return@action
                }

                if (args[0] in SecureConfigManager.SUPPORTED_KEYS) {
                    val key = args[0]
                    runNotSync {
                        SecureConfigManager.deleteSecure(key)
                        AuditLog.log("DELETEKEY", sender.name, "banking key: $key")
                        sender.send("§aĐã xóa §b$key §akhỏi database.")
                        sender.send("§eGõ §b/nap2s reload §eđể áp dụng.")
                    }
                    return@action
                }

                if (args.size < 2) {
                    sender.send("§cCách dùng: /$commandTree <provider-id> <field>")
                    sender.send("§7Fields: ${SecureConfigManager.PROVIDER_FIELDS.joinToString(", ")}")
                    return@action
                }

                val providerId = args[0]
                val field = args[1]
                if (field !in SecureConfigManager.PROVIDER_FIELDS) {
                    sender.send("§cField '$field' không hợp lệ. Chấp nhận: ${SecureConfigManager.PROVIDER_FIELDS.joinToString(", ")}")
                    return@action
                }

                runNotSync {
                    SecureConfigManager.deleteProviderKey(providerId, field)
                    AuditLog.log("DELETEKEY", sender.name, "provider key: $providerId/$field")
                    sender.send("§aĐã xóa §b$providerId/$field §akhỏi database.")
                    sender.send("§eGõ §b/nap2s reload §eđể áp dụng.")
                }
            }
        }

        /**
         * Xem audit log gần nhất
         */
        private fun auditLogCmd() = command {
            description("Xem audit log thao tác admin")

            action {
                runNotSync {
                    val logs = AuditLog.getRecent(20)
                    sender.send("§b§l══ AUDIT LOG (20 gần nhất) ══")
                    if (logs.isEmpty()) {
                        sender.send("  §7Chưa có audit log nào.")
                    } else {
                        logs.forEach { sender.send("  §7$it") }
                    }
                    sender.send("§b§l════════════════════════")
                }
            }
        }

        /**
         * Removes and returns the first argument from the list if it exists and is not an option flag.
         *
         * @receiver MutableList<String> The list from which the first argument will be removed.
         * @return The first argument if it exists and is not an option flag, or null otherwise.
         */
        private fun MutableList<String>.removeFirstArgumentOrNull(): String? {
            if (isEmpty() || first().matches("-[a-z]".toRegex())) return null
            return removeFirst()
        }
    }
}

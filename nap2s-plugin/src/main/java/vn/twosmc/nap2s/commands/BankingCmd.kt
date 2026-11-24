package vn.twosmc.nap2s.commands

import vn.twosmc.nap2s.Nap2S
import vn.twosmc.nap2s.banking.BankingService
import vn.twosmc.nap2s.compat.BedrockCompat
import vn.twosmc.nap2s.compat.BedrockForms
import vn.twosmc.nap2s.gui.BankingAmountUI
import vn.twosmc.nap2s.utils.Utils.Companion.format
import vn.twosmc.nap2s.utils.Utils.Companion.send
import vn.twosmc.nap2s.platform.command.command
import org.bukkit.entity.Player

class BankingCmd {
    companion object {
        fun init() {
            command {
                tabComplete {
                    // Banking tắt → không gợi ý gì ngoài lichsu
                    if (!BankingService.isEnabled()) {
                        return@tabComplete when (args.size) {
                            1 -> listOf("lichsu").filter { it.startsWith(args.last(), true) }
                            else -> emptyList()
                        }
                    }
                    when (args.size) {
                        1 -> (BankingService.getAllowedAmounts().map { it.toString() }
                            + listOf("gui", "status", "cancel", "clear", "lichsu"))
                            .filter { it.startsWith(args.last(), true) }
                        else -> emptyList()
                    }
                }

                action {
                    val player = sender as? Player ?: run {
                        sender.sendMessage("§cLệnh này chỉ dành cho người chơi.")
                        return@action
                    }

                    // Cho phép xem lịch sử ngay cả khi banking tắt
                    if (args.size == 1 && args.first().equals("lichsu", true)) {
                        if (BedrockCompat.isBedrockPlayer(player) && BedrockForms.sendHistoryForm(player)) {
                            return@action
                        }
                        player.send("§eDùng §b/nap2s lichsu §eđể xem lịch sử giao dịch.")
                        return@action
                    }

                    // Banking tắt (hoặc bị khóa do config lỗi) → chặn mọi thao tác khác
                    if (!BankingService.isEnabled()) {
                        player.send(Nap2S.instance.language.bankingDisabled)
                        return@action
                    }

                    // Bedrock player không có args → native form
                    if (args.isEmpty()) {
                        if (BedrockCompat.isBedrockPlayer(player) && BedrockForms.sendNapAtmForm(player)) {
                            return@action
                        }
                        BankingAmountUI(player)
                        return@action
                    }

                    when (args.first().lowercase()) {
                        "gui" -> {
                            if (BedrockCompat.isBedrockPlayer(player) && BedrockForms.sendNapAtmForm(player)) {
                                return@action
                            }
                            BankingAmountUI(player)
                        }
                        "status" -> BankingService.showStatus(player)
                        "cancel", "clear" -> BankingService.cancelOrder(player)
                        else -> {
                            val amount = args.first().toIntOrNull()
                            if (amount == null) {
                                player.send(
                                    Nap2S.instance.language.bankingInvalidAmount.replace(
                                        "%AMOUNTS%",
                                        "${Nap2S.instance.banking.minCustomAmount.format()} - " +
                                            "${Nap2S.instance.banking.maxCustomAmount.format()} (bội số 1.000)"
                                    )
                                )
                                return@action
                            }
                            BankingService.createOrder(player, amount)
                        }
                    }
                }

                register(Nap2S.instance, "napatm")
            }
        }
    }
}

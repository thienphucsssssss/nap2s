package vn.twosmc.nap2s.commands

import vn.twosmc.nap2s.Nap2S
import vn.twosmc.nap2s.card.Card
import vn.twosmc.nap2s.card.CardPrice
import vn.twosmc.nap2s.card.CardType
import vn.twosmc.nap2s.gui.CardTypeUI
import vn.twosmc.nap2s.compat.BedrockCompat
import vn.twosmc.nap2s.compat.BedrockForms
import vn.twosmc.nap2s.providers.CardProvider
import vn.twosmc.nap2s.utils.Utils.Companion.send
import vn.twosmc.nap2s.platform.command.command
import org.bukkit.entity.Player

class MainCmd {
    companion object {
        fun init() {
            val main = Nap2S.instance

            // lệnh nạp thẻ
            // cú pháp nạp nhanh: /napthe <loại thẻ> <mệnh giá> <số seri> <mã thẻ>
            // subcommands: /napthe lichsu, /napthe gui
            command {
                tabComplete {
                    when (args.size) {
                        1 -> {
                            val sub = listOf("lichsu", "gui")
                            val types = CardProvider.getAvailableCardType().map { it.name.lowercase() }
                            (sub + types).filter { it.startsWith(args.last().lowercase()) }
                        }
                        2 -> CardPrice.entries.map { it.value.toString() }
                            .filter { it.startsWith(args.last()) }
                        3 -> listOf("seri")
                        4 -> listOf("code")
                        else -> emptyList()
                    }
                }

                action a@{
                    if (sender !is Player) {
                        sender.sendMessage("§cLệnh này chỉ dành cho người chơi")
                        return@a
                    }
                    val player = sender as Player

                    // Subcommand: lichsu
                    if (args.size == 1 && args[0].equals("lichsu", true)) {
                        if (BedrockCompat.isBedrockPlayer(player) && BedrockForms.sendHistoryForm(player)) {
                            return@a
                        }
                        // Java player fallback: dùng chat history hiện tại
                        player.send("§eDùng §b/nap2s lichsu §eđể xem lịch sử giao dịch.")
                        return@a
                    }

                    // nạp nhanh
                    if (args.size == 4) {
                        val cardType = CardType[args[0]]
                        val cardPrice = CardPrice[args[1].toInt()]
                        val cardSeri = args[2]
                        val cardCode = args[3]
                        if (cardType == null) {
                            player.send(main.language.errorUnknownCardType)
                            return@a
                        }
                        if (cardPrice == null) {
                            player.send(main.language.errorUnknownCardPrice)
                            return@a
                        }
                        val card = Card(cardSeri, cardCode, cardPrice, cardType)
                        CardProvider.submitCard(player, card)
                    } else {
                        // Bedrock → native form, fallback chest GUI nếu form thất bại
                        if (BedrockCompat.isBedrockPlayer(player) && BedrockForms.sendNapTheForm(player)) {
                            return@a
                        }
                        CardTypeUI(player)
                    }
                }

                register(Nap2S.instance, "napthe")
            }
        }
    }
}

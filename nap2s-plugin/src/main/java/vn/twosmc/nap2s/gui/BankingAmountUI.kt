package vn.twosmc.nap2s.gui

import vn.twosmc.nap2s.Nap2S
import vn.twosmc.nap2s.banking.BankingService
import vn.twosmc.nap2s.config.BankingConfig
import vn.twosmc.nap2s.platform.input.InputMode
import vn.twosmc.nap2s.platform.input.InputRouter
import vn.twosmc.nap2s.utils.Utils.Companion.format
import vn.twosmc.nap2s.utils.Utils.Companion.send
import vn.twosmc.nap2s.platform.gui.GuiInventory
import vn.twosmc.nap2s.platform.gui.GuiItemStack
import vn.twosmc.nap2s.platform.gui.color
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta

class BankingAmountUI(private val viewer: Player) {

    private val bankingConfig: BankingConfig
        get() = Nap2S.instance.banking

    init {
        if (!BankingService.isEnabled()) {
            viewer.send(Nap2S.instance.language.bankingDisabled)
        } else {
            buildAndOpen()
        }
    }

    private fun buildAndOpen() {
        val amounts = BankingService.getAllowedAmounts()
        val bonusPercent = bankingConfig.rewardBonusPercent
        val amountRows = amounts.chunked(9).size.coerceAtLeast(1)
        val rows = (amountRows + 1).coerceAtLeast(3)

        val gui = GuiInventory(
            Nap2S.instance, rows * 9,
            "&8[&6&lNap2S&8] &fNạp tiền ngân hàng".color()
        )
        gui.lock()

        val bgItem = createItem(Material.BLACK_STAINED_GLASS_PANE, " ")
        for (i in 0 until rows * 9) {
            gui.setItem(i, GuiItemStack(bgItem))
        }

        amounts.chunked(9).forEachIndexed { rowIndex, rowAmounts ->
            val rowStart = (rowIndex + 1) * 9
            val startOffset = ((9 - rowAmounts.size) / 2).coerceAtLeast(0)

            rowAmounts.forEachIndexed { index, amount ->
                val slot = rowStart + startOffset + index
                val pointAmount = vn.twosmc.nap2s.card.CardPrice[amount]?.getPointAmount() ?: (amount / 1000)
                val bonusAmount = if (bonusPercent > 0) (pointAmount * bonusPercent) / 100 else 0
                val totalPoints = pointAmount + bonusAmount

                val item = createItem(
                    Material.GOLD_INGOT,
                    "&e&l${amount.format()} VNĐ".color(),
                    listOfNotNull(
                        "&7Nhận được: &f${pointAmount.format()} ${Nap2S.instance.config.pointUnit}".color(),
                        if (bonusPercent > 0) "&aThưởng ATM +${bonusPercent}%: &e+${bonusAmount.format()} ${Nap2S.instance.config.pointUnit}".color() else null,
                        if (bonusPercent > 0) "&6&lTổng nhận: &e&l${totalPoints.format()} ${Nap2S.instance.config.pointUnit}".color() else null,
                        "",
                        "&eNhấp để tạo giao dịch".color()
                    )
                )

                gui.setItem(
                    slot,
                    GuiItemStack(item).onClick {
                        viewer.closeInventory()
                        BankingService.createOrder(viewer, amount)
                    }
                )
            }
        }

        if (bankingConfig.allowCustomAmount) {
            val customSlot = ((rows - 1) * 9) + 4
            val accentItem = createItem(Material.GRAY_STAINED_GLASS_PANE, " ")
            gui.setItem(customSlot - 1, GuiItemStack(accentItem))
            gui.setItem(customSlot + 1, GuiItemStack(accentItem))

            val customItem = createItem(
                Material.NAME_TAG,
                "&d&lNhập số tiền".color(),
                listOf(
                    "&7Nhấp để nhập số tiền theo ý bạn".color(),
                    "&7Tối thiểu: &f${bankingConfig.minCustomAmount.format()} VNĐ".color(),
                    "&7Tối đa: &f${bankingConfig.maxCustomAmount.format()} VNĐ".color(),
                    "&7Chỉ nhận bội số của &f1.000 VNĐ".color(),
                    if (bonusPercent > 0) "&aThưởng ATM +${bonusPercent}%".color() else "&7Không có thưởng thêm".color()
                )
            )
            gui.setItem(
                customSlot,
                GuiItemStack(customItem).onClick {
                    viewer.closeInventory()
                    promptCustomAmount(viewer)
                }
            )
        }

        gui.unlock()
        gui.openIventory(viewer)
    }

    private fun promptCustomAmount(player: Player) {
        val lang = Nap2S.instance.language
        val mode = Nap2S.instance.config.bankingInputMode

        // Prompt chỉ gửi khi mode = CHAT (anvil hiển title trên GUI, không cần chat prompt)
        if (mode == InputMode.CHAT) {
            player.send(
                lang.bankingCustomAmountPrompt
                    .replace("%MIN%", bankingConfig.minCustomAmount.format())
                    .replace("%MAX%", bankingConfig.maxCustomAmount.format())
            )
        }

        InputRouter.requestInput(
            player = player,
            mode = mode,
            title = "Nhập số tiền (VNĐ)",
            initialText = bankingConfig.minCustomAmount.toString(),
            onSubmit = { input ->
                val trimmed = input.trim()
                if (trimmed.equals("HỦY", true) || trimmed.equals("HUY", true)
                    || trimmed.equals("cancel", true)) {
                    player.send(lang.inputCanceled)
                    return@requestInput
                }

                if (!trimmed.matches(Regex("\\d+"))) {
                    player.send(
                        lang.bankingInvalidAmount.replace(
                            "%AMOUNTS%",
                            "${bankingConfig.minCustomAmount.format()} - ${bankingConfig.maxCustomAmount.format()} (bội số 1.000)"
                        )
                    )
                    return@requestInput
                }

                val amount = trimmed.toIntOrNull()
                if (amount == null || !bankingConfig.isValidCustomAmount(amount)) {
                    player.send(
                        lang.bankingInvalidAmount.replace(
                            "%AMOUNTS%",
                            "${bankingConfig.minCustomAmount.format()} - ${bankingConfig.maxCustomAmount.format()} (bội số 1.000)"
                        )
                    )
                    return@requestInput
                }

                BankingService.createOrder(player, amount)
            },
            onCancel = { player.send(lang.inputCanceled) }
        )
    }

    companion object {
        private fun createItem(material: Material, name: String, lore: List<String> = emptyList()): ItemStack {
            val item = ItemStack(material)
            val meta: ItemMeta = item.itemMeta ?: return item
            meta.setDisplayName(name)
            if (lore.isNotEmpty()) {
                meta.lore = lore
            }
            item.itemMeta = meta
            return item
        }
    }
}

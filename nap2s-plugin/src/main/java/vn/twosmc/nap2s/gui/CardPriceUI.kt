package vn.twosmc.nap2s.gui

import vn.twosmc.nap2s.Nap2S
import vn.twosmc.nap2s.card.CardPrice
import vn.twosmc.nap2s.card.CardType
import vn.twosmc.nap2s.providers.CardProvider
import vn.twosmc.nap2s.utils.Utils.Companion.format
import vn.twosmc.nap2s.platform.gui.ConfiguredUI
import vn.twosmc.nap2s.platform.gui.GuiIcon.Companion.getGuiIcon
import vn.twosmc.nap2s.platform.gui.getGuiFillSlots
import vn.twosmc.nap2s.platform.gui.color
import org.bukkit.entity.Player

class CardPriceUI(viewer: Player?, private val cardType: CardType) :
    ConfiguredUI(viewer, "menu/napthe/menhgia.yml", Nap2S.instance) {

    constructor() : this(null, CardType.GARENA)

    init {
        if (viewer?.isOnline == true) {
            // Build + open trên main thread — Bukkit GUI phải sync
            build()
        }
    }

    private fun build() {
        val viewer = viewer!!
        val config = getConfig()
        val route = CardProvider.getRoute(cardType, requireActive = false)
        val bonus = route?.bonusPercent ?: 0

        fun applyTokens(value: String, price: CardPrice? = null): String {
            val replacements = mapOf(
                "%CARD_TYPE%" to cardType.displayName,
                "%PROVIDER%" to (route?.provider?.displayName ?: "&8Đang cập nhật".color()),
                "%RATE%" to (route?.formattedPayoutRate() ?: "--"),
                "%BONUS%" to (if (bonus > 0) "+$bonus%" else "0%"),
                "%BONUS_BADGE%" to (route?.bonusBadge?.ifBlank { "&7Ổn định".color() } ?: "&7Ổn định".color()),
                "%BONUS_DESCRIPTION%" to (
                    route?.bonusDescription?.ifBlank {
                        if (bonus > 0) "&7Cộng thêm &6$bonus% &7xu".color() else "&7Tỷ lệ ổn định".color()
                    } ?: "&7Tỷ lệ ổn định".color()
                ),
                "%PRICE%" to (price?.value?.format() ?: "0")
            )

            return replacements.entries.fold(value) { current, (key, replacement) ->
                current.replace(key, replacement)
            }.color()
        }

        lock()

        setItem(
            config.getGuiFillSlots("background.fill"),
            config.getGuiIcon("background").toGuiItemStack()
        )
        setItem(
            config.getGuiFillSlots("frame.fill"),
            config.getGuiIcon("frame").toGuiItemStack()
        )

        setItem(
            config.getInt("header.slot"),
            config.getGuiIcon("header").apply {
                name = applyTokens(name)
                lore = lore.map { applyTokens(it) }
            }.toGuiItemStack()
        )

        setItem(
            config.getInt("route-summary.slot"),
            config.getGuiIcon("route-summary").apply {
                name = applyTokens(name)
                lore = lore.map { applyTokens(it) }
            }.toGuiItemStack()
        )

        setItem(
            config.getInt("guide.slot"),
            config.getGuiIcon("guide").apply {
                name = applyTokens(name)
                lore = lore.map { applyTokens(it) }
            }.toGuiItemStack()
        )

        setItem(
            config.getInt("bonus-summary.slot"),
            config.getGuiIcon("bonus-summary").apply {
                name = applyTokens(name)
                lore = lore.map { applyTokens(it) }
            }.toGuiItemStack()
        )

        setItem(
            config.getInt("close.slot"),
            config.getGuiIcon("close").toGuiItemStack {
                CardTypeUI(viewer)
            }
        )

        val priceSlots = config.getGuiFillSlots("prices.fill")
        val priceIcon = config.getGuiIcon("prices")
        val priceEntries = CardPrice.entries
        for (i in priceSlots.indices) {
            if (i >= priceEntries.size) break

            val price = priceEntries[i]
            val button = priceIcon.clone().apply {
                name = applyTokens(name, price)
                lore = lore.map { applyTokens(it, price) }
            }.toGuiItemStack {
                CardProvider.askCardInfo(viewer, cardType, price)
            }

            setItem(priceSlots[i], button)
        }

        unlock()
        open()
    }
}

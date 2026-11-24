package vn.twosmc.nap2s.gui

import vn.twosmc.nap2s.Nap2S
import vn.twosmc.nap2s.card.CardType
import vn.twosmc.nap2s.database.ConfigDAO
import vn.twosmc.nap2s.providers.CardProvider
import vn.twosmc.nap2s.platform.gui.ConfiguredUI
import vn.twosmc.nap2s.platform.gui.GuiIcon.Companion.getGuiIcon
import vn.twosmc.nap2s.platform.gui.GuiItemStack
import vn.twosmc.nap2s.platform.gui.getGuiFillSlots
import vn.twosmc.nap2s.platform.gui.color
import vn.twosmc.nap2s.platform.gui.split
import org.bukkit.entity.Player

class CardTypeUI(viewer: Player?) : ConfiguredUI(viewer, "menu/napthe/loaithe.yml", Nap2S.instance) {

    constructor() : this(null)

    private val lang = Nap2S.instance.language

    init {
        if (viewer?.isOnline == true) {
            // Build + open trên main thread — Bukkit GUI phải sync
            build()
        }
    }

    private fun build() {
        val configDao = ConfigDAO.getInstance()
        val viewer = viewer!!
        val config = getConfig()
        val featured = CardProvider.getFeaturedRoute()
        val featuredCard = featured?.first?.displayName ?: "Chưa có"
        val featuredRoute = featured?.second
        val routeMode = if (CardProvider.isMultiProviderMode()) "Tối ưu tỷ lệ" else "Tương thích cũ"
        val providerCount = CardProvider.getProviders().size.toString()
        val defaultProvider = CardProvider.getDefaultProvider()?.displayName ?: "&cChưa có".color()

        fun applyTokens(value: String, replacements: Map<String, String>): String {
            return replacements.entries.fold(value) { current, (key, replacement) ->
                current.replace(key, replacement)
            }.color()
        }

        fun replacementMap(cardType: CardType? = null): Map<String, String> {
            val route = cardType?.let { CardProvider.getRoute(it, requireActive = false) }
            val bonus = route?.bonusPercent ?: 0
            return mapOf(
                "%ROUTING_MODE%" to routeMode,
                "%PROVIDER_COUNT%" to providerCount,
                "%DEFAULT_PROVIDER%" to defaultProvider,
                "%FEATURED_CARD%" to featuredCard,
                "%FEATURED_PROVIDER%" to (featuredRoute?.provider?.displayName ?: "&8Đang cập nhật".color()),
                "%FEATURED_RATE%" to (featuredRoute?.formattedPayoutRate() ?: "--"),
                "%FEATURED_BONUS%" to (if ((featuredRoute?.bonusPercent ?: 0) > 0) "+${featuredRoute?.bonusPercent}%" else "0%"),
                "%CARD_TYPE%" to (cardType?.displayName ?: featuredCard),
                "%STATUS%" to if (cardType?.isActive() == true) "&aSẵn sàng".color() else "&cTạm dừng".color(),
                "%PROVIDER%" to (route?.provider?.displayName ?: "&8Đang cập nhật".color()),
                "%RATE%" to (route?.formattedPayoutRate() ?: "--"),
                "%BONUS%" to (if (bonus > 0) "+$bonus%" else "0%"),
                "%BONUS_BADGE%" to (route?.bonusBadge?.ifBlank { "&7Ổn định".color() } ?: "&7Ổn định".color()),
                "%BONUS_DESCRIPTION%" to (
                    route?.bonusDescription?.ifBlank {
                        if (bonus > 0) "&7Cộng thêm &6$bonus% &7xu".color() else "&7Tỷ lệ ổn định".color()
                    } ?: "&7Tỷ lệ ổn định".color()
                )
            )
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
                val replacements = replacementMap()
                name = applyTokens(name, replacements)
                lore = lore.map { applyTokens(it, replacements) }
            }.toGuiItemStack()
        )

        setItem(
            config.getInt("info.slot"),
            config.getGuiIcon("info").apply {
                val message = configDao.get("announcement") ?: lang.uiNoAnnouncement
                lore = message.color().replace("\\n", "\n").split(30)
            }.toGuiItemStack()
        )

        setItem(
            config.getInt("route-summary.slot"),
            config.getGuiIcon("route-summary").apply {
                val replacements = replacementMap()
                name = applyTokens(name, replacements)
                lore = lore.map { applyTokens(it, replacements) }
            }.toGuiItemStack()
        )

        setItem(
            config.getInt("banking-recommend.slot"),
            config.getGuiIcon("banking-recommend").apply {
                val replacements = replacementMap()
                name = applyTokens(name, replacements)
                lore = lore.map { applyTokens(it, replacements) }
            }.toGuiItemStack()
        )

        setItem(
            config.getInt("close.slot"),
            config.getGuiIcon("close").toGuiItemStack {
                viewer.closeInventory()
            }
        )

        val cardSlots = config.getGuiFillSlots("cards.fill")
        val activeIcon = config.getGuiIcon("cards.active")
        val disabledIcon = config.getGuiIcon("cards.disabled")
        val blankIcon = config.getGuiIcon("cards.blank")
        val cardTypes = CardType.entries
            .sortedBy { !it.isActive() }
            .filter { Nap2S.instance.config.cardTypes[it] == true }
            .toTypedArray()
        for (i in cardSlots.indices) {
            val slotId = cardSlots[i]
            val button: GuiItemStack
            if (i < cardTypes.size) {
                val cardType = cardTypes[i]
                val icon = (if (cardType.isActive()) activeIcon else disabledIcon).clone().apply {
                    val replacements = replacementMap(cardType)
                    name = applyTokens(name, replacements)
                    lore = lore.map { applyTokens(it, replacements) }
                }
                button = icon.toGuiItemStack {
                    if (cardType.isActive()) {
                        CardPriceUI(viewer, cardType)
                    }
                }
            } else {
                button = blankIcon.toGuiItemStack()
            }

            setItem(slotId, button)
        }

        unlock()
        open()
    }
}

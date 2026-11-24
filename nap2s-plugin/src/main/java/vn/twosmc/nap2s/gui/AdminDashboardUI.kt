package vn.twosmc.nap2s.gui

import vn.twosmc.nap2s.Nap2S
import vn.twosmc.nap2s.card.CardType
import vn.twosmc.nap2s.providers.CardProvider
import vn.twosmc.nap2s.platform.gui.ConfiguredUI
import vn.twosmc.nap2s.platform.gui.GuiIcon.Companion.getGuiIcon
import vn.twosmc.nap2s.platform.gui.GuiItemStack
import vn.twosmc.nap2s.platform.gui.getGuiFillSlots
import vn.twosmc.nap2s.platform.gui.color
import org.bukkit.entity.Player

class AdminDashboardUI(viewer: Player?) : ConfiguredUI(viewer, "menu/admin/dashboard.yml", Nap2S.instance) {

    constructor() : this(null)

    init {
        if (viewer?.isOnline == true) {
            // Build + open trên main thread — Bukkit GUI phải sync
            build()
        }
    }

    private fun build() {
        val viewer = viewer!!
        val config = getConfig()
        val providers = CardProvider.getProviders()
        val featured = CardProvider.getFeaturedRoute()
        val routeMode = if (CardProvider.isMultiProviderMode()) "Nhiều cổng nạp" else "Tương thích cũ"
        val defaultProvider = CardProvider.getDefaultProvider()?.displayName ?: "&cChưa có".color()

        fun applyTokens(value: String, replacements: Map<String, String>): String {
            return replacements.entries.fold(value) { current, (key, replacement) ->
                current.replace(key, replacement)
            }.color()
        }

        fun summaryReplacements(): Map<String, String> {
            val featuredRoute = featured?.second
            return mapOf(
                "%ROUTING_MODE%" to routeMode,
                "%DEFAULT_PROVIDER%" to defaultProvider,
                "%PROVIDER_COUNT%" to providers.size.toString(),
                "%FEATURED_CARD%" to (featured?.first?.displayName ?: "Chưa có"),
                "%FEATURED_PROVIDER%" to (featuredRoute?.provider?.displayName ?: "&8Đang cập nhật".color())
            )
        }

        fun cardReplacements(cardType: CardType): Map<String, String> {
            val route = CardProvider.getRoute(cardType, requireActive = false)
            val bonus = route?.bonusPercent ?: 0
            return mapOf(
                "%CARD_TYPE%" to cardType.displayName,
                "%STATUS%" to if (cardType.isActive()) "&aSẵn sàng".color() else "&cTạm dừng".color(),
                "%PROVIDER%" to (route?.provider?.displayName ?: "&8Chưa có".color()),
                "%RATE%" to (route?.formattedPayoutRate() ?: "--"),
                "%BONUS%" to (if (bonus > 0) "+$bonus%" else "0%"),
                "%BONUS_DESCRIPTION%" to (
                    route?.bonusDescription?.ifBlank {
                        if (bonus > 0) "&7Cộng thêm &6$bonus% &7xu".color() else "&7Không có ưu đãi riêng".color()
                    } ?: "&7Không có ưu đãi riêng".color()
                )
            )
        }

        fun providerLore(): List<String> {
            if (providers.isEmpty()) {
                return config.getStringList("providers.lore").map(String::color)
            }

            return providers.mapIndexed { index, provider ->
                val routedCards = CardType.entries.count {
                    Nap2S.instance.config.cardTypes[it] == true &&
                        CardProvider.getRoute(it, requireActive = false)?.provider?.id == provider.id
                }
                "&f${index + 1}. ${provider.displayName} &8- &7Ưu tiên: &f${provider.priority} &8| &7Phụ trách: &f$routedCards &7loại thẻ"
                    .color()
            }
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
                val replacements = summaryReplacements()
                name = applyTokens(name, replacements)
                lore = lore.map { applyTokens(it, replacements) }
            }.toGuiItemStack()
        )

        setItem(
            config.getInt("overview.slot"),
            config.getGuiIcon("overview").apply {
                val replacements = summaryReplacements()
                name = applyTokens(name, replacements)
                lore = lore.map { applyTokens(it, replacements) }
            }.toGuiItemStack()
        )

        setItem(
            config.getInt("providers.slot"),
            config.getGuiIcon("providers").apply {
                lore = providerLore()
            }.toGuiItemStack()
        )

        setItem(
            config.getInt("guide.slot"),
            config.getGuiIcon("guide").apply {
                val replacements = summaryReplacements()
                name = applyTokens(name, replacements)
                lore = lore.map { applyTokens(it, replacements) }
            }.toGuiItemStack()
        )

        setItem(
            config.getInt("reload.slot"),
            config.getGuiIcon("reload").toGuiItemStack {
                Nap2S.instance.reload()
                viewer.sendMessage("§aNap2S đã reload xong, dashboard đã được làm mới.")
                AdminDashboardUI(viewer)
            }
        )

        setItem(
            config.getInt("legend.slot"),
            config.getGuiIcon("legend").toGuiItemStack()
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
            .filter { Nap2S.instance.config.cardTypes[it] == true }
            .toTypedArray()

        for (i in cardSlots.indices) {
            val slotId = cardSlots[i]
            val button: GuiItemStack
            if (i < cardTypes.size) {
                val cardType = cardTypes[i]
                val icon = (if (cardType.isActive()) activeIcon else disabledIcon).clone().apply {
                    val replacements = cardReplacements(cardType)
                    name = applyTokens(name, replacements)
                    lore = lore.map { applyTokens(it, replacements) }
                }
                button = icon.toGuiItemStack()
            } else {
                button = blankIcon.toGuiItemStack()
            }

            setItem(slotId, button)
        }

        unlock()
        open()
    }
}

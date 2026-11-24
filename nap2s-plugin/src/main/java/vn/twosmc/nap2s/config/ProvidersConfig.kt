package vn.twosmc.nap2s.config

import vn.twosmc.nap2s.card.CardType
import vn.twosmc.nap2s.utils.Utils.Companion.color
import org.bukkit.configuration.ConfigurationSection

class ProvidersConfig : FileConfig("providers") {
    val routingMode = RoutingMode.of(config.getString("routing.mode"))
    val fallbackToLegacy = config.getBoolean("routing.fallback-to-legacy", true)
    val defaultProviderId = config.getString("routing.default-provider")?.trim().orEmpty()

    val routeOverrides = CardType.entries.mapNotNull { type ->
        val value = config.getString("routing.overrides.${type.name.lowercase()}")?.trim()
            ?.takeUnless { it.isNullOrBlank() || it.equals("auto", true) }
            ?: return@mapNotNull null
        type to value
    }.toMap()

    val promotions = CardType.entries.mapNotNull { type ->
        val section = config.getConfigurationSection("promotions.cards.${type.name.lowercase()}")
            ?: return@mapNotNull null
        val bonusPercent = section.getInt("bonus-percent", 0)
        val description = section.getString("description", "")!!.color()
        if (bonusPercent <= 0 && description.isBlank()) {
            return@mapNotNull null
        }

        type to CardPromotion(
            bonusPercent = bonusPercent,
            badge = section.getString("badge", "&6Ưu tiên")!!.color(),
            description = description
        )
    }.toMap()

    val providers = config.getConfigurationSection("providers")
        ?.getKeys(false)
        ?.mapNotNull { id ->
            val section = config.getConfigurationSection("providers.$id") ?: return@mapNotNull null
            if (!section.getBoolean("enabled", false)) {
                return@mapNotNull null
            }

            val cardsSection = section.getConfigurationSection("cards")
            val cards = CardType.entries.mapNotNull { type ->
                val cardSection = cardsSection?.getConfigurationSection(type.name.lowercase())
                    ?: return@mapNotNull null
                if (!cardSection.getBoolean("enabled", true)) {
                    return@mapNotNull null
                }

                type to ProviderCardConfig(
                    payoutRate = cardSection.getDouble("payout-rate", 0.0),
                    providerCode = cardSection.getString("provider-code")
                        ?.trim()
                        ?.takeUnless { it.isBlank() }
                )
            }.toMap()

            if (cards.isEmpty()) {
                return@mapNotNull null
            }

            ProviderDefinition(
                id = id,
                displayName = section.getString("display-name", id)!!.color(),
                family = section.getString("family", "chargingws-v2")!!.lowercase(),
                priority = section.getInt("priority", 0),
                section = section,
                cards = cards
            )
        }
        ?.associateBy { it.id }
        ?: emptyMap()

    data class ProviderDefinition(
        val id: String,
        val displayName: String,
        val family: String,
        val priority: Int,
        val section: ConfigurationSection,
        val cards: Map<CardType, ProviderCardConfig>
    )

    data class ProviderCardConfig(
        val payoutRate: Double,
        val providerCode: String?
    )

    data class CardPromotion(
        val bonusPercent: Int,
        val badge: String,
        val description: String
    )

    enum class RoutingMode {
        BEST_RATE,
        PRIORITY;

        companion object {
            fun of(value: String?) = when (value?.trim()?.lowercase()) {
                "priority" -> PRIORITY
                else -> BEST_RATE
            }
        }
    }
}

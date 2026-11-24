package vn.twosmc.nap2s.providers

import vn.twosmc.nap2s.Nap2S
import vn.twosmc.nap2s.Nap2S.Companion.transactional
import vn.twosmc.nap2s.card.Card
import vn.twosmc.nap2s.card.CardPrice
import vn.twosmc.nap2s.card.CardResult
import vn.twosmc.nap2s.card.CardType
import vn.twosmc.nap2s.card.CardWaiting
import vn.twosmc.nap2s.config.FileConfig
import vn.twosmc.nap2s.config.ProvidersConfig
import vn.twosmc.nap2s.config.SecureConfigManager
import vn.twosmc.nap2s.database.LogDAO
import vn.twosmc.nap2s.security.DonationRateLimiter
import vn.twosmc.nap2s.providers.types.Card2KCP
import vn.twosmc.nap2s.providers.types.CardVipCP
import vn.twosmc.nap2s.providers.types.ChargingWsV2CP
import vn.twosmc.nap2s.providers.types.GachThe1SCP
import vn.twosmc.nap2s.providers.types.TheSieuReCP

import vn.twosmc.nap2s.utils.Utils.Companion.format
import vn.twosmc.nap2s.utils.Utils.Companion.runAsyncTimer
import vn.twosmc.nap2s.utils.Utils.Companion.runNotSync
import vn.twosmc.nap2s.utils.Utils.Companion.runSync
import vn.twosmc.nap2s.utils.Utils.Companion.send
import vn.twosmc.nap2s.utils.Utils.Companion.severe
import vn.twosmc.nap2s.utils.Utils.Companion.warning
import vn.twosmc.nap2s.platform.chat.ChatListener
import vn.twosmc.nap2s.platform.input.InputRouter
import vn.twosmc.nap2s.platform.utils.sendMessages
import vn.twosmc.nap2s.platform.utils.httpPost
import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import java.util.Locale
import java.util.UUID

abstract class CardProvider(private val definition: ProvidersConfig.ProviderDefinition) {
    protected val main = Nap2S.instance
    val id get() = definition.id
    val displayName get() = definition.displayName
    val priority get() = definition.priority

    private var task: BukkitTask? = null
    protected open var statusCards = definition.cards.keys.associateWith { true }

    companion object {
        lateinit var instance: CardProvider
            private set

        private val providers = linkedMapOf<String, CardProvider>()
        private var routingMode = ProvidersConfig.RoutingMode.BEST_RATE
        private var defaultProviderId = ""
        private var routeOverrides = emptyMap<CardType, String>()
        private var promotions = emptyMap<CardType, ProvidersConfig.CardPromotion>()
        private var multiProviderMode = false

        fun init(settings: ProvidersConfig, legacyProviderId: String) {
            stopAll()
            providers.clear()
            promotions = settings.promotions
            routeOverrides = settings.routeOverrides
            routingMode = settings.routingMode

            val modernProviders = settings.providers.values.mapNotNull { definition ->
                runCatching {
                    createProvider(definition)
                }.onFailure { ex ->
                    warning("Không thể tải provider ${definition.id}: ${ex.message}")
                }.getOrNull()
            }

            if (modernProviders.isNotEmpty()) {
                multiProviderMode = true
                modernProviders.forEach { providers[it.id] = it }
                defaultProviderId = settings.defaultProviderId
                    .takeIf { providers.containsKey(it) }
                    ?: providers.keys.first()
            } else {
                if (!settings.fallbackToLegacy) {
                    throw IllegalStateException("Không có provider nào được bật trong providers.yml")
                }

                multiProviderMode = false
                val legacyProvider = createLegacyProvider(legacyProviderId)
                providers[legacyProvider.id] = legacyProvider
                defaultProviderId = legacyProvider.id
            }

            instance = providers[defaultProviderId] ?: providers.values.first()
            providers.values.forEach(CardProvider::startTask)
        }

        fun isMultiProviderMode() = multiProviderMode

        fun getProviders(): List<CardProvider> = providers.values.toList()

        fun getDefaultProvider(): CardProvider? = providers[defaultProviderId]

        fun getStatus(type: CardType) = getRoute(type) != null

        fun getAvailableCardType() = CardType.entries.filter {
            Nap2S.instance.config.cardTypes[it] == true && getStatus(it)
        }

        fun getPromotion(type: CardType) = promotions[type]

        fun getRoute(type: CardType, requireActive: Boolean = true): CardRoute? {
            if (Nap2S.instance.config.cardTypes[type] != true) {
                return null
            }

            val candidate = getCandidates(type, requireActive).firstOrNull() ?: return null
            val promotion = promotions[type]
            return CardRoute(
                provider = candidate.provider,
                payoutRate = candidate.payoutRate,
                bonusPercent = promotion?.bonusPercent ?: 0,
                bonusBadge = promotion?.badge.orEmpty(),
                bonusDescription = promotion?.description.orEmpty()
            )
        }

        fun getFeaturedRoute(): Pair<CardType, CardRoute>? {
            return CardType.entries
                .filter { Nap2S.instance.config.cardTypes[it] == true }
                .mapNotNull { type ->
                    (getRoute(type, requireActive = true) ?: getRoute(type, requireActive = false))
                        ?.let { type to it }
                }
                .sortedWith(
                    compareByDescending<Pair<CardType, CardRoute>> { it.second.bonusPercent }
                        .thenByDescending { it.second.payoutRate }
                        .thenBy { it.first.ordinal }
                )
                .firstOrNull()
        }

        /** Cooldown map: playerUUID -> lastSubmitTime */
        private val cooldowns = mutableMapOf<UUID, Long>()
        private const val COOLDOWN_MS = 5000L

        fun submitCard(player: Player, card: Card) {
            val now = System.currentTimeMillis()
            val lastSubmit = cooldowns[player.uniqueId]
            if (lastSubmit != null && now - lastSubmit < COOLDOWN_MS) {
                player.send("§cVui lòng đợi vài giây trước khi nạp thẻ tiếp.")
                return
            }
            DonationRateLimiter.checkCardLimit(player.uniqueId)?.let {
                player.send(it)
                return
            }
            DonationRateLimiter.checkAmountLimit(player.uniqueId, card.price.value)?.let {
                player.send(it)
                return
            }
            cooldowns[player.uniqueId] = now

            val route = getRoute(card.type) ?: run {
                player.send(
                    Nap2S.instance.language.cardRouteUnavailable
                        .replace("%CARD_TYPE%", card.type.displayName)
                )
                return
            }

            card.providerId = route.provider.id

            if (route.bonusPercent > 0) {
                player.send(
                    Nap2S.instance.language.cardRouteBonus
                        .replace("%CARD_TYPE%", card.type.displayName)
                        .replace("%BONUS%", route.bonusPercent.toString())
                        .replace("%POINT_UNIT%", Nap2S.instance.config.pointUnit)
                )
            }

            route.provider.processCard(player, card)
        }

        fun askCardInfo(player: Player, type: CardType, price: CardPrice) {
            val route = getRoute(type) ?: run {
                player.send(
                    Nap2S.instance.language.cardRouteUnavailable
                        .replace("%CARD_TYPE%", type.displayName)
                )
                return
            }

            route.provider.askCardInfo(player, type, price)
        }

        private fun createProvider(definition: ProvidersConfig.ProviderDefinition): CardProvider {
            val section = definition.section
            val id = definition.id

            // Helper: đọc credential từ DB (encrypted) → fallback file config
            fun secureGet(field: String) = SecureConfigManager.getProviderKey(
                id, field, section.getString(field) ?: ""
            )

            return when (definition.family) {
                "chargingws-v2" -> ChargingWsV2CP(
                    definition = definition,
                    partnerId = secureGet("partner-id"),
                    partnerKey = secureGet("partner-key"),
                    apiUrl = secureGet("api-url")
                )

                "gachthe1s" -> GachThe1SCP(
                    definition = definition,
                    partnerId = secureGet("partner-id"),
                    partnerKey = secureGet("partner-key"),
                    apiUrl = secureGet("api-url")
                )

                "card2k" -> Card2KCP(
                    definition = definition,
                    partnerId = secureGet("partner-id"),
                    partnerKey = secureGet("partner-key")
                )



                "thesieure" -> TheSieuReCP(
                    definition = definition,
                    partnerId = secureGet("partner-id"),
                    partnerKey = secureGet("partner-key")
                )

                "cardvip" -> CardVipCP(
                    definition = definition,
                    partnerId = secureGet("partner-id"),
                    partnerKey = secureGet("partner-key")
                )

                else -> throw IllegalArgumentException("Provider family ${definition.family} không được hỗ trợ")
            }
        }

        private fun createLegacyProvider(providerId: String): CardProvider {
            val config = FileConfig("providers/$providerId").apply { reload() }.config
            val definition = legacyDefinition(
                if (providerId.equals("default", true)) "chargingws-v2" else providerId
            )
            fun secureGet(field: String) = SecureConfigManager.getProviderKey(
                providerId, field, config.getString(field) ?: ""
            )
            return when (providerId.lowercase()) {
                "default", "chargingws-v2" -> ChargingWsV2CP(
                    definition = definition,
                    partnerId = secureGet("partner-id"),
                    partnerKey = secureGet("partner-key"),
                    apiUrl = secureGet("api-url")
                )

                // TheSieuToc đã ngừng hoạt động (30/4/2026)

                // GameBank đã bị loại bỏ

                "card2k" -> Card2KCP(
                    definition = definition,
                    partnerId = secureGet("partner-id"),
                    partnerKey = secureGet("partner-key")
                )

                "gachthe1s" -> GachThe1SCP(
                    definition = definition,
                    partnerId = secureGet("partner-id"),
                    partnerKey = secureGet("partner-key"),
                    apiUrl = secureGet("api-url")
                )

                else -> throw IllegalArgumentException("Không tìm thấy provider $providerId")
            }
        }

        private fun legacyDefinition(providerId: String): ProvidersConfig.ProviderDefinition {
            val yaml = YamlConfiguration()
            return ProvidersConfig.ProviderDefinition(
                id = providerId,
                displayName = when (providerId.lowercase()) {
                    "gachthe1s" -> "§6GachThe1S"
                    "card2k" -> "§eCard2K"

                    else -> providerId
                },
                family = providerId.lowercase(),
                priority = 0,
                section = yaml,
                cards = legacyCards(
                    CardType.VIETTEL,
                    CardType.MOBIFONE,
                    CardType.VINAPHONE,
                    CardType.GATE,
                    CardType.ZING,
                    CardType.GARENA,
                    CardType.VCOIN,
                    CardType.VIETNAMOBILE,
                    CardType.SCOIN
                )
            )
        }

        private fun legacyCards(vararg types: CardType) = types.associateWith {
            ProvidersConfig.ProviderCardConfig(
                payoutRate = 0.0,
                providerCode = null
            )
        }

        private fun stopAll() {
            providers.values.forEach(CardProvider::stopTask)
        }

        private fun getCandidates(type: CardType, requireActive: Boolean): List<ProviderCandidate> {
            val overrideId = routeOverrides[type]

            return providers.values
                .mapNotNull { provider ->
                    val config = provider.getCardConfig(type) ?: return@mapNotNull null
                    if (requireActive && !provider.getStatus(type)) {
                        return@mapNotNull null
                    }

                    ProviderCandidate(
                        provider = provider,
                        payoutRate = config.payoutRate,
                        priority = provider.priority,
                        isOverride = provider.id == overrideId
                    )
                }
                .sortedWith(
                    compareByDescending<ProviderCandidate> { it.isOverride }
                        .thenByDescending {
                            if (routingMode == ProvidersConfig.RoutingMode.BEST_RATE) it.payoutRate else it.priority.toDouble()
                        }
                        .thenByDescending {
                            if (routingMode == ProvidersConfig.RoutingMode.BEST_RATE) it.priority.toDouble() else it.payoutRate
                        }
                        .thenBy { it.provider.id }
                )
        }
    }

    protected fun getEnabledCards() = definition.cards.keys

    protected fun getCardConfig(type: CardType) = definition.cards[type]

    private fun startTask() {
        stopTask()
        task = runAsyncTimer(0L, 20L * 10, ::updateStatus)
    }

    private fun stopTask() {
        task?.cancel()
        task = null
    }

    protected abstract fun getApiUrl(): String
    open fun getStatusUrl() = ""

    fun processCard(player: Player, card: Card) {
        val lang = main.language

        // Validate seri/pin chỉ chứa ký tự hợp lệ (chữ số và chữ cái)
        val validPattern = Regex("^[a-zA-Z0-9]+$")
        if (!card.seri.matches(validPattern)) {
            player.send("§cSố seri chỉ được chứa chữ cái và chữ số.")
            return
        }
        if (!card.pin.matches(validPattern)) {
            player.send("§cMã thẻ chỉ được chứa chữ cái và chữ số.")
            return
        }

        if (card.seri.length > 20) {
            player.send(lang.errorSeriTooLong)
            return
        }
        if (card.pin.length > 20) {
            player.send(lang.errorPinTooLong)
            return
        }

        player.sendMessages(lang.cardCharging.map {
            it.replace("%CARD_TYPE%", card.type.displayName)
                .replace("%CARD_PRICE%", card.price.value.format())
                .replace("%SERI%", card.seri)
                .replace("%CODE%", card.pin)
                .replace("%PROVIDER%", displayName)
        })

        runNotSync {
            runCatching {
                val log = LogDAO.getInstance()
                card.logId = log.insertLog(player, card)
                val result = doRequest(player.name, card)

                if (result.isSuccess) {
                    onRequestSuccess(player, result)
                } else {
                    player.sendMessages(lang.cardChargedFailed.map {
                        it.replace("%ERROR%", result.message ?: lang.errorUnknown)
                    })
                }
            }.onFailure {
                it.warning("Lỗi nạp thẻ: ${it.message}")
                player.sendMessages(lang.cardChargedError)
            }
        }
    }

    open fun onRequestSuccess(player: Player, result: CardResult) {
        onChargeSuccess(player, result.card)
    }

    protected open fun onChargeSuccess(player: Player, card: Card) = transactional {
        val baseAmount = card.price.getPointAmount()
        var amount = baseAmount
        val commands = card.price.getCommands().map { it.replace("%PLAYER%", player.name) }
        val config = main.config
        var extraPercent = 0
        var plannedExtraName: String? = null

        val plannedExtra = main.plannedExtras.getCurrentExtra()
        if (plannedExtra != null) {
            amount = plannedExtra.calculateAmount(amount)
            extraPercent = plannedExtra.getPercentage()
            plannedExtraName = plannedExtra.name
        } else if (config.extraRate > 0 && config.extraUntil > System.currentTimeMillis()) {
            amount += (amount * config.extraRate).toInt()
            extraPercent = (config.extraRate * 100).toInt()
        }

        val cardBonus = getPromotion(card.type)?.bonusPercent ?: 0
        if (cardBonus > 0) {
            amount += (amount * cardBonus) / 100
        }

        Nap2S.instance.playerPoints.api.give(player.uniqueId, amount)

        main.language.cardChargedSuccessfully.map {
            it.replace("%AMOUNT%", amount.toString())
                .replace("%POINT_UNIT%", main.config.pointUnit)
        }.let { player.sendMessages(it) }

        if (config.announceCharge) {
            main.language.cardChargedAnnounce.forEach {
                it.replace("%PLAYER%", player.name)
                    .replace("%CARD_PRICE%", card.price.value.toString())
                    .replace("%AMOUNT%", amount.toString())
                    .replace("%POINT_UNIT%", main.config.pointUnit)
                    .apply { Bukkit.broadcastMessage(this) }
            }
        }
        if (extraPercent > 0) {
            val extraMsg = if (plannedExtraName != null) {
                main.language.cardChargedWithExtraPlanned
                    .replace("%EXTRA_NAME%", plannedExtraName)
            } else {
                main.language.cardChargedWithExtra
            }
                .replace("%RATE%", extraPercent.toString())
                .replace("%PERCENT%", extraPercent.toString())

            player.send(extraMsg)
        }
        if (cardBonus > 0) {
            player.send(
                main.language.cardChargedWithCardBonus
                    .replace("%CARD_TYPE%", card.type.displayName)
                    .replace("%BONUS%", cardBonus.toString())
                    .replace("%POINT_UNIT%", main.config.pointUnit)
            )
        }
        if (card.logId != null) {
            LogDAO.getInstance().updatePointReceived(card.logId!!, amount)
        }

        DonationRateLimiter.recordCard(player.uniqueId, card.price.value)
        main.updateLeaderBoard(player.uniqueId, card.price.value, amount)
        runSync {
            commands.forEach { Bukkit.dispatchCommand(Bukkit.getConsoleSender(), it) }
        }
    }

    open fun doRequest(playerName: String, card: Card): CardResult {
        val parameters = getRequestParameters(playerName, card)
        val headers = getRequestHeaders(playerName, card)
        return parseResponse(card, httpPost(getApiUrl(), parameters = parameters, headers = headers))
    }

    protected abstract fun parseResponse(card: Card, response: String): CardResult

    protected abstract fun getRequestParameters(playerName: String, card: Card): Map<String, String>

    protected open fun getRequestHeaders(playerName: String, card: Card): Map<String, String>? = null

    fun getStatus(type: CardType) = statusCards.getOrDefault(type, false)

    fun askCardInfo(player: Player, type: CardType, price: CardPrice) {
        val lang = main.language
        player.closeInventory()

        // Bước 1: Nhập seri — qua InputRouter (anvil hoặc chat tuỳ config)
        InputRouter.requestInput(
            player = player,
            mode = main.config.cardInputMode,
            title = lang.inputAnvilSeri,
            placeholder = lang.inputAnvilSeri,
            onSubmit = { seri ->
                if (seri.isBlank()) {
                    player.send(lang.inputCanceled)
                    return@requestInput
                }

                // Bước 2: Nhập mã thẻ (pending đã clear trước callback → không bị self-block)
                InputRouter.requestInput(
                    player = player,
                    mode = main.config.cardInputMode,
                    title = lang.inputAnvilPin,
                    placeholder = lang.inputAnvilPin,
                    onSubmit = { pin ->
                        if (pin.isBlank()) {
                            player.send(lang.inputCanceled)
                            return@requestInput
                        }
                        val card = Card(seri, pin, price, type).apply {
                            providerId = id
                        }
                        runNotSync { processCard(player, card) }
                    },
                    onCancel = { player.send(lang.inputCanceled) }
                )
            },
            onCancel = { player.send(lang.inputCanceled) }
        )
    }

    protected open fun updateStatus() {
    }

    protected open fun CardWaiting.isProcessed() = false

    protected fun checkWaitingCards() {
        val players = Bukkit.getOnlinePlayers().associateBy { it.uniqueId.toString() }
        val uuids = players.keys.toTypedArray()
        val server = main.config.server
        val cardLogger = LogDAO.getInstance()
        val lang = main.language

        cardLogger.getWaitingCards(uuids, server, id, id == defaultProviderId)
            ?.filter { it.isProcessed() }
            ?.forEach {
                val player = players[it.uuid]
                if (player?.isOnline != true) return@forEach

                cardLogger.stopWaiting(it.id, it.isSuccess)

                if (it.isSuccess) {
                    try {
                        onChargeSuccess(player, it.toCard())
                    } catch (e: Exception) {
                        player.sendMessages(lang.cardChargedError)
                        e.severe("Lỗi onChargeSuccess: Player ${player.name}, ${it.toCard()}")
                    }
                } else {
                    val message = it.message ?: lang.errorUnknown
                    player.sendMessages(lang.cardChargedFailed.map { str ->
                        str.replace("%ERROR%", message)
                    })
                }
            }
    }

    data class CardRoute(
        val provider: CardProvider,
        val payoutRate: Double,
        val bonusPercent: Int,
        val bonusBadge: String,
        val bonusDescription: String
    ) {
        fun formattedPayoutRate(): String {
            val normalized = if (payoutRate <= 0) 0.0 else payoutRate
            return if (normalized % 1.0 == 0.0) {
                normalized.toInt().toString()
            } else {
                String.format(Locale.US, "%.1f", normalized)
            }
        }
    }

    private data class ProviderCandidate(
        val provider: CardProvider,
        val payoutRate: Double,
        val priority: Int,
        val isOverride: Boolean
    )
}

package vn.twosmc.nap2s.providers.types

import vn.twosmc.nap2s.card.Card
import vn.twosmc.nap2s.card.CardResult
import vn.twosmc.nap2s.card.CardType
import vn.twosmc.nap2s.card.CardWaiting
import vn.twosmc.nap2s.config.ProvidersConfig
import vn.twosmc.nap2s.database.LogDAO
import vn.twosmc.nap2s.providers.CardProvider
import vn.twosmc.nap2s.utils.Utils.Companion.md5
import vn.twosmc.nap2s.platform.utils.asIntOrNull
import vn.twosmc.nap2s.platform.utils.asStringOrNull
import vn.twosmc.nap2s.platform.utils.sendMessages
import vn.twosmc.nap2s.platform.utils.httpGet
import vn.twosmc.nap2s.platform.utils.parseJson
import org.bukkit.entity.Player

open class ChargingWsV2CP(
    definition: ProvidersConfig.ProviderDefinition,
    private val partnerId: String,
    private val partnerKey: String,
    private val apiUrl: String,
    private val builtInCodes: Map<CardType, String> = emptyMap()
) : CardProvider(definition) {

    init {
        statusCards = getEnabledCards().associateWith { true }
    }

    override fun getApiUrl() = apiUrl

    override fun getRequestParameters(playerName: String, card: Card): Map<String, String> {
        val telcoCode = getTelcoCode(card.type)
            ?: throw IllegalArgumentException("Loại thẻ ${card.type.displayName} chưa được cấu hình cho provider $id")

        return mapOf(
            "partner_id" to partnerId,
            "request_id" to randomRequestId(),
            "code" to card.pin,
            "serial" to card.seri,
            "telco" to telcoCode,
            "amount" to card.price.value.toString(),
            "sign" to (partnerKey + card.pin + card.seri).md5(),
            "command" to "charging"
        )
    }

    override fun parseResponse(card: Card, response: String) = CardResult(card).apply {
        response.parseJson().asJsonObject.let {
            val status = it["status"].asStringOrNull()
            isSuccess = status == "1" || status == "99"
            message = it["message"].asStringOrNull()

            it["request_id"].asStringOrNull()?.let { transactionId ->
                LogDAO.getInstance().setTransactionId(card.logId!!, transactionId, false)
            }
        }
    }

    override fun onRequestSuccess(player: Player, result: CardResult) {
        LogDAO.getInstance().setWaiting(result.card.logId!!)
        player.sendMessages(main.language.cardChargedSent)
    }

    override fun CardWaiting.isProcessed(): Boolean {
        val card = toCard()
        val telcoCode = getTelcoCode(card.type) ?: return false
        val params = mapOf(
            "partner_id" to partnerId,
            "request_id" to transactionId,
            "telco" to telcoCode,
            "amount" to price.toString(),
            "code" to card.pin,
            "serial" to card.seri,
            "sign" to (partnerKey + card.pin + card.seri).md5(),
            "command" to "check"
        )

        return httpGet(getApiUrl(), parameters = params)
            .parseJson()
            .asJsonObject
            .let {
                val status = it["status"].asIntOrNull() ?: return@let false
                isSuccess = status == 1
                message = when (status) {
                    1 -> null
                    100 -> "Thẻ sai hoặc đã sử dụng"
                    3 -> "Lỗi hệ thống, vui lòng liên hệ admin để được hỗ trợ"
                    2 -> "Nhập sai mệnh giá, giao dịch không thể xử lý thành công"
                    else -> "Lỗi không xác định (status: $status)"
                }
                status != 99
            }
    }

    override fun updateStatus() = checkWaitingCards()

    protected fun getTelcoCode(type: CardType): String? {
        val configuredCode = getCardConfig(type)?.providerCode
        return configuredCode ?: builtInCodes[type] ?: defaultTelcoCode(type)
    }

    private fun randomRequestId() = ('A'..'Z').let { charPool ->
        (1..10).map { charPool.random() }.joinToString("") + (0..9).random()
    }

    private fun defaultTelcoCode(type: CardType) = when (type) {
        CardType.VIETTEL,
        CardType.MOBIFONE,
        CardType.VINAPHONE,
        CardType.GATE,
        CardType.ZING,
        CardType.VCOIN,
        CardType.GARENA,
        CardType.SCOIN -> type.name
        CardType.VIETNAMOBILE -> "VNMOBI"
    }
}

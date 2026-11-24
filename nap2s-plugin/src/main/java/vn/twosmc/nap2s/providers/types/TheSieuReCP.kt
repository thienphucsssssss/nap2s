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

/**
 * Provider cho thesieure.com
 * API endpoint: /chargingws/v2
 * Sign khác ChargingWsV2CP: MD5(partner_key + code + command + partner_id + request_id + serial + telco)
 */
class TheSieuReCP(
    definition: ProvidersConfig.ProviderDefinition,
    private val partnerId: String,
    private val partnerKey: String
) : CardProvider(definition) {

    init {
        statusCards = getEnabledCards().associateWith { true }
    }

    override fun getApiUrl() = "https://thesieure.com/chargingws/v2"

    /**
     * Tạo chữ ký MD5 theo yêu cầu của thesieure.com
     * Công thức: MD5(partner_key + code + command + partner_id + request_id + serial + telco)
     */
    private fun makeSign(code: String, command: String, requestId: String, serial: String, telco: String): String {
        return (partnerKey + code + command + partnerId + requestId + serial + telco).md5()
    }

    override fun getRequestParameters(playerName: String, card: Card): Map<String, String> {
        val telcoCode = getTelcoCode(card.type)
            ?: throw IllegalArgumentException("Loại thẻ ${card.type.displayName} chưa được cấu hình cho provider $id")

        val requestId = randomRequestId()
        val command = "charging"

        return mapOf(
            "partner_id" to partnerId,
            "request_id" to requestId,
            "code" to card.pin,
            "serial" to card.seri,
            "telco" to telcoCode,
            "amount" to card.price.value.toString(),
            "sign" to makeSign(card.pin, command, requestId, card.seri, telcoCode),
            "command" to command
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

    /**
     * Kiểm tra trạng thái thẻ đang chờ, dùng command = "check" cùng endpoint.
     */
    override fun CardWaiting.isProcessed(): Boolean {
        val card = toCard()
        val telcoCode = getTelcoCode(card.type) ?: return false
        val command = "check"

        val params = mapOf(
            "partner_id" to partnerId,
            "request_id" to transactionId,
            "telco" to telcoCode,
            "amount" to price.toString(),
            "code" to card.pin,
            "serial" to card.seri,
            "sign" to makeSign(card.pin, command, transactionId, card.seri, telcoCode),
            "command" to command
        )

        return httpGet(getApiUrl(), parameters = params)
            .parseJson()
            .asJsonObject
            .let {
                val status = it["status"].asIntOrNull() ?: return@let false
                isSuccess = status == 1
                message = when (status) {
                    1 -> null
                    2 -> "Nhập sai mệnh giá, giao dịch không thể xử lý thành công"
                    3 -> "Thẻ sai hoặc đã sử dụng"
                    4 -> "Hệ thống đang bảo trì"
                    100 -> "Gửi thẻ thất bại (lỗi định dạng hoặc thông tin)"
                    else -> "Lỗi không xác định (status: $status)"
                }
                status != 99
            }
    }

    override fun updateStatus() = checkWaitingCards()

    /**
     * Mapping CardType -> mã telco cho thesieure.com.
     * Vietnamobile dùng mã VNMOBI.
     */
    private fun getTelcoCode(type: CardType): String? {
        val configuredCode = getCardConfig(type)?.providerCode
        return configuredCode ?: defaultTelcoCode(type)
    }

    /** Tạo request ID ngẫu nhiên cho mỗi giao dịch. */
    private fun randomRequestId() = ('A'..'Z').let { charPool ->
        (1..10).map { charPool.random() }.joinToString("") + (0..9).random()
    }

    private fun defaultTelcoCode(type: CardType) = when (type) {
        CardType.VIETTEL,
        CardType.MOBIFONE,
        CardType.VINAPHONE,
        CardType.ZING,
        CardType.VCOIN,
        CardType.GARENA,
        CardType.SCOIN -> type.name
        CardType.VIETNAMOBILE -> "VNMOBI"
        CardType.GATE -> null
    }
}

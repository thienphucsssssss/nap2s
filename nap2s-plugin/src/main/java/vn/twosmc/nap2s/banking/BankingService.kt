package vn.twosmc.nap2s.banking

import net.md_5.bungee.api.ChatMessageType
import net.md_5.bungee.api.chat.TextComponent
import vn.twosmc.nap2s.Nap2S
import vn.twosmc.nap2s.Nap2S.Companion.transactional
import vn.twosmc.nap2s.TopupType
import vn.twosmc.nap2s.card.CardPrice
import vn.twosmc.nap2s.config.BankingConfig
import vn.twosmc.nap2s.database.BankingOrderDAO
import vn.twosmc.nap2s.database.LogDAO
import vn.twosmc.nap2s.utils.Utils.Companion.format
import vn.twosmc.nap2s.utils.Utils.Companion.info
import vn.twosmc.nap2s.utils.Utils.Companion.runAsyncTimer
import vn.twosmc.nap2s.utils.Utils.Companion.runNotSync
import vn.twosmc.nap2s.utils.Utils.Companion.runSync
import vn.twosmc.nap2s.utils.Utils.Companion.send
import vn.twosmc.nap2s.utils.Utils.Companion.warning
import vn.twosmc.nap2s.security.DonationRateLimiter
import vn.twosmc.nap2s.webserver.EmbeddedWebServer
import vn.twosmc.nap2s.platform.utils.sendMessages
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object BankingService {
    private const val LEGACY_LOG_CARD_FIELD_MAX = 20
    private const val LOG_TRANSACTION_ID_MAX = 32

    private lateinit var config: BankingConfig
    private var task: BukkitTask? = null
    private var countdownTask: BukkitTask? = null
    private val cooldowns = ConcurrentHashMap<UUID, Long>()
    private val pendingCountdowns = ConcurrentHashMap<UUID, Long>()

    /** Cờ khóa banking khi config lỗi — reset khi init() lại */
    private var forceDisabled = false

    fun init(config: BankingConfig) {
        this.config = config
        forceDisabled = false
        stop()
        if (!config.enabled) return

        if (config.webserverEnabled) {
            EmbeddedWebServer.start(config.webserverPort)
        }

        if (config.providerId.equals("payos", ignoreCase = true)) {
            runNotSync {
                runCatching {
                    PayOSClient.confirmWebhook(config)
                }.onFailure { ex ->
                    ex.warning("Khong the xac nhan webhook PayOS")
                }
            }
        }

        task = runAsyncTimer(20L, 20L * config.pollSeconds, Runnable { expireOverdueOrders() })
        countdownTask = runAsyncTimer(20L, 20L, Runnable { tickActionBarCountdown() })
    }

    fun stop() {
        task?.cancel()
        countdownTask?.cancel()
        task = null
        countdownTask = null
        cooldowns.clear()
        pendingCountdowns.clear()
        QrMapManager.cleanup()
        EmbeddedWebServer.stop()
    }

    /**
     * Tắt banking do config lỗi. Khác với stop():
     * - stop() chỉ dừng task, isEnabled() vẫn trả true nếu config.enabled = true
     * - forceStop() đặt cờ forceDisabled → isEnabled() trả false cho đến khi reload
     */
    fun forceStop() {
        stop()
        forceDisabled = true
    }

    fun isEnabled() = ::config.isInitialized && config.enabled && !forceDisabled

    fun getAllowedAmounts() = if (::config.isInitialized) config.allowedAmounts else emptyList()

    private fun isSupportedAmount(amount: Int): Boolean {
        return amount in config.allowedAmounts || config.isValidCustomAmount(amount)
    }

    private fun resolvePointAmount(amount: Int, cardPrice: CardPrice?): Int {
        return cardPrice?.getPointAmount() ?: (amount / 1000)
    }

    fun createOrder(player: Player, amount: Int) {
        if (!isEnabled()) {
            player.send(Nap2S.instance.language.bankingDisabled)
            return
        }

        val cardPrice = CardPrice[amount]
        if (!isSupportedAmount(amount)) {
            player.send(
                Nap2S.instance.language.bankingInvalidAmount
                    .replace("%AMOUNTS%", "${config.minCustomAmount.format()} - ${config.maxCustomAmount.format()} (bội số 1.000)")
            )
            return
        }
        val pointAmount = resolvePointAmount(amount, cardPrice)

        val now = System.currentTimeMillis()
        val lastCreate = cooldowns[player.uniqueId]
        if (lastCreate != null && now - lastCreate < config.cooldownSeconds * 1000) {
            val remain = config.cooldownSeconds - ((now - lastCreate) / 1000)
            player.send(
                Nap2S.instance.language.bankingCooldown
                    .replace("%SECONDS%", remain.coerceAtLeast(1).toString())
            )
            return
        }

        // Rate limit: giới hạn số lần nạp ATM/ngày + tổng tiền/ngày
        DonationRateLimiter.checkBankingLimit(player.uniqueId)?.let {
            player.send(it)
            return
        }
        DonationRateLimiter.checkAmountLimit(player.uniqueId, amount)?.let {
            player.send(it)
            return
        }

        runNotSync {
            val dao = BankingOrderDAO.getInstance()
            if (!config.allowMultiplePending) {
                val pending = dao.findPendingByPlayer(player.uniqueId.toString(), Nap2S.instance.config.server)
                if (pending != null) {
                    sendPendingOrder(player, pending)
                    player.send(
                        Nap2S.instance.language.bankingPendingExists
                            .replace("%ORDER_ID%", pending.orderId)
                    )
                    return@runNotSync
                }
            }

            player.send(
                Nap2S.instance.language.bankingCreating
                    .replace("%AMOUNT%", amount.format())
            )

            runCatching {
                // Tạo order tùy theo banking provider (sepay hoặc payos)
                val orderId = SepayClient.generateOrderId()
                val orderResult: OrderCreateResult = when (config.providerId.lowercase()) {
                    "payos" -> {
                        val payosResult = PayOSClient.createPaymentLink(config, amount, orderId)
                        OrderCreateResult(
                            orderId = payosResult.orderId,
                            checkoutUrl = payosResult.checkoutUrl,
                            qrImageUrl = payosResult.qrImageUrl,
                            transferContent = payosResult.transferContent,
                            bankName = payosResult.bankName,
                            bankAccountNumber = payosResult.bankAccountNumber,
                            bankAccountHolder = payosResult.bankAccountHolder,
                            expiresAt = payosResult.expiresAt
                        )
                    }
                    else -> {
                        // Mặc định: SePay (embedded mode)
                        val sepayResult = SepayClient.createLocalOrder(config, amount)
                        OrderCreateResult(
                            orderId = sepayResult.orderId,
                            checkoutUrl = sepayResult.checkoutUrl,
                            qrImageUrl = sepayResult.qrImageUrl,
                            transferContent = sepayResult.transferContent,
                            bankName = sepayResult.bankName,
                            bankAccountNumber = sepayResult.bankAccountNumber,
                            bankAccountHolder = sepayResult.bankAccountHolder,
                            expiresAt = sepayResult.expiresAt
                        )
                    }
                }

                dao.insert(
                    orderId = orderResult.orderId,
                    uuid = player.uniqueId.toString(),
                    playerName = player.name,
                    amount = amount,
                    pointAmount = pointAmount,
                    status = "PENDING",
                    checkoutUrl = orderResult.checkoutUrl,
                    qrImageUrl = orderResult.qrImageUrl,
                    transferContent = orderResult.transferContent,
                    gateway = config.providerId,
                    rawResponse = "",
                    server = Nap2S.instance.config.server,
                    expiresAt = orderResult.expiresAt,
                    bankName = orderResult.bankName,
                    bankAccountNumber = orderResult.bankAccountNumber,
                    bankAccountHolder = orderResult.bankAccountHolder
                )
                cooldowns[player.uniqueId] = System.currentTimeMillis()

                val response = CreateOrderResponse(
                    orderId = orderResult.orderId,
                    amount = amount,
                    pointAmount = pointAmount,
                    status = "PENDING",
                    checkoutUrl = orderResult.checkoutUrl,
                    qrImageUrl = orderResult.qrImageUrl,
                    transferContent = orderResult.transferContent,
                    gateway = config.providerId,
                    expiresAt = orderResult.expiresAt,
                    bankName = orderResult.bankName,
                    vaNumber = orderResult.bankAccountNumber,
                    vaHolderName = orderResult.bankAccountHolder
                )
                sendCreatedOrder(player, response)

                if (orderResult.qrImageUrl.isNotBlank()) {
                    runSync {
                        QrMapManager.giveQrMap(
                            player,
                            orderResult.orderId,
                            orderResult.qrImageUrl,
                            orderResult.checkoutUrl
                        )
                    }
                }

                if (orderResult.expiresAt > 0) {
                    pendingCountdowns[player.uniqueId] = orderResult.expiresAt
                }
            }.onFailure { ex ->
                ex.warning("Không thể tạo giao dịch QR")
                player.send(
                    Nap2S.instance.language.bankingBackendError
                        .replace("%ERROR%", ex.message ?: "Không xác định")
                )
            }
        }
    }

    fun showStatus(player: Player) {
        if (!isEnabled()) {
            player.send(Nap2S.instance.language.bankingDisabled)
            return
        }

        runNotSync {
            val pending = BankingOrderDAO.getInstance()
                .findPendingByPlayer(player.uniqueId.toString(), Nap2S.instance.config.server)

            if (pending == null) {
                player.send(Nap2S.instance.language.bankingNoPending)
                return@runNotSync
            }

            sendPendingOrder(player, pending)
        }
    }

    private fun formatCountdown(expiresAt: Long): String {
        val remain = expiresAt - System.currentTimeMillis()
        if (remain <= 0) return "Hết hạn"
        val minutes = remain / 60000
        val seconds = (remain % 60000) / 1000
        return "${minutes} phút ${seconds} giây"
    }

    private fun sendCreatedOrder(player: Player, response: CreateOrderResponse) {
        val countdown = if (response.expiresAt > 0) formatCountdown(response.expiresAt) else "không rõ"
        player.sendMessages(Nap2S.instance.language.bankingCreated.map {
            it.replace("%ORDER_ID%", response.orderId)
                .replace("%AMOUNT%", response.amount.format())
                .replace("%BANK_NAME%", response.bankName.ifBlank { "N/A" })
                .replace("%VA_NUMBER%", response.vaNumber.ifBlank { "N/A" })
                .replace("%VA_HOLDER%", response.vaHolderName.ifBlank { "N/A" })
                .replace("%CHECKOUT_URL%", response.checkoutUrl.ifBlank { "N/A" })
                .replace("%QR_IMAGE_URL%", response.qrImageUrl.ifBlank { "N/A" })
                .replace("%TRANSFER_CONTENT%", response.transferContent.ifBlank { response.orderId })
                .replace("%COUNTDOWN%", countdown)
                .replace("%EXPIRES_AT%", if (response.expiresAt > 0) response.expiresAt.toString() else "không rõ")
        })
    }

    private fun sendPendingOrder(player: Player, order: BankingOrder) {
        val countdown = if (order.expiresAt > 0) formatCountdown(order.expiresAt) else "không rõ"
        val isPayOS = order.gateway.equals("payos", ignoreCase = true)

        // Ưu tiên snapshot từ đơn, fallback config hiện tại (cho đơn cũ chưa có snapshot)
        val bankName = order.bankName.ifBlank { if (isPayOS) "PayOS" else config.bankName }.ifBlank { "N/A" }
        val bankAccNum = order.bankAccountNumber.ifBlank { if (isPayOS) "Mở link thanh toán" else config.bankAccountNumber }.ifBlank { "N/A" }
        val bankAccHolder = order.bankAccountHolder.ifBlank { if (isPayOS) "Thanh toán qua PayOS" else config.bankAccountHolder }.ifBlank { "N/A" }

        player.sendMessages(Nap2S.instance.language.bankingStatus.map {
            it.replace("%ORDER_ID%", order.orderId)
                .replace("%AMOUNT%", order.amount.format())
                .replace("%STATUS%", order.status)
                .replace("%BANK_NAME%", bankName)
                .replace("%VA_NUMBER%", bankAccNum)
                .replace("%VA_HOLDER%", bankAccHolder)
                .replace("%CHECKOUT_URL%", order.checkoutUrl.ifBlank { "N/A" })
                .replace("%QR_IMAGE_URL%", order.qrImageUrl.ifBlank { "N/A" })
                .replace("%TRANSFER_CONTENT%", order.transferContent.ifBlank { order.orderId })
                .replace("%COUNTDOWN%", countdown)
                .replace("%EXPIRES_AT%", order.expiresAt.toString())
        })
    }

    fun cancelOrder(player: Player) {
        if (!isEnabled()) {
            player.send(Nap2S.instance.language.bankingDisabled)
            return
        }
        runNotSync {
            val dao = BankingOrderDAO.getInstance()
            val pending = dao.findPendingByPlayer(player.uniqueId.toString(), Nap2S.instance.config.server)
            if (pending == null) {
                player.send(Nap2S.instance.language.bankingCancelNoOrder)
                return@runNotSync
            }
            dao.updatePendingStatus(pending.orderId, "CANCELLED", "")
            QrMapManager.removeQrMapByOrderId(pending.orderId)
            pendingCountdowns.remove(player.uniqueId)
            player.send(
                Nap2S.instance.language.bankingCancelled
                    .replace("%ORDER_ID%", pending.orderId)
            )
        }
    }

    fun handleWebhookPaid(order: BankingOrder, transactionId: String, rawResponse: String) {
        val statusResp = StatusResponse(
            orderId = order.orderId,
            status = "PAID",
            transactionId = normalizeTransactionId(transactionId, order.orderId),
            paidAt = System.currentTimeMillis()
        )
        rewardPaidOrder(order, statusResp, rawResponse)
    }

    private fun normalizeTransactionId(rawTransactionId: String, fallbackOrderId: String): String {
        val resolved = rawTransactionId.ifBlank { fallbackOrderId }.trim()
        if (resolved.length > LOG_TRANSACTION_ID_MAX) {
            warning("Mã giao dịch banking quá dài (${resolved.length}), sẽ cắt còn $LOG_TRANSACTION_ID_MAX ký tự để lưu DB.")
        }
        return resolved.take(LOG_TRANSACTION_ID_MAX)
    }

    private fun toLegacyBankLogSeri(orderId: String): String {
        return "BANK-$orderId".take(LEGACY_LOG_CARD_FIELD_MAX)
    }

    private fun toLegacyBankLogPin(orderId: String): String {
        return orderId.take(LEGACY_LOG_CARD_FIELD_MAX)
    }

    private fun expireOverdueOrders() {
        if (!isEnabled()) return

        val now = System.currentTimeMillis()
        val orders = BankingOrderDAO.getInstance().getPendingOrders(Nap2S.instance.config.server)
        orders.forEach { order ->
            if (order.expiresAt > 0 && order.expiresAt <= now) {
                BankingOrderDAO.getInstance().updatePendingStatus(order.orderId, "EXPIRED", order.rawResponse)
                QrMapManager.removeQrMapByOrderId(order.orderId)
                notifyPlayer(order.uuid) {
                    pendingCountdowns.remove(it.uniqueId)
                    it.send(
                        Nap2S.instance.language.bankingExpired
                            .replace("%ORDER_ID%", order.orderId)
                            .replace("%STATUS%", "EXPIRED")
                    )
                }
            }
        }
    }

    private fun rewardPaidOrder(order: BankingOrder, status: StatusResponse, rawResponse: String) = transactional {
        val paidAt = status.paidAt.takeIf { it > 0 } ?: System.currentTimeMillis()
        val transactionId = normalizeTransactionId(status.transactionId, order.orderId)
        val updated = BankingOrderDAO.getInstance().markPaid(
            orderId = order.orderId,
            transactionId = transactionId,
            paidAt = paidAt,
            rawResponse = rawResponse
        )
        if (updated <= 0) return@transactional

        val uuid = UUID.fromString(order.uuid)
        val playerName = order.playerName

        DonationRateLimiter.recordBanking(uuid, order.amount)
        val bonusPercent = config.rewardBonusPercent
        val basePoints = order.pointAmount
        val bonusPoints = if (bonusPercent > 0) (basePoints * bonusPercent) / 100 else 0
        val totalPoints = basePoints + bonusPoints

        Nap2S.instance.playerPoints.api.give(uuid, totalPoints)
        Nap2S.instance.updateLeaderBoard(uuid, order.amount, totalPoints, TopupType.BANK)

        val cardPrice = CardPrice[order.amount]
        if (cardPrice != null) {
            val commands = cardPrice.getCommands().map { it.replace("%PLAYER%", playerName) }
            runSync {
                commands.forEach { Bukkit.dispatchCommand(Bukkit.getConsoleSender(), it) }
            }
        }

        val logDao = LogDAO.getInstance()
        val logId = logDao.insertLog(
            name = playerName,
            uuid = order.uuid,
            // Với ATM/QR, hai cột seri/pin chỉ là field legacy của bảng log.
            // Giữ chúng ngắn gọn để không văng DB; mã giao dịch thật nằm ở transaction_id.
            seri = toLegacyBankLogSeri(order.orderId),
            pin = toLegacyBankLogPin(order.orderId),
            type = "BANK",
            amount = order.amount,
            providerId = order.gateway.ifBlank { config.providerId }
        )
        logDao.stopWaiting(logId, true)
        logDao.setTransactionId(logId, transactionId, true)
        logDao.updatePointReceived(logId, totalPoints)
        BankingOrderDAO.getInstance().markRewarded(order.orderId)

        // Xoá QR map theo orderId — hoạt động đúng cả khi tracking RAM mất sau restart
        QrMapManager.removeQrMapByOrderId(order.orderId)

        notifyPlayer(order.uuid) { player ->
            pendingCountdowns.remove(player.uniqueId)

            player.sendMessages(Nap2S.instance.language.bankingPaid.map {
                it.replace("%ORDER_ID%", order.orderId)
                    .replace("%AMOUNT%", order.amount.format())
                    .replace("%POINTS%", basePoints.toString())
                    .replace("%BONUS%%", "${bonusPercent}%")
                    .replace("%BONUS%", bonusPercent.toString())
                    .replace("%BONUS_AMOUNT%", bonusPoints.format())
                    .replace("%TOTAL_POINTS%", totalPoints.format())
                    .replace("%POINT_UNIT%", Nap2S.instance.config.pointUnit)
                    .replace("%TRANSACTION_ID%", transactionId)
            })

            runSync {
                player.sendTitle(
                    "§a§lNạp thành công!",
                    "§e+${totalPoints.format()} ${Nap2S.instance.config.pointUnit} §7(KM +${bonusPercent}%)",
                    10, 60, 20
                )
                player.playSound(player.location, "entity.player.levelup", 1.0f, 1.0f)
            }
        }

        info("Đã xử lý order QR ${order.orderId} cho $playerName - base=$basePoints bonus=$bonusPoints total=$totalPoints")
    }

    private fun notifyPlayer(uuid: String, action: (Player) -> Unit) {
        val player = Bukkit.getPlayer(UUID.fromString(uuid))
        if (player?.isOnline == true) {
            action(player)
        }
    }

    private fun tickActionBarCountdown() {
        val now = System.currentTimeMillis()
        val expired = mutableListOf<UUID>()

        pendingCountdowns.forEach { (uuid, expiresAt) ->
            val remain = expiresAt - now
            val player = Bukkit.getPlayer(uuid)

            if (player == null || !player.isOnline) return@forEach

            if (remain <= 0) {
                expired.add(uuid)
                runSync {
                    player.spigot().sendMessage(
                        ChatMessageType.ACTION_BAR,
                        TextComponent("§cĐơn hàng đã hết hạn. Dùng /napatm để tạo đơn mới.")
                    )
                }
                return@forEach
            }

            val minutes = remain / 60000
            val seconds = (remain % 60000) / 1000
            val timeStr = "${minutes}:${String.format("%02d", seconds)}"
            val bonusInfo = if (config.rewardBonusPercent > 0) " §a§l✘ KM +${config.rewardBonusPercent}%" else ""
            runSync {
                player.spigot().sendMessage(
                    ChatMessageType.ACTION_BAR,
                    TextComponent("§e§l⏳ Đang chờ thanh toán §f$timeStr$bonusInfo")
                )
            }
        }

        expired.forEach { pendingCountdowns.remove(it) }
    }

    /**
     * Kết quả tạo order — dùng chung cho cả SePay và PayOS.
     */
    data class OrderCreateResult(
        val orderId: String,
        val checkoutUrl: String,
        val qrImageUrl: String,
        val transferContent: String,
        val bankName: String,
        val bankAccountNumber: String,
        val bankAccountHolder: String,
        val expiresAt: Long
    )

    data class CreateOrderResponse(
        val orderId: String,
        val amount: Int,
        val pointAmount: Int,
        val status: String,
        val checkoutUrl: String,
        val qrImageUrl: String,
        val transferContent: String,
        val gateway: String,
        val expiresAt: Long,
        val bankName: String = "",
        val vaNumber: String = "",
        val vaHolderName: String = ""
    )

    data class StatusResponse(
        val orderId: String,
        val status: String,
        val transactionId: String,
        val paidAt: Long
    )
}

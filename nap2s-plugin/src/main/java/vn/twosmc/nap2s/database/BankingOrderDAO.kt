package vn.twosmc.nap2s.database

import vn.twosmc.nap2s.Nap2S
import vn.twosmc.nap2s.banking.BankingOrder
import vn.twosmc.nap2s.platform.db.DataAccess
import vn.twosmc.nap2s.platform.db.fetchRecords
import java.sql.ResultSet

abstract class BankingOrderDAO : DataAccess() {
    companion object {
        fun getInstance() = Nap2S.instance.getDAO(BankingOrderDAO::class)
    }

    abstract fun insertScript(): String
    abstract fun findPendingByPlayerScript(): String
    abstract fun findByOrderIdScript(): String
    abstract fun getPendingOrdersScript(): String
    abstract fun updatePendingStatusScript(): String
    abstract fun markPaidScript(): String
    abstract fun markRewardedScript(): String
    abstract fun getAllOrdersByTimeRangeScript(): String
    abstract fun getAllOrdersByTimeRangeAllServersScript(): String

    fun insert(
        orderId: String,
        uuid: String,
        playerName: String,
        amount: Int,
        pointAmount: Int,
        status: String,
        checkoutUrl: String,
        qrImageUrl: String,
        transferContent: String,
        gateway: String,
        rawResponse: String,
        server: String,
        expiresAt: Long,
        bankName: String = "",
        bankAccountNumber: String = "",
        bankAccountHolder: String = ""
    ) = insertScript().statementWithKey {
        setString(1, orderId)
        setString(2, uuid)
        setString(3, playerName)
        setInt(4, amount)
        setInt(5, pointAmount)
        setString(6, status)
        setString(7, checkoutUrl)
        setString(8, qrImageUrl)
        setString(9, transferContent)
        setString(10, "")
        setString(11, gateway)
        setString(12, rawResponse)
        setString(13, server)
        setLong(14, expiresAt)
        val now = System.currentTimeMillis()
        setLong(15, now)
        setLong(16, now)
        setString(17, bankName)
        setString(18, bankAccountNumber)
        setString(19, bankAccountHolder)
        executeUpdate()
        generatedKeys.use {
            if (it.next()) {
                it.getInt(1)
            } else {
                throw IllegalStateException("Không thể lấy ID giao dịch banking vừa tạo")
            }
        }
    }

    fun findPendingByPlayer(uuid: String, server: String): BankingOrder? = findPendingByPlayerScript().statement {
        setString(1, uuid)
        setString(2, server)
        fetchRecords { readOrder() }.firstOrNull()
    }

    fun findByOrderId(orderId: String): BankingOrder? = findByOrderIdScript().statement {
        setString(1, orderId)
        fetchRecords { readOrder() }.firstOrNull()
    }

    fun getPendingOrders(server: String): List<BankingOrder> = getPendingOrdersScript().statement {
        setString(1, server)
        fetchRecords { readOrder() }
    }

    fun updatePendingStatus(orderId: String, status: String, rawResponse: String): Int =
        updatePendingStatusScript().statement {
            setString(1, status)
            setString(2, rawResponse)
            setLong(3, System.currentTimeMillis())
            setString(4, orderId)
            setString(5, "PENDING")
            executeUpdate()
        }

    fun markPaid(orderId: String, transactionId: String, paidAt: Long, rawResponse: String): Int =
        markPaidScript().statement {
            setString(1, "PAID")
            setString(2, transactionId)
            setLong(3, paidAt)
            setString(4, rawResponse)
            setLong(5, System.currentTimeMillis())
            setString(6, orderId)
            setString(7, "PENDING")
            executeUpdate()
        }

    fun markRewarded(orderId: String): Int = markRewardedScript().statement {
        setLong(1, System.currentTimeMillis())
        setString(2, orderId)
        setInt(3, 0)
        executeUpdate()
    }

    /**
     * Lấy tất cả orders trong khoảng thời gian (mọi status).
     * Dùng cho báo cáo doanh thu.
     */
    fun getAllOrdersByTimeRange(server: String, fromMs: Long, toMs: Long): List<BankingOrder> =
        getAllOrdersByTimeRangeScript().statement {
            setString(1, server)
            setLong(2, fromMs)
            setLong(3, toMs)
            fetchRecords { readOrder() }
        }

    /**
     * Lấy tất cả orders cross-server trong khoảng thời gian.
     * Dùng cho báo cáo doanh thu toàn network.
     */
    fun getAllOrdersByTimeRangeAllServers(fromMs: Long, toMs: Long): List<BankingOrder> =
        getAllOrdersByTimeRangeAllServersScript().statement {
            setLong(1, fromMs)
            setLong(2, toMs)
            fetchRecords { readOrder() }
        }

    private fun ResultSet.readOrder() = BankingOrder(
        id = getInt("id"),
        orderId = getString("order_id"),
        uuid = getString("uuid"),
        playerName = getString("player_name"),
        amount = getInt("amount"),
        pointAmount = getInt("point_amount"),
        status = getString("status"),
        checkoutUrl = getString("checkout_url") ?: "",
        qrImageUrl = getString("qr_image_url") ?: "",
        transferContent = getString("transfer_content") ?: "",
        transactionId = getString("transaction_id") ?: "",
        gateway = getString("gateway") ?: "",
        rawResponse = getString("raw_response") ?: "",
        server = getString("server"),
        expiresAt = getLong("expires_at"),
        paidAt = getLong("paid_at"),
        rewarded = getInt("rewarded") == 1,
        createdAt = getLong("created_at"),
        updatedAt = getLong("updated_at"),
        // Bank snapshot — cột mới, có thể chưa tồn tại trong DB cũ
        bankName = runCatching { getString("bank_name") ?: "" }.getOrDefault(""),
        bankAccountNumber = runCatching { getString("bank_account_number") ?: "" }.getOrDefault(""),
        bankAccountHolder = runCatching { getString("bank_account_holder") ?: "" }.getOrDefault("")
    )
}

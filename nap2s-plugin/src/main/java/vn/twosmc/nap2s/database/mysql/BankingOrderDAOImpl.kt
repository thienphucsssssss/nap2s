package vn.twosmc.nap2s.database.mysql

import vn.twosmc.nap2s.database.BankingOrderDAO

class BankingOrderDAOImpl : BankingOrderDAO() {
    override fun insertScript() = """
        INSERT INTO `nap2s_banking_order`
            (`order_id`, `uuid`, `player_name`, `amount`, `point_amount`, `status`, `checkout_url`,
             `qr_image_url`, `transfer_content`, `transaction_id`, `gateway`, `raw_response`,
             `server`, `expires_at`, `created_at`, `updated_at`,
             `bank_name`, `bank_account_number`, `bank_account_holder`)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """.trimIndent()

    override fun findPendingByPlayerScript() = """
        SELECT * FROM `nap2s_banking_order`
        WHERE `uuid` = ? AND `server` = ? AND `status` = 'PENDING'
        ORDER BY `created_at` DESC
        LIMIT 1
    """.trimIndent()

    override fun findByOrderIdScript() = """
        SELECT * FROM `nap2s_banking_order`
        WHERE `order_id` = ?
        LIMIT 1
    """.trimIndent()

    override fun getPendingOrdersScript() = """
        SELECT * FROM `nap2s_banking_order`
        WHERE `server` = ? AND `status` = 'PENDING'
        ORDER BY `created_at` ASC
    """.trimIndent()

    override fun updatePendingStatusScript() = """
        UPDATE `nap2s_banking_order`
        SET `status` = ?, `raw_response` = ?, `updated_at` = ?
        WHERE `order_id` = ? AND `status` = ?
    """.trimIndent()

    override fun markPaidScript() = """
        UPDATE `nap2s_banking_order`
        SET `status` = ?, `transaction_id` = ?, `paid_at` = ?, `raw_response` = ?, `updated_at` = ?
        WHERE `order_id` = ? AND `status` = ?
    """.trimIndent()

    override fun markRewardedScript() = """
        UPDATE `nap2s_banking_order`
        SET `rewarded` = 1, `updated_at` = ?
        WHERE `order_id` = ? AND `rewarded` = ?
    """.trimIndent()

    override fun getAllOrdersByTimeRangeScript() = """
        SELECT * FROM `nap2s_banking_order`
        WHERE `server` = ?
          AND (CASE
                WHEN `status` IN ('PAID', 'REWARDED') AND `paid_at` > 0 THEN `paid_at`
                ELSE `created_at`
              END) >= ?
          AND (CASE
                WHEN `status` IN ('PAID', 'REWARDED') AND `paid_at` > 0 THEN `paid_at`
                ELSE `created_at`
              END) <= ?
        ORDER BY (CASE
                WHEN `status` IN ('PAID', 'REWARDED') AND `paid_at` > 0 THEN `paid_at`
                ELSE `created_at`
              END) DESC
    """.trimIndent()

    override fun getAllOrdersByTimeRangeAllServersScript() = """
        SELECT * FROM `nap2s_banking_order`
        WHERE (CASE
                WHEN `status` IN ('PAID', 'REWARDED') AND `paid_at` > 0 THEN `paid_at`
                ELSE `created_at`
              END) >= ?
          AND (CASE
                WHEN `status` IN ('PAID', 'REWARDED') AND `paid_at` > 0 THEN `paid_at`
                ELSE `created_at`
              END) <= ?
        ORDER BY (CASE
                WHEN `status` IN ('PAID', 'REWARDED') AND `paid_at` > 0 THEN `paid_at`
                ELSE `created_at`
              END) DESC
    """.trimIndent()
}

package vn.twosmc.nap2s.database.mysql

import vn.twosmc.nap2s.database.LogDAO

class LogDAOImpl : LogDAO() {
    override fun insertLogScript() = """
            INSERT INTO `nap2s_napthe_log`
                (`name`, `uuid`, `seri`, `pin`, `type`, `price`, `time`, `server`, `provider`)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

    override fun setWaitingScript() = "UPDATE `nap2s_napthe_log` SET `waiting` = 1 WHERE `id` = ?"

    override fun stopWaitingScript() = """
            UPDATE `nap2s_napthe_log`
            SET `waiting` = 0, `success` = ?
            WHERE `id` = ?;
        """.trimIndent()

    override fun getWaitingCardsScript(includeLegacyBlank: Boolean) = """
            SELECT * FROM `nap2s_napthe_log`
            WHERE `server` = ? AND `waiting` = 1 AND `uuid` IN ({UUIDS})${
                if (includeLegacyBlank) """ AND (`provider` = ? OR COALESCE(`provider`, '') = '')"""
                else """ AND `provider` = ?"""
            };
        """.trimIndent()

    override fun setTransactionIdScript() = """
            UPDATE `nap2s_napthe_log`
            SET `transaction_id` = ?, `success` = ?
            WHERE `id` = ?;
        """.trimIndent()

    override fun getHistoryScriptAllPlayerAllTime() = """
            select
                row_number() over (order by time desc) as rownum,
                id, COALESCE(i.name, l.name) as name, type, seri, price, pointsnhan, time
            from nap2s_napthe_log l left join nap2s_player_info i on i.uuid = l.uuid
            where success = 1 and type not in ('BANK', 'MANUAL')
            order by time desc limit ?, ?;
        """.trimIndent()

    override fun getHistoryScriptAllPlayerByMonth() = """
            select
                row_number() over (order by time desc) as rownum,
                id, COALESCE(i.name, l.name) as name, type, seri, price, pointsnhan, time
            from nap2s_napthe_log l left join nap2s_player_info i on i.uuid = l.uuid
            where success = 1 and type not in ('BANK', 'MANUAL')
            and time >= ? and time <= ?
            order by time desc limit ?, ?;
        """.trimIndent()

    override fun getHistoryScriptByPlayerAllTime() = """
            select
                row_number() over (order by time desc) as rownum,
                id, COALESCE(i.name, l.name) as name, type, seri, price, pointsnhan, time
            from nap2s_napthe_log l left join nap2s_player_info i on i.uuid = l.uuid
            where success = 1 and l.uuid = ? and type not in ('BANK', 'MANUAL')
            order by time desc limit ?, ?;
        """.trimIndent()

    override fun getHistoryScriptByPlayerByMonth() = """
            select
                row_number() over (order by time desc) as rownum,
                id, COALESCE(i.name, l.name) as name, type, seri, price, pointsnhan, time
            from nap2s_napthe_log l left join nap2s_player_info i on i.uuid = l.uuid
            where success = 1 and l.uuid = ? and type not in ('BANK', 'MANUAL')
            and time >= ? and time <= ?
            order by time desc limit ?, ?;
        """.trimIndent()

    override fun getSumScriptAllPlayerAllTime() = """
        SELECT
            SUM(`price`), COUNT(`id`)
        FROM `nap2s_napthe_log`
        WHERE `success` = 1 AND `type` NOT IN ('BANK', 'MANUAL')
    """.trimIndent()

    override fun getSumScriptAllPlayerByMonth() = """
        SELECT
            SUM(`price`), COUNT(`id`)
        FROM `nap2s_napthe_log`
        WHERE `success` = 1 AND `type` NOT IN ('BANK', 'MANUAL')
        and `time` >= ? and `time` <= ?
    """.trimIndent()

    override fun getSumScriptByPlayerAllTime() = """
        SELECT
            SUM(`price`), COUNT(`id`)
        FROM `nap2s_napthe_log`
        WHERE `success` = 1 AND `uuid` = ? AND `type` NOT IN ('BANK', 'MANUAL')
    """.trimIndent()

    override fun getSumScriptByPlayerByMonth() = """
        SELECT
            SUM(`price`), COUNT(`id`)
        FROM `nap2s_napthe_log`
        WHERE `success` = 1 AND `uuid` = ? AND `type` NOT IN ('BANK', 'MANUAL')
        and `time` >= ? and `time` <= ?
    """.trimIndent()

    override fun getRevenueStatsByTimeRangeScript() = """
        SELECT
            COUNT(`id`) AS `count`,
            COALESCE(SUM(`price`), 0) AS `total`
        FROM `nap2s_napthe_log`
        WHERE `success` = 1
          AND `type` NOT IN ('BANK', 'MANUAL')
          AND `server` = ?
          AND `time` >= ? AND `time` <= ?
    """.trimIndent()

    override fun getRevenueStatsAllServersScript() = """
        SELECT
            COUNT(`id`) AS `count`,
            COALESCE(SUM(`price`), 0) AS `total`
        FROM `nap2s_napthe_log`
        WHERE `success` = 1
          AND `type` NOT IN ('BANK', 'MANUAL')
          AND `time` >= ? AND `time` <= ?
    """.trimIndent()

    override fun getTransactionDetailsByIdScript() = """
        SELECT l.*, COALESCE(i.`name`, l.`name`) as `name`
        FROM `nap2s_napthe_log` l
        LEFT JOIN `nap2s_player_info` i ON i.`uuid` = l.`uuid`
        WHERE l.`success` = 1 AND l.`transaction_id` = ?
    """.trimIndent()

    override fun getRecentHistoryByPlayerScript() = """
        SELECT l.*, COALESCE(i.`name`, l.`name`) as `name`
        FROM `nap2s_napthe_log` l
        LEFT JOIN `nap2s_player_info` i ON i.`uuid` = l.`uuid`
        WHERE l.`success` = 1 AND l.`uuid` = ?
        ORDER BY l.`time` DESC
        LIMIT ?
    """.trimIndent()

    override fun updatePointReceivedScript() = """
            UPDATE `nap2s_napthe_log`
            SET `pointsnhan` = ?
            WHERE `id` = ?;
        """.trimIndent()
}

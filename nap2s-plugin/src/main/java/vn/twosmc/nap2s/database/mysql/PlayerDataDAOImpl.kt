package vn.twosmc.nap2s.database.mysql

import vn.twosmc.nap2s.database.PlayerDataDAO

class PlayerDataDAOImpl : PlayerDataDAO() {
    override fun insertDataScript() = """
        INSERT INTO `nap2s_player_data` (`uuid`, `name`, `key`, `value`, `last_updated`)
        VALUES (?, ?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE
            `name` = VALUES(`name`),
            `value` = `value` + VALUES(`value`),
            `last_updated` = VALUES(`last_updated`);
    """.trimIndent()

    override fun getTopScript(): String = """
        SELECT
            row_number() over (order by `value` desc) as rownum,
            d.uuid, ifnull(i.name, d.name) as name, `key`, value
        FROM `nap2s_player_data` d
        LEFT JOIN `nap2s_player_info` i ON d.uuid = i.uuid
        WHERE `key` = ?
        ORDER BY `value` DESC
        LIMIT ?;
    """.trimIndent()

    override fun getDataScript() = """
        SELECT `value`
        FROM `nap2s_player_data`
        WHERE `uuid` = ? AND `key` = ?;
    """.trimIndent()

    override fun getSumDataScript() = """
        SELECT SUM(`value`) as `value`
        FROM `nap2s_player_data`
        WHERE `key` = ?;
    """.trimIndent()

    override fun getAllDataScript() = """
        SELECT `key`, `value`
        FROM `nap2s_player_data`
        WHERE `uuid` = ?;
    """.trimIndent()

    override fun deleteAllDataByUuidScript() = """
        DELETE FROM `nap2s_player_data`
        WHERE `uuid` = ?;
    """.trimIndent()

    override fun deleteByKeyPatternScript() = """
        DELETE FROM `nap2s_player_data`
        WHERE `key` LIKE ?;
    """.trimIndent()
}

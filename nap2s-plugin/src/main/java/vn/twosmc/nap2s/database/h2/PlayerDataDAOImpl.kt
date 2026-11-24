package vn.twosmc.nap2s.database.h2

import vn.twosmc.nap2s.database.PlayerDataDAO

class PlayerDataDAOImpl : PlayerDataDAO() {
    override fun insertDataScript() = """
        MERGE INTO "nap2s_player_data" AS target
        USING (
            VALUES (?, ?, ?, ?, ?)
        ) AS source ("uuid", "name", "key", "value", "last_updated")
        ON target."uuid" = source."uuid" AND target."key" = source."key"
        WHEN MATCHED THEN
            UPDATE SET
                target."name" = source."name",
                target."value" = target."value" + source."value",
                target."last_updated" = source."last_updated"
        WHEN NOT MATCHED THEN
            INSERT ("uuid", "name", "key", "value", "last_updated")
            VALUES (source."uuid", source."name", source."key", source."value", source."last_updated");
    """.trimIndent()

    override fun getTopScript(): String = """
        SELECT
            ROW_NUMBER() OVER (ORDER BY "value" DESC) AS "rownum",
            d."uuid", COALESCE(i."name", d."name") as "name", "key", "value"
        FROM "nap2s_player_data" d
        LEFT JOIN "nap2s_player_info" i ON d."uuid" = i."uuid"
        WHERE "key" = ?
        ORDER BY "value" DESC
        LIMIT ?;
    """.trimIndent()

    override fun getDataScript() = """
        SELECT "value"
        FROM "nap2s_player_data"
        WHERE "uuid" = ? AND "key" = ?;
    """.trimIndent()

    override fun getSumDataScript() = """
        SELECT SUM("value") as "value"
        FROM "nap2s_player_data"
        WHERE "key" = ?;
    """.trimIndent()

    override fun getAllDataScript() = """
        SELECT "key", "value"
        FROM "nap2s_player_data"
        WHERE "uuid" = ?;
    """.trimIndent()

    override fun deleteAllDataByUuidScript() = """
        DELETE FROM "nap2s_player_data"
        WHERE "uuid" = ?;
    """.trimIndent()

    override fun deleteByKeyPatternScript() = """
        DELETE FROM "nap2s_player_data"
        WHERE "key" LIKE ?;
    """.trimIndent()
}

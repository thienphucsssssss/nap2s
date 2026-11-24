package vn.twosmc.nap2s.database.h2

import vn.twosmc.nap2s.database.PlayerInfoDAO

class PlayerInfoDAOImpl : PlayerInfoDAO() {
    override fun updateScript() = """
        MERGE INTO "nap2s_player_info" AS target
            USING (
                VALUES (?, ?, ?)
                ) AS source ("uuid", "name", "last_updated")
        ON target."uuid" = source."uuid"
        WHEN MATCHED THEN
            UPDATE SET
                       target."name" = source."name",
                       target."last_updated" = source."last_updated"
        WHEN NOT MATCHED THEN
            INSERT ("uuid", "name", "last_updated")
            VALUES (source."uuid", source."name", source."last_updated");
    """.trimIndent()

    override fun getUUIDScript() = """
        SELECT "uuid" FROM "nap2s_player_info" WHERE "name" = ?
    """.trimIndent()

    override fun getNameScript() = """
        SELECT "name" FROM "nap2s_player_info" WHERE "uuid" = ?
    """.trimIndent()

}

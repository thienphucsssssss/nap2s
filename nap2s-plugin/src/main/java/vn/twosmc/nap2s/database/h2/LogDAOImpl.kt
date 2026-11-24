package vn.twosmc.nap2s.database.h2

import vn.twosmc.nap2s.database.LogDAO

class LogDAOImpl : LogDAO() {
    override fun insertLogScript() = """
            INSERT INTO "nap2s_napthe_log"
                ("name", "uuid", "seri", "pin", "type", "price", "time", "server", "provider")
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

    override fun setWaitingScript() = """UPDATE "nap2s_napthe_log" SET "waiting" = 1 WHERE "id" = ?"""

    override fun stopWaitingScript() = """UPDATE "nap2s_napthe_log" SET "waiting" = 0, "success" = ? WHERE "id" = ?"""

    override fun getWaitingCardsScript(includeLegacyBlank: Boolean) =
        """SELECT * FROM "nap2s_napthe_log" WHERE "server" = ? AND "waiting" = 1 AND "uuid" IN ({UUIDS})${
            if (includeLegacyBlank) """ AND ("provider" = ? OR COALESCE("provider", '') = '')"""
            else """ AND "provider" = ?"""
        }"""

    override fun setTransactionIdScript() = """
            UPDATE "nap2s_napthe_log"
            set "transaction_id" = ?,  "success" = ?
            where "id" = ?
        """.trimIndent()

    override fun getHistoryScriptAllPlayerAllTime() = """
            SELECT
                ROW_NUMBER() OVER (ORDER BY "time" DESC) AS "rownum",
                "id", COALESCE(i."name", l."name") as "name", "type", "seri", "price", "pointsnhan", "time"
            FROM "nap2s_napthe_log" l left join "nap2s_player_info" i on i."uuid" = l."uuid"
            WHERE "success" = 1 AND "type" NOT IN ('BANK', 'MANUAL')
            ORDER BY "time" DESC
            OFFSET ? ROWS FETCH NEXT ? ROWS ONLY;
        """.trimIndent()

    override fun getHistoryScriptAllPlayerByMonth() = """
            SELECT
                ROW_NUMBER() OVER (ORDER BY "time" DESC) AS "rownum",
                "id", COALESCE(i."name", l."name") as "name", "type", "seri", "price", "pointsnhan", "time"
            FROM "nap2s_napthe_log" l left join "nap2s_player_info" i on i."uuid" = l."uuid"
            WHERE "success" = 1 AND "type" NOT IN ('BANK', 'MANUAL')
            and "time" >= ? and "time" <= ?
            ORDER BY "time" DESC
            OFFSET ? ROWS FETCH NEXT ? ROWS ONLY;
        """.trimIndent()

    override fun getHistoryScriptByPlayerAllTime() = """
            SELECT
                ROW_NUMBER() OVER (ORDER BY "time" DESC) AS "rownum",
                "id", COALESCE(i."name", l."name") as "name", "type", "seri", "price", "pointsnhan", "time"
            FROM "nap2s_napthe_log" l left join "nap2s_player_info" i on i."uuid" = l."uuid"
            WHERE "success" = 1 AND l."uuid" = ? AND "type" NOT IN ('BANK', 'MANUAL')
            ORDER BY "time" DESC
            OFFSET ? ROWS FETCH NEXT ? ROWS ONLY;
        """.trimIndent()

    override fun getHistoryScriptByPlayerByMonth() = """
            SELECT
                ROW_NUMBER() OVER (ORDER BY "time" DESC) AS "rownum",
                "id", COALESCE(i."name", l."name") as "name", "type", "seri", "price", "pointsnhan", "time"
            FROM "nap2s_napthe_log" l left join "nap2s_player_info" i on i."uuid" = l."uuid"
            WHERE "success" = 1 AND l."uuid" = ? AND "type" NOT IN ('BANK', 'MANUAL')
            and "time" >= ? and "time" <= ?
            ORDER BY "time" DESC
            OFFSET ? ROWS FETCH NEXT ? ROWS ONLY;
        """.trimIndent()

    override fun getSumScriptAllPlayerAllTime() = """
        SELECT
            SUM("price"), COUNT("id")
        FROM "nap2s_napthe_log"
        WHERE "success" = 1 AND "type" NOT IN ('BANK', 'MANUAL')
    """.trimIndent()

    override fun getSumScriptAllPlayerByMonth() = """
        SELECT
            SUM("price"), COUNT("id")
        FROM "nap2s_napthe_log"
        WHERE "success" = 1 AND "type" NOT IN ('BANK', 'MANUAL')
        and "time" >= ? and "time" <= ?
    """.trimIndent()

    override fun getSumScriptByPlayerAllTime() = """
        SELECT
            SUM("price"), COUNT("id")
        FROM "nap2s_napthe_log"
        WHERE "success" = 1 AND "uuid" = ? AND "type" NOT IN ('BANK', 'MANUAL')
    """.trimIndent()

    override fun getSumScriptByPlayerByMonth() = """
        SELECT
            SUM("price"), COUNT("id")
        FROM "nap2s_napthe_log"
        WHERE "success" = 1 AND "uuid" = ? AND "type" NOT IN ('BANK', 'MANUAL')
        and "time" >= ? and "time" <= ?
    """.trimIndent()

    override fun getRevenueStatsByTimeRangeScript() = """
        SELECT
            COUNT("id") AS "count",
            COALESCE(SUM("price"), 0) AS "total"
        FROM "nap2s_napthe_log"
        WHERE "success" = 1
          AND "type" NOT IN ('BANK', 'MANUAL')
          AND "server" = ?
          AND "time" >= ? AND "time" <= ?
    """.trimIndent()

    override fun getRevenueStatsAllServersScript() = """
        SELECT
            COUNT("id") AS "count",
            COALESCE(SUM("price"), 0) AS "total"
        FROM "nap2s_napthe_log"
        WHERE "success" = 1
          AND "type" NOT IN ('BANK', 'MANUAL')
          AND "time" >= ? AND "time" <= ?
    """.trimIndent()

    override fun getTransactionDetailsByIdScript() = """
        SELECT l.*, COALESCE(i."name", l."name") as "name"
        FROM "nap2s_napthe_log" l
        LEFT JOIN "nap2s_player_info" i ON i."uuid" = l."uuid"
        WHERE l."success" = 1 AND l."transaction_id" = ?
    """.trimIndent()

    override fun getRecentHistoryByPlayerScript() = """
        SELECT l.*, COALESCE(i."name", l."name") as "name"
        FROM "nap2s_napthe_log" l
        LEFT JOIN "nap2s_player_info" i ON i."uuid" = l."uuid"
        WHERE l."success" = 1 AND l."uuid" = ?
        ORDER BY l."time" DESC
        LIMIT ?
    """.trimIndent()

    override fun updatePointReceivedScript() = """
            UPDATE "nap2s_napthe_log"
            SET "pointsnhan" = ?
            WHERE "id" = ?;
        """.trimIndent()
}

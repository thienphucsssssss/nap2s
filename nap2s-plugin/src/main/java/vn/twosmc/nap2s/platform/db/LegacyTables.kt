package vn.twosmc.nap2s.platform.db

import java.sql.Connection

/** Preserves the data and migration version from installations predating the namespace change. */
internal object LegacyTables {
    private const val OLD_PREFIX = "dotman_"
    private const val NEW_PREFIX = "nap2s_"

    private val suffixes = listOf(
        "config",
        "config_head",
        "napthe_log",
        "point_log",
        "player_data",
        "player_info",
        "banking_order"
    )

    fun rename(connection: Connection, databaseType: String) {
        val isMySql = databaseType == "mysql"
        val catalog = if (isMySql) connection.catalog else null
        val schema = if (isMySql) null else connection.schema
        val tables = connection.metaData.getTables(catalog, schema, "%", arrayOf("TABLE")).use { result ->
            buildSet {
                while (result.next()) add(result.getString("TABLE_NAME"))
            }
        }

        val renames = suffixes.map { OLD_PREFIX + it to NEW_PREFIX + it }
            .filter { (old, _) -> old in tables }
        check(renames.none { (_, new) -> new in tables }) {
            "Cơ sở dữ liệu chứa cả bảng cũ và mới cùng tên. Vui lòng kiểm tra bản sao lưu trước khi nâng cấp."
        }
        if (renames.isEmpty()) return

        connection.createStatement().use { statement ->
            if (isMySql) {
                val pairs = renames.joinToString(", ") { (old, new) -> "`$old` TO `$new`" }
                statement.execute("RENAME TABLE $pairs")
            } else {
                for ((old, new) in renames) {
                    statement.execute("""ALTER TABLE "$old" RENAME TO "$new"""")
                }
            }
        }
    }
}

package vn.twosmc.nap2s.platform.db

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.DriverManager

class LegacyTablesTest {
    @Test
    fun `renames legacy tables and preserves migration version and rows`() {
        withDatabase { connection ->
            connection.createStatement().use { statement ->
                statement.execute("""CREATE TABLE "dotman_config" ("key" VARCHAR PRIMARY KEY, "value" VARCHAR)""")
                statement.execute("""INSERT INTO "dotman_config" VALUES ('migration_version', '7')""")
                statement.execute("""CREATE TABLE "dotman_napthe_log" ("id" INT PRIMARY KEY)""")
                statement.execute("""INSERT INTO "dotman_napthe_log" VALUES (42)""")
            }

            LegacyTables.rename(connection, "h2")
            LegacyTables.rename(connection, "h2")

            connection.createStatement().use { statement ->
                statement.executeQuery("""SELECT "value" FROM "nap2s_config" WHERE "key" = 'migration_version'""").use {
                    it.next()
                    assertEquals("7", it.getString(1))
                }
                statement.executeQuery("""SELECT "id" FROM "nap2s_napthe_log"""").use {
                    it.next()
                    assertEquals(42, it.getInt(1))
                }
                statement.executeQuery("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'").use {
                    val tables = buildSet {
                        while (it.next()) add(it.getString(1))
                    }
                    assertEquals(setOf("nap2s_config", "nap2s_napthe_log"), tables)
                }
            }
        }
    }

    @Test
    fun `refuses to overwrite a current table before renaming any legacy tables`() {
        withDatabase { connection ->
            connection.createStatement().use { statement ->
                statement.execute("""CREATE TABLE "dotman_config" ("value" INT)""")
                statement.execute("""CREATE TABLE "dotman_point_log" ("id" INT)""")
                statement.execute("""CREATE TABLE "nap2s_point_log" ("id" INT)""")
            }

            assertThrows(IllegalStateException::class.java) { LegacyTables.rename(connection, "h2") }

            connection.createStatement().use { statement ->
                statement.executeQuery("""SELECT "value" FROM "dotman_config"""").close()
            }
        }
    }

    private fun withDatabase(test: (Connection) -> Unit) {
        DriverManager.getConnection("jdbc:h2:mem:;MODE=MySQL").use(test)
    }
}

package vn.twosmc.nap2s.database.h2

import vn.twosmc.nap2s.database.ConfigDAO

class ConfigDAOImpl : ConfigDAO() {
    override fun isTableExistsScript() =
        "SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'nap2s_config' and TABLE_SCHEMA = 'PUBLIC'"

    override fun getScript() = """SELECT * FROM "nap2s_config" WHERE "key" = ?"""

    override fun setScript() = """MERGE INTO "nap2s_config"("key", "value") KEY("key") VALUES (?, ?)"""

    override fun deleteScript() = """DELETE FROM "nap2s_config" WHERE "key" = ?"""

    override fun getByPrefixScript() = """SELECT * FROM "nap2s_config" WHERE "key" LIKE ? ORDER BY "key" DESC"""
}
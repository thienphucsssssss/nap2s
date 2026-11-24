package vn.twosmc.nap2s.database

import vn.twosmc.nap2s.Nap2S
import vn.twosmc.nap2s.platform.db.DataAccess
import vn.twosmc.nap2s.platform.db.fetch
import vn.twosmc.nap2s.platform.db.fetchRecords

abstract class ConfigDAO : DataAccess() {
    companion object {
        fun getInstance() = Nap2S.instance.getDAO(ConfigDAO::class)
    }

    // region scripts
    abstract fun isTableExistsScript(): String
    abstract fun getScript(): String
    abstract fun setScript(): String
    abstract fun deleteScript(): String
    abstract fun getByPrefixScript(): String
    // endregion

    // region queriers
    fun isTableExists() = isTableExistsScript().statement {
        executeQuery().use { it.next() }
    }

    fun get(key: String) = if (!isTableExists()) null else getScript().statement {
        setString(1, key)
        fetch {
            if (next()) getString("value")
            else null
        }
    }

    fun set(key: String, value: String) = if (!isTableExists()) null else setScript().statement {
        setString(1, key)
        setString(2, value)
        executeUpdate()
    }

    fun delete(key: String) = deleteScript().statement {
        setString(1, key)
        executeUpdate()
    }

    /**
     * Lấy tất cả config có key bắt đầu bằng prefix.
     * Dùng cho AuditLog (prefix "audit_").
     */
    fun getByPrefix(prefix: String): List<Pair<String, String>> = if (!isTableExists()) emptyList()
    else getByPrefixScript().statement {
        setString(1, "$prefix%")
        fetchRecords {
            getString("key") to getString("value")
        }
    }
    // endregion
}

package vn.twosmc.nap2s.database

import vn.twosmc.nap2s.Nap2S
import vn.twosmc.nap2s.utils.TopType
import vn.twosmc.nap2s.platform.db.DataAccess
import vn.twosmc.nap2s.platform.db.fetchRecords
import vn.twosmc.nap2s.platform.db.update

abstract class PlayerDataDAO : DataAccess() {
    companion object {
        fun getInstance() = Nap2S.instance.getDAO(PlayerDataDAO::class)
    }

    abstract fun insertDataScript(): String
    abstract fun getTopScript(): String
    abstract fun getDataScript(): String
    abstract fun getSumDataScript(): String
    abstract fun getAllDataScript(): String
    abstract fun deleteAllDataByUuidScript(): String

    fun insertData(uuid: String, key: String, value: Int) {
        insertDataScript().statement {
            setString(1, uuid)
            setString(2, "converted_to_uuid")
            setString(3, key)
            setInt(4, value)
            setLong(5, System.currentTimeMillis())

            executeUpdate()
        }
    }

    fun getData(uuid: String, key: String) = getDataScript().statement {
        setString(1, uuid)
        setString(2, key)

        fetchRecords {
            getInt("value")
        }.firstOrNull() ?: 0
    }

    fun getSumData(key: String) = getSumDataScript().statement {
        setString(1, key)

        fetchRecords {
            getInt("value")
        }.firstOrNull() ?: 0
    }

    fun insertAllType(uuid: String, key: String, value: Int) = TopType.entries.forEach {
        insertData(uuid, it.parseKey(key), value)
    }

    fun getTop(key: String, limit: Int) = run {
        getTopScript().statement {
            setString(1, key)
            setInt(2, limit)

            fetchRecords {
                getString("name")!! to getInt("value")
            }
        }
    }

    fun getAllData(uuid: String) = run {
        getAllDataScript().statement {
            setString(1, uuid)

            fetchRecords {
                getString("key")!! to getInt("value")
            }.toMap()
        }
    }

    abstract fun deleteByKeyPatternScript(): String

    fun deleteAllDataByUuid(uuid: String): Int {
        return deleteAllDataByUuidScript().statement {
            setString(1, uuid)
            update()
        }
    }

    /**
     * Xóa tất cả data có key kết thúc bằng suffix (VD: "_WEEK", "_MONTH", "_DAILY").
     * Dùng cho reset mốc nạp theo chu kỳ.
     */
    fun deleteByKeyPattern(suffix: String): Int {
        return deleteByKeyPatternScript().statement {
            setString(1, "%$suffix")
            update()
        }
    }
}

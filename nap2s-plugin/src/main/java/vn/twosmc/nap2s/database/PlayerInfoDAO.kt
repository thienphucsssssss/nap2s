package vn.twosmc.nap2s.database

import vn.twosmc.nap2s.Nap2S
import vn.twosmc.nap2s.platform.db.DataAccess

abstract class PlayerInfoDAO : DataAccess() {
    companion object {
        fun getInstance() = Nap2S.instance.getDAO(PlayerInfoDAO::class)
    }

    abstract fun updateScript(): String
    abstract fun getUUIDScript(): String
    abstract fun getNameScript(): String

    fun updateData(uuid: String, name: String) {
        updateScript().statement {
            setString(1, uuid)
            setString(2, name)
            setLong(3, System.currentTimeMillis())

            executeUpdate()
        }
    }

    fun getUUID(name: String) = getUUIDScript().statement {
        setString(1, name)
        executeQuery().use {
            if (it.next()) it.getString("uuid") else null
        }
    }

    fun getName(uuid: String) = getNameScript().statement {
        setString(1, uuid)
        executeQuery().use {
            if (it.next()) it.getString("name") else null
        }
    }
}

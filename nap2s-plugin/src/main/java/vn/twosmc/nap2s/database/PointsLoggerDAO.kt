package vn.twosmc.nap2s.database

import vn.twosmc.nap2s.Nap2S
import vn.twosmc.nap2s.platform.db.DataAccess
import org.bukkit.entity.Player

abstract class PointsLoggerDAO : DataAccess() {
    companion object {
        fun getInstance() = Nap2S.instance.getDAO(PointsLoggerDAO::class)
    }

    abstract fun insertLogScript(): String

    fun insertLog(player: Player, amount: Int, pointFrom: Int, pointTo: Int, content: String? = null) {
        insertLogScript().statement {
            setString(1, player.name)
            setString(2, player.uniqueId.toString())
            setInt(3, amount)
            setInt(4, pointFrom)
            setInt(5, pointTo)
            setLong(6, System.currentTimeMillis())
            setString(7, Nap2S.instance.config.server)
            setString(8, content)

            executeUpdate()
        }
    }
}
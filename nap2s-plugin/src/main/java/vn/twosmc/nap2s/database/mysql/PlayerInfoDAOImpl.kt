package vn.twosmc.nap2s.database.mysql

import vn.twosmc.nap2s.database.PlayerInfoDAO

class PlayerInfoDAOImpl : PlayerInfoDAO() {
    override fun updateScript() = """
        insert into nap2s_player_info (uuid, name, last_updated)
        values (@uuid:=?, @name:=?, @time:=?)
        on duplicate key update name = @name, last_updated = @time;
    """.trimIndent()

    override fun getUUIDScript() = "SELECT `uuid` FROM `nap2s_player_info` WHERE `name` = ?"

    override fun getNameScript() = "SELECT `name` FROM `nap2s_player_info` WHERE `uuid` = ?"
}

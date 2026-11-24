package vn.twosmc.nap2s.database.mysql

import vn.twosmc.nap2s.database.ConfigDAO

class ConfigDAOImpl : ConfigDAO() {
    override fun isTableExistsScript() = "SHOW TABLES LIKE 'nap2s_config'"

    override fun getScript() = "SELECT * FROM nap2s_config WHERE `key` = ?"

    override fun setScript() =
        "INSERT INTO nap2s_config (`key`, `value`) VALUES (?, @value:=?) ON DUPLICATE KEY UPDATE `value` = @value"

    override fun deleteScript() = "DELETE FROM nap2s_config WHERE `key` = ?"

    override fun getByPrefixScript() = "SELECT * FROM nap2s_config WHERE `key` LIKE ? ORDER BY `key` DESC"
}

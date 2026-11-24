package vn.twosmc.nap2s.database.mysql

import vn.twosmc.nap2s.database.PointsLoggerDAO

class PointsLoggerDAOImpl : PointsLoggerDAO() {
    override fun insertLogScript() = """
            INSERT INTO `nap2s_point_log`
                (`name`, `uuid`, `amount`, `point_from`, `point_to`, `time`, `server`, `content`)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?);
        """.trimIndent()
}

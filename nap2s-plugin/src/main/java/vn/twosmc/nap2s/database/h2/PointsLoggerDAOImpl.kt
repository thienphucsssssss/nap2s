package vn.twosmc.nap2s.database.h2

import vn.twosmc.nap2s.database.PointsLoggerDAO

class PointsLoggerDAOImpl : PointsLoggerDAO() {
    override fun insertLogScript() = """
            insert into "nap2s_point_log"
                ("name", "uuid", "amount", "point_from", "point_to", "time", "server", "content")
            values (?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
}

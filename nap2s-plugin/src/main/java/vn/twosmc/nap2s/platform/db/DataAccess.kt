package vn.twosmc.nap2s.platform.db

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Statement

/**
 * Base class cho tất cả DAO
 *
 * Cung cấp:
 * - statement {} : chạy SQL với PreparedStatement
 * - statementWithKey {} : chạy SQL với RETURN_GENERATED_KEYS
 * - Bên trong block: fetchRecords {}, fetch {}, update()
 *
 * Connection lấy từ thread-local (nếu đang trong transaction) hoặc từ pool.
 */
abstract class DataAccess {
    companion object {
        /**
         * Thread-local connection dùng cho transaction.
         * Khi nằm trong transactional {}, connection được set ở đây
         * và tất cả DAO methods sẽ dùng chung connection này.
         */
        internal val threadConnection = ThreadLocal<Connection?>()
    }

    /**
     * Lấy connection: ưu tiên transaction thread-local, fallback về pool.
     * Trả kèm flag isTransactional để biết có cần close hay không.
     */
    private fun acquireConnection(): Pair<Connection, Boolean> {
        val txConn = threadConnection.get()
        if (txConn != null) {
            return txConn to true // transaction connection — không close
        }
        return NapPlugin.instance.dbPool!!.getConnection() to false
    }

    /**
     * Chạy SQL statement. Return giá trị từ lambda.
     * Connection tự động close nếu không trong transaction.
     */
    protected fun <T> String.statement(block: PreparedStatement.() -> T): T {
        val (conn, isTransactional) = acquireConnection()
        try {
            return conn.prepareStatement(this).use { it.block() }
        } finally {
            if (!isTransactional) conn.close()
        }
    }

    /**
     * Chạy SQL statement với RETURN_GENERATED_KEYS (cho INSERT).
     * Connection tự động close nếu không trong transaction.
     */
    protected fun <T> String.statementWithKey(block: PreparedStatement.() -> T): T {
        val (conn, isTransactional) = acquireConnection()
        try {
            return conn.prepareStatement(this, Statement.RETURN_GENERATED_KEYS).use { it.block() }
        } finally {
            if (!isTransactional) conn.close()
        }
    }
}

// ─── Extension functions cho PreparedStatement, dùng bên trong statement {} ───

/**
 * Chạy query và map từng row của ResultSet thành object T.
 * Trả về List<T>.
 */
fun <T> PreparedStatement.fetchRecords(mapper: ResultSet.() -> T): List<T> {
    return executeQuery().use { rs ->
        val results = mutableListOf<T>()
        while (rs.next()) {
            results.add(rs.mapper())
        }
        results
    }
}

/**
 * Chạy query và truyền ResultSet cho lambda xử lý thủ công.
 * Dùng khi cần đọc 1 row hoặc aggregate.
 */
fun <T> PreparedStatement.fetch(mapper: ResultSet.() -> T): T {
    return executeQuery().use { rs -> rs.mapper() }
}

/**
 * Shortcut cho executeUpdate(). Dùng cho DELETE/UPDATE statements.
 */
fun PreparedStatement.update(): Int = executeUpdate()

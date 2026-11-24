package vn.twosmc.nap2s.platform.db

import java.sql.Connection

/**
 * Transaction support
 *
 * Wrap một block code trong begin/commit/rollback.
 * Tất cả DataAccess methods bên trong block sẽ dùng chung connection
 * thông qua thread-local.
 */

/**
 * Receiver class cho transactional block.
 * Hiện tại không thêm method gì — giữ để tương thích API cũ:
 * `transactional { ... }` với `this` là Transaction.
 */
class Transaction internal constructor(internal val connection: Connection)

/**
 * Chạy [action] trong một database transaction.
 * - Set autoCommit = false
 * - Lưu connection vào thread-local để DAO methods dùng chung
 * - Commit nếu thành công, rollback nếu exception
 * - Sau cùng luôn close connection và clear thread-local
 *
 * @param connection Connection lấy từ pool
 * @param action Lambda chạy trong context Transaction
 */
fun transactional(connection: Connection, action: Transaction.() -> Unit) {
    val previousConn = DataAccess.threadConnection.get()
    connection.autoCommit = false
    DataAccess.threadConnection.set(connection)
    try {
        val tx = Transaction(connection)
        tx.action()
        connection.commit()
    } catch (e: Exception) {
        try {
            connection.rollback()
        } catch (rollbackEx: Exception) {
            e.addSuppressed(rollbackEx)
        }
        throw e
    } finally {
        try {
            connection.autoCommit = true
        } catch (_: Exception) { }
        try {
            connection.close()
        } catch (_: Exception) { }
        // Restore previous connection (nếu có nested transaction)
        DataAccess.threadConnection.set(previousConn)
    }
}

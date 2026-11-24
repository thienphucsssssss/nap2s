package vn.twosmc.nap2s.platform.db

import org.bukkit.plugin.java.JavaPlugin
import java.sql.Connection
import java.util.logging.Level

/**
 * Chạy SQL migration files từ resources
 *
 * Migration files nằm trong resources/<resourcePath>/ với tên dạng 0001.sql, 0002.sql, ...
 * Chỉ chạy các file có version > currentVersion.
 *
 * Nếu bất kỳ migration file nào fail → throw exception để chặn plugin tiếp tục
 * boot với schema chưa hoàn chỉnh.
 *
 * @param plugin JavaPlugin (để đọc resources)
 * @param connection Connection hiện tại (không close — caller quản lý)
 * @param resourcePath Đường dẫn thư mục migration trong resources (VD: "db/migrations/mysql")
 * @param currentVersion Version hiện tại đã migrate (0 = chưa migrate bao giờ)
 */
class DBMigrator(
    private val plugin: JavaPlugin,
    private val connection: Connection,
    private val resourcePath: String,
    private val currentVersion: Int
) {
    /**
     * Chạy tất cả migration files chưa được áp dụng.
     * Trả về version cao nhất đã chạy thành công (hoặc currentVersion nếu không có gì mới).
     *
     * @throws RuntimeException nếu bất kỳ migration nào fail — plugin PHẢI dừng.
     */
    fun migrate(): Int {
        var latestVersion = currentVersion

        // Quét migration files: 0001.sql, 0002.sql, ...
        // Duyệt từ currentVersion + 1, dừng khi không tìm thấy file
        var version = currentVersion + 1
        while (true) {
            val fileName = String.format("%04d.sql", version)
            val fullPath = "$resourcePath/$fileName"

            val sql = readResource(fullPath) ?: break // Không còn migration file → dừng

            plugin.logger.info("Đang chạy migration: $fullPath")
            try {
                executeSql(sql)
                latestVersion = version
                plugin.logger.info("Migration $fullPath thành công.")
            } catch (e: Exception) {
                plugin.logger.log(Level.SEVERE, "Migration $fullPath thất bại!", e)
                // Fail hard — không cho plugin tiếp tục với DB nửa vời
                throw RuntimeException("Migration $fullPath thất bại. Plugin không thể tiếp tục.", e)
            }

            version++
        }

        if (latestVersion > currentVersion) {
            plugin.logger.info("Database đã migrate từ v$currentVersion lên v$latestVersion.")
        }

        return latestVersion
    }

    /**
     * Đọc file resource trong plugin JAR.
     * Trả null nếu file không tồn tại.
     */
    private fun readResource(path: String): String? {
        return plugin.getResource(path)?.bufferedReader()?.use { it.readText() }
    }

    /**
     * Chạy một chuỗi SQL (có thể chứa nhiều statement, phân tách bằng dấu ;).
     *
     * Parse flow:
     * 1. Strip comment (--) theo TỪNG DÒNG trước (giữ nguyên nội dung sau comment)
     * 2. Join lại thành chuỗi liền
     * 3. Split bằng dấu ;
     * 4. Lọc bỏ dòng trống
     *
     * Điều này tránh bug: chunk "-- comment\nCREATE TABLE..." bị lọc bỏ nguyên khối
     * khi filter startsWith("--") sau split(";").
     */
    private fun executeSql(sql: String) {
        // Bước 1: Bỏ comment theo từng dòng — giữ phần code SQL
        val cleanedSql = sql.lines()
            .map { line ->
                val commentIdx = line.indexOf("--")
                if (commentIdx >= 0) line.substring(0, commentIdx) else line
            }
            .joinToString("\n")

        // Bước 2: Tách statement bằng dấu ;
        val statements = cleanedSql.split(";")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        connection.createStatement().use { stmt ->
            for (s in statements) {
                stmt.execute(s)
            }
        }
    }
}

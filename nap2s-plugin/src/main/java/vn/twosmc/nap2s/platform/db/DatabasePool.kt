package vn.twosmc.nap2s.platform.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.sql.Connection
import java.util.logging.Level

/**
 * Quản lý connection pool cho MySQL/MariaDB và H2.
 * Dùng HikariCP (shade vào jar)
 *
 * Schema config.yml:
 * ```yaml
 * database:
 *   engine: h2          # h2 | mysql | mariadb
 *   h2:
 *     file: nap2s       # tên file H2 (không cần đuôi .mv.db)
 *   mysql:
 *     host: localhost
 *     port: 3306
 *     user: 'root'
 *     password: ''
 *     database: nap2s
 * ```
 *
 * @param plugin JavaPlugin instance (lấy dataFolder cho H2)
 * @param config ConfigurationSection "database" từ config.yml
 */
class DatabasePool(plugin: JavaPlugin, config: ConfigurationSection) {
    private val hikari: HikariDataSource
    private val type: String

    init {
        // Đọc engine từ config — key "engine" (không phải "type")
        type = (config.getString("engine") ?: "h2").lowercase()
        val hikariConfig = HikariConfig()
        hikariConfig.poolName = "Nap2S-DB-Pool"

        when (type) {
            "mysql", "mariadb" -> {
                // Đọc sub-section "mysql" — key đúng schema config.yml
                val mysqlSection = config.getConfigurationSection("mysql")
                val host = mysqlSection?.getString("host") ?: "localhost"
                val port = mysqlSection?.getInt("port", 3306) ?: 3306
                val database = mysqlSection?.getString("database") ?: "nap2s"
                val username = mysqlSection?.getString("user") ?: "root"  // key "user" (không phải "username")
                val password = mysqlSection?.getString("password") ?: ""

                hikariConfig.jdbcUrl = "jdbc:mysql://$host:$port/$database?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8"
                hikariConfig.username = username
                hikariConfig.password = password
                hikariConfig.driverClassName = "com.mysql.cj.jdbc.Driver"

                hikariConfig.maximumPoolSize = mysqlSection?.getInt("pool-size", 10) ?: 10
                hikariConfig.minimumIdle = mysqlSection?.getInt("minimum-idle", 2) ?: 2
                hikariConfig.connectionTimeout = mysqlSection?.getLong("connection-timeout", 30000) ?: 30000
                hikariConfig.idleTimeout = mysqlSection?.getLong("idle-timeout", 600000) ?: 600000
                hikariConfig.maxLifetime = mysqlSection?.getLong("max-lifetime", 1800000) ?: 1800000
            }

            else -> {
                // H2 embedded database — đọc file name từ database.h2.file
                val h2Section = config.getConfigurationSection("h2")
                val fileName = h2Section?.getString("file") ?: "nap2s"
                val dbFile = File(plugin.dataFolder, fileName)

                hikariConfig.jdbcUrl = "jdbc:h2:${dbFile.absolutePath};MODE=MySQL"
                hikariConfig.driverClassName = "org.h2.Driver"
                hikariConfig.maximumPoolSize = 5
            }
        }

        hikari = try {
            HikariDataSource(hikariConfig)
        } catch (e: Exception) {
            plugin.logger.log(Level.SEVERE, "Không thể khởi tạo database pool ($type)", e)
            throw e
        }

        plugin.logger.info("Database pool ($type) đã sẵn sàng.")
    }

    /** Lấy một connection từ pool. Caller phải close() sau khi dùng xong. */
    fun getConnection(): Connection = hikari.connection

    /** Đóng pool — gọi khi plugin disable. */
    fun disconnect() {
        if (!hikari.isClosed) {
            hikari.close()
        }
    }

    /** Trả tên loại DB: "mysql", "mariadb" hoặc "h2" */
    fun getTypeName(): String = if (type == "mariadb") "mysql" else type
}

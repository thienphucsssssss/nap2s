package vn.twosmc.nap2s.platform.db

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.plugin.java.JavaPlugin
import kotlin.reflect.KClass

/**
 * Base class cho Nap2S plugin
 *
 * Cung cấp:
 * - [dbPool] : database connection pool (HikariCP)
 * - [initDatabase] : khởi tạo pool từ config section
 * - [getDAO] : factory tạo DAO implementation dựa trên loại DB (h2/mysql)
 *
 */
abstract class NapPlugin : JavaPlugin() {

    /** Connection pool — khởi tạo khi gọi [initDatabase]. */
    var dbPool: DatabasePool? = null
        protected set

    /**
     * Khởi tạo database pool từ ConfigurationSection "database".
     * Schema: engine (h2/mysql/mariadb), h2.file, mysql.host/port/user/password/database.
     */
    fun initDatabase(config: ConfigurationSection) {
        // Đóng pool cũ nếu đang reload
        dbPool?.disconnect()
        dbPool = DatabasePool(this, config)
    }

    /**
     * Factory tạo DAO concrete implementation.
     *
     * Mapping: abstract DAO class → package h2/ hoặc mysql/ chứa class tên <DAO>Impl.
     * VD: ConfigDAO::class → database.h2.ConfigDAOImpl hoặc database.mysql.ConfigDAOImpl
     *
     * Convention: Implementation class phải nằm cùng package structure,
     * trong sub-package h2/ hoặc mysql/, tên = <OriginalName>Impl
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : DataAccess> getDAO(clazz: KClass<T>): T {
        val dbType = dbPool?.getTypeName() ?: "h2"
        val originalPackage = clazz.java.`package`.name
        val implClassName = "${originalPackage}.${dbType}.${clazz.simpleName}Impl"

        return try {
            Class.forName(implClassName).getDeclaredConstructor().newInstance() as T
        } catch (e: Exception) {
            logger.severe("Không tìm thấy DAO implementation: $implClassName")
            throw e
        }
    }

    companion object {
        /**
         * Singleton instance — set bởi subclass (Nap2S) khi onEnable.
         * Dùng bởi DataAccess để lấy connection pool.
         */
        lateinit var instance: NapPlugin
            internal set
    }
}

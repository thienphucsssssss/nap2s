package vn.twosmc.nap2s.platform.config

import net.md_5.bungee.api.ChatColor
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.InputStreamReader

/**
 * Wrapper cho YAML config file
 *
 * Tự động:
 * - Save default resource nếu file chưa tồn tại
 * - Load/reload YamlConfiguration
 * - get(key) trả String đã translate color codes
 * - getList(key) trả List<String> đã translate color codes
 *
 * LƯU Ý: constructor KHÔNG gọi open reload() nữa — thay bằng gọi trực tiếp
 * doReload() (final) để tránh virtual dispatch bug khi subclass override reload()
 * và dùng field chưa init.
 *
 * @param plugin JavaPlugin instance
 * @param fileName Tên file (VD: "config", "messages") — không cần .yml
 */
open class FileConfig(private val plugin: JavaPlugin, private val fileName: String) {

    /** Raw YamlConfiguration — dùng để đọc mọi kiểu data */
    lateinit var config: YamlConfiguration
        private set

    private val file: File
        get() = File(plugin.dataFolder, "$fileName.yml")

    init {
        saveDefault()
        // Gọi doReload() (non-open) thay vì reload() (open)
        // để tránh virtual dispatch vào subclass.reload() khi field chưa init
        doReload()
    }

    /**
     * Copy file mặc định từ resources vào plugin data folder nếu chưa tồn tại.
     */
    private fun saveDefault() {
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            plugin.saveResource("$fileName.yml", false)
        }
    }

    /**
     * Load config từ file trên disk (internal, non-open).
     * Nếu resource tồn tại trong JAR, set defaults từ đó.
     */
    private fun doReload() {
        config = YamlConfiguration.loadConfiguration(file)

        // Set defaults từ resource trong JAR (nếu có)
        val resourceStream = plugin.getResource("$fileName.yml")
        if (resourceStream != null) {
            val defaults = YamlConfiguration.loadConfiguration(InputStreamReader(resourceStream))
            config.setDefaults(defaults)
        }
    }

    /**
     * Reload config từ file trên disk.
     * Subclass có thể override để thêm logic reload riêng (VD: loadComponents()).
     * An toàn gọi từ bên ngoài — field subclass đã init xong.
     */
    open fun reload() {
        doReload()
    }

    /**
     * Lấy String từ config, đã translate color codes (&).
     * Trả empty string nếu key không tồn tại.
     */
    open fun get(key: String): String {
        val raw = config.getString(key) ?: return ""
        return translateColor(raw)
    }

    /**
     * Lấy List<String> từ config, đã translate color codes (&).
     */
    open fun getList(key: String): List<String> {
        return config.getStringList(key).map { translateColor(it) }
    }

    /**
     * Translate & color codes → Minecraft color.
     */
    private fun translateColor(text: String): String {
        return ChatColor.translateAlternateColorCodes('&', text)
    }
}

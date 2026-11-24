package vn.twosmc.nap2s.network

import vn.twosmc.nap2s.Nap2S
import vn.twosmc.nap2s.database.ConfigDAO
import vn.twosmc.nap2s.utils.Utils.Companion.info
import vn.twosmc.nap2s.utils.Utils.Companion.warning

/**
 * Quản lý Network Mode — cho server network (nhiều cụm dùng chung DB).
 *
 * Khi bật:
 * - MySQL/MariaDB bắt buộc (H2 không hỗ trợ multi-server)
 * - Top nạp, mốc nạp, báo cáo có thể xem cross-server
 * - Auto-detect danh sách servers từ DB (không cần config thủ công)
 *
 * Config: config.yml → network-mode: false
 */
object NetworkManager {

    private var enabled = false
    private var serverId = ""

    /**
     * Khởi tạo — đọc config, validate DB engine
     */
    fun init() {
        val config = Nap2S.instance.config
        enabled = config.config.getBoolean("network-mode", false)
        serverId = config.server

        if (enabled) {
            val engine = config.config.getString("database.engine", "h2")!!.lowercase()
            if (engine == "h2") {
                warning("Network mode bật nhưng đang dùng H2! Chuyển sang MySQL/MariaDB để hoạt động cross-server.")
                warning("Tạm tắt network mode.")
                enabled = false
                return
            }
            info("Network mode: BẬT — server ID: $serverId")
        }
    }

    /**
     * Network mode có đang bật không
     */
    fun isEnabled() = enabled

    /**
     * Server ID hiện tại
     */
    fun getServerId() = serverId

    /**
     * Lấy danh sách tất cả server IDs từ DB.
     * Auto-detect: query DISTINCT server từ bảng log + banking_orders.
     */
    fun getAllServerIds(): List<String> {
        if (!enabled) return listOf(serverId)
        return try {
            val configs = ConfigDAO.getInstance().getByPrefix("known_server_")
            if (configs.isEmpty()) {
                // Fallback: chỉ server hiện tại
                listOf(serverId)
            } else {
                configs.map { it.second }.distinct()
            }
        } catch (_: Exception) {
            listOf(serverId)
        }
    }

    /**
     * Đăng ký server hiện tại vào DB (để các server khác biết).
     * Gọi khi plugin enable.
     */
    fun registerServer() {
        if (!enabled) return
        try {
            ConfigDAO.getInstance().set("known_server_$serverId", serverId)
            info("Network: đã đăng ký server '$serverId' vào database.")
        } catch (e: Exception) {
            e.warning("Không thể đăng ký server vào network")
        }
    }
}

package vn.twosmc.nap2s.compat

import vn.twosmc.nap2s.utils.Utils.Companion.info
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID

/**
 * Tương thích Bedrock player qua Geyser/Floodgate.
 * Soft dependency — không crash nếu Floodgate chưa cài.
 *
 * Bedrock player:
 * - Không gửi QR map (map không hiện tốt), gửi link qua chat
 * - GUI chest vẫn OK (Geyser auto convert)
 */
object BedrockCompat {

    private var floodgateAvailable = false

    /**
     * Khởi tạo — check Floodgate có đang chạy không
     */
    fun init() {
        floodgateAvailable = Bukkit.getPluginManager().getPlugin("floodgate") != null
    }

    /**
     * Kiểm tra player có phải Bedrock (qua Floodgate) không.
     * Trả false nếu Floodgate chưa cài.
     */
    fun isBedrockPlayer(player: Player): Boolean {
        if (!floodgateAvailable) return false
        return try {
            // Floodgate prefix UUID với "00000000-0000-0000" pattern
            // hoặc dùng API: org.geysermc.floodgate.api.FloodgateApi
            val api = Class.forName("org.geysermc.floodgate.api.FloodgateApi")
            val getInstance = api.getMethod("getInstance")
            val instance = getInstance.invoke(null)
            val isFloodgate = api.getMethod("isFloodgatePlayer", UUID::class.java)
            isFloodgate.invoke(instance, player.uniqueId) as Boolean
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Floodgate có đang chạy không
     */
    fun isAvailable() = floodgateAvailable
}

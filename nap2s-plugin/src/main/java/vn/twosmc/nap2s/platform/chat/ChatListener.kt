package vn.twosmc.nap2s.platform.chat

import vn.twosmc.nap2s.Nap2S
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent

/**
 * Chat listener 1 lần cho 1 player
 *
 * Đăng ký listener, đợi player chat, gọi callback rồi tự hủy.
 * Cancel event để tin nhắn không hiện trong chat.
 *
 * Cách dùng:
 * ```
 * ChatListener(player) {
 *     val text = message  // tin nhắn player vừa gõ
 *     // xử lý...
 * }
 * ```
 *
 * @param player Player cần lắng nghe
 * @param callback Lambda nhận ChatContext (có `message` property)
 */
class ChatListener(player: Player, callback: ChatContext.() -> Unit) {
    init {
        val uuid = player.uniqueId

        val listener = object : Listener {
            @EventHandler(priority = EventPriority.LOWEST)
            fun onChat(event: AsyncPlayerChatEvent) {
                if (event.player.uniqueId != uuid) return
                event.isCancelled = true

                // Tự hủy ngay — chỉ nhận 1 lần
                HandlerList.unregisterAll(this)

                // Gọi callback
                val ctx = ChatContext(event.message)
                ctx.callback()
            }
        }

        Bukkit.getPluginManager().registerEvents(listener, Nap2S.instance)
    }
}

/**
 * Context cho ChatListener callback.
 * @property message Tin nhắn player vừa gõ.
 */
class ChatContext(val message: String)

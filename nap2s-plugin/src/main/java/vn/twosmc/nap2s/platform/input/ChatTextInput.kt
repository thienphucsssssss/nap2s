package vn.twosmc.nap2s.platform.input

import vn.twosmc.nap2s.Nap2S
import vn.twosmc.nap2s.utils.Utils.Companion.runSync
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent

/**
 * Text input qua chat — fallback mặc định.
 *
 * Flow:
 * 1. Gửi prompt (title) và hướng dẫn hủy cho player
 * 2. Đăng ký listener 1 lần, đợi player chat
 * 3. Nếu gõ "hủy"/"huy"/"cancel" → gọi onCancel
 * 4. Nếu gõ text khác → gọi onSubmit
 * 5. Tự hủy listener sau khi nhận
 *
 * Luôn trả [InputSession] (không bao giờ null) — chat luôn khả dụng.
 *
 * LƯU Ý: AsyncPlayerChatEvent chạy trên async thread.
 * Callback onSubmit/onCancel được wrap bằng runSync để đảm bảo
 * Bukkit API (register listener, send message, v.v.) chạy trên main thread.
 */
class ChatTextInput : TextInput {
    override fun request(
        player: Player,
        title: String,
        initialText: String,
        placeholder: String,
        onSubmit: (String) -> Unit,
        onCancel: (() -> Unit)?
    ): InputSession {
        val lang = Nap2S.instance.language

        // Gửi prompt chat — chỉ khi mode chat, không gửi khi anvil
        player.sendMessage("${Nap2S.instance.config.prefix} $title")
        player.sendMessage("${Nap2S.instance.config.prefix} ${lang.inputCancel}")

        val uuid = player.uniqueId
        var handled = false // Guard chống callback gọi 2 lần

        val listener = object : Listener {
            @EventHandler(priority = EventPriority.LOWEST)
            fun onChat(event: AsyncPlayerChatEvent) {
                if (event.player.uniqueId != uuid) return
                if (handled) return
                handled = true

                event.isCancelled = true
                HandlerList.unregisterAll(this)

                val text = event.message.trim()
                // Nhận cả "hủy" (có dấu), "huy" (không dấu), và "cancel"
                if (text.equals("hủy", true) || text.equals("huy", true)
                    || text.equals("cancel", true)) {
                    // Wrap bằng runSync — AsyncPlayerChatEvent chạy trên async thread,
                    // callback có thể gọi Bukkit API (send message, register listener, v.v.)
                    runSync { onCancel?.invoke() }
                } else {
                    runSync { onSubmit(text) }
                }
            }
        }

        Bukkit.getPluginManager().registerEvents(listener, Nap2S.instance)

        // Trả session có cancel() thật — unregister listener
        return object : InputSession {
            override fun cancel() {
                if (!handled) {
                    handled = true
                    HandlerList.unregisterAll(listener)
                }
            }
        }
    }
}

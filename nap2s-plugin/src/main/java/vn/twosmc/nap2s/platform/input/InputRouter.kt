package vn.twosmc.nap2s.platform.input

import vn.twosmc.nap2s.Nap2S
import vn.twosmc.nap2s.compat.BedrockCompat
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Central routing cho text input — quyết định dùng anvil hay chat.
 *
 * Quy tắc:
 * 1. Bedrock player → luôn chat (anvil không hiển thị đúng trên Bedrock)
 * 2. Java + mode ANVIL → thử [AnvilTextInput], nếu fail → fallback [ChatTextInput]
 * 3. Java + mode CHAT → dùng [ChatTextInput] luôn
 *
 * Tracking pending input:
 * - Mỗi player chỉ có 1 session active tại 1 thời điểm
 * - Session cũ bị cancel thật (hủy listener / đóng anvil) khi:
 *   - Player bắt đầu input mới
 *   - Player quit server
 *   - Plugin disable/reload
 * - Session được clear TRƯỚC khi gọi onSubmit → cho phép flow 2 bước (seri → pin)
 */
object InputRouter {
    /** Map player UUID → session đang active. InputSession có cancel() thật. */
    private val pending = ConcurrentHashMap<UUID, InputSession>()

    private val anvilInput = AnvilTextInput()
    private val chatInput = ChatTextInput()

    /**
     * Mở input cho player theo mode được chỉ định.
     *
     * @param player Player cần nhập
     * @param mode ANVIL hoặc CHAT (từ config)
     * @param title Tiêu đề (anvil: title GUI, chat: prompt message)
     * @param initialText Text mặc định hiện sẵn
     * @param placeholder Text gợi ý
     * @param onSubmit Callback khi submit — được gọi SAU khi pending đã clear
     * @param onCancel Callback khi cancel — được gọi SAU khi pending đã clear
     */
    fun requestInput(
        player: Player,
        mode: InputMode,
        title: String,
        initialText: String = "",
        placeholder: String = "",
        onSubmit: (String) -> Unit,
        onCancel: (() -> Unit)? = null
    ) {
        val uuid = player.uniqueId

        // Cancel session cũ nếu đang pending (chống multi-input)
        pending.remove(uuid)?.cancel()

        // Bedrock → luôn chat, bất kể config
        val effectiveMode = if (BedrockCompat.isBedrockPlayer(player)) InputMode.CHAT else mode

        // Wrap onSubmit: clear pending TRƯỚC khi gọi callback
        // → cho phép callback tạo session mới (flow 2 bước seri → pin)
        val wrappedSubmit: (String) -> Unit = { text ->
            pending.remove(uuid)  // Clear trước
            onSubmit(text)        // Callback có thể gọi requestInput() lần 2
        }

        val wrappedCancel: (() -> Unit) = {
            pending.remove(uuid)
            onCancel?.invoke()
        }

        val session: InputSession? = when (effectiveMode) {
            InputMode.ANVIL -> {
                // Thử anvil, nếu fail (null) → tự động fallback chat
                anvilInput.request(player, title, initialText, placeholder, wrappedSubmit, wrappedCancel)
                    ?: chatInput.request(player, title, initialText, placeholder, wrappedSubmit, wrappedCancel)
            }
            InputMode.CHAT -> {
                chatInput.request(player, title, initialText, placeholder, wrappedSubmit, wrappedCancel)
            }
        }

        if (session != null) {
            pending[uuid] = session
        }
    }

    /** Hủy input đang chờ của 1 player — gọi cancel() thật trên session. */
    fun clearPending(uuid: UUID) {
        pending.remove(uuid)?.cancel()
    }

    /**
     * Hủy tất cả session đang chờ — gọi khi plugin disable/reload.
     * Copy snapshot trước khi clear để tránh ConcurrentModification
     * nếu callback cancel chỉnh map pending.
     */
    fun clearAll() {
        val snapshot = ArrayList(pending.values)
        pending.clear()
        snapshot.forEach { session ->
            runCatching { session.cancel() }
        }
    }
}

/**
 * Chế độ nhập liệu cho Java player.
 * - [ANVIL]: mở anvil GUI (UX tốt, trực quan)
 * - [CHAT]: nhập qua chat (fallback, luôn hoạt động)
 */
enum class InputMode {
    ANVIL, CHAT;

    companion object {
        /**
         * Parse mềm — nếu giá trị sai thì log warning và trả fallback.
         * Không throw exception → plugin không crash vì admin gõ sai config.
         */
        fun parseOrDefault(value: String, fallback: InputMode = ANVIL): InputMode {
            return try {
                valueOf(value.trim().uppercase())
            } catch (e: IllegalArgumentException) {
                Nap2S.instance.logger.warning(
                    "[Config] Giá trị input mode '$value' không hợp lệ. " +
                    "Chỉ chấp nhận: anvil, chat. Dùng '${fallback.name.lowercase()}' mặc định."
                )
                fallback
            }
        }
    }
}

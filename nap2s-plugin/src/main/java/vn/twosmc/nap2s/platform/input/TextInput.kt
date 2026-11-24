package vn.twosmc.nap2s.platform.input

import org.bukkit.entity.Player

/**
 * Interface nhận text input từ player.
 *
 * Implementation chỉ làm: hiện ô nhập → nhận text → trả kết quả.
 * Không chứa logic validate hay business.
 *
 * Có 2 implementation:
 * - [ChatTextInput]: nhận text qua chat (fallback mặc định)
 * - [AnvilTextInput]: nhận text qua anvil GUI (UX tốt hơn cho Java player)
 */
interface TextInput {
    /**
     * Mở input cho player.
     *
     * @param player Player cần nhập
     * @param title Tiêu đề hiển thị (anvil: title trên GUI, chat: prompt message)
     * @param initialText Text mặc định hiện sẵn trong ô nhập
     * @param placeholder Text gợi ý khi ô nhập trống
     * @param onSubmit Callback khi player submit text
     * @param onCancel Callback khi player hủy (đóng GUI / gõ "huy")
     * @return [InputSession] nếu mở thành công, null nếu không mở được (cần fallback)
     */
    fun request(
        player: Player,
        title: String,
        initialText: String = "",
        placeholder: String = "",
        onSubmit: (String) -> Unit,
        onCancel: (() -> Unit)? = null
    ): InputSession?
}

/**
 * Session đại diện cho 1 input đang mở.
 * Có thể [cancel] để hủy listener/đóng GUI thực sự — không chỉ xóa khỏi map.
 */
interface InputSession {
    /** Hủy session — unregister listener hoặc đóng anvil inventory. */
    fun cancel()
}

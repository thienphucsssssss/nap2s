package vn.twosmc.nap2s.platform.input

import vn.twosmc.nap2s.Nap2S
import vn.twosmc.nap2s.utils.Utils.Companion.runSync
import net.wesjd.anvilgui.AnvilGUI
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * Text input qua AnvilGUI (WesJD/AnvilGUI).
 *
 * Flow:
 * 1. Mở anvil inventory với title và text mặc định
 * 2. Player sửa text trong ô anvil
 * 3. Click output slot → onSubmit(text), đóng GUI
 * 4. Đóng GUI mà chưa click output → onCancel
 *
 * Có cờ [submitted] để guard giữa submit thành công và đóng/cancel.
 * Nếu thư viện không load được (NMS not found) → trả null → InputRouter fallback chat.
 */
class AnvilTextInput : TextInput {
    override fun request(
        player: Player,
        title: String,
        initialText: String,
        placeholder: String,
        onSubmit: (String) -> Unit,
        onCancel: (() -> Unit)?
    ): InputSession? {
        return try {
            // Cờ guard — chống onClose fire cancel sau khi đã submit
            var submitted = false

            AnvilGUI.Builder().apply {
                plugin(Nap2S.instance)
                title(title)
                text(initialText.ifBlank { placeholder.ifBlank { " " } })

                // Item bên trái anvil — diamond với tên "Nhấp để nhập"
                itemLeft(createAnvilItem())

                onClick { slot, state ->
                    if (slot != AnvilGUI.Slot.OUTPUT) return@onClick emptyList()

                    // Đánh dấu submitted TRƯỚC khi close
                    submitted = true
                    val text = state.text

                    listOf(
                        AnvilGUI.ResponseAction.run {
                            // onSubmit gọi trên main thread (AnvilGUI callback chạy trên main thread)
                            onSubmit(text)
                        },
                        AnvilGUI.ResponseAction.close()
                    )
                }

                onClose { _ ->
                    // Chỉ fire cancel nếu CHƯA submit
                    if (!submitted) {
                        onCancel?.invoke()
                    }
                }

                open(player)
            }

            // Trả session — cancel() = đóng inventory player
            object : InputSession {
                override fun cancel() {
                    if (!submitted) {
                        submitted = true // Ngăn onClose fire cancel
                        // Đóng inventory trên main thread
                        runSync { player.closeInventory() }
                    }
                }
            }
        } catch (e: Throwable) {
            // Catch Throwable (không chỉ Exception) — bắt cả NoClassDefFoundError,
            // LinkageError từ NMS mismatch hoặc library không tương thích.
            Nap2S.instance.logger.warning(
                "[AnvilGUI] Không mở được anvil cho ${player.name}: ${e.javaClass.simpleName} - ${e.message}"
            )
            null // → InputRouter sẽ fallback sang chat
        }
    }

    companion object {
        /** Tạo item hiển thị bên trái anvil. */
        private fun createAnvilItem(): ItemStack {
            val lang = Nap2S.instance.language
            val item = ItemStack(Material.DIAMOND)
            val meta = item.itemMeta ?: return item
            meta.setDisplayName(lang.inputAnvilClick)
            item.itemMeta = meta
            return item
        }
    }
}

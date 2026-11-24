package vn.twosmc.nap2s

import vn.twosmc.nap2s.banking.QrMapManager
import vn.twosmc.nap2s.database.PlayerInfoDAO
import vn.twosmc.nap2s.platform.input.InputRouter
import vn.twosmc.nap2s.utils.Utils.Companion.runNotSync
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerLoginEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerQuitEvent

class Nap2SListener : Listener {
    private fun updateUUID(player: Player) {
        val uuid = player.uniqueId.toString()
        val name = player.name
        runNotSync { PlayerInfoDAO.getInstance().updateData(uuid, name) }
    }

    @EventHandler
    fun onLogin(e: PlayerLoginEvent) = updateUUID(e.player)

    @EventHandler
    fun onQuit(e: PlayerQuitEvent) {
        updateUUID(e.player)
        // Hủy input session đang chờ (chat listener / anvil GUI)
        InputRouter.clearPending(e.player.uniqueId)
    }

    @EventHandler
    fun onJoin(e: PlayerJoinEvent) {
        val player = e.player

        Nap2S.instance.milestonesMaster.onJoin(player)
        UpdateChecker.sendUpdateMessage(player)

        // Rebuild QR map PENDING hoặc dọn map đã hết hiệu lực sau restart/reload
        QrMapManager.rebuildOrCleanupMaps(player)
    }

    @EventHandler(ignoreCancelled = true)
    fun onDropQrMap(e: PlayerDropItemEvent) {
        if (!QrMapManager.isQrMap(e.itemDrop.itemStack)) return
        e.isCancelled = true
        e.player.sendMessage("§c[Nap2S] §fQR nạp tiền không thể vứt ra ngoài khi giao dịch còn hiệu lực.")
    }

    @EventHandler(ignoreCancelled = true)
    fun onInventoryClick(e: InventoryClickEvent) {
        val currentIsQr = QrMapManager.isQrMap(e.currentItem)
        val cursorIsQr = QrMapManager.isQrMap(e.cursor)
        if (!currentIsQr && !cursorIsQr) return

        val dropActions = setOf(
            InventoryAction.DROP_ALL_CURSOR,
            InventoryAction.DROP_ONE_CURSOR,
            InventoryAction.DROP_ALL_SLOT,
            InventoryAction.DROP_ONE_SLOT
        )

        val moveToOther = e.action == InventoryAction.MOVE_TO_OTHER_INVENTORY
        val placeIntoTopInventory = cursorIsQr && e.rawSlot < e.view.topInventory.size

        if (e.action in dropActions || moveToOther || placeIntoTopInventory) {
            e.isCancelled = true
            (e.whoClicked as? Player)?.sendMessage("§c[Nap2S] §fQR nạp tiền đang bị khóa, bạn không thể làm rơi hoặc cất nó sang chỗ khác.")
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onInventoryDrag(e: InventoryDragEvent) {
        if (!QrMapManager.isQrMap(e.oldCursor)) return
        val draggingIntoTopInventory = e.rawSlots.any { it < e.view.topInventory.size }
        if (!draggingIntoTopInventory) return

        e.isCancelled = true
        (e.whoClicked as? Player)?.sendMessage("§c[Nap2S] §fQR nạp tiền đang bị khóa, bạn không thể kéo nó sang chỗ khác.")
    }
}

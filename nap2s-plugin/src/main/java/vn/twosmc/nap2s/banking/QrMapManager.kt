package vn.twosmc.nap2s.banking

import vn.twosmc.nap2s.Nap2S
import vn.twosmc.nap2s.compat.BedrockCompat
import vn.twosmc.nap2s.database.BankingOrderDAO
import vn.twosmc.nap2s.utils.Utils.Companion.runNotSync
import vn.twosmc.nap2s.utils.Utils.Companion.runSync
import vn.twosmc.nap2s.utils.Utils.Companion.warning
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.MapMeta
import org.bukkit.map.MapCanvas
import org.bukkit.map.MapRenderer
import org.bukkit.map.MapView
import org.bukkit.persistence.PersistentDataType
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.net.URL
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO

/**
 * Quản lý QR map cho giao dịch ATM/QR.
 * Java và Bedrock đều sẽ thử nhận QR map. Bedrock vẫn được gửi thêm link dự phòng.
 *
 * Tracking RAM (activeMapIds, playerMaps) chỉ sống trong 1 session.
 * Sau restart/reload, map được rebuild từ DB khi player join.
 */
object QrMapManager {
    /** orderId → MapView.id (chỉ sống trong RAM) */
    private val activeMapIds = ConcurrentHashMap<String, Int>()
    /** player UUID → orderId */
    private val playerMaps = ConcurrentHashMap<UUID, String>()
    private val qrKey by lazy { NamespacedKey(Nap2S.instance, "banking_qr_map") }

    // ─── Tạo QR map mới cho player ─────────────────────────────────
    fun giveQrMap(player: Player, orderId: String, qrImageUrl: String, checkoutUrl: String = "") {
        if (qrImageUrl.isBlank()) return

        val isBedrock = BedrockCompat.isBedrockPlayer(player)
        if (isBedrock) {
            player.sendMessage("§a[Nap2S] §fĐang thử hiển thị QR map cho Bedrock.")
            if (checkoutUrl.isNotBlank()) {
                player.sendMessage("§7Nếu map không hiện rõ, mở link thanh toán: §b§n$checkoutUrl")
            }
            if (qrImageUrl != checkoutUrl) {
                player.sendMessage("§7Link ảnh QR dự phòng: §b§n$qrImageUrl")
            }
        }

        runNotSync {
            val image = downloadAndScale(qrImageUrl)
            if (image == null) {
                Exception("Không thể tải ảnh QR: $qrImageUrl").warning("QR Map")
                return@runNotSync
            }

            runSync {
                if (!player.isOnline) return@runSync

                // Xoá map cũ nếu có (theo tracking RAM hoặc scan inventory)
                removeAllQrMaps(player)

                val mapView = Bukkit.createMap(player.world)
                mapView.scale = MapView.Scale.FARTHEST
                mapView.isLocked = true
                mapView.renderers.forEach { mapView.removeRenderer(it) }
                mapView.addRenderer(QrImageRenderer(image))

                val mapItem = ItemStack(Material.FILLED_MAP)
                val meta = mapItem.itemMeta as? MapMeta ?: return@runSync
                meta.mapView = mapView
                meta.setDisplayName("§6§lQR Nạp Tiền §8| §f$orderId")
                meta.lore = listOf(
                    "§7Quét mã QR này để chuyển khoản",
                    "§7Đơn hàng: §f$orderId",
                    "§cKhông được vứt bỏ item này"
                )
                meta.persistentDataContainer.set(qrKey, PersistentDataType.STRING, orderId)
                mapItem.itemMeta = meta

                val inventory = player.inventory

                // Đếm số item cần dời khỏi tay (mainhand + offhand)
                val needSlots = listOf(inventory.itemInMainHand, inventory.itemInOffHand)
                    .count { it.type != Material.AIR }

                // Kiểm tra đủ slot trống TRƯỚC KHI thao tác — tránh dupe
                if (needSlots > 0) {
                    val emptySlots = (0 until 36).count {
                        inventory.getItem(it) == null || inventory.getItem(it)?.type == Material.AIR
                    }
                    if (emptySlots < needSlots) {
                        player.sendMessage("§c[Nap2S] §fCần thêm ít nhất $needSlots ô trống trong túi đồ để mở QR full-screen.")
                        return@runSync
                    }

                    // An toàn: dời item sang slot trống
                    if (inventory.itemInMainHand.type != Material.AIR) {
                        inventory.addItem(inventory.itemInMainHand.clone())
                        inventory.setItemInMainHand(ItemStack(Material.AIR))
                    }
                    if (inventory.itemInOffHand.type != Material.AIR) {
                        inventory.addItem(inventory.itemInOffHand.clone())
                        inventory.setItemInOffHand(ItemStack(Material.AIR))
                    }
                }

                inventory.setItemInMainHand(mapItem)
                inventory.setItemInOffHand(ItemStack(Material.AIR))
                player.updateInventory()

                activeMapIds[orderId] = mapView.id
                playerMaps[player.uniqueId] = orderId

                player.sendMessage("§a[Nap2S] §fĐã mở QR full-screen. Hãy giữ bản đồ ở tay chính để quét.")
            }
        }
    }

    // ─── Xoá QR map theo player (dùng khi player request) ──────────
    fun removeQrMap(player: Player) {
        val orderId = playerMaps.remove(player.uniqueId)
        if (orderId != null) {
            activeMapIds.remove(orderId)
        }
        // Luôn scan inventory để xoá dù tracking RAM mất
        removeQrItemsFromInventory(player)
    }

    // ─── Xoá QR map theo orderId (dùng khi PAID/EXPIRED/CANCELLED) ─
    /**
     * Xoá QR map theo orderId — không phụ thuộc tracking RAM.
     * Thử xoá theo RAM trước, fallback scan inventory bằng qrKey.
     * Gọi từ cả main thread lẫn async (sẽ tự runSync).
     */
    fun removeQrMapByOrderId(orderId: String) {
        activeMapIds.remove(orderId)
        val entry = playerMaps.entries.find { it.value == orderId }
        if (entry != null) {
            playerMaps.remove(entry.key)
        }

        // Tìm UUID player từ tracking hoặc từ DB
        val playerUuid: UUID? = entry?.key ?: run {
            try {
                val order = BankingOrderDAO.getInstance().findByOrderId(orderId)
                order?.uuid?.let { UUID.fromString(it) }
            } catch (_: Exception) {
                null
            }
        }

        if (playerUuid == null) return
        val player = Bukkit.getPlayer(playerUuid)
        if (player?.isOnline != true) return

        runSync {
            if (!player.isOnline) return@runSync
            removeQrItemsFromInventory(player, orderId)
        }
    }

    // ─── Scan inventory và xoá item QR map ─────────────────────────
    /**
     * Xoá tất cả item QR map khỏi inventory.
     * Nếu orderId != null, chỉ xoá item có orderId khớp.
     * Nếu orderId == null, xoá tất cả QR map items.
     */
    private fun removeQrItemsFromInventory(player: Player, targetOrderId: String? = null) {
        val inventory = player.inventory
        var removed = false

        for (i in 0 until inventory.size) {
            val item = inventory.getItem(i) ?: continue
            if (item.type != Material.FILLED_MAP) continue
            val meta = item.itemMeta as? MapMeta ?: continue
            val oid = meta.persistentDataContainer.get(qrKey, PersistentDataType.STRING) ?: continue
            if (targetOrderId != null && oid != targetOrderId) continue
            inventory.setItem(i, null)
            removed = true
        }

        // Check offhand
        val offHand = inventory.itemInOffHand
        if (offHand.type == Material.FILLED_MAP) {
            val meta = offHand.itemMeta as? MapMeta
            val oid = meta?.persistentDataContainer?.get(qrKey, PersistentDataType.STRING)
            if (oid != null && (targetOrderId == null || oid == targetOrderId)) {
                inventory.setItemInOffHand(ItemStack(Material.AIR))
                removed = true
            }
        }

        // Check mainhand
        val mainHand = inventory.itemInMainHand
        if (mainHand.type == Material.FILLED_MAP) {
            val meta = mainHand.itemMeta as? MapMeta
            val oid = meta?.persistentDataContainer?.get(qrKey, PersistentDataType.STRING)
            if (oid != null && (targetOrderId == null || oid == targetOrderId)) {
                inventory.setItemInMainHand(ItemStack(Material.AIR))
                removed = true
            }
        }

        if (removed) {
            player.updateInventory()
            player.sendMessage("§a[Nap2S] §7Đã xoá bản đồ QR code khỏi túi đồ.")
        }
    }

    /**
     * Xoá tất cả QR map items khỏi player (không phân biệt orderId).
     */
    private fun removeAllQrMaps(player: Player) {
        playerMaps.remove(player.uniqueId)?.let { activeMapIds.remove(it) }
        removeQrItemsFromInventory(player)
    }

    // ─── Download và scale ảnh QR ──────────────────────────────────
    private fun downloadAndScale(url: String): BufferedImage? {
        return runCatching {
            val connection = URL(url).openConnection()
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("User-Agent", "Nap2S-Plugin/1.0")
            val original = ImageIO.read(connection.getInputStream()) ?: return null

            val canvas = BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB)
            val graphics = canvas.createGraphics()
            graphics.color = Color.WHITE
            graphics.fillRect(0, 0, 128, 128)
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED)
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)

            val targetSize = 112
            val offset = (128 - targetSize) / 2
            graphics.drawImage(original, offset, offset, targetSize, targetSize, null)
            graphics.dispose()

            val sharpened = BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB)
            for (x in 0 until 128) {
                for (y in 0 until 128) {
                    val rgb = canvas.getRGB(x, y)
                    val alpha = rgb ushr 24 and 0xFF
                    if (alpha < 32) {
                        sharpened.setRGB(x, y, Color.WHITE.rgb)
                        continue
                    }

                    val red = rgb shr 16 and 0xFF
                    val green = rgb shr 8 and 0xFF
                    val blue = rgb and 0xFF
                    val luminance = (red * 299 + green * 587 + blue * 114) / 1000
                    sharpened.setRGB(x, y, if (luminance < 200) Color.BLACK.rgb else Color.WHITE.rgb)
                }
            }

            sharpened
        }.onFailure {
            it.warning("Lỗi tải ảnh QR")
        }.getOrNull()
    }

    // ─── Cleanup khi reload/stop ───────────────────────────────────
    /**
     * Clear tracking RAM khi reload config hoặc tắt banking.
     * KHÔNG xoá item khỏi inventory — để rebuildOrCleanupMaps() xử lý
     * khi player join lại (rebuild nếu PENDING, xoá nếu đã hết hiệu lực).
     */
    fun cleanup() {
        activeMapIds.clear()
        playerMaps.clear()
    }

    // ─── Rebuild/cleanup QR map khi player join ────────────────────
    /**
     * Gọi khi player join. Quét inventory tìm QR map items:
     * - PENDING + qrImageUrl còn tải được → rebuild MapView mới, khôi phục tracking
     * - PENDING + qrImageUrl lỗi → xoá map, gửi link dự phòng
     * - Không còn PENDING (PAID/EXPIRED/CANCELLED/không tồn tại) → xoá map
     *
     * Không lưu slot index — khi write-back sẽ scan lại bằng qrKey+orderId
     * để tránh race condition nếu player di chuyển item trong lúc async.
     */
    fun rebuildOrCleanupMaps(player: Player) {
        // Bước 1: Thu thập orderId từ inventory (main thread) — chỉ lấy danh sách, không lưu slot
        val orphanOrderIds = collectOrphanOrderIds(player)
        if (orphanOrderIds.isEmpty()) return

        // Bước 2: Query DB + download ảnh (async)
        runNotSync {
            val dao = BankingOrderDAO.getInstance()

            data class RebuildAction(
                val orderId: String,
                val action: String, // "rebuild" hoặc "remove"
                val image: BufferedImage? = null,
                val checkoutUrl: String = ""
            )

            val actions = mutableListOf<RebuildAction>()

            for (orderId in orphanOrderIds) {
                val order = dao.findByOrderId(orderId)

                if (order == null || !order.isPending()) {
                    actions.add(RebuildAction(orderId, "remove"))
                    continue
                }

                // Đơn còn PENDING → thử rebuild
                if (order.qrImageUrl.isBlank()) {
                    actions.add(RebuildAction(orderId, "remove", checkoutUrl = order.checkoutUrl))
                    continue
                }

                val image = downloadAndScale(order.qrImageUrl)
                if (image != null) {
                    actions.add(RebuildAction(orderId, "rebuild", image = image, checkoutUrl = order.checkoutUrl))
                } else {
                    actions.add(RebuildAction(orderId, "remove", checkoutUrl = order.checkoutUrl))
                }
            }

            if (actions.isEmpty()) return@runNotSync

            // Bước 3: Thao tác inventory (sync) — scan lại bằng orderId, không dùng slot cũ
            runSync {
                if (!player.isOnline) return@runSync
                val inv = player.inventory
                var anyChanged = false

                for (result in actions) {
                    when (result.action) {
                        "remove" -> {
                            // Scan và xoá tất cả item có orderId này
                            if (removeQrItemsByOrderId(inv, result.orderId)) {
                                anyChanged = true
                            }
                            if (result.checkoutUrl.isNotBlank()) {
                                player.sendMessage("§e[Nap2S] §fĐơn §e${result.orderId} §fvẫn đang chờ thanh toán.")
                                player.sendMessage("§7Dùng §b/napatm status §7hoặc mở link: §b§n${result.checkoutUrl}")
                            }
                        }
                        "rebuild" -> {
                            val image = result.image ?: continue

                            // Tìm item cũ theo orderId và thay bằng item mới
                            val mapView = Bukkit.createMap(player.world)
                            mapView.scale = MapView.Scale.FARTHEST
                            mapView.isLocked = true
                            mapView.renderers.forEach { mapView.removeRenderer(it) }
                            mapView.addRenderer(QrImageRenderer(image))

                            val mapItem = ItemStack(Material.FILLED_MAP)
                            val meta = mapItem.itemMeta as? MapMeta ?: continue
                            meta.mapView = mapView
                            meta.setDisplayName("§6§lQR Nạp Tiền §8| §f${result.orderId}")
                            meta.lore = listOf(
                                "§7Quét mã QR này để chuyển khoản",
                                "§7Đơn hàng: §f${result.orderId}",
                                "§cKhông được vứt bỏ item này"
                            )
                            meta.persistentDataContainer.set(qrKey, PersistentDataType.STRING, result.orderId)
                            mapItem.itemMeta = meta

                            // Xoá item cũ trước, rồi đặt item mới vào slot đầu tiên tìm được
                            val replacedSlot = replaceQrItemByOrderId(inv, result.orderId, mapItem)
                            if (replacedSlot) {
                                activeMapIds[result.orderId] = mapView.id
                                playerMaps[player.uniqueId] = result.orderId
                                anyChanged = true
                                player.sendMessage("§a[Nap2S] §fĐã khôi phục bản đồ QR cho đơn §e${result.orderId}§f.")
                            }
                        }
                    }
                }

                if (anyChanged) {
                    player.updateInventory()
                }
            }
        }
    }

    /**
     * Thu thập danh sách orderId orphan (có trong inventory nhưng không có tracking RAM).
     * Deduplicate kết quả.
     */
    private fun collectOrphanOrderIds(player: Player): Set<String> {
        val result = mutableSetOf<String>()
        val inventory = player.inventory

        for (i in 0 until inventory.size) {
            val item = inventory.getItem(i) ?: continue
            if (item.type != Material.FILLED_MAP) continue
            val meta = item.itemMeta as? MapMeta ?: continue
            val orderId = meta.persistentDataContainer.get(qrKey, PersistentDataType.STRING) ?: continue
            if (!activeMapIds.containsKey(orderId)) {
                result.add(orderId)
            }
        }

        // Offhand + mainhand
        listOf(inventory.itemInOffHand, inventory.itemInMainHand).forEach { hand ->
            if (hand.type == Material.FILLED_MAP) {
                val meta = hand.itemMeta as? MapMeta
                val oid = meta?.persistentDataContainer?.get(qrKey, PersistentDataType.STRING)
                if (oid != null && !activeMapIds.containsKey(oid)) {
                    result.add(oid)
                }
            }
        }

        return result
    }

    /**
     * Scan inventory và xoá tất cả QR map items có orderId khớp.
     * Trả true nếu có item bị xoá.
     */
    private fun removeQrItemsByOrderId(inventory: org.bukkit.inventory.PlayerInventory, orderId: String): Boolean {
        var removed = false

        for (i in 0 until inventory.size) {
            val item = inventory.getItem(i) ?: continue
            if (item.type != Material.FILLED_MAP) continue
            val meta = item.itemMeta as? MapMeta ?: continue
            val oid = meta.persistentDataContainer.get(qrKey, PersistentDataType.STRING) ?: continue
            if (oid == orderId) {
                inventory.setItem(i, null)
                removed = true
            }
        }

        // Check offhand
        val offHand = inventory.itemInOffHand
        if (offHand.type == Material.FILLED_MAP) {
            val meta = offHand.itemMeta as? MapMeta
            if (meta?.persistentDataContainer?.get(qrKey, PersistentDataType.STRING) == orderId) {
                inventory.setItemInOffHand(ItemStack(Material.AIR))
                removed = true
            }
        }

        // Check mainhand
        val mainHand = inventory.itemInMainHand
        if (mainHand.type == Material.FILLED_MAP) {
            val meta = mainHand.itemMeta as? MapMeta
            if (meta?.persistentDataContainer?.get(qrKey, PersistentDataType.STRING) == orderId) {
                inventory.setItemInMainHand(ItemStack(Material.AIR))
                removed = true
            }
        }

        return removed
    }

    /**
     * Tìm item QR map có orderId khớp, xoá item cũ và đặt item mới vào đúng slot đó.
     * Trả true nếu thay thành công.
     */
    private fun replaceQrItemByOrderId(
        inventory: org.bukkit.inventory.PlayerInventory,
        orderId: String,
        newItem: ItemStack
    ): Boolean {
        // Tìm slot đầu tiên chứa QR map với orderId khớp
        for (i in 0 until inventory.size) {
            val item = inventory.getItem(i) ?: continue
            if (item.type != Material.FILLED_MAP) continue
            val meta = item.itemMeta as? MapMeta ?: continue
            val oid = meta.persistentDataContainer.get(qrKey, PersistentDataType.STRING) ?: continue
            if (oid == orderId) {
                // Xoá tất cả duplicate trước
                removeQrItemsByOrderId(inventory, orderId)
                // Đặt item mới vào slot đầu tiên
                inventory.setItem(i, newItem)
                return true
            }
        }

        // Check offhand
        val offHand = inventory.itemInOffHand
        if (offHand.type == Material.FILLED_MAP) {
            val meta = offHand.itemMeta as? MapMeta
            if (meta?.persistentDataContainer?.get(qrKey, PersistentDataType.STRING) == orderId) {
                removeQrItemsByOrderId(inventory, orderId)
                inventory.setItemInOffHand(newItem)
                return true
            }
        }

        // Check mainhand
        val mainHand = inventory.itemInMainHand
        if (mainHand.type == Material.FILLED_MAP) {
            val meta = mainHand.itemMeta as? MapMeta
            if (meta?.persistentDataContainer?.get(qrKey, PersistentDataType.STRING) == orderId) {
                removeQrItemsByOrderId(inventory, orderId)
                inventory.setItemInMainHand(newItem)
                return true
            }
        }

        return false
    }

    // ─── Utilities ─────────────────────────────────────────────────
    fun isQrMap(item: ItemStack?): Boolean {
        if (item == null || item.type != Material.FILLED_MAP) return false
        val meta = item.itemMeta as? MapMeta ?: return false
        return meta.persistentDataContainer.has(qrKey, PersistentDataType.STRING)
    }
}

class QrImageRenderer(private val image: BufferedImage) : MapRenderer() {
    private var rendered = false

    override fun render(mapView: MapView, canvas: MapCanvas, player: Player) {
        if (rendered) return
        canvas.drawImage(0, 0, image)
        rendered = true
    }
}

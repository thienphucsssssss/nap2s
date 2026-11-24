package vn.twosmc.nap2s.platform.gui

import net.md_5.bungee.api.ChatColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap

// ─── GuiItemStack ─────────────────────────────────────────────

/**
 * Wrapper cho ItemStack với onClick handler
 */
class GuiItemStack(val item: ItemStack, private var clickHandler: (() -> Unit)? = null) {

    constructor(item: ItemStack) : this(item, null)

    /** Đặt click handler — dùng DSL: GuiItemStack(item).onClick { ... } */
    fun onClick(handler: () -> Unit): GuiItemStack {
        clickHandler = handler
        return this
    }

    /** Gọi click handler (nếu có). */
    fun handleClick() {
        clickHandler?.invoke()
    }

    /** Có click handler hay không. */
    fun hasClickHandler() = clickHandler != null
}

// ─── GuiIcon ──────────────────────────────────────────────────

/**
 * Icon definition parse từ YAML config
 *
 * Các key YAML hỗ trợ:
 * - material: tên Material (VD: "DIAMOND", "BLACK_STAINED_GLASS_PANE")
 * - name: display name (hỗ trợ & color codes)
 * - lore: list of strings
 * - amount: số lượng item (default 1)
 * - glow: boolean — enchant glow effect
 */
data class GuiIcon(
    var material: Material = Material.STONE,
    var name: String = "",
    var lore: List<String> = emptyList(),
    var amount: Int = 1,
    var glow: Boolean = false
) {
    /** Tạo GuiItemStack không có click handler. */
    fun toGuiItemStack(): GuiItemStack {
        return GuiItemStack(buildItem())
    }

    /** Tạo GuiItemStack với click handler. */
    fun toGuiItemStack(onClick: () -> Unit): GuiItemStack {
        return GuiItemStack(buildItem(), onClick)
    }

    /** Clone icon để apply placeholder khác nhau. */
    fun clone(): GuiIcon = copy(lore = lore.toMutableList())

    /** Build ItemStack từ icon definition. */
    private fun buildItem(): ItemStack {
        val item = ItemStack(material, amount.coerceAtLeast(1))
        val meta: ItemMeta = item.itemMeta ?: return item
        if (name.isNotBlank()) {
            meta.setDisplayName(translateColor(name))
        }
        if (lore.isNotEmpty()) {
            meta.lore = lore.map { translateColor(it) }
        }
        if (glow) {
            // Tương thích cả Spigot cũ lẫn 1.20.5+ (Registry-based)
            val unbreaking = runCatching {
                org.bukkit.enchantments.Enchantment.getByName("UNBREAKING")
                    ?: org.bukkit.enchantments.Enchantment.getByName("DURABILITY")
            }.getOrNull()
            if (unbreaking != null) {
                meta.addEnchant(unbreaking, 1, true)
            }
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS)
        }
        item.itemMeta = meta
        return item
    }

    companion object {
        /**
         * Extension: đọc GuiIcon từ ConfigurationSection.
         *
         * VD YAML:
         * ```
         * header:
         *   material: DIAMOND
         *   name: "&eHeader"
         *   lore:
         *     - "&7Line 1"
         *   glow: true
         * ```
         */
        fun ConfigurationSection.getGuiIcon(path: String): GuiIcon {
            val section = getConfigurationSection(path) ?: return GuiIcon()
            val iconSection = section.getConfigurationSection("icon") ?: section
            val materialName = iconSection.getString("material")
                ?: iconSection.getString("type")
                ?: section.getString("material")
                ?: section.getString("type")
                ?: "STONE"
            val material = runCatching { Material.valueOf(materialName.uppercase()) }
                .getOrDefault(Material.STONE)

            return GuiIcon(
                material = material,
                name = section.getString("name")
                    ?: iconSection.getString("name")
                    ?: "",
                lore = section.getStringList("lore").ifEmpty { iconSection.getStringList("lore") },
                amount = section.getInt("amount", iconSection.getInt("amount", 1)),
                glow = section.getBoolean("glow", iconSection.getBoolean("glow", false))
            )
        }
    }
}

// ─── GuiInventory ─────────────────────────────────────────────

/**
 * Chest inventory wrapper
 */
class GuiInventory(private val plugin: JavaPlugin, private val size: Int, private val title: String) : Listener {
    private val inventory: Inventory = Bukkit.createInventory(null, size, title)
    private val clickHandlers = ConcurrentHashMap<Int, () -> Unit>()
    private var locked = false
    private var registered = false

    /** Khóa inventory — không cho player lấy item. */
    fun lock() { locked = true }

    /** Mở khóa inventory. */
    fun unlock() { locked = false }

    /** Đặt item vào slot (single slot). */
    fun setItem(slot: Int, guiItem: GuiItemStack) {
        if (slot < 0 || slot >= size) return
        inventory.setItem(slot, guiItem.item)
        if (guiItem.hasClickHandler()) {
            clickHandlers[slot] = { guiItem.handleClick() }
        }
    }

    /** Đặt item vào nhiều slots (fill). */
    fun setItem(slots: IntArray, guiItem: GuiItemStack) {
        for (slot in slots) {
            setItem(slot, guiItem)
        }
    }

    /** Mở inventory cho player. Chú ý: API cũ dùng tên "openIventory" (typo gốc). */
    fun openIventory(player: Player) {
        openInventory(player)
    }

    /** Mở inventory cho player. */
    fun openInventory(player: Player) {
        if (!registered) {
            Bukkit.getPluginManager().registerEvents(this, plugin)
            registered = true
        }
        player.openInventory(inventory)
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        if (event.view.topInventory != inventory) return

        val clickedTop = event.clickedInventory == inventory
        val movesIntoTop = event.action == InventoryAction.MOVE_TO_OTHER_INVENTORY
        val hotbarSwap = event.hotbarButton >= 0
        val collectToCursor = event.action == InventoryAction.COLLECT_TO_CURSOR

        if (clickedTop || locked || movesIntoTop || hotbarSwap || collectToCursor) {
            event.isCancelled = true
        }

        if (!clickedTop) return

        val handler = clickHandlers[event.rawSlot]
        if (handler != null) {
            handler.invoke()
        }
    }

    @EventHandler
    fun onDrag(event: InventoryDragEvent) {
        if (event.view.topInventory != inventory) return
        if (event.rawSlots.any { it < inventory.size }) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onClose(event: InventoryCloseEvent) {
        if (event.inventory != inventory) return
        HandlerList.unregisterAll(this)
        registered = false
    }

    }

// ─── ConfiguredUI ─────────────────────────────────────────────

/**
 * UI base class load config từ YAML
 *
 * Cung cấp:
 * - viewer: Player đang xem UI
 * - getConfig(): YamlConfiguration
 * - setItem(slot/slots, item): thêm item
 * - lock()/unlock(): khóa/mở
 * - open(): mở UI cho viewer
 *
 * Config file được load từ plugin data folder rồi cache lại.
 */
open class ConfiguredUI(
    protected val viewer: Player?,
    private val configPath: String,
    private val plugin: JavaPlugin
) {
    private val gui: GuiInventory
    private val config: YamlConfiguration

    init {
        config = loadConfig(configPath, plugin)
        val title = translateColor(config.getString("title") ?: config.getString("name") ?: "GUI")
        val rows = config.getInt("rows", 6)
        gui = GuiInventory(plugin, rows * 9, title)
    }

    /** Lấy config đã load. */
    fun getConfig(): YamlConfiguration = config

    /** Đặt item vào slot đơn. */
    fun setItem(slot: Int, guiItem: GuiItemStack) {
        gui.setItem(slot, guiItem)
    }

    /** Đặt item vào nhiều slots (fill). */
    fun setItem(slots: IntArray, guiItem: GuiItemStack) {
        gui.setItem(slots, guiItem)
    }

    /** Khóa inventory. */
    fun lock() { gui.lock() }

    /** Mở khóa inventory. */
    fun unlock() { gui.unlock() }

    /** Mở inventory cho viewer. */
    fun open() {
        viewer?.let { gui.openInventory(it) }
    }

    companion object {
        private val configCache = ConcurrentHashMap<String, YamlConfiguration>()

        /** Load config từ plugin data folder, cache lại. */
        private fun loadConfig(path: String, plugin: JavaPlugin): YamlConfiguration {
            return configCache.getOrPut(path) {
                val file = File(plugin.dataFolder, path)
                if (!file.exists()) {
                    file.parentFile?.mkdirs()
                    plugin.saveResource(path, false)
                }
                val config = YamlConfiguration.loadConfiguration(file)
                // Set defaults từ resource nếu có
                val stream = plugin.getResource(path)
                if (stream != null) {
                    config.setDefaults(YamlConfiguration.loadConfiguration(InputStreamReader(stream)))
                }
                config
            }
        }

        /** Xóa cache — gọi khi reload plugin. */
        fun reloadConfigs(plugin: JavaPlugin) {
            configCache.clear()
        }
    }
}

// ─── GUI Utilities ────────────────────────────────────────────

/**
 * Extension: parse fill slots từ config — thay thế getGuiFillSlots().
 *
 * Hỗ trợ format:
 * - "1,2,3,4" → slot riêng lẻ
 * - "1-9" → range
 * - "1-9,18-26" → mix
 */
fun ConfigurationSection.getGuiFillSlots(path: String): IntArray {
    val raw = get(path) ?: return intArrayOf()
    return when (raw) {
        is String -> parseFillSlots(raw)
        is List<*> -> parsePatternFill(raw.filterIsInstance<String>())
        else -> intArrayOf()
    }
}

/** Parse chuỗi fill slots thành IntArray. */
private fun parseFillSlots(raw: String): IntArray {
    val result = mutableListOf<Int>()
    for (part in raw.split(",")) {
        val trimmed = part.trim()
        if (trimmed.contains("-")) {
            val rangeParts = trimmed.split("-")
            if (rangeParts.size == 2) {
                val start = rangeParts[0].trim().toIntOrNull() ?: continue
                val end = rangeParts[1].trim().toIntOrNull() ?: continue
                for (i in start..end) {
                    result.add(i)
                }
            }
        } else {
            trimmed.toIntOrNull()?.let { result.add(it) }
        }
    }
    return result.toIntArray()
}

/** Parse kieu fill cu dang grid:
 *  - 'xxxxxxxxx'
 *  - 'x       x'
 *  - '  x x x  '
 */
private fun parsePatternFill(rows: List<String>): IntArray {
    val result = mutableListOf<Int>()
    rows.forEachIndexed { rowIndex, row ->
        row.forEachIndexed { colIndex, char ->
            if (!char.isWhitespace()) {
                result += rowIndex * 9 + colIndex
            }
        }
    }
    return result.toIntArray()
}

/**
 * String.split(maxLen) — word-wrap text.
 */
fun String.split(maxLineLength: Int): List<String> {
    if (length <= maxLineLength) return listOf(this)

    val lines = mutableListOf<String>()
    val words = split(" ")
    var currentLine = StringBuilder()

    for (word in words) {
        if (currentLine.isEmpty()) {
            currentLine.append(word)
        } else if (currentLine.length + 1 + word.length <= maxLineLength) {
            currentLine.append(" ").append(word)
        } else {
            lines.add(currentLine.toString())
            currentLine = StringBuilder(word)
        }
    }
    if (currentLine.isNotEmpty()) {
        lines.add(currentLine.toString())
    }
    return lines
}

/** Translate & color codes → Minecraft. */
private fun translateColor(text: String): String {
    return ChatColor.translateAlternateColorCodes('&', text)
}

// Thêm color() extension dùng trong GUI files (nếu chưa import Utils.color)
fun String.color(): String = ChatColor.translateAlternateColorCodes('&', this)

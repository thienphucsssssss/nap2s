package vn.twosmc.nap2s.utils

import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.BaseComponent
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.ComponentBuilder
import net.md_5.bungee.api.chat.HoverEvent
import vn.twosmc.nap2s.Nap2S
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.command.CommandSender
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitTask
import java.security.MessageDigest
import java.text.DecimalFormat
import java.util.logging.Level

class Utils {
    companion object {
        fun Throwable.severe(message: String) = Nap2S.instance.logger.log(Level.SEVERE, message, this)

        fun Throwable.warning(message: String) = Nap2S.instance.logger.log(Level.WARNING, message, this)

        fun warning(message: String) = Nap2S.instance.logger.warning(message)

        fun info(message: String) = Nap2S.instance.logger.info(message)

        fun sendServerMessages(statusMessages: List<String>) = statusMessages.forEach {
            Bukkit.broadcastMessage("${Nap2S.instance.config.prefix} $it".color())
        }

        fun CommandSender.send(message: String) = sendMessage("${Nap2S.instance.config.prefix} $message".color())

        /**
         * Chạy async timer — bỏ Folia check, dùng Bukkit scheduler trực tiếp.
         * Folia support sẽ thêm lại ở phase sau nếu cần.
         */
        fun runAsyncTimer(delay: Long, period: Long, r: Runnable): BukkitTask =
            Bukkit.getScheduler().runTaskTimerAsynchronously(Nap2S.instance, r, delay, period)

        /** Chạy task async — dùng Bukkit scheduler. */
        fun runAsync(r: Runnable) {
            Bukkit.getScheduler().runTaskAsynchronously(Nap2S.instance, r)
        }

        /** Chạy code trên thread khác main (nếu đang main → schedule async). */
        fun runNotSync(r: Runnable) {
            if (Bukkit.isPrimaryThread()) {
                Bukkit.getScheduler().runTaskAsynchronously(Nap2S.instance, r)
            } else {
                r.run()
            }
        }

        /** Chạy code trên main thread. */
        fun runSync(r: Runnable) {
            if (Bukkit.isPrimaryThread()) {
                r.run()
            } else {
                Bukkit.getScheduler().runTask(Nap2S.instance, r)
            }
        }

        fun List<String>.color() = map { it.color() }

        fun String.color(): String = ChatColor.translateAlternateColorCodes('&', this)

        fun makePagination(command: String, page: Int, maxPage: Int, makeButton: Boolean) : Array<BaseComponent> {
            return ComponentBuilder("\n§r").run {
                if (makeButton && page > 1) {
                    append("<< Trang trước")
                    color(ChatColor.GREEN)
                    event(ClickEvent(ClickEvent.Action.RUN_COMMAND, "$command ${page - 1}"))
                    event(HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        ComponentBuilder("§aClick để quay về trang trước").create()
                    ))
                    append(" | ").reset().color(ChatColor.GRAY)
                }

                append("Trang $page/$maxPage")

                color(ChatColor.YELLOW)
                if (makeButton && page < maxPage) {
                    append(" | ").color(ChatColor.GRAY)
                    append("Trang sau >>")
                    color(ChatColor.GREEN)
                    event(ClickEvent(ClickEvent.Action.RUN_COMMAND, "$command ${page + 1}"))
                    event(HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        ComponentBuilder("§aClick để đi đến trang sau").create()
                    ))
                }
                create()
            }
        }

        fun Int.format(): String {
            val format = DecimalFormat("#,###")
            val decimalFormatSymbols = format.decimalFormatSymbols
            decimalFormatSymbols.groupingSeparator = '.'
            format.decimalFormatSymbols = decimalFormatSymbols
            return format.format(this)
        }

        /**
         * Tạo item từ Material name — thay XMaterial.parseItem().
         * Target Spigot 1.20.4+ nên dùng Material.valueOf() trực tiếp.
         */
        fun createItem(material: Material, name: String, vararg lore: String): ItemStack {
            return ItemStack(material).apply {
                itemMeta = itemMeta?.apply {
                    setDisplayName(name.color())
                    this.lore = lore.toList().color()
                }
            }
        }

        fun String.md5(): String {
            return MessageDigest.getInstance("MD5").digest(toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }
}

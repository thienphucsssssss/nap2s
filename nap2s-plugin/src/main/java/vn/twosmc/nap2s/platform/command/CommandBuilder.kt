package vn.twosmc.nap2s.platform.command

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.plugin.java.JavaPlugin

/**
 * DSL builder cho Bukkit commands
 *
 * Cách dùng:
 * ```
 * command {
 *     action { /* sender, args, commandTree available */ }
 *     tabComplete { /* args available, return List<String> */ }
 *     addSubCommand(subCmd(), "name1", "name2")
 *     description("Mô tả lệnh")
 *     register(plugin, "commandName")
 * }
 * ```
 */
fun command(builder: CommandBuilder.() -> Unit): CommandBuilder {
    return CommandBuilder().apply(builder)
}

/**
 * Context cho action {} và tabComplete {} — cung cấp sender, args, commandTree.
 */
class CommandContext(
    val sender: CommandSender,
    val args: List<String>,
    val commandTree: String
) {
    /**
     * Gửi danh sách sub-commands (usage) cho sender.
     */
    fun sendSubCommandsUsage(sender: CommandSender, commandTree: String) {
        // Tìm CommandBuilder đang chứa context này → gọi listSubCommands
        // Workaround: dùng companion reference
        _parentBuilder?.sendSubCommandsUsage(sender, commandTree)
    }

    internal var _parentBuilder: CommandBuilder? = null
}

/**
 * Command builder — DSL cho đăng ký lệnh Bukkit.
 */
class CommandBuilder {
    private var actionHandler: (CommandContext.() -> Unit)? = null
    private var tabCompleteHandler: (CommandContext.() -> List<String>)? = null
    private var commandDescription: String = ""
    private val subCommands = linkedMapOf<String, SubCommandEntry>()

    /**
     * Set handler chính khi lệnh được chạy.
     * Bên trong block: `sender`, `args`, `commandTree` có sẵn.
     */
    fun action(handler: CommandContext.() -> Unit) {
        actionHandler = handler
    }

    /**
     * Set tab-complete handler.
     * Bên trong block: `args` có sẵn, return List<String>.
     */
    fun tabComplete(handler: CommandContext.() -> List<String>) {
        tabCompleteHandler = handler
    }

    /**
     * Thêm sub-command với một hoặc nhiều alias.
     */
    fun addSubCommand(subCommand: CommandBuilder, vararg names: String) {
        for (name in names) {
            subCommands[name.lowercase()] = SubCommandEntry(name, subCommand)
        }
    }

    /**
     * Set description cho lệnh (dùng khi hiện help).
     */
    fun description(desc: String) {
        commandDescription = desc
    }

    /**
     * Đăng ký lệnh với Bukkit.
     */
    fun register(plugin: JavaPlugin, commandName: String) {
        val pluginCommand = plugin.getCommand(commandName) ?: run {
            plugin.logger.warning("Không tìm thấy command '$commandName' trong plugin.yml")
            return
        }
        pluginCommand.setExecutor(buildExecutor())
        pluginCommand.tabCompleter = buildTabCompleter()
    }

    /**
     * Gửi danh sách sub-commands cho sender (help message).
     */
    internal fun sendSubCommandsUsage(sender: CommandSender, commandTree: String) {
        // Gom các unique sub-commands (loại bỏ alias trùng object)
        val unique = subCommands.entries
            .distinctBy { it.value.builder }
            .groupBy({ it.value.builder }, { it.key })

        for ((builder, aliases) in unique) {
            val mainName = aliases.first()
            val desc = builder.commandDescription.ifBlank { "Không có mô tả" }
            sender.sendMessage("§a/$commandTree $mainName §7- §f$desc")
        }
    }

    /**
     * Build CommandExecutor — phân phối vào sub-commands hoặc action handler.
     */
    private fun buildExecutor(): CommandExecutor {
        return CommandExecutor { sender, command, label, rawArgs ->
            val args = rawArgs.toList()
            val commandTree = label

            // Check sub-command
            if (args.isNotEmpty()) {
                val subName = args[0].lowercase()
                val subEntry = subCommands[subName]
                if (subEntry != null) {
                    val subArgs = args.drop(1)
                    val subTree = "$commandTree ${args[0]}"
                    subEntry.builder.execute(sender, subArgs, subTree)
                    return@CommandExecutor true
                }
            }

            // Fallback: action handler
            val ctx = CommandContext(sender, args, commandTree)
            ctx._parentBuilder = this
            actionHandler?.invoke(ctx)
            true
        }
    }

    /**
     * Build TabCompleter — phân phối vào sub-commands hoặc tab handler.
     */
    private fun buildTabCompleter(): TabCompleter {
        return TabCompleter { sender, command, alias, rawArgs ->
            val args = rawArgs.toList()
            val commandTree = alias

            // Nếu đang gõ argument đầu tiên → gợi ý sub-commands + custom
            if (args.size == 1) {
                val subNames = subCommands.keys.filter { it.startsWith(args[0].lowercase()) }
                val customCompletions = tabCompleteHandler?.let {
                    val ctx = CommandContext(sender, args, commandTree)
                    ctx._parentBuilder = this
                    it.invoke(ctx)
                } ?: emptyList()
                return@TabCompleter (subNames + customCompletions).distinct()
            }

            // Nếu sub-command đã match → delegate
            if (args.isNotEmpty()) {
                val subName = args[0].lowercase()
                val subEntry = subCommands[subName]
                if (subEntry != null) {
                    return@TabCompleter subEntry.builder.tabComplete(sender, args.drop(1), "$commandTree ${args[0]}")
                }
            }

            // Fallback: custom tab handler
            tabCompleteHandler?.let {
                val ctx = CommandContext(sender, args, commandTree)
                ctx._parentBuilder = this
                it.invoke(ctx)
            } ?: emptyList()
        }
    }

    // ─── Internal helpers ────────────────────────────────────

    /** Execute handler trực tiếp (dùng cho sub-command delegation). */
    private fun execute(sender: CommandSender, args: List<String>, commandTree: String) {
        if (args.isNotEmpty()) {
            val subName = args[0].lowercase()
            val subEntry = subCommands[subName]
            if (subEntry != null) {
                subEntry.builder.execute(sender, args.drop(1), "$commandTree ${args[0]}")
                return
            }
        }

        val ctx = CommandContext(sender, args, commandTree)
        ctx._parentBuilder = this
        actionHandler?.invoke(ctx)
    }

    /** Tab-complete cho sub-command context. */
    private fun tabComplete(sender: CommandSender, args: List<String>, commandTree: String): List<String> {
        if (args.size == 1) {
            val subNames = subCommands.keys.filter { it.startsWith(args[0].lowercase()) }
            val custom = tabCompleteHandler?.let {
                val ctx = CommandContext(sender, args, commandTree)
                ctx._parentBuilder = this
                it.invoke(ctx)
            } ?: emptyList()
            return (subNames + custom).distinct()
        }

        if (args.isNotEmpty()) {
            val subName = args[0].lowercase()
            val subEntry = subCommands[subName]
            if (subEntry != null) {
                return subEntry.builder.tabComplete(sender, args.drop(1), "$commandTree ${args[0]}")
            }
        }

        return tabCompleteHandler?.let {
            val ctx = CommandContext(sender, args, commandTree)
            ctx._parentBuilder = this
            it.invoke(ctx)
        } ?: emptyList()
    }

    /** Entry cho sub-command: tên chính + builder reference. */
    private data class SubCommandEntry(
        val name: String,
        val builder: CommandBuilder
    )
}

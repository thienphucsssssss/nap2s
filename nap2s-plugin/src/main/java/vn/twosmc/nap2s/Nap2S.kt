package vn.twosmc.nap2s

import vn.twosmc.nap2s.banking.BankingService
import vn.twosmc.nap2s.commands.AdminCmd
import vn.twosmc.nap2s.commands.BankingCmd
import vn.twosmc.nap2s.commands.MainCmd
import vn.twosmc.nap2s.utils.PeriodicResetManager
import vn.twosmc.nap2s.security.DonationRateLimiter
import vn.twosmc.nap2s.compat.BedrockCompat
import vn.twosmc.nap2s.network.NetworkManager
import vn.twosmc.nap2s.platform.input.InputRouter
import vn.twosmc.nap2s.commands.TopNapCmd
import vn.twosmc.nap2s.commands.SetupWizardCmd
import vn.twosmc.nap2s.utils.ConfigValidator
import vn.twosmc.nap2s.config.BankingConfig
import vn.twosmc.nap2s.config.Discord
import vn.twosmc.nap2s.config.Language
import vn.twosmc.nap2s.config.MainConfig
import vn.twosmc.nap2s.config.Milestones
import vn.twosmc.nap2s.config.MilestonesMaster
import vn.twosmc.nap2s.config.PlannedExtras
import vn.twosmc.nap2s.config.ProvidersConfig
import vn.twosmc.nap2s.database.ConfigDAO
import vn.twosmc.nap2s.database.PlayerDataDAO
import vn.twosmc.nap2s.database.PlayerInfoDAO
import vn.twosmc.nap2s.gui.CardPriceUI
import vn.twosmc.nap2s.gui.CardTypeUI
import vn.twosmc.nap2s.providers.CardProvider
import vn.twosmc.nap2s.utils.Utils.Companion.format
import vn.twosmc.nap2s.utils.Utils.Companion.runNotSync
import vn.twosmc.nap2s.utils.Utils.Companion.warning
import vn.twosmc.nap2s.platform.gui.ConfiguredUI
import vn.twosmc.nap2s.platform.db.NapPlugin
import vn.twosmc.nap2s.platform.db.DBMigrator
import vn.twosmc.nap2s.platform.db.LegacyTables
import vn.twosmc.nap2s.platform.db.Transaction
import vn.twosmc.nap2s.utils.Utils.Companion.color
import net.md_5.bungee.api.ChatColor
import org.black_ixx.playerpoints.PlayerPoints
import org.bukkit.Bukkit
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.logging.Level

class Nap2S : NapPlugin() {

    lateinit var expansion: Expansion
        private set
    lateinit var playerPoints: PlayerPoints
        private set
    var prefix = "&6&lNap2S >&r".color()
        private set

    lateinit var config: MainConfig
        private set
    lateinit var providerSettings: ProvidersConfig
        private set
    lateinit var banking: BankingConfig
        private set
    lateinit var language: Language
        private set
    lateinit var milestones: Milestones
        private set
    lateinit var milestonesMaster: MilestonesMaster
        private set
    lateinit var discord: Discord
        private set
    lateinit var plannedExtras: PlannedExtras
        private set

    override fun onEnable() {
        instance = this
        NapPlugin.instance = this

        saveDefaultConfig()

        server.pluginManager.registerEvents(Nap2SListener(), this)
        val playerPoints = server.pluginManager.getPlugin("PlayerPoints") as PlayerPoints?
        if (playerPoints == null) {
            logger.log(Level.WARNING, "Không tìm thấy PlayerPoints.")
            server.pluginManager.disablePlugin(this)
            return
        }
        this.playerPoints = playerPoints
        reload()
        if (!isEnabled) return

        MainCmd.init()
        BankingCmd.init()
        AdminCmd.init()
        TopNapCmd.init()
        UpdateChecker.init()
        logStartupSummary()
    }

    private fun migrate() {
        val pool = checkNotNull(dbPool)
        val databaseType = pool.getTypeName()
        pool.getConnection().use { LegacyTables.rename(it, databaseType) }

        val configDao = ConfigDAO.getInstance()
        val currentVersion = (configDao.get("migration_version") ?: "0").toInt()
        val path = "db/migrations/$databaseType"
        val latestVersion = pool.getConnection().use {
            DBMigrator(this, it, path, currentVersion).migrate()
        }
        if (latestVersion > currentVersion) {
            configDao.set("migration_version", latestVersion.toString())
        }
    }

    fun reload() {
        // Hủy mọi input session đang chờ trước khi reload config
        // (tránh callback với state cũ sau reload mềm /nap2s reload)
        InputRouter.clearAll()
        config = MainConfig()
        prefix = config.prefix
        initDatabase(config.config.getConfigurationSection("database")!!)
        migrate()
        language = Language()
        if (::milestones.isInitialized) {
            milestones.removeBossBars()
        }
        milestones = Milestones()
        if (::milestonesMaster.isInitialized) {
            milestonesMaster.removeBossBars()
        }
        milestonesMaster = MilestonesMaster()
        discord = Discord()
        plannedExtras = PlannedExtras()
        providerSettings = ProvidersConfig()
        banking = BankingConfig()

        CardTypeUI()
        CardPriceUI()
        ConfiguredUI.reloadConfigs(this)

        try {
            CardProvider.init(providerSettings, config.provider)
        } catch (e: Exception) {
            logger.severe("Không thể khởi tạo hệ thống provider: ${e.message}")
            server.pluginManager.disablePlugin(this)
            return
        }

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            if (::expansion.isInitialized) {
                expansion.unregister()
            }
            expansion = Expansion()
        } else {
            warning("PlaceholderAPI chưa được cài đặt, một số tính năng sẽ không hoạt động.")
        }

        BankingService.init(banking)
        PeriodicResetManager.init()
        DonationRateLimiter.init()
        BedrockCompat.init()
        NetworkManager.init()
        NetworkManager.registerServer()
        // Kiểm tra config — nếu có lỗi nghiêm trọng, tắt banking để tránh lỗi runtime
        if (!ConfigValidator.validate()) {
            if (banking.enabled) {
                warning("§c[Config] Có lỗi cấu hình nghiêm trọng — nạp ATM/QR sẽ bị tắt cho đến khi sửa.")
                BankingService.forceStop()
            }
        }
    }

    override fun onDisable() {
        // Hủy mọi input session đang chờ (chat listener / anvil GUI)
        InputRouter.clearAll()
        dbPool?.disconnect()
        if (::expansion.isInitialized) {
            expansion.unregister()
        }
        if (::milestonesMaster.isInitialized) {
            milestonesMaster.removeBossBars()
        }
        if (::milestones.isInitialized) {
            milestones.removeBossBars()
        }
        PeriodicResetManager.stop()
        BankingService.stop()
        SetupWizardCmd.cleanup()
    }

    fun updateLeaderBoard(uuid: UUID, amount: Int, pointAmount: Int, type: TopupType = TopupType.CARD) {
        val dataDAO = PlayerDataDAO.getInstance()

        val uuidStr = uuid.toString()
        dataDAO.insertAllType(uuidStr, TOP_KEY_DONATE_TOTAL, amount)
        dataDAO.insertAllType(uuidStr, TOP_KEY_POINT_FROM_CARD, pointAmount)

        // Check mốc nạp cho tất cả chu kỳ (all, week, month, daily)
        for (milestoneType in listOf("all", "week", "month", "daily")) {
            val keySuffix = milestoneType.uppercase()
            val currentPlayer = dataDAO.getData(uuidStr, "${TOP_KEY_DONATE_TOTAL}_$keySuffix")
            val currentServer = dataDAO.getSumData("${TOP_KEY_DONATE_TOTAL}_$keySuffix")

            Bukkit.getPlayer(uuid)?.takeIf { it.isOnline }?.let { player ->
                milestones.getAll().filter { it.type == milestoneType }.forEach {
                    it.check(player, currentPlayer, amount)
                }
            }
            milestonesMaster.getAll().filter { it.type == milestoneType }.forEach {
                it.sumCheck(currentServer, amount)
            }
        }

        runNotSync {
            val balance = playerPoints.api.look(uuid)
            val playerName = PlayerInfoDAO.getInstance().getName(uuidStr) ?: return@runNotSync
            val timeStr = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy"))
            val replacements = mapOf(
                "%PLAYER%" to playerName,
                "%AMOUNT%" to amount.format(),
                "%POINT_AMOUNT%" to pointAmount.toString(),
                "%POINT_UNIT%" to config.pointUnit,
                "%BALANCE%" to balance.toString(),
                "%METHOD%" to type.typeName,
                "%TIME%" to timeStr,
                "%SERVER%" to config.server
            )
            discord.webhooks.forEach { sender ->
                sender.send(replacements)
            }
        }
    }

    private fun logStartupSummary() {
        // NAP2S rendered in the ANSI Shadow FIGlet style.
        val wordmark = listOf(
            "#FFF1B8" to "███╗   ██╗ █████╗ ██████╗ ██████╗ ███████╗",
            "#FFE485" to "████╗  ██║██╔══██╗██╔══██╗╚════██╗██╔════╝",
            "#FFD052" to "██╔██╗ ██║███████║██████╔╝ █████╔╝███████╗",
            "#FFB82E" to "██║╚██╗██║██╔══██║██╔═══╝ ██╔═══╝ ╚════██║",
            "#E99A1C" to "██║ ╚████║██║  ██║██║     ███████╗███████║",
            "#B97014" to "╚═╝  ╚═══╝╚═╝  ╚═╝╚═╝     ╚══════╝╚══════╝",
        )
        val gold = ChatColor.of("#FFD052")
        val muted = ChatColor.of("#87909C")
        val divider = "─".repeat(wordmark.maxOf { it.second.length })

        Bukkit.getConsoleSender().sendMessage(buildString {
            // One multiline message keeps the console prefix outside the wordmark.
            appendLine()
            for ((color, line) in wordmark) {
                appendLine("${ChatColor.of(color)}  $line")
            }
            appendLine()
            appendLine("$muted  DEV DISCORD  ${gold}•  @lilarie")
            appendLine("$muted  $divider")
            append("$gold  ◆ Đã khởi động  $muted•  v${description.version}")
        })
    }

    companion object {
        lateinit var instance: Nap2S
            private set

        fun transactional(action: Transaction.() -> Unit) {
            if (Bukkit.isPrimaryThread()) {
                throw IllegalStateException("Cannot run transactional code on the main thread")
            }
            vn.twosmc.nap2s.platform.db.transactional(instance.dbPool!!.getConnection(), action)
        }
    }
}

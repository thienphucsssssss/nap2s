package vn.twosmc.nap2s.config

import vn.twosmc.nap2s.card.CardPrice
import vn.twosmc.nap2s.card.CardType
import vn.twosmc.nap2s.utils.Utils.Companion.color
import vn.twosmc.nap2s.platform.input.InputMode
import vn.twosmc.nap2s.utils.dateAndTimeFormat

class MainConfig : FileConfig("config") {
    val checkUpdate = config.getBoolean("check-update", true)
    val announceCharge = config.getBoolean("announce-charge", true)
    val prefix = config.getString("prefix", "&6&lNap2S > &r")!!.color()
    val pointUnit = config.getString("point-unit", "point")!!.color()
    val extraUntil = dateAndTimeFormat.parse(config.getString("extra-until", "01/01/1970 00:00")).time
    val extraRate = config.getDouble("extra-rate")
    val provider = config.getString("provider", "")!!
    val server = config.getString("server", "")!!
    val cardTypes = config.getConfigurationSection("card-types")!!.run {
        CardType.entries.associateWith { getBoolean(it.name.lowercase()) }
    }

    val amounts = config.getConfigurationSection("donate-amounts")!!.run {
        CardPrice.entries.associateWith { getInt(it.value.toString()) }
    }

    val commands = config.getConfigurationSection("donate-commands")!!.run {
        CardPrice.entries.associateWith { getStringList(it.value.toString()).toList() }
    }

    val manualBase = config.getDouble("manual.point-base")
    val manualExtra = config.getDouble("manual.point-extra")
    /** Chế độ nhập thẻ cào cho Java player: anvil | chat. Parse mềm — sai thì fallback. */
    val cardInputMode = InputMode.parseOrDefault(
        config.getString("card-input.java-mode", "anvil")!!
    )

    /** Chế độ nhập số tiền ATM tuỳ chọn cho Java player: anvil | chat. */
    val bankingInputMode = InputMode.parseOrDefault(
        config.getString("banking-input.java-custom-amount-mode", "anvil")!!
    )
}

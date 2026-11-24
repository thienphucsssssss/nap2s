package vn.twosmc.nap2s.card

import vn.twosmc.nap2s.Nap2S
import vn.twosmc.nap2s.providers.CardProvider

enum class CardType(val displayName: String, vararg val alternative: String = emptyArray()) {
    VIETTEL("Viettel"),
    MOBIFONE("Mobifone", "mobiphone"),
    VINAPHONE("Vinaphone"),
    GATE("Gate"),
    VCOIN("Vcoin"),
    VIETNAMOBILE("Vietnamobile", "vn-mobile"),
    ZING("Zing"),
    GARENA("Garena"),
    SCOIN("Scoin"),
    ;

    fun isActive() = CardProvider.getStatus(this)

    companion object {
        operator fun get(name: String) = (runCatching { valueOf(name.uppercase()) }.getOrNull()
            ?: entries.find { it.alternative.contains(name.lowercase()) })
            .takeIf { Nap2S.instance.config.cardTypes[it] == true }
    }
}

package vn.twosmc.nap2s.providers.types

import vn.twosmc.nap2s.card.CardType
import vn.twosmc.nap2s.config.ProvidersConfig

/**
 * Provider cho cardvip.vn
 * Dùng giao thức ChargingWS V2 giống GachThe1S (cùng sign MD5(key+code+serial))
 * Chỉ override API URL
 */
class CardVipCP(
    definition: ProvidersConfig.ProviderDefinition,
    partnerId: String,
    partnerKey: String
) : ChargingWsV2CP(
    definition = definition,
    partnerId = partnerId,
    partnerKey = partnerKey,
    apiUrl = "https://cardvip.vn/chargingws/v2",
    builtInCodes = mapOf(
        CardType.VIETTEL to "VIETTEL",
        CardType.VINAPHONE to "VINAPHONE",
        CardType.MOBIFONE to "MOBIFONE",
        CardType.ZING to "ZING",
        CardType.GATE to "GATE",
        CardType.VCOIN to "VCOIN",
        CardType.GARENA to "GARENA"
    )
)

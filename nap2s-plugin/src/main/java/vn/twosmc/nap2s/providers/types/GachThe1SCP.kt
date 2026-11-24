package vn.twosmc.nap2s.providers.types

import vn.twosmc.nap2s.card.CardType
import vn.twosmc.nap2s.config.ProvidersConfig

class GachThe1SCP(
    definition: ProvidersConfig.ProviderDefinition,
    partnerId: String,
    partnerKey: String,
    apiUrl: String
) : ChargingWsV2CP(
    definition = definition,
    partnerId = partnerId,
    partnerKey = partnerKey,
    apiUrl = apiUrl,
    builtInCodes = mapOf(
        CardType.GARENA to "GARENA2",
        CardType.VIETNAMOBILE to "VNMB"
    )
)

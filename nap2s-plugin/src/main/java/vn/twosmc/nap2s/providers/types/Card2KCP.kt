package vn.twosmc.nap2s.providers.types

import vn.twosmc.nap2s.card.CardType
import vn.twosmc.nap2s.config.ProvidersConfig

class Card2KCP(
    definition: ProvidersConfig.ProviderDefinition,
    partnerId: String,
    partnerKey: String
) : ChargingWsV2CP(
    definition = definition,
    partnerId = partnerId,
    partnerKey = partnerKey,
    apiUrl = "https://card2k.com/chargingws/v2",
    builtInCodes = mapOf(
        CardType.VIETNAMOBILE to "VNMOBI"
    )
)

package vn.twosmc.nap2s.config

import vn.twosmc.nap2s.utils.Utils.Companion.info
import vn.twosmc.nap2s.utils.Utils.Companion.warning
import vn.twosmc.nap2s.discord.Webhook

class Discord : FileConfig("discord") {
    val webhooks: List<Webhook> = run {
        val hooks = config.getList("discord-hooks") ?: emptyList<Any>()
        hooks.mapNotNull { raw ->
            val map = raw as? Map<*, *>
            if (map == null) {
                warning("discord-hooks không hợp lệ")
                return@mapNotNull null
            }
            val enabled = map["enabled"] as? Boolean ?: false
            if (!enabled) return@mapNotNull null

            val url = map["url"] as? String
            if (url.isNullOrBlank()) {
                warning("url không hợp lệ")
                return@mapNotNull null
            }

            val payload = map["payload"] as? Map<*, *>
            if (payload == null) {
                warning("payload không hợp lệ")
                return@mapNotNull null
            }

            Webhook(url, payload)
        }
    }

    init {
        info("Đã nạp ${webhooks.size} webhook discord.")
    }
}

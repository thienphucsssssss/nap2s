package vn.twosmc.nap2s.discord

import vn.twosmc.nap2s.platform.utils.httpPost
import vn.twosmc.nap2s.platform.utils.toJson
import vn.twosmc.nap2s.utils.Utils.Companion.warning
import vn.twosmc.nap2s.utils.applyReplacements

class Webhook(private val url: String, private val payload: Map<*, *>) {
    fun send(replacements: Map<String, String>) {
        val processedPayload = applyReplacements(payload, replacements) as? Map<*, *> ?: return
        val json = processedPayload.toJson()
        try {
            httpPost(url, json, "application/json")
        } catch (e: Exception) {
            e.warning("Gửi Discord webhook thất bại")
        }
    }
}

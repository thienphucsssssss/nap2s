package vn.twosmc.nap2s.platform.utils

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URI

/**
 * HTTP & JSON utilities
 */

// ─── HTTP Utilities ────────────────────────────────────────────

/**
 * HTTP GET request
 * @param url URL cần gọi
 * @param parameters Query parameters (sẽ append vào URL)
 * @return Response body as String
 */
fun httpGet(url: String, parameters: Map<String, String>? = null): String {
    val fullUrl = if (parameters.isNullOrEmpty()) url
    else "$url?${encodeParams(parameters)}"

    val connection = URI(fullUrl).toURL().openConnection() as HttpURLConnection
    connection.requestMethod = "GET"
    connection.connectTimeout = 10000
    connection.readTimeout = 10000

    return connection.inputStream.bufferedReader().use { it.readText() }
}

/**
 * HTTP POST request
 * @param url URL cần gọi
 * @param body Request body (thường là JSON string)
 * @param contentType Content-Type header (default: application/json)
 * @param parameters Form parameters (được encode thành body)
 * @param headers Custom HTTP headers
 * @return Response body as String
 */
fun httpPost(
    url: String,
    body: String? = null,
    contentType: String = "application/json",
    parameters: Map<String, String>? = null,
    headers: Map<String, String>? = null
): String {
    val connection = URI(url).toURL().openConnection() as HttpURLConnection
    connection.requestMethod = "POST"
    connection.doOutput = true
    connection.connectTimeout = 10000
    connection.readTimeout = 10000
    connection.setRequestProperty("Content-Type", contentType)
    headers?.forEach { (key, value) -> connection.setRequestProperty(key, value) }

    val requestBody = body ?: parameters?.let { encodeParams(it) } ?: ""
    OutputStreamWriter(connection.outputStream).use { it.write(requestBody) }

    return BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
}

/** Encode Map thành URL form-encoded string. */
private fun encodeParams(params: Map<String, String>): String {
    return params.entries.joinToString("&") {
        "${java.net.URLEncoder.encode(it.key, "UTF-8")}=${java.net.URLEncoder.encode(it.value, "UTF-8")}"
    }
}

// ─── JSON Utilities ────────────────────────────────────────────

/**
 * Parse String thành JsonObject
 */
fun String.parseJson(): JsonObject = JsonParser.parseString(this).asJsonObject

/**
 * Chuyển object thành JSON string
 * Đơn giản dùng Gson (đã có trong classpath vì Spigot bundle nó).
 */
fun Any.toJson(): String = com.google.gson.Gson().toJson(this)

// ─── JsonElement Extensions ─────────────────────────────────────

/**
 * Lấy Int từ JsonElement, trả null nếu không parse được.
 */
fun JsonElement?.asIntOrNull(): Int? {
    if (this == null || isJsonNull) return null
    return try {
        asInt
    } catch (_: Exception) {
        null
    }
}

/**
 * Lấy String từ JsonElement, trả null nếu null hoặc isJsonNull.
 */
fun JsonElement?.asStringOrNull(): String? {
    if (this == null || isJsonNull) return null
    return try {
        asString
    } catch (_: Exception) {
        null
    }
}

// ─── Text Utilities ─────────────────────────────────────────────

/**
 * Parse hex color string (#RRGGBB) thành Int.
 */
fun parseHexColorToInt(hex: String): Int {
    val clean = hex.removePrefix("#")
    return clean.toInt(16)
}

// ─── Bukkit Extensions ──────────────────────────────────────────

/**
 * Gửi nhiều messages cho player
 */
fun org.bukkit.command.CommandSender.sendMessages(messages: List<String>) {
    for (msg in messages) {
        sendMessage(msg)
    }
}

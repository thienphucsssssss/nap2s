package vn.twosmc.nap2s.webserver

import fi.iki.elonen.NanoHTTPD
import vn.twosmc.nap2s.utils.Utils.Companion.info
import vn.twosmc.nap2s.utils.Utils.Companion.warning
import java.util.logging.Logger

/**
 * Web nhỏ đi kèm plugin để mở trang thanh toán và nhận webhook.
 */
class EmbeddedWebServer(private val port: Int) : NanoHTTPD(port) {

    init {
        // Suppress "Could not send response to the client" / Broken pipe spam.
        // NanoHTTPD log ERROR cho mỗi client disconnect (health check, bot, timeout) —
        // hoàn toàn vô hại nhưng spam console liên tục.
        // Chỉ chặn SocketException (Broken pipe / Connection reset), giữ lại lỗi thật.
        Logger.getLogger(NanoHTTPD::class.java.name).filter = java.util.logging.Filter { record ->
            val thrown = record.thrown
            // Chặn nếu cause là SocketException (Broken pipe, Connection reset)
            if (thrown is java.net.SocketException) return@Filter false
            if (thrown?.cause is java.net.SocketException) return@Filter false
            true
        }
    }

    private val webhookHandler = WebhookHandler()
    private val payosWebhookHandler = PayOSWebhookHandler()
    private val paymentPageHandler = PaymentPageHandler()

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri ?: ""
        val method = session.method

        return try {
            when {
                method == Method.POST && uri.endsWith("/nap2s/ipn/banking") -> {
                    webhookHandler.handle(session)
                }
                method == Method.POST && uri.endsWith("/nap2s/ipn/sepay") -> {
                    webhookHandler.handle(session)
                }
                method == Method.POST && uri.endsWith("/nap2s/ipn/payos") -> {
                    payosWebhookHandler.handle(session)
                }
                method == Method.GET && uri.matches(Regex(".*/orders/[^/]+$")) -> {
                    val orderId = uri.substringAfterLast("/orders/")
                    paymentPageHandler.handle(orderId)
                }
                method == Method.GET && (uri == "/" || uri == "/health") -> {
                    newFixedLengthResponse(
                        Response.Status.OK,
                        "application/json",
                        """{"status":"ok","server":"Nap2S","port":$port}"""
                    )
                }
                else -> {
                    newFixedLengthResponse(
                        Response.Status.NOT_FOUND,
                        "text/plain",
                        "Not Found"
                    )
                }
            }
        } catch (e: Exception) {
            e.warning("EmbeddedWebServer error")
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                """{"error":"${e.message ?: "Internal Server Error"}"}"""
            )
        }
    }

    companion object {
        private var instance: EmbeddedWebServer? = null

        fun start(port: Int) {
            stop()
            try {
                val server = EmbeddedWebServer(port)
                server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true)
                instance = server
                info("Nap2S WebServer đã khởi động trên port $port")
            } catch (e: Exception) {
                e.warning("Không thể khởi động Nap2S WebServer trên port $port")
            }
        }

        fun stop() {
            instance?.stop()
            instance = null
        }

        fun isRunning() = instance?.isAlive == true
    }
}

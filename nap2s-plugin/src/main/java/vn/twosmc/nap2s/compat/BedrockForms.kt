package vn.twosmc.nap2s.compat

import vn.twosmc.nap2s.Nap2S
import vn.twosmc.nap2s.banking.BankingService
import vn.twosmc.nap2s.card.Card
import vn.twosmc.nap2s.card.CardPrice
import vn.twosmc.nap2s.database.LogDAO
import vn.twosmc.nap2s.providers.CardProvider
import vn.twosmc.nap2s.utils.Utils.Companion.format
import vn.twosmc.nap2s.utils.Utils.Companion.info
import vn.twosmc.nap2s.utils.Utils.Companion.runNotSync
import vn.twosmc.nap2s.utils.Utils.Companion.send
import vn.twosmc.nap2s.utils.Utils.Companion.warning
import org.bukkit.entity.Player
import java.lang.reflect.Method
import java.util.List as JavaList
import java.util.UUID

/**
 * Native Bedrock forms qua Floodgate Cumulus API (reflection).
 *
 * Khong them dependency compile-time: neu server khong co Floodgate/Cumulus,
 * moi ham se tra false de fallback ve chest GUI.
 */
object BedrockForms {

    private fun sendForm(player: Player, formOrBuilder: Any): Boolean {
        return try {
            val apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi")
            val api = apiClass.getMethod("getInstance").invoke(null)

            info("BedrockForms: form class = ${formOrBuilder.javaClass.name}")
            info("BedrockForms: form interfaces = ${formOrBuilder.javaClass.interfaces.map { it.name }}")

            val candidates = apiClass.methods.filter {
                it.name == "sendForm" && it.parameterCount == 2 && it.parameterTypes[0] == UUID::class.java
            }
            info("BedrockForms: sendForm overloads = ${candidates.map { it.parameterTypes[1].name }}")

            val matched = candidates.firstOrNull {
                it.parameterTypes[1].isAssignableFrom(formOrBuilder.javaClass)
            }

            if (matched != null) {
                info("BedrockForms: matched sendForm(UUID, ${matched.parameterTypes[1].name})")
                return matched.invoke(api, player.uniqueId, formOrBuilder) as? Boolean ?: true
            }

            for (candidate in candidates) {
                try {
                    info("BedrockForms: thu sendForm(UUID, ${candidate.parameterTypes[1].name})")
                    return candidate.invoke(api, player.uniqueId, formOrBuilder) as? Boolean ?: true
                } catch (_: IllegalArgumentException) {
                    continue
                }
            }

            warning("BedrockForms: khong tim duoc overload sendForm phu hop cho ${formOrBuilder.javaClass.name}")
            false
        } catch (e: Exception) {
            e.warning("BedrockForms: gui form that bai cho ${player.name}")
            false
        }
    }

    private fun findMethod(target: Any, name: String, parameterCount: Int): Method? {
        return target.javaClass.methods.firstOrNull { it.name == name && it.parameterCount == parameterCount }
    }

    private fun invokeValidResultHandler(builder: Any, callback: (Any) -> Unit) {
        val candidates = builder.javaClass.methods.filter {
            it.name == "validResultHandler" && it.parameterCount == 1
        }
        val method = candidates.firstOrNull { method ->
            method.parameterTypes.firstOrNull()
                ?.methods
                ?.any { it.name == "accept" && it.parameterCount == 1 } == true
        } ?: candidates.firstOrNull() ?: run {
            warning("BedrockForms: khong tim thay validResultHandler tren ${builder.javaClass.simpleName}")
            return
        }

        val paramType = method.parameterTypes[0]
        val proxy = java.lang.reflect.Proxy.newProxyInstance(
            paramType.classLoader,
            arrayOf(paramType)
        ) { _, invoked, args ->
            if (invoked.name == "accept" && !args.isNullOrEmpty()) {
                try {
                    val payload = args.last()
                    info("BedrockForms: validResultHandler -> method=${invoked.name}, payload=${payload.javaClass.name}")
                    callback(payload)
                } catch (e: Exception) {
                    e.warning("BedrockForms: loi trong form callback")
                }
            }
            null
        }

        method.invoke(builder, proxy)
    }

    private fun buildCustomForm(
        title: String,
        components: List<FormComponent>,
        onResult: (List<Any>) -> Unit
    ): Any? {
        return try {
            val builderClass = Class.forName("org.geysermc.cumulus.form.CustomForm")
            val builder = builderClass.getMethod("builder").invoke(null)
            builder.javaClass.getMethod("title", String::class.java).invoke(builder, title)

            for (component in components) {
                when (component) {
                    is FormComponent.Label -> {
                        builder.javaClass.getMethod("label", String::class.java)
                            .invoke(builder, component.text)
                    }
                    is FormComponent.Dropdown -> {
                        builder.javaClass.getMethod("dropdown", String::class.java, JavaList::class.java)
                            .invoke(builder, component.label, component.options)
                    }
                    is FormComponent.Input -> {
                        builder.javaClass.getMethod("input", String::class.java, String::class.java)
                            .invoke(builder, component.label, component.placeholder)
                    }
                }
            }

            invokeValidResultHandler(builder) { response ->
                val results = mutableListOf<Any>()
                for ((idx, component) in components.withIndex()) {
                    when (component) {
                        is FormComponent.Label -> results += ""
                        is FormComponent.Dropdown -> {
                            val method = response.javaClass.methods.firstOrNull {
                                it.name == "asDropdown" && it.parameterCount == 1
                            } ?: return@invokeValidResultHandler
                            results += method.invoke(response, idx)
                        }
                        is FormComponent.Input -> {
                            val method = response.javaClass.methods.firstOrNull {
                                it.name == "asInput" && it.parameterCount == 1
                            } ?: return@invokeValidResultHandler
                            results += method.invoke(response, idx)
                        }
                    }
                }
                onResult(results)
            }

            builder
        } catch (e: Exception) {
            e.warning("BedrockForms: build CustomForm that bai")
            null
        }
    }

    private fun buildSimpleForm(
        title: String,
        buttons: List<String>,
        onResult: (Int) -> Unit
    ): Any? {
        return try {
            val builderClass = Class.forName("org.geysermc.cumulus.form.SimpleForm")
            val builder = builderClass.getMethod("builder").invoke(null)
            builder.javaClass.getMethod("title", String::class.java).invoke(builder, title)

            for (button in buttons) {
                builder.javaClass.getMethod("button", String::class.java).invoke(builder, button)
            }

            invokeValidResultHandler(builder) { response ->
                val clickedId = response.javaClass.methods.firstOrNull {
                    it.name == "clickedButtonId" && it.parameterCount == 0
                }?.invoke(response) as? Int ?: return@invokeValidResultHandler
                onResult(clickedId)
            }

            builder
        } catch (e: Exception) {
            e.warning("BedrockForms: build SimpleForm that bai")
            null
        }
    }

    private fun buildModalForm(
        title: String,
        content: String,
        button1: String,
        button2: String,
        onResult: (Int) -> Unit
    ): Any? {
        return try {
            val builderClass = Class.forName("org.geysermc.cumulus.form.ModalForm")
            val builder = builderClass.getMethod("builder").invoke(null)
            builder.javaClass.getMethod("title", String::class.java).invoke(builder, title)
            builder.javaClass.getMethod("content", String::class.java).invoke(builder, content)
            builder.javaClass.getMethod("button1", String::class.java).invoke(builder, button1)
            builder.javaClass.getMethod("button2", String::class.java).invoke(builder, button2)

            invokeValidResultHandler(builder) { response ->
                val clickedId = response.javaClass.methods.firstOrNull {
                    it.name == "clickedButtonId" && it.parameterCount == 0
                }?.invoke(response) as? Int ?: return@invokeValidResultHandler
                onResult(clickedId)
            }

            builder
        } catch (e: Exception) {
            e.warning("BedrockForms: build ModalForm that bai")
            null
        }
    }

    sealed class FormComponent {
        data class Label(val text: String) : FormComponent()
        data class Dropdown(val label: String, val options: List<String>) : FormComponent()
        data class Input(val label: String, val placeholder: String) : FormComponent()
    }

    fun sendNapTheForm(player: Player): Boolean {
        if (!BedrockCompat.isAvailable()) return false
        info("BedrockForms: thử mở form nạp thẻ cho ${player.name}")

        val availableTypes = CardProvider.getAvailableCardType()
        if (availableTypes.isEmpty()) {
            player.send("§cKhông có cổng nạp thẻ nào hoạt động.")
            return true
        }

        val typeNames = availableTypes.map { type ->
            val route = CardProvider.getRoute(type)
            if (route != null && route.bonusPercent > 0) {
                "${type.displayName} (+${route.bonusPercent}%%)"
            } else {
                type.displayName
            }
        }
        val priceValues = CardPrice.entries.map { it.value }
        val priceLabels = priceValues.map { it.format() }
        val promotionLines = availableTypes.mapNotNull { type ->
            val route = CardProvider.getRoute(type) ?: return@mapNotNull null
            if (route.bonusPercent <= 0) return@mapNotNull null
            "${type.displayName}: +${route.bonusPercent}%%"
        }
        val promoLabel = if (promotionLines.isNotEmpty()) {
            "Khuyến mãi hiện tại: " + promotionLines.joinToString(" | ")
        } else {
            "Chọn loại thẻ, mệnh giá, nhập seri và mã thẻ."
        }

        val form = buildCustomForm(
            title = "Nạp thẻ",
            components = listOf(
                FormComponent.Label(promoLabel),
                FormComponent.Dropdown("Loại:", typeNames),
                FormComponent.Dropdown("Mệnh giá:", priceLabels),
                FormComponent.Input("Số seri", "Nhập số seri"),
                FormComponent.Input("Mã thẻ", "Nhập mã thẻ")
            )
        ) { results ->
            try {
                info("BedrockForms: callback napthe -> results=${results.joinToString(" | ")}")

                if (results.size < 5) {
                    player.send("§cDữ liệu không đủ. Vui lòng thử lại.")
                    return@buildCustomForm
                }

                val typeIdx = (results[1] as? Int) ?: 0
                val priceIdx = (results[2] as? Int) ?: 0
                val seri = (results[3] as? String)?.trim().orEmpty()
                val code = (results[4] as? String)?.trim().orEmpty()

                if (typeIdx !in availableTypes.indices || priceIdx !in priceValues.indices) {
                    player.send("§cLựa chọn không hợp lệ.")
                    return@buildCustomForm
                }

                if (seri.isBlank() || code.isBlank()) {
                    player.send("§cVui lòng nhập đầy đủ seri và mã thẻ.")
                    return@buildCustomForm
                }

                val cardPrice = CardPrice[priceValues[priceIdx]]
                if (cardPrice == null) {
                    player.send("§cMệnh giá không hợp lệ.")
                    return@buildCustomForm
                }

                val card = Card(seri, code, cardPrice, availableTypes[typeIdx])
                info("BedrockForms: submit napthe -> player=${player.name}, type=${card.type.name}, price=${card.price.value}")
                CardProvider.submitCard(player, card)
            } catch (e: Exception) {
                e.warning("BedrockForms: lỗi xử lý submit napthe")
                player.send("§cLỗi xử lý form. Vui lòng thử lại.")
            }
        } ?: return false

        return sendForm(player, form)
    }

    fun sendNapAtmForm(player: Player): Boolean {
        if (!BedrockCompat.isAvailable()) return false
        info("BedrockForms: thử mở form nạp ATM cho ${player.name}")

        val allowedAmounts = BankingService.getAllowedAmounts()
        if (allowedAmounts.isEmpty()) {
            player.send("§cChuyển khoản chưa được bật.")
            return true
        }

        val banking = Nap2S.instance.banking
        val amountLabels = allowedAmounts.map { "${it.format()}đ" }

        val components = mutableListOf<FormComponent>(
            FormComponent.Dropdown("Chọn nhanh", amountLabels)
        )
        if (banking.allowCustomAmount) {
            components += FormComponent.Input(
                "Hoặc nhập số tiền (bội 1.000)",
                "${banking.minCustomAmount.format()} - ${banking.maxCustomAmount.format()}"
            )
        }

        val form = buildCustomForm(
            title = "Nạp chuyển khoản",
            components = components
        ) { results ->
            try {
                val amountIdx = (results[0] as? Int) ?: 0
                val customText = if (results.size >= 2) (results[1] as? String)?.trim().orEmpty() else ""
                val amount: Int

                if (customText.isNotBlank()) {
                    // Bedrock mobile: chấp nhận "50.000" / "50,000" → 50000 (UX tốt hơn)
                    // Java path (BankingAmountUI) chỉ nhận \d+ — khác biệt cố ý cho mobile
                    val parsed = customText.replace(".", "").replace(",", "").toIntOrNull()
                    if (parsed == null) {
                        player.send("§cSố tiền không hợp lệ.")
                        return@buildCustomForm
                    }
                    if (!banking.isValidCustomAmount(parsed)) {
                        player.send("§cSố tiền phải từ ${banking.minCustomAmount.format()} đến ${banking.maxCustomAmount.format()}, bội số 1.000.")
                        return@buildCustomForm
                    }
                    amount = parsed
                } else {
                    if (amountIdx !in allowedAmounts.indices) {
                        player.send("§cSố tiền không hợp lệ.")
                        return@buildCustomForm
                    }
                    amount = allowedAmounts[amountIdx]
                }

                info("BedrockForms: submit napatm -> player=${player.name}, amount=$amount")
                BankingService.createOrder(player, amount)
            } catch (e: Exception) {
                e.warning("BedrockForms: lỗi xử lý submit napatm")
                player.send("§cLỗi xử lý form. Vui lòng thử lại.")
            }
        } ?: return false

        return sendForm(player, form)
    }

    fun sendHistoryForm(player: Player): Boolean {
        if (!BedrockCompat.isAvailable()) return false
        info("BedrockForms: thử mở form lịch sử cho ${player.name}")

        runNotSync {
            try {
                val logs = LogDAO.getInstance().getRecentHistoryByPlayer(player.uniqueId.toString(), 20)
                if (logs.isEmpty()) {
                    player.send("§7Chưa có giao dịch nào.")
                    return@runNotSync
                }

                val buttons = logs.map { entry ->
                    "${entry.type} - ${entry.amount.format()}đ\n§7${entry.time}"
                }

                val form = buildSimpleForm("Lịch sử nạp", buttons) { idx ->
                    if (idx in logs.indices) {
                        sendTxDetailForm(player, logs[idx])
                    }
                } ?: return@runNotSync

                sendForm(player, form)
            } catch (_: Exception) {
                player.send("§cKhông thể tải lịch sử. Thử lại sau.")
            }
        }

        return true
    }

    private fun sendTxDetailForm(player: Player, txData: LogDAO.RecentHistoryEntry) {
        val content = buildString {
            appendLine("Người chơi: ${txData.playerName}")
            appendLine("Loại: ${txData.type}")
            appendLine("Số tiền: ${txData.amount.format()}đ")
            appendLine("Xu: ${txData.pointsReceived}")
            appendLine("Server: ${txData.server}")
            appendLine("Thời gian: ${txData.time}")
            if (txData.transactionId.isNotBlank()) {
                appendLine("Mã GD: ${txData.transactionId}")
            }
        }

        val form = buildModalForm(
            title = "Chi tiết",
            content = content,
            button1 = "Quay lại",
            button2 = "Đóng"
        ) { btnId ->
            if (btnId == 0) sendHistoryForm(player)
        } ?: return

        sendForm(player, form)
    }
}

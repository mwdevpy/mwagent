package com.mwagent.tools.web

import com.mwagent.data.settings.ToolSettings
import com.mwagent.llm.ToolSpec
import com.mwagent.net.HttpClientProvider
import com.mwagent.net.fetchText
import com.mwagent.tools.Tool
import com.mwagent.tools.ToolArgumentException
import com.mwagent.tools.ToolOutcome
import com.mwagent.tools.objectSchema
import com.mwagent.tools.optionalArray
import com.mwagent.tools.requireString
import com.mwagent.tools.stringArrayProperty
import com.mwagent.tools.stringProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Request
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

@Serializable
internal data class RatesResponse(
    val result: String = "",
    @SerialName("base_code") val base: String = "",
    @SerialName("time_last_update_utc") val updated: String = "",
    val rates: Map<String, Double> = emptyMap(),
    @SerialName("error-type") val errorType: String? = null,
)

class CurrencyTool(private val httpClients: HttpClientProvider) : Tool {
    private val json = Json { ignoreUnknownKeys = true }

    override val spec = ToolSpec(
        name = NAME,
        description = "Current exchange rates for 160 currencies, updated daily, and conversion of an amount. " +
            "Use ISO codes: USD, EUR, RUB, KZT, UAH, BYN, TRY, CNY, GBP and so on.",
        parameters = objectSchema(required = listOf(FROM)) {
            stringProperty(FROM, "Currency to convert from, for example USD")
            stringArrayProperty(TO, "Currencies to convert to, default RUB, EUR, USD")
            stringProperty(AMOUNT, "Amount in the from currency, default 1")
        },
    )

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        val from = code(arguments.requireString(FROM))
        val targets = arguments.optionalArray(TO).mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.let(::code) }
            .ifEmpty { DEFAULT_TARGETS }.filter { it != from }.distinct().take(MAX_TARGETS)
        val amount = (arguments[AMOUNT] as? JsonPrimitive)?.contentOrNull?.replace(',', '.')?.replace(" ", "")?.toBigDecimalOrNull() ?: BigDecimal.ONE
        val body = httpClients.current().fetchText(Request.Builder().url("$ENDPOINT$from").build())
        val rates = json.decodeFromString(RatesResponse.serializer(), body)
        if (rates.result != "success") {
            return ToolOutcome.error(if (rates.errorType == "unsupported-code") "Unknown currency $from" else "The rates service failed: ${rates.errorType ?: "no data"}")
        }
        val lines = targets.map { target ->
            val rate = rates.rates[target] ?: return@map "$target: unknown currency"
            val converted = amount.multiply(BigDecimal(rate)).setScale(if (rate < 0.01) 6 else 2, RoundingMode.HALF_UP)
            "${format(amount)} $from = ${converted.toPlainString()} $target (rate ${BigDecimal(rate).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()})"
        }
        return ToolOutcome((lines + "Updated ${rates.updated}. Rates by Exchange Rate API (exchangerate-api.com).").joinToString("\n"))
    }

    private fun code(raw: String): String {
        val code = raw.trim().uppercase(Locale.ROOT)
        if (!code.matches(CODE)) throw ToolArgumentException("\"$raw\" is not a three-letter currency code")
        return code
    }

    private fun format(amount: BigDecimal): String = amount.stripTrailingZeros().toPlainString()

    companion object {
        const val NAME = "currency_rates"
        private const val FROM = "from"
        private const val TO = "to"
        private const val AMOUNT = "amount"
        private const val ENDPOINT = "https://open.er-api.com/v6/latest/"
        private const val MAX_TARGETS = 20
        private val DEFAULT_TARGETS = listOf("RUB", "EUR", "USD")
        private val CODE = Regex("[A-Z]{3}")
    }
}

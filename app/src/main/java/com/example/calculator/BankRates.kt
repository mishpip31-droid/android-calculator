package com.example.calculator

import java.math.BigDecimal
import org.json.JSONArray
import org.json.JSONObject

/**
 * Сторона сделки с точки зрения банка, как на табло обменника:
 * [BUY] — «покупка»: банк покупает валюту, вы её сдаёте;
 * [SELL] — «продажа»: банк продаёт валюту, вы её покупаете.
 */
enum class Side { BUY, SELL }

/** Курс одной валюты в банке (BYN за 1 единицу). null — банк эту операцию сейчас не проводит. */
data class BankQuote(val buy: BigDecimal?, val sell: BigDecimal?) {
    fun get(side: Side) = if (side == Side.BUY) buy else sell
}

data class Bank(val id: String, val name: String, val quotes: Map<Currency, BankQuote>) {
    fun rate(c: Currency, side: Side): BigDecimal? = quotes[c]?.get(side)
}

/** Крайнее значение курса и банки, в которых оно есть. */
data class Extreme(val value: BigDecimal, val banks: List<String>)

data class Extremes(val min: Extreme, val max: Extreme)

data class BankRates(
    val banks: List<Bank>,
    val source: String,
    val updatedAt: Long,
) {
    fun bank(id: String): Bank? = if (id == BEST_ID) best() else banks.firstOrNull { it.id == id }

    /** Минимальный и максимальный курс покупки или продажи по всем банкам. */
    fun extremes(c: Currency, side: Side): Extremes? {
        val values = banks.mapNotNull { b -> b.rate(c, side)?.let { b.name to it } }
        if (values.isEmpty()) return null
        val min = values.minOf { it.second }
        val max = values.maxOf { it.second }
        fun at(v: BigDecimal) = Extreme(v, values.filter { it.second.compareTo(v) == 0 }.map { it.first })
        return Extremes(at(min), at(max))
    }

    /**
     * «Лучший курс»: для каждой валюты самая высокая покупка
     * (выгодно сдать) и самая низкая продажа (выгодно купить).
     */
    fun best(): Bank {
        val quotes = linkedMapOf<Currency, BankQuote>()
        for (c in listOf(Currency.USD, Currency.EUR)) {
            val buy = banks.mapNotNull { it.rate(c, Side.BUY) }.maxOrNull()
            val sell = banks.mapNotNull { it.rate(c, Side.SELL) }.minOrNull()
            if (buy != null || sell != null) quotes[c] = BankQuote(buy, sell)
        }
        return Bank(BEST_ID, "Лучший курс", quotes)
    }

    companion object {
        const val BEST_ID = "best"
    }
}

/** Разбор курсов банков из разных источников. */
object BankRatesParser {
    private val ROW = Regex(
        """<tr class="currencies-courses__row-main[^"]*"([^>]*)>(.*?)</tr>""",
        RegexOption.DOT_MATCHES_ALL,
    )
    private val ALIAS = Regex("""data-bank-sef-alias="([^"]*)"""")
    private val NAME = Regex("""alt="([^"]*)"""")
    private val CELL = Regex(
        """<td class="currencies-courses__currency-cell[^"]*"[^>]*>(.*?)</td>""",
        RegexOption.DOT_MATCHES_ALL,
    )
    private val NUMBER = Regex("""\d+[.,]\d+""")

    /**
     * Таблица банков со страницы myfin.by/currency/usd (или /eur):
     * в каждой строке «Сдать» (покупка банка) и «Купить» (продажа банка).
     * Рекламные строки пропускаются. Возвращает id → (название, курс).
     */
    fun parseMyfin(html: String): Map<String, Pair<String, BankQuote>> {
        val result = linkedMapOf<String, Pair<String, BankQuote>>()
        for (row in ROW.findAll(html)) {
            val attrs = row.groupValues[1]
            if (attrs.contains("data-row-type=\"ad\"")) continue
            val id = ALIAS.find(attrs)?.groupValues?.get(1)?.takeIf { it.isNotEmpty() } ?: continue
            if (result.containsKey(id)) continue
            val body = row.groupValues[2]
            val name = NAME.find(body)?.groupValues?.get(1)?.let(::decodeHtml)?.trim()
                ?.takeIf { it.isNotEmpty() } ?: id
            val cells = CELL.findAll(body).map { cell ->
                NUMBER.find(cell.groupValues[1])?.value?.replace(',', '.')?.let(::BigDecimal)
                    ?.takeIf { it.signum() > 0 }
            }.toList()
            val buy = cells.getOrNull(0)
            val sell = cells.getOrNull(1)
            if (buy == null && sell == null) continue
            result[id] = name to BankQuote(buy, sell)
        }
        return result
    }

    /** Объединяет таблицы по USD и EUR в список банков (по алфавиту). */
    fun mergeMyfin(
        usd: Map<String, Pair<String, BankQuote>>,
        eur: Map<String, Pair<String, BankQuote>>,
    ): List<Bank> {
        val ids = LinkedHashSet<String>().apply { addAll(usd.keys); addAll(eur.keys) }
        return ids.map { id ->
            val quotes = linkedMapOf<Currency, BankQuote>()
            usd[id]?.let { quotes[Currency.USD] = it.second }
            eur[id]?.let { quotes[Currency.EUR] = it.second }
            Bank(id, usd[id]?.first ?: eur[id]?.first ?: id, quotes)
        }.sortedBy { it.name.lowercase() }
    }

    /**
     * API Беларусбанка (belarusbank.by/api/kursExchange?city=…): курсы по отделениям.
     * Берём лучшее по городу: самую высокую покупку и самую низкую продажу.
     */
    fun parseBelarusbank(json: String): Bank? {
        val array = JSONArray(json)
        val quotes = linkedMapOf<Currency, BankQuote>()
        for (c in listOf(Currency.USD, Currency.EUR)) {
            val ins = ArrayList<BigDecimal>()
            val outs = ArrayList<BigDecimal>()
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                positive(o.optString("${c.name}_in"))?.let(ins::add)
                positive(o.optString("${c.name}_out"))?.let(outs::add)
            }
            val buy = ins.maxOrNull()
            val sell = outs.minOrNull()
            if (buy != null || sell != null) quotes[c] = BankQuote(buy, sell)
        }
        return if (quotes.isEmpty()) null else Bank("belarusbank", "Беларусбанк", quotes)
    }

    /**
     * Открытый API Альфа-Банка: записи вида «sellIso=USD, buyIso=BYN».
     * sellRate — по нему клиент сдаёт валюту (покупка банка), buyRate — покупает (продажа банка).
     */
    fun parseAlfa(json: String): Bank? {
        val array = JSONObject(json).getJSONArray("rates")
        val quotes = linkedMapOf<Currency, BankQuote>()
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            if (o.optString("buyIso") != "BYN") continue
            val c = when (o.optString("sellIso")) {
                "USD" -> Currency.USD
                "EUR" -> Currency.EUR
                else -> continue
            }
            val quantity = BigDecimal(o.optInt("quantity", 1).coerceAtLeast(1))
            fun per(key: String) = positive(o.opt(key)?.toString())?.divide(quantity)
            quotes[c] = BankQuote(per("sellRate"), per("buyRate"))
        }
        return if (quotes.isEmpty()) null else Bank("alfabank", "Альфа Банк", quotes)
    }

    private fun positive(s: String?): BigDecimal? =
        s?.trim()?.replace(',', '.')?.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }

    private fun decodeHtml(s: String) = s
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&laquo;", "«")
        .replace("&raquo;", "»")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
}

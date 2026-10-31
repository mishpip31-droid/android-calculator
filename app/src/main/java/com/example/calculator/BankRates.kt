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

/** Отделение банка: адрес, курс и время его обновления. */
data class Branch(val address: String, val quote: BankQuote, val time: String = "")

data class Bank(
    val id: String,
    val name: String,
    val quotes: Map<Currency, BankQuote>,
    val branches: Map<Currency, List<Branch>> = emptyMap(),
) {
    fun rate(c: Currency, side: Side): BigDecimal? = quotes[c]?.get(side)

    /** Отделения, где курс [c] на стороне [side] равен [value]. */
    fun branchesWith(c: Currency, side: Side, value: BigDecimal): List<Branch> =
        branches[c].orEmpty().filter { it.quote.get(side)?.compareTo(value) == 0 }
}

/** Банк, как он разобран со страницы одной валюты. */
data class ParsedBank(val name: String, val quote: BankQuote, val branches: List<Branch>)

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
    private val BRANCH_ROW = Regex(
        """<tr data-bank-sef-alias="([^"]*)"[^>]*>(.*?)</tr>""",
        RegexOption.DOT_MATCHES_ALL,
    )
    private val BRANCH_NAME = Regex(
        """class="currencies-courses__branch-name"[^>]*>(.*?)</a>""",
        RegexOption.DOT_MATCHES_ALL,
    )
    private val BRANCH_TIME = Regex("""ic-update-time[^>]*>\s*</i>\s*([^<]+?)\s*<""")
    private val TAGS = Regex("<[^>]+>")
    private val SPACES = Regex("\\s+")

    /**
     * Таблица банков со страницы myfin.by/currency/usd (или /eur):
     * в каждой строке «Сдать» (покупка банка) и «Купить» (продажа банка).
     * Рекламные строки пропускаются. Под строкой банка — его отделения с адресами.
     */
    fun parseMyfin(html: String): Map<String, ParsedBank> {
        val quotes = linkedMapOf<String, Pair<String, BankQuote>>()
        for (row in ROW.findAll(html)) {
            val attrs = row.groupValues[1]
            if (attrs.contains("data-row-type=\"ad\"")) continue
            val id = ALIAS.find(attrs)?.groupValues?.get(1)?.takeIf { it.isNotEmpty() } ?: continue
            if (quotes.containsKey(id)) continue
            val body = row.groupValues[2]
            val name = NAME.find(body)?.groupValues?.get(1)?.let(::decodeHtml)?.trim()
                ?.takeIf { it.isNotEmpty() } ?: id
            val quote = cellsQuote(body) ?: continue
            quotes[id] = name to quote
        }

        val branches = linkedMapOf<String, MutableList<Branch>>()
        for (row in BRANCH_ROW.findAll(html)) {
            val id = row.groupValues[1]
            if (id !in quotes) continue
            val body = row.groupValues[2]
            val address = BRANCH_NAME.find(body)?.groupValues?.get(1)
                ?.let { decodeHtml(it.replace(TAGS, " ")).replace(SPACES, " ").trim() }
                ?.takeIf { it.isNotEmpty() } ?: continue
            val quote = cellsQuote(body) ?: continue
            val time = BRANCH_TIME.find(body)?.groupValues?.get(1)?.trim().orEmpty()
            val list = branches.getOrPut(id) { ArrayList() }
            if (list.none { it.address == address }) list += Branch(address, quote, time)
        }

        return quotes.mapValues { (id, nq) -> ParsedBank(nq.first, nq.second, branches[id].orEmpty()) }
    }

    /** Первые две ячейки курса в строке: покупка и продажа. */
    private fun cellsQuote(rowHtml: String): BankQuote? {
        val cells = CELL.findAll(rowHtml).map { cell ->
            NUMBER.find(cell.groupValues[1])?.value?.replace(',', '.')?.let(::BigDecimal)
                ?.takeIf { it.signum() > 0 }
        }.toList()
        val buy = cells.getOrNull(0)
        val sell = cells.getOrNull(1)
        return if (buy == null && sell == null) null else BankQuote(buy, sell)
    }

    /** Объединяет таблицы по USD и EUR в список банков (по алфавиту). */
    fun mergeMyfin(usd: Map<String, ParsedBank>, eur: Map<String, ParsedBank>): List<Bank> {
        val ids = LinkedHashSet<String>().apply { addAll(usd.keys); addAll(eur.keys) }
        return ids.map { id ->
            val quotes = linkedMapOf<Currency, BankQuote>()
            val branches = linkedMapOf<Currency, List<Branch>>()
            usd[id]?.let { quotes[Currency.USD] = it.quote; branches[Currency.USD] = it.branches }
            eur[id]?.let { quotes[Currency.EUR] = it.quote; branches[Currency.EUR] = it.branches }
            Bank(id, usd[id]?.name ?: eur[id]?.name ?: id, quotes, branches)
        }.sortedBy { it.name.lowercase() }
    }

    /**
     * API Беларусбанка (belarusbank.by/api/kursExchange?city=…): курсы по отделениям.
     * Берём лучшее по городу: самую высокую покупку и самую низкую продажу.
     */
    fun parseBelarusbank(json: String): Bank? {
        val array = JSONArray(json)
        val quotes = linkedMapOf<Currency, BankQuote>()
        val branches = linkedMapOf<Currency, List<Branch>>()
        for (c in listOf(Currency.USD, Currency.EUR)) {
            val list = ArrayList<Branch>()
            val ins = ArrayList<BigDecimal>()
            val outs = ArrayList<BigDecimal>()
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                val inRate = positive(o.optString("${c.name}_in"))
                val outRate = positive(o.optString("${c.name}_out"))
                inRate?.let(ins::add)
                outRate?.let(outs::add)
                if (inRate != null || outRate != null) {
                    val address = listOf(
                        "${o.optString("name_type")} ${o.optString("name")}",
                        "${o.optString("street_type")}${o.optString("street")}",
                        o.optString("home_number"),
                    ).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", ")
                    val time = o.optString("kurs_date_time").substringAfter(' ', "").take(5)
                    if (address.isNotEmpty() && list.none { it.address == address }) {
                        list += Branch(address, BankQuote(inRate, outRate), time)
                    }
                }
            }
            if (list.isNotEmpty()) branches[c] = list
            val buy = ins.maxOrNull()
            val sell = outs.minOrNull()
            if (buy != null || sell != null) quotes[c] = BankQuote(buy, sell)
        }
        return if (quotes.isEmpty()) null else Bank("belarusbank", "Беларусбанк", quotes, branches)
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

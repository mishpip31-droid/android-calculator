package com.example.calculator

import android.content.Context
import java.io.IOException
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

/**
 * Курсы покупки и продажи в банках Минска.
 * Основной источник — таблица всех банков на myfin.by; если она недоступна —
 * официальные API Беларусбанка и Альфа-Банка. Последние курсы хранятся на телефоне.
 */
class BankRatesRepository(context: Context) {
    private val prefs = context.getSharedPreferences("bank_rates", Context.MODE_PRIVATE)

    fun cached(): BankRates? {
        val raw = prefs.getString(KEY, null) ?: return null
        return try {
            val o = JSONObject(raw)
            val banks = o.getJSONArray("banks")
            BankRates(
                banks = (0 until banks.length()).map { i ->
                    val b = banks.getJSONObject(i)
                    val q = b.getJSONObject("q")
                    val quotes = linkedMapOf<Currency, BankQuote>()
                    val branches = linkedMapOf<Currency, List<Branch>>()
                    val br = b.optJSONObject("br")
                    for (c in listOf(Currency.USD, Currency.EUR)) {
                        q.optJSONObject(c.name)?.let { quotes[c] = readQuote(it) }
                        val arr = br?.optJSONArray(c.name) ?: continue
                        branches[c] = (0 until arr.length()).map { j ->
                            val o = arr.getJSONObject(j)
                            Branch(o.getString("a"), readQuote(o), o.optString("t"))
                        }
                    }
                    Bank(b.getString("id"), b.getString("name"), quotes, branches)
                },
                source = o.getString("source"),
                updatedAt = o.getLong("t"),
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun readQuote(o: JSONObject) = BankQuote(
        o.optString("b").takeIf { it.isNotEmpty() }?.let(::BigDecimal),
        o.optString("s").takeIf { it.isNotEmpty() }?.let(::BigDecimal),
    )

    private fun writeQuote(o: JSONObject, quote: BankQuote): JSONObject {
        quote.buy?.let { o.put("b", it.toPlainString()) }
        quote.sell?.let { o.put("s", it.toPlainString()) }
        return o
    }

    /** Блокирующая загрузка — вызывать не из главного потока. */
    @Throws(IOException::class)
    fun fetch(): BankRates {
        val rates = try {
            fetchMyfin()
        } catch (e: Exception) {
            fetchOfficial()
        }
        save(rates)
        return rates
    }

    private fun fetchMyfin(): BankRates {
        val usd = BankRatesParser.parseMyfin(get("https://myfin.by/currency/usd"))
        val eur = BankRatesParser.parseMyfin(get("https://myfin.by/currency/eur"))
        val banks = BankRatesParser.mergeMyfin(usd, eur)
        if (banks.size < 3) throw IOException("myfin.by: таблица банков не распознана")
        return BankRates(banks, "myfin.by", System.currentTimeMillis())
    }

    private fun fetchOfficial(): BankRates {
        val banks = listOfNotNull(
            runCatching {
                val city = URLEncoder.encode("Минск", "UTF-8")
                BankRatesParser.parseBelarusbank(get("https://belarusbank.by/api/kursExchange?city=$city"))
            }.getOrNull(),
            runCatching {
                BankRatesParser.parseAlfa(get("https://developerhub.alfabank.by:8273/partner/1.0.1/public/rates"))
            }.getOrNull(),
        )
        if (banks.isEmpty()) throw IOException("Курсы банков недоступны")
        return BankRates(banks.sortedBy { it.name }, "API банков", System.currentTimeMillis())
    }

    private fun save(rates: BankRates) {
        val banks = JSONArray()
        for (b in rates.banks) {
            val q = JSONObject()
            for ((c, quote) in b.quotes) q.put(c.name, writeQuote(JSONObject(), quote))
            val br = JSONObject()
            for ((c, list) in b.branches) {
                val arr = JSONArray()
                for (branch in list) {
                    arr.put(writeQuote(JSONObject().put("a", branch.address).put("t", branch.time), branch.quote))
                }
                br.put(c.name, arr)
            }
            banks.put(JSONObject().put("id", b.id).put("name", b.name).put("q", q).put("br", br))
        }
        val o = JSONObject().put("banks", banks).put("source", rates.source).put("t", rates.updatedAt)
        prefs.edit().putString(KEY, o.toString()).apply()
    }

    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) Calculator")
            connection.setRequestProperty("Accept-Language", "ru")
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code: $url")
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }


    private companion object {
        const val KEY = "rates"
    }
}

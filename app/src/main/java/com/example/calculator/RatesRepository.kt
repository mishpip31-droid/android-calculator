package com.example.calculator

import android.content.Context
import java.io.IOException
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * Загружает курсы: USD и EUR — официальный курс Нацбанка Беларуси,
 * BTC — мировая цена в долларах, пересчитанная по курсу НБРБ.
 * Последние курсы хранятся на телефоне, чтобы конвертер работал без интернета.
 */
class RatesRepository(context: Context) {
    private val prefs = context.getSharedPreferences("rates", Context.MODE_PRIVATE)

    fun cached(): Rates? {
        val raw = prefs.getString(KEY, null) ?: return null
        return try {
            val o = JSONObject(raw)
            val btc = o.optString("btcUsd").takeIf { it.isNotEmpty() }?.let(::BigDecimal)
            Rates.of(
                usd = BigDecimal(o.getString("usd")),
                eur = BigDecimal(o.getString("eur")),
                btcUsd = btc,
                nbrbDate = o.getString("date"),
                updatedAt = o.getLong("t"),
                btcSource = o.optString("btcSrc").takeIf { it.isNotEmpty() },
            )
        } catch (e: Exception) {
            null
        }
    }

    /** Блокирующая загрузка — вызывать не из главного потока. */
    @Throws(IOException::class)
    fun fetch(): Rates {
        val (usd, date) = nbrb("USD")
        val (eur, _) = nbrb("EUR")

        var btcUsd: BigDecimal? = null
        var source: String? = null
        for ((name, url, parse) in BTC_SOURCES) {
            try {
                btcUsd = parse(get(url))
                source = name
                break
            } catch (e: Exception) {
                // пробуем следующий источник
            }
        }
        val old = cached()
        if (btcUsd == null && old != null && old.has(Currency.BTC)) {
            // Нет свежей цены биткоина — оставляем прошлую.
            val prevUsd = old.bynPerUnit.getValue(Currency.USD)
            btcUsd = old.bynPerUnit.getValue(Currency.BTC).divide(prevUsd, java.math.MathContext.DECIMAL64)
            source = old.btcSource
        }

        val now = System.currentTimeMillis()
        val o = JSONObject()
            .put("usd", usd.toPlainString())
            .put("eur", eur.toPlainString())
            .put("date", date)
            .put("t", now)
        if (btcUsd != null) o.put("btcUsd", btcUsd.toPlainString()).put("btcSrc", source ?: "")
        prefs.edit().putString(KEY, o.toString()).apply()

        return Rates.of(usd, eur, btcUsd, date, now, source)
    }

    private fun nbrb(code: String): Pair<BigDecimal, String> =
        try {
            RatesParser.parseNbrb(get("https://api.nbrb.by/exrates/rates/$code?parammode=2"))
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("Неверный ответ НБРБ", e)
        }

    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "Calculator-Android")
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code: $url")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val KEY = "rates"

        val BTC_SOURCES: List<Triple<String, String, (String) -> BigDecimal>> = listOf(
            Triple(
                "CoinGecko",
                "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd",
                RatesParser::parseCoinGecko,
            ),
            Triple("Coinbase", "https://api.coinbase.com/v2/prices/BTC-USD/spot", RatesParser::parseCoinbase),
            Triple("Binance", "https://api.binance.com/api/v3/ticker/price?symbol=BTCUSDT", RatesParser::parseBinance),
        )
    }
}

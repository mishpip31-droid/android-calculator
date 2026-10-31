package com.example.calculator

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import org.json.JSONObject

enum class Currency(val title: String, val decimals: Int) {
    BYN("Белорусский рубль", 2),
    USD("Доллар США", 2),
    EUR("Евро", 2),
    BTC("Биткоин", 8),
}

/**
 * Курсы валют: сколько белорусских рублей стоит 1 единица валюты.
 * @param nbrbDate дата официального курса НБРБ (ГГГГ-ММ-ДД)
 * @param updatedAt когда курсы были загружены (мс)
 */
data class Rates(
    val bynPerUnit: Map<Currency, BigDecimal>,
    val nbrbDate: String,
    val updatedAt: Long,
    val btcSource: String? = null,
    /** Цена биткоина в долларах (чтобы пересчитать BTC по курсу доллара в выбранном банке). */
    val btcUsd: BigDecimal? = null,
) {
    fun has(c: Currency) = bynPerUnit.containsKey(c)

    /**
     * Курсы выбранного банка: USD и EUR — его покупка или продажа,
     * BTC — мировая цена × курс доллара в этом банке.
     */
    fun withBank(bank: Bank, side: Side): Rates {
        val map = linkedMapOf(Currency.BYN to BigDecimal.ONE)
        val usd = bank.rate(Currency.USD, side)
        val eur = bank.rate(Currency.EUR, side)
        if (usd != null) map[Currency.USD] = usd
        if (eur != null) map[Currency.EUR] = eur
        if (usd != null && btcUsd != null) map[Currency.BTC] = btcUsd.multiply(usd, MC)
        return copy(bynPerUnit = map)
    }

    fun convert(amount: BigDecimal, from: Currency, to: Currency): BigDecimal? {
        val f = bynPerUnit[from] ?: return null
        val t = bynPerUnit[to] ?: return null
        if (t.signum() == 0) return null
        return amount.multiply(f, MC).divide(t, MC)
    }

    companion object {
        private val MC = MathContext(20, RoundingMode.HALF_EVEN)

        fun of(usd: BigDecimal, eur: BigDecimal, btcUsd: BigDecimal?, nbrbDate: String, updatedAt: Long, btcSource: String?): Rates {
            val map = linkedMapOf(Currency.BYN to BigDecimal.ONE, Currency.USD to usd, Currency.EUR to eur)
            if (btcUsd != null) map[Currency.BTC] = btcUsd.multiply(usd, MC)
            return Rates(map, nbrbDate, updatedAt, if (btcUsd != null) btcSource else null, btcUsd)
        }
    }
}

object Money {
    /** Сумма для поля ввода: 2 знака для денег (без «.00» у целых), до 8 для BTC. */
    fun format(value: BigDecimal, c: Currency): String {
        val rounded = value.setScale(c.decimals, RoundingMode.HALF_UP)
        val stripped = rounded.stripTrailingZeros()
        if (stripped.signum() == 0) return "0"
        return if (c == Currency.BTC || stripped.scale() <= 0) stripped.toPlainString() else rounded.toPlainString()
    }

    /** Курс для подписи: «2.9512», «312 345.67» — с пробелами между тысячами. */
    fun formatRate(value: BigDecimal): String {
        val scale = if (value >= BigDecimal(1000)) 2 else 4
        val plain = value.setScale(scale, RoundingMode.HALF_UP).toPlainString()
        val intPart = plain.substringBefore('.')
        val frac = plain.substringAfter('.', "")
        val grouped = intPart.reversed().chunked(3).joinToString(" ").reversed()
        return if (frac.isEmpty()) grouped else "$grouped.$frac"
    }

    /** Курс банка: как на табло — минимум 2 знака, без лишних нулей: «2.935», «3.30». */
    fun formatBankRate(value: BigDecimal): String {
        val v = value.stripTrailingZeros()
        return (if (v.scale() < 2) v.setScale(2) else v).toPlainString()
    }

    /** Разбирает ввод пользователя: «1 234,5», «100+50». Пусто или ошибка — null. */
    fun parse(text: String): BigDecimal? {
        val t = text.replace(" ", "").replace(' '.toString(), "").replace(',', '.')
        if (t.isEmpty()) return null
        return try {
            Evaluator.evaluate(t)
        } catch (e: Exception) {
            null
        }
    }
}

/** Разбор ответов серверов с курсами. */
object RatesParser {
    private val MC = MathContext(20, RoundingMode.HALF_EVEN)

    /** api.nbrb.by/exrates/rates/USD?parammode=2 → (BYN за 1 единицу, дата). */
    fun parseNbrb(json: String): Pair<BigDecimal, String> {
        val o = JSONObject(json)
        val rate = BigDecimal(o.get("Cur_OfficialRate").toString())
        val scale = BigDecimal(o.getInt("Cur_Scale"))
        return rate.divide(scale, MC) to o.getString("Date").take(10)
    }

    /** api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd */
    fun parseCoinGecko(json: String): BigDecimal =
        BigDecimal(JSONObject(json).getJSONObject("bitcoin").get("usd").toString())

    /** api.coinbase.com/v2/prices/BTC-USD/spot */
    fun parseCoinbase(json: String): BigDecimal =
        BigDecimal(JSONObject(json).getJSONObject("data").getString("amount"))

    /** api.binance.com/api/v3/ticker/price?symbol=BTCUSDT */
    fun parseBinance(json: String): BigDecimal =
        BigDecimal(JSONObject(json).getString("price"))
}

package com.example.calculator

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class CurrencyTest {
    private val rates = Rates.of(
        usd = BigDecimal("3.0000"),
        eur = BigDecimal("3.5000"),
        btcUsd = BigDecimal("100000"),
        nbrbDate = "2026-10-31",
        updatedAt = 0,
        btcSource = "CoinGecko",
    )

    private fun conv(amount: String, from: Currency, to: Currency) =
        Money.format(rates.convert(BigDecimal(amount), from, to)!!, to)

    @Test fun convertsThroughByn() {
        assertEquals("300", conv("100", Currency.USD, Currency.BYN))
        assertEquals("100", conv("300", Currency.BYN, Currency.USD))
        assertEquals("116.67", conv("100", Currency.EUR, Currency.USD))
        assertEquals("85.71", conv("100", Currency.USD, Currency.EUR))
    }

    @Test fun bitcoin() {
        assertEquals("300000", conv("1", Currency.BTC, Currency.BYN))
        assertEquals("0.001", conv("100", Currency.USD, Currency.BTC))
        assertEquals("0.00000033", conv("0.1", Currency.BYN, Currency.BTC))
    }

    @Test fun missingBitcoinRate() {
        val noBtc = Rates.of(BigDecimal(3), BigDecimal("3.5"), null, "2026-10-31", 0, "CoinGecko")
        assertFalse(noBtc.has(Currency.BTC))
        assertNull(noBtc.btcSource)
        assertNull(noBtc.convert(BigDecimal.ONE, Currency.BTC, Currency.BYN))
    }

    @Test fun formatting() {
        assertEquals("12.50", Money.format(BigDecimal("12.5"), Currency.USD))
        assertEquals("0", Money.format(BigDecimal("0.001"), Currency.USD))
        assertEquals("2.9512", Money.formatRate(BigDecimal("2.9512")))
        assertEquals("312 345.67", Money.formatRate(BigDecimal("312345.6666")))
    }

    @Test fun parsingInput() {
        assertEquals(BigDecimal("1234.5"), Money.parse("1 234,5"))
        assertEquals("150", Evaluator.format(Money.parse("100+50")!!))
        assertNull(Money.parse(""))
        assertNull(Money.parse("1.2.3"))
    }

    @Test fun parsesNbrb() {
        val json = """{"Cur_ID":431,"Date":"2026-10-31T00:00:00","Cur_Abbreviation":"USD",
            "Cur_Scale":1,"Cur_Name":"Доллар США","Cur_OfficialRate":2.9512}"""
        val (rate, date) = RatesParser.parseNbrb(json)
        assertEquals(0, rate.compareTo(BigDecimal("2.9512")))
        assertEquals("2026-10-31", date)

        val scaled = """{"Date":"2026-10-31T00:00:00","Cur_Scale":100,"Cur_OfficialRate":3.4567}"""
        assertEquals(0, RatesParser.parseNbrb(scaled).first.compareTo(BigDecimal("0.034567")))
    }

    @Test fun parsesBitcoinSources() {
        assertEquals(0, RatesParser.parseCoinGecko("""{"bitcoin":{"usd":104250.5}}""").compareTo(BigDecimal("104250.5")))
        assertEquals(0, RatesParser.parseCoinbase("""{"data":{"amount":"104250.50","base":"BTC","currency":"USD"}}""").compareTo(BigDecimal("104250.5")))
        assertEquals(0, RatesParser.parseBinance("""{"symbol":"BTCUSDT","price":"104250.50000000"}""").compareTo(BigDecimal("104250.5")))
    }
}

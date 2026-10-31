package com.example.calculator

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BankRatesTest {
    // Фрагмент реальной разметки myfin.by: рекламная строка, обычные строки, строка отделения.
    private val myfinUsd = """
        <table><tbody>
        <tr class="currencies-courses__row-main currencies-courses__row-main--ad" data-row-type="ad" data-bank-sef-alias="mtbank"><td><span class="fake-link"><img class="load_image" alt="Myfin Обмен"></span></td><td class="currencies-courses__currency-cell "><span>2.935</span></td><td class="currencies-courses__currency-cell "><span>2.953</span></td><td></td></tr>
        <tr class="currencies-courses__row-main " data-row-type="default" data-currencies-courses-bank-id="6" id="bank-row-14" data-bank-sef-alias="alfabank"><td class="pos-r"><span><span class="bank-logo"><img class="load_image" src="/x.svg" alt="Альфа Банк" data-url-img="a.svg"></span> Альфа Банк </span></td><td class="currencies-courses__currency-cell "><span>2.935</span></td><td class="currencies-courses__currency-cell "><span>2.975</span></td><td class="currencies-courses__icon-cell"><span class="currencies-courses__pin"> 32 </span></td></tr>
        <tr class="currencies-courses__row-additional" id="filial-row-14" data-bank-sef-alias="alfabank"><td colspan="4"><table><tbody>
        <tr class="currencies-courses__custom-row currencies-courses__row-main currencies-courses__row-main--filial" data-bank-sef-alias="alfabank"><td colspan="4"></td></tr>
        <tr data-bank-sef-alias="alfabank" data-has-appointment="0" class=""><td><a class="currencies-courses__branch-name" href="/bank/alfabank/department/204"> г. Минск, тр. Логойский, 10 </a><div class="light-gray fs-14"><i class="ic-update-time mr-5"></i> 22:33 </div></td><td class="currencies-courses__currency-cell "><span>2.935</span><div class="currencies-courses__calc-container"><i class="ic-calc rate_buy" data-currency="USD" ></i></div></td><td class="currencies-courses__currency-cell "><span>2.975</span><div><i class="ic-calc rate_sell"></i></div></td><td></td></tr>
        <tr data-bank-sef-alias="alfabank" data-has-appointment="0" class=""><td><a class="currencies-courses__branch-name" href="/bank/alfabank/department/205"> г. Минск, ул. Сурганова, 43 &amp; ТЦ </a><div class="light-gray fs-14"><i class="ic-update-time mr-5"></i> 21:10 </div></td><td class="currencies-courses__currency-cell "><span>2.92</span></td><td class="currencies-courses__currency-cell "><span>2.99</span></td><td></td></tr>
        <tr data-bank-sef-alias="alfabank" data-has-appointment="0" class=""><td><a class="currencies-courses__branch-name" href="/x"> г. Минск, тр. Логойский, 10 </a></td><td class="currencies-courses__currency-cell "><span>2.935</span></td><td class="currencies-courses__currency-cell "><span>2.975</span></td></tr>
        </tbody></table></td></tr>
        <tr class="currencies-courses__row-main " data-row-type="default" data-bank-sef-alias="mtbank"><td><img alt="МТбанк"></td><td class="currencies-courses__currency-cell "><span class="best accent tooltip-click" title="Лучший курс">2.94</span></td><td class="currencies-courses__currency-cell "><span class="best accent">2.948</span></td></tr>
        <tr class="currencies-courses__row-main " data-row-type="default" data-bank-sef-alias="rrb-bank"><td><img alt="Банк РРБ"></td><td class="currencies-courses__currency-cell "><span>-</span></td><td class="currencies-courses__currency-cell "><span>2.95</span></td></tr>
        <tr class="currencies-courses__row-main " data-row-type="default" data-bank-sef-alias="statusbank"><td><img alt="СтатусБанк"></td><td class="currencies-courses__currency-cell "></td><td class="currencies-courses__currency-cell "></td></tr>
        <tr class="currencies-courses__row-main " data-row-type="default" data-bank-sef-alias="tkbank"><td><img alt="ТК &quot;Банк&quot;"></td><td class="currencies-courses__currency-cell "><span>2.92</span></td><td class="currencies-courses__currency-cell "><span>2,98</span></td></tr>
        </tbody></table>
    """.trimIndent()

    private val myfinEur = """
        <tr class="currencies-courses__row-main " data-row-type="default" data-bank-sef-alias="alfabank"><td><img alt="Альфа Банк"></td><td class="currencies-courses__currency-cell "><span>3.275</span></td><td class="currencies-courses__currency-cell "><span>3.315</span></td></tr>
        <tr class="currencies-courses__row-main " data-row-type="default" data-bank-sef-alias="priorbank"><td><img alt="Приорбанк"></td><td class="currencies-courses__currency-cell "><span>3.28</span></td><td class="currencies-courses__currency-cell "><span>3.32</span></td></tr>
    """.trimIndent()

    private fun bd(s: String) = BigDecimal(s)

    @Test fun parsesMyfinSkippingAdsAndBranches() {
        val usd = BankRatesParser.parseMyfin(myfinUsd)
        assertEquals(listOf("alfabank", "mtbank", "rrb-bank", "tkbank"), usd.keys.toList())
        assertEquals("Альфа Банк", usd.getValue("alfabank").name)
        assertEquals(BankQuote(bd("2.935"), bd("2.975")), usd.getValue("alfabank").quote)
        assertEquals(BankQuote(bd("2.94"), bd("2.948")), usd.getValue("mtbank").quote)
        assertEquals(BankQuote(null, bd("2.95")), usd.getValue("rrb-bank").quote)
        assertEquals("ТК \"Банк\"", usd.getValue("tkbank").name)
        assertEquals(bd("2.98"), usd.getValue("tkbank").quote.sell)
    }

    @Test fun mergesUsdAndEur() {
        val banks = BankRatesParser.mergeMyfin(
            BankRatesParser.parseMyfin(myfinUsd),
            BankRatesParser.parseMyfin(myfinEur),
        )
        assertEquals(listOf("Альфа Банк", "Банк РРБ", "МТбанк", "Приорбанк", "ТК \"Банк\""), banks.map { it.name })
        val alfa = banks.first { it.id == "alfabank" }
        assertEquals(bd("3.315"), alfa.rate(Currency.EUR, Side.SELL))
        assertNull(banks.first { it.id == "priorbank" }.quotes[Currency.USD])
    }

    private fun sampleRates(): BankRates {
        val banks = BankRatesParser.mergeMyfin(
            BankRatesParser.parseMyfin(myfinUsd),
            BankRatesParser.parseMyfin(myfinEur),
        )
        return BankRates(banks, "myfin.by", 0)
    }

    @Test fun extremes() {
        val br = sampleRates()
        val buy = br.extremes(Currency.USD, Side.BUY)!!
        assertEquals(bd("2.92"), buy.min.value)
        assertEquals(listOf("ТК \"Банк\""), buy.min.banks)
        assertEquals(bd("2.94"), buy.max.value)
        assertEquals(listOf("МТбанк"), buy.max.banks)

        val sell = br.extremes(Currency.USD, Side.SELL)!!
        assertEquals(bd("2.948"), sell.min.value)
        assertEquals(bd("2.98"), sell.max.value)
    }

    @Test fun bestCombinesHighestBuyAndLowestSell() {
        val best = sampleRates().best()
        assertEquals(bd("2.94"), best.rate(Currency.USD, Side.BUY))
        assertEquals(bd("2.948"), best.rate(Currency.USD, Side.SELL))
        assertEquals(bd("3.28"), best.rate(Currency.EUR, Side.BUY))
        assertEquals(bd("3.315"), best.rate(Currency.EUR, Side.SELL))
        assertEquals(best, sampleRates().bank(BankRates.BEST_ID))
    }

    @Test fun convertWithBankRates() {
        val nbrb = Rates.of(bd("2.9462"), bd("3.2801"), bd("100000"), "2026-10-31", 0, "CoinGecko")
        val alfa = sampleRates().bank("alfabank")!!

        // Покупаю 100 USD — по курсу продажи банка.
        val buying = nbrb.withBank(alfa, Side.SELL)
        assertEquals("297.50", Money.format(buying.convert(bd("100"), Currency.USD, Currency.BYN)!!, Currency.BYN))
        // Сдаю 100 USD — по курсу покупки банка.
        val selling = nbrb.withBank(alfa, Side.BUY)
        assertEquals("293.50", Money.format(selling.convert(bd("100"), Currency.USD, Currency.BYN)!!, Currency.BYN))
        // BTC пересчитывается через доллар этого банка.
        assertEquals("297500", Money.format(buying.convert(bd("1"), Currency.BTC, Currency.BYN)!!, Currency.BYN))
    }

    @Test fun bankWithoutCurrencyHasNoRate() {
        val nbrb = Rates.of(bd("2.9462"), bd("3.2801"), null, "2026-10-31", 0, null)
        val prior = sampleRates().bank("priorbank")!!
        val r = nbrb.withBank(prior, Side.SELL)
        assertTrue(r.has(Currency.EUR))
        assertNull(r.convert(BigDecimal.ONE, Currency.USD, Currency.BYN))
    }

    @Test fun parsesBelarusbankBestAcrossBranches() {
        val json = """[
            {"USD_in":"2.9350","USD_out":"2.9850","EUR_in":"3.2400","EUR_out":"3.3100","name":"Минск"},
            {"USD_in":"2.9400","USD_out":"2.9900","EUR_in":"0.0000","EUR_out":"3.3000","name":"Минск"}
        ]"""
        val bank = BankRatesParser.parseBelarusbank(json)!!
        assertEquals("Беларусбанк", bank.name)
        assertEquals(BankQuote(bd("2.9400"), bd("2.9850")), bank.quotes[Currency.USD])
        assertEquals(BankQuote(bd("3.2400"), bd("3.3000")), bank.quotes[Currency.EUR])
    }

    @Test fun parsesAlfaApi() {
        val json = """{"rates":[
            {"sellRate":87.0,"sellIso":"EUR","buyRate":97.0,"buyIso":"RUB","quantity":1},
            {"sellRate":3.55,"sellIso":"RUB","buyRate":3.75,"buyIso":"BYN","quantity":100},
            {"sellRate":3.200000,"sellIso":"EUR","buyRate":3.400000,"buyIso":"BYN","quantity":1},
            {"sellRate":2.900000,"sellIso":"USD","buyRate":3.000000,"buyIso":"BYN","quantity":1}
        ]}"""
        val bank = BankRatesParser.parseAlfa(json)!!
        assertEquals(0, bank.rate(Currency.USD, Side.BUY)!!.compareTo(bd("2.9")))
        assertEquals(0, bank.rate(Currency.USD, Side.SELL)!!.compareTo(bd("3.0")))
        assertNotNull(bank.quotes[Currency.EUR])
        assertEquals(2, bank.quotes.size)
    }

    @Test fun bankRateFormatting() {
        assertEquals("2.935", Money.formatBankRate(bd("2.9350")))
        assertEquals("3.30", Money.formatBankRate(bd("3.3")))
        assertEquals("3.00", Money.formatBankRate(bd("3.000000")))
    }

    @Test fun parsesBranchesWithAddresses() {
        val alfa = BankRatesParser.parseMyfin(myfinUsd).getValue("alfabank")
        assertEquals(2, alfa.branches.size) // дубликат адреса отброшен
        val first = alfa.branches[0]
        assertEquals("г. Минск, тр. Логойский, 10", first.address)
        assertEquals("22:33", first.time)
        assertEquals(BankQuote(bd("2.935"), bd("2.975")), first.quote)
        assertEquals("г. Минск, ул. Сурганова, 43 & ТЦ", alfa.branches[1].address)
        assertTrue(BankRatesParser.parseMyfin(myfinUsd).getValue("mtbank").branches.isEmpty())
    }

    @Test fun branchesWithGivenRate() {
        val alfa = sampleRates().bank("alfabank")!!
        val best = alfa.branchesWith(Currency.USD, Side.SELL, bd("2.975"))
        assertEquals(listOf("г. Минск, тр. Логойский, 10"), best.map { it.address })
        assertEquals(1, alfa.branchesWith(Currency.USD, Side.BUY, bd("2.92")).size)
        assertTrue(alfa.branchesWith(Currency.EUR, Side.BUY, bd("3.275")).isEmpty())
    }

    @Test fun belarusbankBranches() {
        val json = """[
            {"kurs_date_time":"2026-10-30 16:00:00","USD_in":"2.9350","USD_out":"2.9850","EUR_in":"0","EUR_out":"0",
             "name_type":"г.","name":"Минск","street_type":"ул. ","street":"Ольшевского","home_number":"1/3"}
        ]"""
        val bank = BankRatesParser.parseBelarusbank(json)!!
        val b = bank.branches.getValue(Currency.USD).single()
        assertEquals("г. Минск, ул. Ольшевского, 1/3", b.address)
        assertEquals("16:00", b.time)
        assertNull(bank.branches[Currency.EUR])
    }
}

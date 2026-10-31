package com.example.calculator

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/**
 * Конвертер BYN / USD / EUR / BTC. Ввод в любой строке сразу пересчитывает остальные.
 */
class ConverterActivity : Activity() {
    private lateinit var repository: RatesRepository
    private lateinit var status: TextView
    private lateinit var refresh: View

    private val fields = LinkedHashMap<Currency, EditText>()
    private val rateLabels = HashMap<Currency, TextView>()

    private var rates: Rates? = null
    private var source: Currency = Currency.USD
    private var amount: BigDecimal? = null
    private var updating = false
    private var loading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_converter)

        repository = RatesRepository(this)
        status = findViewById(R.id.ratesStatus)
        refresh = findViewById(R.id.btnRefresh)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        refresh.setOnClickListener { loadRates(force = true) }
        findViewById<View>(R.id.btnToCalculator).setOnClickListener { returnToCalculator() }

        val container = findViewById<LinearLayout>(R.id.currencyRows)
        for (c in Currency.entries) addRow(container, c)

        val prefs = getSharedPreferences("converter", MODE_PRIVATE)
        source = savedInstanceState?.getString(KEY_SOURCE)?.let(Currency::valueOf)
            ?: prefs.getString(KEY_SOURCE, null)?.let { runCatching { Currency.valueOf(it) }.getOrNull() }
            ?: Currency.USD

        rates = repository.cached()
        showStatus()

        // Начальная сумма: из поворота экрана или из калькулятора.
        val initial = savedInstanceState?.getString(KEY_AMOUNT)
            ?: intent.getStringExtra(EXTRA_VALUE)
            ?: "1"
        setAmount(source, Money.parse(initial))
        fields.getValue(source).apply {
            requestFocus()
            setSelection(text.length)
        }

        val stale = rates?.let { System.currentTimeMillis() - it.updatedAt > REFRESH_AFTER_MS } ?: true
        if (stale) loadRates(force = false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_SOURCE, source.name)
        outState.putString(KEY_AMOUNT, fields.getValue(source).text.toString())
    }

    override fun onPause() {
        super.onPause()
        getSharedPreferences("converter", MODE_PRIVATE).edit().putString(KEY_SOURCE, source.name).apply()
    }

    private fun addRow(container: LinearLayout, c: Currency) {
        val row = LayoutInflater.from(this).inflate(R.layout.item_currency, container, false)
        row.findViewById<TextView>(R.id.currencyCode).text = c.name
        row.findViewById<TextView>(R.id.currencyName).text = c.title
        rateLabels[c] = row.findViewById(R.id.currencyRate)
        val field = row.findViewById<EditText>(R.id.currencyAmount)
        fields[c] = field
        row.setOnClickListener {
            field.requestFocus()
            field.setSelection(field.text.length)
            getSystemService(InputMethodManager::class.java).showSoftInput(field, 0)
        }
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                if (updating) return
                source = c
                amount = Money.parse(s.toString())
                recalculate()
            }
        })
        container.addView(row)
    }

    /** Ставит сумму в строку валюты и пересчитывает остальные. */
    private fun setAmount(c: Currency, value: BigDecimal?) {
        source = c
        amount = value
        updating = true
        fields.getValue(c).setText(value?.let { Money.format(it, c) } ?: "")
        updating = false
        recalculate()
    }

    private fun recalculate() {
        val r = rates
        val value = amount
        updating = true
        for ((c, field) in fields) {
            if (c == source) continue
            val converted = if (r != null && value != null) r.convert(value, source, c) else null
            field.setText(converted?.let { Money.format(it, c) } ?: "")
        }
        updating = false
    }

    private fun loadRates(force: Boolean) {
        if (loading) return
        loading = true
        refresh.isEnabled = false
        status.text = getString(R.string.rates_loading)
        thread {
            val result = runCatching { repository.fetch() }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                loading = false
                refresh.isEnabled = true
                result.onSuccess {
                    rates = it
                    recalculate()
                    showStatus()
                }.onFailure {
                    showStatus(failed = true, manual = force)
                }
            }
        }
    }

    private fun showStatus(failed: Boolean = false, manual: Boolean = false) {
        val r = rates
        for (c in Currency.entries) {
            val label = rateLabels.getValue(c)
            label.text = when {
                c == Currency.BYN -> ""
                r == null || !r.has(c) -> getString(R.string.rate_unknown)
                else -> "1 ${c.name} = ${Money.formatRate(r.bynPerUnit.getValue(c))} BYN"
            }
            label.visibility = if (c == Currency.BYN) View.GONE else View.VISIBLE
        }
        status.text = when {
            r == null && failed -> getString(R.string.rates_no_connection_empty)
            r == null -> getString(R.string.rates_loading)
            else -> buildString {
                append(getString(R.string.rates_nbrb, formatDate(r.nbrbDate)))
                r.btcSource?.let { append(" · BTC: ").append(it) }
                append('\n')
                append(getString(R.string.rates_updated, formatUpdated(r.updatedAt)))
                if (failed) append(" · ").append(getString(R.string.rates_offline))
            }
        }
        if (failed && manual && r != null) {
            android.widget.Toast.makeText(this, R.string.rates_offline_toast, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatDate(isoDate: String): String = try {
        val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(isoDate)
        SimpleDateFormat("dd.MM.yyyy", Locale.US).format(d!!)
    } catch (e: Exception) {
        isoDate
    }

    private fun formatUpdated(time: Long): String =
        SimpleDateFormat("dd.MM HH:mm", Locale.US).format(Date(time))

    /** Возвращает в калькулятор сумму из строки, где сейчас курсор (или исходной). */
    private fun returnToCalculator() {
        val focused = fields.entries.firstOrNull { it.value.hasFocus() }?.key ?: source
        val value = Money.parse(fields.getValue(focused).text.toString())
        if (value != null) {
            setResult(RESULT_OK, Intent().putExtra(EXTRA_VALUE, Money.format(value, focused)))
        }
        finish()
    }

    companion object {
        /** Число в обычной записи (минус — ASCII «-»). */
        const val EXTRA_VALUE = "value"
        private const val KEY_SOURCE = "source"
        private const val KEY_AMOUNT = "amount"
        private const val REFRESH_AFTER_MS = 60 * 60 * 1000L
    }
}

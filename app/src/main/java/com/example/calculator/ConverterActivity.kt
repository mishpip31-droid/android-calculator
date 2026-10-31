package com.example.calculator

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/**
 * Конвертер BYN / USD / EUR / BTC. Ввод в любой строке сразу пересчитывает остальные.
 * Курс — официальный НБРБ, лучший по банкам или конкретного банка (покупка/продажа).
 */
class ConverterActivity : Activity() {
    private lateinit var repository: RatesRepository
    private lateinit var bankRepository: BankRatesRepository
    private lateinit var status: TextView
    private lateinit var refresh: View
    private lateinit var sourceButton: TextView
    private lateinit var sideToggle: View
    private lateinit var sideBuyButton: TextView
    private lateinit var sideSellButton: TextView
    private lateinit var sideHint: TextView
    private lateinit var tableUsd: TextView
    private lateinit var tableEur: TextView
    private lateinit var buyRange: TextView
    private lateinit var sellRange: TextView
    private lateinit var bankTable: LinearLayout
    private lateinit var bankTableHeader: View
    private lateinit var bankFooter: TextView

    private val fields = LinkedHashMap<Currency, EditText>()
    private val rateLabels = HashMap<Currency, TextView>()

    /** Официальные курсы НБРБ (+ BTC). */
    private var rates: Rates? = null
    private var bankRates: BankRates? = null

    /** "nbrb", [BankRates.BEST_ID] или id банка. */
    private var sourceId = SOURCE_NBRB

    /** SELL — «покупаю валюту» (курс продажи банка), BUY — «продаю валюту». */
    private var side = Side.SELL
    private var tableCurrency = Currency.USD

    private var source: Currency = Currency.USD
    private var amount: BigDecimal? = null
    private var updating = false
    private var loadingNbrb = false
    private var loadingBanks = false
    private var banksFailed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_converter)

        repository = RatesRepository(this)
        bankRepository = BankRatesRepository(this)
        status = findViewById(R.id.ratesStatus)
        refresh = findViewById(R.id.btnRefresh)
        sourceButton = findViewById(R.id.btnRateSource)
        sideToggle = findViewById(R.id.sideToggle)
        sideBuyButton = findViewById(R.id.btnSideBuy)
        sideSellButton = findViewById(R.id.btnSideSell)
        sideHint = findViewById(R.id.sideHint)
        tableUsd = findViewById(R.id.btnTableUsd)
        tableEur = findViewById(R.id.btnTableEur)
        buyRange = findViewById(R.id.bankBuyRange)
        sellRange = findViewById(R.id.bankSellRange)
        bankTable = findViewById(R.id.bankTable)
        bankTableHeader = findViewById(R.id.bankTableHeader)
        bankFooter = findViewById(R.id.bankFooter)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        refresh.setOnClickListener { loadAll(force = true) }
        findViewById<View>(R.id.btnToCalculator).setOnClickListener { returnToCalculator() }
        sourceButton.setOnClickListener { chooseSource() }
        sideSellButton.setOnClickListener { setSide(Side.SELL) }
        sideBuyButton.setOnClickListener { setSide(Side.BUY) }
        tableUsd.setOnClickListener { setTableCurrency(Currency.USD) }
        tableEur.setOnClickListener { setTableCurrency(Currency.EUR) }

        val container = findViewById<LinearLayout>(R.id.currencyRows)
        for (c in Currency.entries) addRow(container, c)

        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        source = enumOr(savedInstanceState?.getString(KEY_SOURCE) ?: prefs.getString(KEY_SOURCE, null), Currency.USD)
        sourceId = prefs.getString(KEY_RATE_SOURCE, SOURCE_NBRB) ?: SOURCE_NBRB
        side = enumOr(prefs.getString(KEY_SIDE, null), Side.SELL)
        tableCurrency = enumOr(prefs.getString(KEY_TABLE_CURRENCY, null), Currency.USD)

        rates = repository.cached()
        bankRates = bankRepository.cached()

        // Начальная сумма: из поворота экрана или из калькулятора.
        val initial = savedInstanceState?.getString(KEY_AMOUNT)
            ?: intent.getStringExtra(EXTRA_VALUE)
            ?: "1"
        amount = Money.parse(initial)
        updating = true
        fields.getValue(source).setText(amount?.let { Money.format(it, source) } ?: "")
        updating = false
        fields.getValue(source).apply {
            requestFocus()
            setSelection(text.length)
        }

        renderAll()
        loadAll(force = false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_SOURCE, source.name)
        outState.putString(KEY_AMOUNT, fields.getValue(source).text.toString())
    }

    override fun onPause() {
        super.onPause()
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putString(KEY_SOURCE, source.name)
            .putString(KEY_RATE_SOURCE, sourceId)
            .putString(KEY_SIDE, side.name)
            .putString(KEY_TABLE_CURRENCY, tableCurrency.name)
            .apply()
    }

    // ---------- Строки валют ----------

    private fun addRow(container: LinearLayout, c: Currency) {
        val row = LayoutInflater.from(this).inflate(R.layout.item_currency, container, false)
        row.findViewById<TextView>(R.id.currencyCode).text = c.name
        row.findViewById<TextView>(R.id.currencyName).text = c.title
        rateLabels[c] = row.findViewById(R.id.currencyRate)
        val field = row.findViewById<EditText>(R.id.currencyAmount)
        val clear = row.findViewById<View>(R.id.currencyClear)
        fields[c] = field
        fun updateClear() {
            clear.visibility = if (field.hasFocus() && field.text.isNotEmpty()) View.VISIBLE else View.GONE
        }
        field.setOnFocusChangeListener { _, _ -> updateClear() }
        clear.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            field.setText("") // пересчёт очистит и остальные строки
            field.requestFocus()
            getSystemService(InputMethodManager::class.java).showSoftInput(field, 0)
        }
        row.setOnClickListener {
            field.requestFocus()
            field.setSelection(field.text.length)
            getSystemService(InputMethodManager::class.java).showSoftInput(field, 0)
        }
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                updateClear()
                if (updating) return
                source = c
                amount = Money.parse(s.toString())
                recalculate()
            }
        })
        container.addView(row)
    }

    /** Курсы, по которым сейчас считаем: НБРБ или выбранного банка. */
    private fun effectiveRates(): Rates? {
        val base = rates
        val bank = selectedBank() ?: return base
        val fallback = base ?: Rates(mapOf(Currency.BYN to BigDecimal.ONE), "", 0)
        return fallback.withBank(bank, side)
    }

    private fun selectedBank(): Bank? =
        if (sourceId == SOURCE_NBRB) null else bankRates?.bank(sourceId)

    private fun recalculate() {
        val r = effectiveRates()
        val value = amount
        updating = true
        for ((c, field) in fields) {
            if (c == source) continue
            val converted = if (r != null && value != null) r.convert(value, source, c) else null
            field.setText(converted?.let { Money.format(it, c) } ?: "")
        }
        updating = false
    }

    // ---------- Выбор курса ----------

    private fun chooseSource() {
        val banks = bankRates?.banks.orEmpty()
        val ids = ArrayList<String>()
        val labels = ArrayList<String>()
        ids += SOURCE_NBRB
        labels += getString(R.string.source_nbrb)
        if (banks.isNotEmpty()) {
            ids += BankRates.BEST_ID
            labels += getString(R.string.source_best)
            for (b in banks) {
                ids += b.id
                labels += b.name
            }
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.rate_source_title)
            .setSingleChoiceItems(labels.toTypedArray(), ids.indexOf(sourceId).coerceAtLeast(0)) { dialog, which ->
                selectSource(ids[which])
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun selectSource(id: String) {
        sourceId = id
        renderAll()
    }

    private fun setSide(newSide: Side) {
        side = newSide
        renderAll()
    }

    private fun setTableCurrency(c: Currency) {
        tableCurrency = c
        renderBankTable()
    }

    // ---------- Отрисовка ----------

    private fun renderAll() {
        val bank = selectedBank()
        val bankMode = sourceId != SOURCE_NBRB
        sourceButton.text = getString(
            R.string.rate_source_label,
            when {
                !bankMode -> getString(R.string.source_nbrb)
                sourceId == BankRates.BEST_ID -> getString(R.string.source_best)
                else -> bank?.name ?: sourceId
            },
        )
        sideToggle.visibility = if (bankMode) View.VISIBLE else View.GONE
        sideHint.visibility = if (bankMode) View.VISIBLE else View.GONE
        sideSellButton.isSelected = side == Side.SELL
        sideBuyButton.isSelected = side == Side.BUY
        sideHint.setText(if (side == Side.SELL) R.string.side_hint_sell else R.string.side_hint_buy)

        showRateLabels()
        showStatus()
        recalculate()
        renderBankTable()
    }

    private fun showRateLabels() {
        val r = effectiveRates()
        val bank = selectedBank()
        val sideName = getString(if (side == Side.BUY) R.string.range_buy else R.string.range_sell).lowercase()
        for (c in Currency.entries) {
            val label = rateLabels.getValue(c)
            label.visibility = if (c == Currency.BYN) View.GONE else View.VISIBLE
            label.text = when {
                c == Currency.BYN -> ""
                r == null || !r.has(c) ->
                    if (bank != null && c != Currency.BTC) getString(R.string.bank_rate_missing) else getString(R.string.rate_unknown)
                else -> buildString {
                    val value = r.bynPerUnit.getValue(c)
                    append("1 ${c.name} = ")
                    append(if (bank != null && c != Currency.BTC) Money.formatBankRate(value) else Money.formatRate(value))
                    append(" BYN")
                    if (bank != null && c != Currency.BTC) append(" · ").append(sideName)
                }
            }
        }
    }

    private fun showStatus(failed: Boolean = false) {
        val r = rates
        status.text = when {
            loadingNbrb && r == null -> getString(R.string.rates_loading)
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
    }

    private fun renderBankTable() {
        tableUsd.isSelected = tableCurrency == Currency.USD
        tableEur.isSelected = tableCurrency == Currency.EUR
        bankTable.removeAllViews()
        val br = bankRates
        if (br == null) {
            buyRange.text = getString(if (loadingBanks) R.string.banks_loading else R.string.banks_unavailable)
            sellRange.visibility = View.GONE
            bankTableHeader.visibility = View.GONE
            bankFooter.visibility = View.GONE
            return
        }
        sellRange.visibility = View.VISIBLE
        bankTableHeader.visibility = View.VISIBLE
        bankFooter.visibility = View.VISIBLE

        val c = tableCurrency
        val buyExt = br.extremes(c, Side.BUY)
        val sellExt = br.extremes(c, Side.SELL)
        // Выгодно сдать — по максимальной покупке, купить — по минимальной продаже.
        buyRange.text = rangeText(R.string.range_buy, buyExt, bestIsMax = true)
        sellRange.text = rangeText(R.string.range_sell, sellExt, bestIsMax = false)

        // Выгодно: сдать — по самой высокой покупке, купить — по самой низкой продаже.
        val bestBuy = buyExt?.max?.value
        val bestSell = sellExt?.min?.value
        val bestColor = getColor(R.color.best)
        val primary = getColor(R.color.text_primary)
        for (bank in br.banks) {
            val q = bank.quotes[c] ?: continue
            val row = LayoutInflater.from(this).inflate(R.layout.item_bank_row, bankTable, false)
            row.findViewById<TextView>(R.id.bankName).text = bank.name
            fun cell(id: Int, cellSide: Side, value: BigDecimal?, best: BigDecimal?) {
                val tv = row.findViewById<TextView>(id)
                tv.text = value?.let(Money::formatBankRate) ?: "—"
                val isBest = value != null && best != null && value.compareTo(best) == 0
                tv.setTextColor(if (isBest) bestColor else primary)
                tv.setTypeface(null, if (isBest) Typeface.BOLD else Typeface.NORMAL)
                // Нажатие на курс — адреса отделений, где он сейчас действует.
                if (value != null) {
                    tv.setBackgroundResource(R.drawable.history_item_bg)
                    tv.setOnClickListener { showBranches(bank, c, cellSide, value) }
                }
            }
            cell(R.id.bankBuy, Side.BUY, q.buy, bestBuy)
            cell(R.id.bankSell, Side.SELL, q.sell, bestSell)
            if (bank.id == sourceId) row.setBackgroundResource(R.drawable.selected_row_bg)
            row.setOnClickListener { selectSource(bank.id) }
            bankTable.addView(row)
        }
        bankFooter.text = buildString {
            append(getString(R.string.banks_footer, br.source, formatUpdated(br.updatedAt)))
            if (banksFailed) append(" (").append(getString(R.string.banks_footer_offline)).append(')')
        }
    }

    /** «Покупка: мин 2.92 (ТК Банк) · макс 2.942 (Белинвестбанк)» — лучшая часть зелёным. */
    private fun rangeText(labelRes: Int, ext: Extremes?, bestIsMax: Boolean): CharSequence {
        val label = getString(labelRes)
        if (ext == null) return "$label: —"
        val out = android.text.SpannableStringBuilder("$label: ")
        fun part(prefix: String, e: Extreme, highlight: Boolean) {
            val start = out.length
            out.append(prefix).append(' ').append(Money.formatBankRate(e.value))
                .append(" (").append(bankNames(e.banks)).append(')')
            if (highlight) {
                out.setSpan(
                    android.text.style.ForegroundColorSpan(getColor(R.color.best)),
                    start, out.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                out.setSpan(
                    android.text.style.StyleSpan(Typeface.BOLD),
                    start, out.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
        part("мин", ext.min, highlight = !bestIsMax)
        out.append(" · ")
        part("макс", ext.max, highlight = bestIsMax)
        return out
    }

    /** Список отделений банка с этим курсом; нажатие на адрес открывает карту. */
    private fun showBranches(bank: Bank, c: Currency, side: Side, value: BigDecimal) {
        val all = bank.branches[c].orEmpty()
        val matching = bank.branchesWith(c, side, value)
        val sideName = getString(if (side == Side.BUY) R.string.range_buy else R.string.range_sell).lowercase()
        val rateText = "$sideName ${c.name} ${Money.formatBankRate(value)}"
        if (all.isEmpty()) {
            if (loadingBanks) {
                Toast.makeText(this, R.string.branches_loading, Toast.LENGTH_SHORT).show()
                return
            }
            // Часто это курс в мобильном приложении банка — отделений у него нет.
            AlertDialog.Builder(this)
                .setCustomTitle(dialogTitle("${bank.name} · $rateText"))
                .setMessage(R.string.branches_none)
                .setPositiveButton(R.string.find_on_map) { _, _ -> openMap(bank.name) }
                .setNegativeButton(R.string.close, null)
                .show()
            return
        }
        // Если точного совпадения нет (курс могли обновить) — все отделения, лучшие сверху.
        val list = matching.ifEmpty {
            all.sortedWith(
                compareBy<Branch, BigDecimal?>(nullsLast<BigDecimal>()) { it.quote.get(side) }
                    .let { if (side == Side.BUY) it.reversed() else it },
            )
        }
        val subtitle = if (matching.isNotEmpty()) {
            getString(R.string.branches_subtitle, rateText.replaceFirstChar { it.uppercase() }, matching.size)
        } else {
            getString(R.string.branches_subtitle_all, all.size)
        }
        val adapter = object : android.widget.BaseAdapter() {
            override fun getCount() = list.size
            override fun getItem(position: Int) = list[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = convertView ?: LayoutInflater.from(this@ConverterActivity)
                    .inflate(R.layout.item_branch, parent, false)
                val b = list[position]
                view.findViewById<TextView>(R.id.branchAddress).text = b.address
                view.findViewById<TextView>(R.id.branchRates).text = branchRatesText(b, side, value)
                return view
            }
        }
        AlertDialog.Builder(this)
            .setCustomTitle(branchesTitle(bank.name, subtitle))
            .setAdapter(adapter) { _, which -> openMap(list[which].address) }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    /** «Покупка 3.255 · Продажа 3.315 · 23:11» — выбранный курс зелёным. */
    private fun branchRatesText(b: Branch, side: Side, value: BigDecimal): CharSequence {
        val out = android.text.SpannableStringBuilder()
        fun part(label: Int, partSide: Side, v: BigDecimal?) {
            val start = out.length
            out.append(getString(label)).append(' ').append(v?.let(Money::formatBankRate) ?: "—")
            if (partSide == side && v != null && v.compareTo(value) == 0) {
                out.setSpan(
                    android.text.style.ForegroundColorSpan(getColor(R.color.best)),
                    start, out.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                out.setSpan(
                    android.text.style.StyleSpan(Typeface.BOLD),
                    start, out.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
        part(R.string.range_buy, Side.BUY, b.quote.buy)
        out.append("   ")
        part(R.string.range_sell, Side.SELL, b.quote.sell)
        if (b.time.isNotEmpty()) out.append("   ").append(b.time)
        return out
    }

    /** Заголовок списка отделений: банк крупно, курс и подсказка мельче. */
    private fun branchesTitle(bankName: String, subtitle: String): View {
        val pad = (20 * resources.displayMetrics.density).toInt()
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad / 2)
            addView(TextView(context).apply {
                text = bankName
                textSize = 20f
                setTypeface(null, Typeface.BOLD)
                setTextColor(getColor(R.color.text_primary))
            })
            addView(TextView(context).apply {
                text = subtitle
                textSize = 15f
                setTextColor(getColor(R.color.text_primary))
                setPadding(0, pad / 4, 0, 0)
            })
            addView(TextView(context).apply {
                setText(R.string.branches_hint)
                textSize = 13f
                setTextColor(getColor(R.color.text_secondary))
                setPadding(0, pad / 4, 0, 0)
            })
        }
    }

    /** Заголовок диалога в несколько строк (стандартный обрезается до одной). */
    private fun dialogTitle(text: String): TextView {
        val pad = (20 * resources.displayMetrics.density).toInt()
        return TextView(this).apply {
            this.text = text
            setPadding(pad, pad, pad, pad / 2)
            textSize = 17f
            setTextColor(getColor(R.color.text_primary))
        }
    }

    private fun openMap(address: String) {
        val query = if (address.contains("Минск", ignoreCase = true)) address else "Минск, $address"
        val geo = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0?q=" + android.net.Uri.encode(query)))
        try {
            startActivity(geo)
        } catch (e: android.content.ActivityNotFoundException) {
            // Нет приложения карт — открываем Яндекс Карты в браузере.
            val web = android.net.Uri.parse("https://yandex.by/maps/?text=" + android.net.Uri.encode(query))
            try {
                startActivity(Intent(Intent.ACTION_VIEW, web))
            } catch (e2: android.content.ActivityNotFoundException) {
                Toast.makeText(this, R.string.no_map_app, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun bankNames(names: List<String>) =
        if (names.size <= 2) names.joinToString(", ") else "${names[0]} и ещё ${names.size - 1}"

    // ---------- Загрузка ----------

    private fun loadAll(force: Boolean) {
        val now = System.currentTimeMillis()
        val nbrbStale = rates?.let { now - it.updatedAt > REFRESH_AFTER_MS } ?: true
        val banksStale = bankRates?.let { now - it.updatedAt > REFRESH_AFTER_MS } ?: true
        if (force || nbrbStale) loadNbrb(manual = force)
        if (force || banksStale) loadBanks()
    }

    private fun loadNbrb(manual: Boolean) {
        if (loadingNbrb) return
        loadingNbrb = true
        updateRefreshButton()
        showStatus()
        thread {
            val result = runCatching { repository.fetch() }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                loadingNbrb = false
                updateRefreshButton()
                result.onSuccess {
                    rates = it
                    renderAll()
                }.onFailure {
                    showStatus(failed = true)
                    if (manual && rates != null) {
                        Toast.makeText(this, R.string.rates_offline_toast, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun loadBanks() {
        if (loadingBanks) return
        loadingBanks = true
        updateRefreshButton()
        if (bankRates == null) renderBankTable()
        thread {
            val result = runCatching { bankRepository.fetch() }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                loadingBanks = false
                updateRefreshButton()
                result.onSuccess {
                    bankRates = it
                    banksFailed = false
                }.onFailure {
                    banksFailed = true
                }
                renderAll()
            }
        }
    }

    private fun updateRefreshButton() {
        refresh.isEnabled = !loadingNbrb && !loadingBanks
        refresh.alpha = if (refresh.isEnabled) 1f else 0.4f
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

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default

    companion object {
        /** Число в обычной записи (минус — ASCII «-»). */
        const val EXTRA_VALUE = "value"
        private const val PREFS = "converter"
        private const val KEY_SOURCE = "source"
        private const val KEY_AMOUNT = "amount"
        private const val KEY_RATE_SOURCE = "rate_source"
        private const val KEY_SIDE = "side"
        private const val KEY_TABLE_CURRENCY = "table_currency"
        private const val SOURCE_NBRB = "nbrb"
        private const val REFRESH_AFTER_MS = 60 * 60 * 1000L
    }
}

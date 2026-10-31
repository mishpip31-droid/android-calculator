package com.example.calculator

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Калькулятор поверх других приложений: окно с клавиатурой, которое можно
 * перетаскивать, менять размер, сворачивать в «пузырёк» и закрывать броском на ✕.
 */
class FloatingCalculatorService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var ui: Context
    private val calculator = Calculator()
    private var lastExpression = ""

    private var window: View? = null
    private var windowParams: WindowManager.LayoutParams? = null
    private var bubble: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var trash: View? = null
    private var translucent = false

    private val density get() = resources.displayMetrics.density
    private val screenW get() = resources.displayMetrics.widthPixels
    private val screenH get() = resources.displayMetrics.heightPixels
    private fun dp(v: Int) = (v * density).toInt()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        wm = getSystemService(WindowManager::class.java)
        ui = ContextThemeWrapper(this, ThemeManager.style(ThemeManager.mode(this)))
        startInForeground()
        val prefs = prefs()
        calculator.restore(prefs.getString(KEY_EXPR, "") ?: "", prefs.getBoolean(KEY_EVALUATED, false))
        lastExpression = prefs.getString(KEY_LAST, "") ?: ""
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                if (window == null && bubble == null) showWindow() else if (bubble != null) expand()
                // Основное приложение сворачивается — остаётся только плавающее окно.
                ThemedActivity.moveAppToBack()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        saveState()
        listOf(window, bubble, trash).forEach { v -> v?.let { runCatching { wm.removeView(it) } } }
        window = null
        bubble = null
        trash = null
        FloatingTileService.refresh(this)
        super.onDestroy()
    }

    // ---------- Уведомление (без него Android закроет окно в фоне) ----------

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.floating_channel), NotificationManager.IMPORTANCE_LOW),
            )
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val expand = PendingIntent.getService(this, 1, Intent(this, javaClass).setAction(ACTION_EXPAND), flags)
        val stop = PendingIntent.getService(this, 2, Intent(this, javaClass).setAction(ACTION_STOP), flags)
        @Suppress("DEPRECATION")
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, CHANNEL)
        else Notification.Builder(this)
        val notification = builder
            .setSmallIcon(R.drawable.ic_tile_calculator)
            .setContentTitle(getString(R.string.floating_notification_title))
            .setContentText(getString(R.string.floating_notification_text))
            .setContentIntent(expand)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_tile_calculator),
                    getString(R.string.floating_close),
                    stop,
                ).build(),
            )
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    // ---------- Окно калькулятора ----------

    private fun overlayParams(width: Int) = WindowManager.LayoutParams(
        width,
        WindowManager.LayoutParams.WRAP_CONTENT,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
        // Не забираем фокус: нажатия вне окна уходят приложению под ним.
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.START }

    @SuppressLint("InflateParams")
    private fun showWindow() {
        val prefs = prefs()
        val view = LayoutInflater.from(ui).inflate(R.layout.floating_calculator, null)
        val width = prefs.getInt(KEY_WIDTH, dp(264)).coerceIn(dp(MIN_WIDTH_DP), maxWidth())
        val params = overlayParams(width).apply {
            x = prefs.getInt(KEY_X, (screenW - width) / 2)
            y = prefs.getInt(KEY_Y, screenH / 5)
        }
        translucent = prefs.getBoolean(KEY_TRANSLUCENT, false)
        view.alpha = if (translucent) 0.75f else 1f
        window = view
        windowParams = params
        bindKeys(view)
        setupWindowGestures(view, params)
        applyKeyHeights(view, width)
        wm.addView(view, params)
        render()
        FloatingTileService.refresh(this)
    }

    private fun maxWidth() = minOf(screenW - dp(16), dp(420))

    private fun bindKeys(view: View) {
        val digits = mapOf(
            R.id.f0 to '0', R.id.f1 to '1', R.id.f2 to '2', R.id.f3 to '3', R.id.f4 to '4',
            R.id.f5 to '5', R.id.f6 to '6', R.id.f7 to '7', R.id.f8 to '8', R.id.f9 to '9',
        )
        fun key(id: Int, action: () -> Unit) = view.findViewById<View>(id).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            action()
            render()
        }
        digits.forEach { (id, d) -> key(id) { calculator.inputDigit(d) } }
        key(R.id.fPlus) { calculator.inputOperator('+') }
        key(R.id.fMinus) { calculator.inputOperator('−') }
        key(R.id.fMul) { calculator.inputOperator('×') }
        key(R.id.fDiv) { calculator.inputOperator('÷') }
        key(R.id.fPercent) { calculator.inputPercent() }
        key(R.id.fDot) { calculator.inputDot() }
        key(R.id.fParen) { calculator.inputParen() }
        key(R.id.fC) {
            calculator.clear()
            lastExpression = ""
        }
        key(R.id.fBack) { calculator.backspace() }
        key(R.id.fEq) {
            val before = calculator.expression
            val entry = calculator.evaluate()
            if (calculator.error == null && calculator.justEvaluated) lastExpression = entry?.expression ?: before
            entry?.let(::saveToHistory)
        }
        view.findViewById<View>(R.id.fBack).setOnLongClickListener {
            calculator.clear()
            lastExpression = ""
            render()
            true
        }
        view.findViewById<View>(R.id.fMinimize).setOnClickListener { collapse() }
        view.findViewById<View>(R.id.fClose).setOnClickListener { stopSelf() }
        view.findViewById<View>(R.id.fOpenApp).setOnClickListener { openApp() }
        view.findViewById<View>(R.id.fPaste).setOnClickListener { paste() }
        // Удержание на результате — скопировать.
        view.findViewById<View>(R.id.fResult).setOnLongClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            copy(currentValueText())
            true
        }
    }

    /** Перетаскивание за полоску, изменение размера за уголок, бросок на ✕ — закрыть. */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupWindowGestures(view: View, params: WindowManager.LayoutParams) {
        val handle = view.findViewById<View>(R.id.fHandle)
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var dragging = false
        handle.setOnLongClickListener {
            // Удержание на полоске — полупрозрачное окно (и обратно).
            translucent = !translucent
            view.alpha = if (translucent) 0.75f else 1f
            prefs().edit().putBoolean(KEY_TRANSLUCENT, translucent).apply()
            true
        }
        handle.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = e.rawX
                    touchY = e.rawY
                    dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - touchX
                    val dy = e.rawY - touchY
                    if (!dragging && hypot(dx, dy) > dp(8)) {
                        dragging = true
                        v.cancelLongPress()
                        showTrash()
                        view.alpha = 0.85f
                    }
                    if (dragging) {
                        params.x = (startX + dx).toInt()
                        params.y = (startY + dy).toInt().coerceAtLeast(0)
                        wm.updateViewLayout(view, params)
                        highlightTrash(e.rawX, e.rawY)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        view.alpha = if (translucent) 0.75f else 1f
                        val drop = isOverTrash(e.rawX, e.rawY)
                        hideTrash()
                        if (drop) {
                            stopSelf()
                        } else {
                            params.x = params.x.coerceIn(-params.width / 2, screenW - params.width / 2)
                            wm.updateViewLayout(view, params)
                            saveState()
                        }
                        return@setOnTouchListener true
                    }
                }
            }
            dragging
        }

        val resize = view.findViewById<View>(R.id.fResize)
        var startW = 0
        resize.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startW = params.width
                    touchX = e.rawX
                }
                MotionEvent.ACTION_MOVE -> {
                    params.width = (startW + (e.rawX - touchX)).toInt().coerceIn(dp(MIN_WIDTH_DP), maxWidth())
                    applyKeyHeights(view, params.width)
                    wm.updateViewLayout(view, params)
                }
                MotionEvent.ACTION_UP -> saveState()
            }
            true
        }
    }

    /** Высота клавиш растёт вместе с шириной окна. */
    private fun applyKeyHeights(view: View, width: Int) {
        val keyH = ((width - dp(14)) / 4f * 0.66f).toInt().coerceIn(dp(36), dp(64))
        val root = (view as? android.view.ViewGroup)?.getChildAt(0) as? android.view.ViewGroup ?: return
        for (i in 0 until root.childCount) {
            val row = root.getChildAt(i)
            if (row is android.widget.LinearLayout && row.orientation == android.widget.LinearLayout.HORIZONTAL &&
                row.childCount == 4 && row.getChildAt(0).id in KEY_ROW_FIRST_IDS
            ) {
                row.layoutParams = row.layoutParams.apply { height = keyH }
            }
        }
    }

    private fun render() {
        val view = window ?: return
        val expr = view.findViewById<TextView>(R.id.fExpression)
        val result = view.findViewById<TextView>(R.id.fResult)
        val error = calculator.error
        if (calculator.justEvaluated) {
            expr.text = lastExpression
            result.text = "= ${calculator.expression}"
        } else {
            expr.text = calculator.expression
            result.text = calculator.preview()?.let { "= $it" } ?: calculator.expression.ifEmpty { "0" }
        }
        result.setTextColor(ui.themeColor(if (error != null) R.attr.c_error else R.attr.c_text_primary))
        if (error != null) result.text = error
        renderRates(view)
    }

    /** ≈ $ и ≈ € по официальному курсу (последние сохранённые курсы). */
    private fun renderRates(view: View) {
        val usd = view.findViewById<TextView>(R.id.fUsd)
        val eur = view.findViewById<TextView>(R.id.fEur)
        val value = calculator.currentValue()?.replace('−', '-')?.let { runCatching { BigDecimal(it) }.getOrNull() }
        val rates = RatesRepository(this).cached()
        fun chip(tv: TextView, c: Currency, sign: String) {
            val v = if (value != null && rates != null && value.signum() != 0) rates.convert(value, Currency.BYN, c) else null
            tv.visibility = if (v == null) View.GONE else View.VISIBLE
            if (v != null) {
                val text = Money.format(v, c)
                tv.text = "≈ $text $sign"
                tv.setOnClickListener { copy(text) }
            }
        }
        chip(usd, Currency.USD, "$")
        chip(eur, Currency.EUR, "€")
    }

    private fun currentValueText(): String =
        (calculator.currentValue() ?: calculator.expression).replace('−', '-')

    private fun saveToHistory(entry: HistoryEntry) {
        val store = HistoryStore(this)
        val history = History().apply { replaceAll(store.load()) }
        if (history.add(entry)) store.save(history.entries)
    }

    // ---------- Буфер обмена ----------

    private fun copy(text: String) {
        if (text.isEmpty()) return
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), text))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, getString(R.string.floating_copied, text), Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Android 10+ даёт читать буфер только приложению с фокусом ввода,
     * поэтому на мгновение делаем окно фокусируемым.
     */
    private fun paste() {
        val view = window ?: return
        val params = windowParams ?: return
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        wm.updateViewLayout(view, params)
        view.requestFocus()
        view.postDelayed({
            val clip = runCatching {
                getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
            }.getOrNull()
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            if (window != null) wm.updateViewLayout(view, params)
            val number = clip?.let(::extractNumber)
            if (number == null) {
                Toast.makeText(this, R.string.floating_paste_empty, Toast.LENGTH_SHORT).show()
            } else {
                calculator.insertNumber(Evaluator.format(number).replace('-', '−'))
                render()
            }
        }, 150)
    }

    /** Первое число в тексте: «Сумма: 1 234,56 BYN» → 1234.56. */
    private fun extractNumber(text: String): BigDecimal? {
        val m = Regex("""-?\d[\d\s ]*(?:[.,]\d+)?""").find(text) ?: return null
        val s = m.value.replace(Regex("[\\s\\u00A0]"), "").replace(',', '.')
        return runCatching { BigDecimal(s).round(MathContext.DECIMAL64) }.getOrNull()
    }

    // ---------- Пузырёк ----------

    private fun collapse() {
        val view = window ?: return
        saveState()
        wm.removeView(view)
        window = null
        showBubble()
    }

    private fun expand() {
        bubble?.let { runCatching { wm.removeView(it) } }
        bubble = null
        if (window == null) showWindow()
    }

    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    private fun showBubble() {
        val view = LayoutInflater.from(ui).inflate(R.layout.floating_bubble, null)
        val params = overlayParams(WindowManager.LayoutParams.WRAP_CONTENT).apply {
            x = screenW
            y = prefs().getInt(KEY_BUBBLE_Y, screenH / 3)
        }
        bubble = view
        bubbleParams = params
        updatePeek(view, atRight = true)
        wm.addView(view, params)
        view.post { snapBubble(view, params, toRight = true) }

        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var dragging = false
        view.findViewById<View>(R.id.bBubble).setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = e.rawX
                    touchY = e.rawY
                    dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - touchX
                    val dy = e.rawY - touchY
                    if (!dragging && hypot(dx, dy) > dp(8)) {
                        dragging = true
                        showTrash()
                        updatePeek(view, atRight = null)
                    }
                    if (dragging) {
                        params.x = (startX + dx).toInt()
                        params.y = (startY + dy).toInt().coerceAtLeast(0)
                        wm.updateViewLayout(view, params)
                        highlightTrash(e.rawX, e.rawY)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!dragging) {
                        if (e.actionMasked == MotionEvent.ACTION_UP) expand()
                    } else {
                        val drop = isOverTrash(e.rawX, e.rawY)
                        hideTrash()
                        if (drop) {
                            stopSelf()
                        } else {
                            // Прилипает к ближайшему краю.
                            val toRight = e.rawX > screenW / 2
                            snapBubble(view, params, toRight)
                        }
                    }
                }
            }
            true
        }
    }

    private fun snapBubble(view: View, params: WindowManager.LayoutParams, toRight: Boolean) {
        updatePeek(view, toRight)
        view.post {
            params.x = if (toRight) screenW - view.width + dp(10) else -dp(10)
            if (bubble != null) wm.updateViewLayout(view, params)
            prefs().edit().putInt(KEY_BUBBLE_Y, params.y).apply()
        }
    }

    /** Подсказка с последним результатом — со стороны экрана, а не за краем. */
    private fun updatePeek(view: View, atRight: Boolean?) {
        val text = calculator.currentValue()?.let { "= $it" }
        val left = view.findViewById<TextView>(R.id.bPeekLeft)
        val right = view.findViewById<TextView>(R.id.bPeekRight)
        left.text = text
        right.text = text
        left.visibility = if (text != null && atRight == true) View.VISIBLE else View.GONE
        right.visibility = if (text != null && atRight == false) View.VISIBLE else View.GONE
    }

    // ---------- Зона «закрыть» ----------

    @SuppressLint("InflateParams")
    private fun showTrash() {
        if (trash != null) return
        val view = LayoutInflater.from(ui).inflate(R.layout.floating_trash, null)
        val params = WindowManager.LayoutParams(
            dp(64), dp(64),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(48)
        }
        trash = view
        wm.addView(view, params)
    }

    private fun trashCenter(): Pair<Float, Float> = screenW / 2f to (screenH - dp(48) - dp(32)).toFloat()

    private fun isOverTrash(x: Float, y: Float): Boolean {
        val (cx, cy) = trashCenter()
        return abs(x - cx) < dp(70) && abs(y - cy) < dp(70)
    }

    private fun highlightTrash(x: Float, y: Float) {
        val t = trash ?: return
        val scale = if (isOverTrash(x, y)) 1.25f else 1f
        t.scaleX = scale
        t.scaleY = scale
    }

    private fun hideTrash() {
        trash?.let { runCatching { wm.removeView(it) } }
        trash = null
    }

    // ---------- Прочее ----------

    private fun openApp() {
        val value = calculator.currentValue()
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (value != null) intent.putExtra(EXTRA_INSERT_NUMBER, value.replace('−', '-'))
        startActivity(intent)
        stopSelf()
    }

    private fun prefs() = getSharedPreferences("floating", MODE_PRIVATE)

    private fun saveState() {
        val e = prefs().edit()
            .putString(KEY_EXPR, calculator.expression)
            .putBoolean(KEY_EVALUATED, calculator.justEvaluated)
            .putString(KEY_LAST, lastExpression)
        windowParams?.let { if (window != null) e.putInt(KEY_X, it.x).putInt(KEY_Y, it.y).putInt(KEY_WIDTH, it.width) }
        e.apply()
    }

    companion object {
        const val ACTION_STOP = "com.example.calculator.floating.STOP"
        const val ACTION_EXPAND = "com.example.calculator.floating.EXPAND"
        private const val CHANNEL = "floating"
        private const val NOTIFICATION_ID = 7
        private const val MIN_WIDTH_DP = 220
        private const val KEY_X = "x"
        private const val KEY_Y = "y"
        private const val KEY_WIDTH = "w"
        private const val KEY_BUBBLE_Y = "by"
        private const val KEY_EXPR = "expr"
        private const val KEY_EVALUATED = "evaluated"
        private const val KEY_LAST = "last"
        private const val KEY_TRANSLUCENT = "translucent"
        private val KEY_ROW_FIRST_IDS = setOf(R.id.fC, R.id.f7, R.id.f4, R.id.f1, R.id.fBack)

        @Volatile
        var running = false
            private set

        /** Запуск с проверкой разрешения; без него открывает настройки. Вызывать с экрана приложения. */
        fun start(context: Context): Boolean {
            if (!Settings.canDrawOverlays(context)) return false
            val intent = Intent(context, FloatingCalculatorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
            return true
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingCalculatorService::class.java))
        }

        fun permissionIntent(context: Context) = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            android.net.Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

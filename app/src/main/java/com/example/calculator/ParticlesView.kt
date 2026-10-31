package com.example.calculator

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import kotlin.random.Random

/**
 * Праздничная анимация: падающие эмодзи (снежинки, сердечки, листья…).
 * Работает несколько секунд и останавливается, чтобы не тратить батарею.
 */
class ParticlesView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private class P(var x: Float, var y: Float, val vy: Float, val drift: Float, val size: Float,
                    var rot: Float, val vr: Float, val emoji: String, val phase: Float)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val particles = ArrayList<P>()
    private var emoji: List<String> = emptyList()
    private var spawnUntil = 0L
    private var lastFrame = 0L
    private var running = false

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun start(emoji: List<String>, durationMs: Long = 9000) {
        if (emoji.isEmpty()) return
        // Учитываем системную настройку «Отключить анимацию».
        val scale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        if (scale == 0f) return
        this.emoji = emoji
        spawnUntil = System.currentTimeMillis() + durationMs
        lastFrame = 0
        running = true
        postInvalidateOnAnimation()
    }

    fun stop() {
        running = false
        particles.clear()
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stop()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!running || width == 0) return
        val now = System.currentTimeMillis()
        val dt = if (lastFrame == 0L) 0.016f else ((now - lastFrame) / 1000f).coerceAtMost(0.05f)
        lastFrame = now
        val density = resources.displayMetrics.density

        if (now < spawnUntil && particles.size < 22 && Random.nextFloat() < 0.18f) {
            particles += P(
                x = Random.nextFloat() * width,
                y = -40 * density,
                vy = (45 + Random.nextFloat() * 70) * density,
                drift = (8 + Random.nextFloat() * 18) * density,
                size = (14 + Random.nextFloat() * 14) * density,
                rot = Random.nextFloat() * 360,
                vr = Random.nextFloat() * 60 - 30,
                emoji = emoji[Random.nextInt(emoji.size)],
                phase = Random.nextFloat() * 6.28f,
            )
        }

        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.y += p.vy * dt
            p.rot += p.vr * dt
            val x = p.x + kotlin.math.sin(p.y / (60 * density) + p.phase) * p.drift
            if (p.y > height + 40 * density) {
                it.remove()
                continue
            }
            paint.textSize = p.size
            // Ближе к низу — прозрачнее, чтобы не мешать кнопкам.
            paint.alpha = (255 * (1f - (p.y / height).coerceIn(0f, 1f) * 0.6f)).toInt()
            canvas.save()
            canvas.rotate(p.rot, x, p.y)
            canvas.drawText(p.emoji, x, p.y, paint)
            canvas.restore()
        }

        if (now < spawnUntil || particles.isNotEmpty()) postInvalidateOnAnimation() else running = false
    }
}

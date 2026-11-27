package io.github.shhrrtnvr.bravialauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.PI
import kotlin.math.sin

/**
 * Falling petals or snow, drawn at ~30 fps. Only runs while home is on screen
 * ([start]/[stop] from the activity), so it costs nothing while an app is open.
 */
class ParticlesView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private class P(val x: Float, val size: Float, val alpha: Float, val delay: Float, val dur: Float)

    private var theme: Theme = Theme.UME
    private var items: List<P> = emptyList()
    private var running = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG)
    private val oval = RectF()

    fun setTheme(t: Theme) {
        theme = t
        val n = when (t) { Theme.SAKURA -> 22; Theme.YUKI -> 70; Theme.UME -> 10 }
        items = List(n) { i ->
            when (t) {
                // Same spacing formulas as the mockups.
                Theme.SAKURA -> P(((10 + (i * 41.3) % 95) / 100).toFloat(), 1f, 1f, ((i * 0.9) % 11).toFloat(), (9 + (i % 4) * 1.6).toFloat())
                Theme.YUKI -> P((((i * 29.3) % 100) / 100).toFloat(), (3 + i % 5).toFloat(), (0.6 + (i % 4) * 0.1).toFloat(), ((i * 0.47) % 12).toFloat(), (8 + (i % 6) * 1.2).toFloat())
                Theme.UME -> P(((45 + (i * 23.7) % 55) / 100).toFloat(), 1f, 1f, ((i * 1.3) % 13).toFloat(), (11 + (i % 4) * 1.5).toFloat())
            }
        }
        invalidate()
    }

    /** Seconds the animation is frozen at (see [pause]), or null while it runs or is stopped. */
    private var frozenAt: Float? = null

    fun start() { frozenAt = null; if (!running) { running = true; invalidate() } }
    fun stop() { running = false; frozenAt = null }

    /** Keep the petals/snow on screen where they are, but stop redrawing. */
    fun pause() {
        if (!running) return
        frozenAt = SystemClock.uptimeMillis() / 1000f
        running = false
    }

    override fun onDraw(canvas: Canvas) {
        if ((!running && frozenAt == null) || items.isEmpty()) return
        val d = resources.displayMetrics.density
        val sx = width / 960f
        val sy = height / 540f
        val t = frozenAt ?: (SystemClock.uptimeMillis() / 1000f)
        for (p in items) {
            val f = (((t + p.dur - p.delay) % p.dur) / p.dur)
            val baseX = p.x * width
            when (theme) {
                Theme.SAKURA -> {
                    val x = baseX - 160 * sx * f
                    val y = (-20 + 600 * f) * sy
                    paint.color = 0xEFAFBE or (fade(f, 0.95f, 0.2f) shl 24)
                    canvas.save()
                    canvas.rotate(520 * f, x, y)
                    oval.set(x - 5 * d, y - 3.5f * d, x + 5 * d, y + 3.5f * d)
                    canvas.drawOval(oval, paint)
                    canvas.restore()
                }
                Theme.YUKI -> {
                    val sway = sin(f * 2 * PI).toFloat() * 14 * sx
                    val x = baseX + sway
                    val y = (-20 + 600 * f) * sy
                    val a = (fade(f, 1f, 0.6f) * p.alpha).toInt()
                    val r = p.size / 2 * d
                    outline.color = 0x8DA2BC or ((a * 0.55f).toInt() shl 24)
                    canvas.drawCircle(x, y, r + 0.8f * d, outline)
                    paint.color = 0xFFFFFF or (a shl 24)
                    canvas.drawCircle(x, y, r, paint)
                }
                Theme.UME -> {
                    val x = baseX - 120 * sx * f
                    val y = (-10 + 570 * f) * sy
                    paint.color = 0xC8392D or (fade(f, 0.9f, 0.1f) shl 24)
                    canvas.drawCircle(x, y, 3.5f * d, paint)
                }
            }
        }
        if (running) postInvalidateDelayed(33)
    }

    /** Fade in over the first 8% of the fall, then ease from [peak] to [end] opacity. */
    private fun fade(f: Float, peak: Float, end: Float): Int {
        val a = if (f < 0.08f) peak * f / 0.08f else peak + (end - peak) * (f - 0.08f) / 0.92f
        return (a.coerceIn(0f, 1f) * 255).toInt()
    }
}

package io.github.shhrrtnvr.bravialauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout

/**
 * The frosted backdrop shared by all keys: a small, box-blurred copy of the scene.
 * Keys draw their own patch of it scaled up, which looks like live backdrop blur but
 * costs nothing per frame (the scene behind is static).
 */
class GlassSource(scene: Bitmap, val root: View) {
    private val factor = 8
    val blurred: Bitmap = blur(Bitmap.createScaledBitmap(scene, (scene.width / factor).coerceAtLeast(1), (scene.height / factor).coerceAtLeast(1), true))
    private val scaleX = scene.width.toFloat() / blurred.width
    private val scaleY = scene.height.toFloat() / blurred.height
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val matrix = Matrix()
    private val src = RectF()
    private val dst = RectF()

    /** Draw the blurred scene behind [view] into its own canvas (0,0 = view's top-left). */
    fun draw(canvas: Canvas, view: View) {
        var x = 0f
        var y = 0f
        var v: View = view
        while (v !== root) {
            val parent = v.parent as? View ?: break
            x += v.left - parent.scrollX
            y += v.top - parent.scrollY
            v = parent
        }
        src.set(x / scaleX, y / scaleY, (x + view.width) / scaleX, (y + view.height) / scaleY)
        dst.set(0f, 0f, view.width.toFloat(), view.height.toFloat())
        matrix.setRectToRect(src, dst, Matrix.ScaleToFit.FILL)
        canvas.drawBitmap(blurred, matrix, paint)
    }

    private fun blur(b: Bitmap): Bitmap {
        val w = b.width
        val h = b.height
        val px = IntArray(w * h)
        b.getPixels(px, 0, w, 0, 0, w, h)
        repeat(3) {
            boxPass(px, w, h, 4, horizontal = true)
            boxPass(px, w, h, 4, horizontal = false)
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun boxPass(px: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val out = IntArray(len)
        for (line in 0 until lines) {
            fun at(i: Int): Int {
                val k = i.coerceIn(0, len - 1)
                return if (horizontal) px[line * w + k] else px[k * w + line]
            }
            var rs = 0; var gs = 0; var bs = 0
            for (i in -r..r) { val c = at(i); rs += (c shr 16) and 255; gs += (c shr 8) and 255; bs += c and 255 }
            val n = 2 * r + 1
            for (i in 0 until len) {
                out[i] = Color.rgb(rs / n, gs / n, bs / n)
                val add = at(i + r + 1); val sub = at(i - r)
                rs += ((add shr 16) and 255) - ((sub shr 16) and 255)
                gs += ((add shr 8) and 255) - ((sub shr 8) and 255)
                bs += (add and 255) - (sub and 255)
            }
            for (i in 0 until len) {
                if (horizontal) px[line * w + i] = out[i] else px[i * w + line] = out[i]
            }
        }
    }
}

/**
 * A keycap made of frosted glass. Focus (D-pad) makes it grow, lift on a deep soft shadow and
 * catch a diagonal sheen, Apple TV style, with a thin ring in the theme's accent colour.
 */
class GlassKey @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    var glass: GlassSource? = null
        set(value) { field = value; invalidate() }
    /** Modifier keys get the theme's tinted face. */
    var modifier = false
        set(value) { field = value; invalidate() }
    /** Non-focusable panels (the clock bar) are a little more see-through. */
    var panel = false
        set(value) { field = value; invalidate() }
    var theme: Theme = Theme.UME
        set(value) { field = value; invalidate() }
    var radiusDp = 18f
        set(value) { field = value; invalidate(); invalidateOutline() }
    /** Show the focused look while a child (e.g. the search field) has focus. */
    var lit = false
        set(value) { if (field != value) { field = value; animateFocus(value); invalidate() } }
    /** Selected option in a group of keys (e.g. the current sort): small accent bar. */
    var marked = false
        set(value) { field = value; invalidate() }

    private val looksFocused get() = isFocused || lit

    private val d = resources.displayMetrics.density
    private val rect = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private var sheen: LinearGradient? = null

    init {
        setWillNotDraw(false)
        clipToPadding = false
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radiusDp * d)
                outline.alpha = 0.5f
            }
        }
        // Rounded corners via the outline: far cheaper per frame than clipPath.
        clipToOutline = true
        elevation = 2 * d
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        sheen = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(Color.argb(247, 255, 255, 255), Color.argb(158, 255, 255, 255), Color.argb(217, 255, 255, 255)),
            floatArrayOf(0f, .44f, 1f), Shader.TileMode.CLAMP,
        )
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        animateFocus(gainFocus)
        invalidate()
    }

    private fun animateFocus(on: Boolean) {
        // Wide keys (the search box) only lift; growing 6% would push into neighbours.
        val s = if (on) (if (width > 400 * d) 1.02f else 1.06f) else 1f
        animate().scaleX(s).scaleY(s).translationY(if (on) -4 * d else 0f).translationZ(if (on) 14 * d else 0f)
            .setDuration(160).start()
    }

    override fun onDraw(canvas: Canvas) {
        val r = radiusDp * d
        rect.set(0f, 0f, width.toFloat(), height.toFloat())

        glass?.draw(canvas, this) ?: canvas.drawColor(Color.argb(90, 255, 255, 255))
        when {
            looksFocused -> { fill.shader = sheen; fill.color = Color.WHITE }
            panel -> { fill.shader = null; fill.color = Color.argb(107, 255, 255, 255) }
            modifier -> { fill.shader = null; fill.color = theme.modFace }
            else -> { fill.shader = null; fill.color = Color.argb(153, 255, 255, 255) }
        }
        canvas.drawRect(rect, fill)
        fill.shader = null
        // Keycap lip along the bottom and a bright top edge, like light on glass.
        fill.color = Color.argb(9, 0, 0, 0)
        canvas.drawRect(0f, height - 4 * d, width.toFloat(), height.toFloat(), fill)
        fill.color = Color.argb(230, 255, 255, 255)
        canvas.drawRect(0f, 0f, width.toFloat(), 1 * d, fill)
        if (marked) {
            fill.color = theme.accent
            val w = 22 * d
            val y = height - 10 * d
            rect.set(width / 2f - w / 2, y, width / 2f + w / 2, y + 3 * d)
            canvas.drawRoundRect(rect, 1.5f * d, 1.5f * d, fill)
            rect.set(0f, 0f, width.toFloat(), height.toFloat())
        }

        val inset = 0.5f * d
        rect.inset(inset, inset)
        edge.strokeWidth = 1 * d
        edge.color = Color.argb(204, 255, 255, 255)
        canvas.drawRoundRect(rect, r - inset, r - inset, edge)
        if (looksFocused) {
            rect.inset(0.5f * d, 0.5f * d)
            edge.strokeWidth = 1.5f * d
            edge.color = theme.accent
            canvas.drawRoundRect(rect, r - d, r - d, edge)
        }
    }

    companion object {
        /** Redraw every key under [group] (e.g. after scrolling, so the glass matches what's behind). */
        fun invalidateAll(group: ViewGroup) {
            for (i in 0 until group.childCount) {
                val child = group.getChildAt(i)
                if (child is GlassKey) child.invalidate()
                if (child is ViewGroup) invalidateAll(child)
            }
        }
    }
}

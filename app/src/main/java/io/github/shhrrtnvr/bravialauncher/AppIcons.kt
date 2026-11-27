package io.github.shhrrtnvr.bravialauncher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import androidx.core.graphics.PathParser

/**
 * Our own coherent icon set (style C, design/mockups "App icons"): a soft brand-colour tint
 * with a simple symbol or letter in the brand colour. Not the companies' logos — just
 * their colour plus a generic shape, so kids still spot "the red one with the play button".
 */
object AppIcons {

    /** Generic symbols, 24×24 viewBox. `line` ones are stroked, others filled (even-odd). */
    class Glyph(val path: String, val line: Boolean)

    private val PLAY = Glyph("M9 6.5v11l9-5.5z", line = false)
    private val DIAMOND = Glyph("M12 2.5 21.5 12 12 21.5 2.5 12z M10 8.5v7l5.5-3.5z", line = false)
    private val TV = Glyph("M4 5.5h16a1.5 1.5 0 0 1 1.5 1.5v9a1.5 1.5 0 0 1-1.5 1.5H4A1.5 1.5 0 0 1 2.5 16V7A1.5 1.5 0 0 1 4 5.5z M8 20.5h8", line = true)
    private val DOWN = Glyph("M12 4v11 M7 10.5l5 5 5-5 M5.5 19.5h13", line = true)
    private val RHOMBUS = Glyph("M12 3 20 11 12 19 4 11z", line = false)
    private val CHEVRON = Glyph("M9.5 5.5 16 12l-6.5 6.5", line = true)
    val GEAR = Glyph("M12 8.5a3.5 3.5 0 1 0 0 7 3.5 3.5 0 0 0 0-7z M12 2.5v3 M12 18.5v3 M2.5 12h3 M18.5 12h3 M5.3 5.3l2.1 2.1 M16.6 16.6l2.1 2.1 M5.3 18.7l2.1-2.1 M16.6 7.4l2.1-2.1", line = true)
    val INPUT = Glyph("M3.5 7V5.5A1.5 1.5 0 0 1 5 4h14a1.5 1.5 0 0 1 1.5 1.5v13A1.5 1.5 0 0 1 19 20H5a1.5 1.5 0 0 1-1.5-1.5V17 M2 12h11 M9.5 8.5 13 12l-3.5 3.5", line = true)
    val GRID = Glyph("M5 5h4v4H5z M15 5h4v4h-4z M5 15h4v4H5z M15 15h4v4h-4z", line = true)
    val SEARCH = Glyph("M10.5 4a6.5 6.5 0 1 0 0 13 6.5 6.5 0 0 0 0-13z M15.3 15.3 20 20", line = true)
    /** A sprout (Puzzle Sprouts). */
    private val SPROUT = Glyph("M11 11.5h2V21h-2z M12 13C11.2 8.4 7.6 6.2 3.8 6.9c.9 4 4.2 6.4 8.2 6.1z M12 11.2c.9-4.8 4.4-7.2 8.3-6.5-.8 4.2-4.2 6.8-8.3 6.5z", line = false)
    /** A generic game controller (for the Xbox on HDMI 4). */
    private val GAMEPAD = Glyph("M7 7.5h10a4 4 0 0 1 3.9 3.1l1 4.6a2.3 2.3 0 0 1-3.9 2L16 15.5H8l-2 1.7a2.3 2.3 0 0 1-3.9-2l1-4.6A4 4 0 0 1 7 7.5z M8 10v3 M6.5 11.5h3 M15.5 11h.01 M17.5 13h.01", line = true)

    /** Xbox key: Xbox green, game controller. */
    fun xbox(): Drawable = GlyphIcon(c("#107C10"), GAMEPAD.path, GAMEPAD.line, null)

    private class Spec(val colour: Int, val glyph: Glyph? = null, val letter: String? = null)

    private fun c(hex: String) = Color.parseColor(hex)

    /** Hand-picked: the apps the family uses. Everything else gets an automatic letter icon. */
    private val specs = mapOf(
        "com.google.android.youtube.tv" to Spec(c("#E53935"), PLAY),
        "com.stremio.one" to Spec(c("#6B4DE6"), DIAMOND),
        "com.sony.dtv.tvx" to Spec(c("#3B7DD8"), TV),
        "com.viewlift.hoichoi" to Spec(c("#C2185B"), letter = "h"),
        "com.netflix.ninja" to Spec(c("#E50914"), letter = "N"),
        "com.amazon.amazonvideo.livingroom" to Spec(c("#1A98FF"), letter = "p"),
        "com.nvidia.bbciplayer" to Spec(c("#F54997"), letter = "i"),
        "air.ITVMobilePlayer" to Spec(c("#7E9A00"), letter = "X"),
        "com.channel4.ondemand" to Spec(c("#2E8B57"), letter = "4"),
        "com.esaba.downloader" to Spec(c("#F37021"), DOWN),
        "org.xbmc.kodi" to Spec(c("#17B2E7"), RHOMBUS),
        "com.spotify.tv.android" to Spec(c("#1DB954"), letter = "S"),
        "com.plexapp.android" to Spec(c("#D18F00"), CHEVRON),
        "com.google.android.youtube.tvmusic" to Spec(c("#E53935"), letter = "♪"),
        "org.smarttube.stable" to Spec(c("#D32F2F"), PLAY),
        "io.github.shhrrtnvr.sprouts" to Spec(c("#3F9B4A"), SPROUT),
    )

    fun hasOwnIcon(packageName: String) = packageName in specs

    /** Icon for an app: hand-picked spec, else first letter in a colour taken from its real icon. */
    fun forApp(packageName: String, label: String, brand: Int): Drawable {
        if (packageName == Apps.XBOX) return xbox()
        val spec = specs[packageName]
        if (spec != null) return GlyphIcon(spec.colour, spec.glyph?.path, spec.glyph?.line ?: false, spec.letter)
        val letter = label.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "•"
        return GlyphIcon(brand, null, false, letter)
    }

    /** Neutral symbol (Settings, Inputs, All apps) in the theme's ink; [GlyphIcon.colour] follows the theme. */
    fun symbol(glyph: Glyph, ink: Int): GlyphIcon = GlyphIcon(ink, glyph.path, glyph.line, null, neutral = true)

    /**
     * The colour to use for an app without a hand-picked icon: the most vivid hue in its real
     * icon, darkened enough to read on the white glass. Greyish icons get a neutral ink.
     */
    fun brandColour(icon: Drawable?): Int {
        icon ?: return NEUTRAL
        val size = 32
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        try {
            icon.setBounds(0, 0, size, size)
            icon.draw(Canvas(bmp))
        } catch (e: Exception) {
            return NEUTRAL
        }
        val px = IntArray(size * size)
        bmp.getPixels(px, 0, size, 0, 0, size, size)
        bmp.recycle()
        // Weight hues (12 bins) by saturation × value; take the heaviest bin's average colour.
        val weight = FloatArray(12)
        val sumH = FloatArray(12); val sumS = FloatArray(12); val sumV = FloatArray(12)
        val hsv = FloatArray(3)
        for (p in px) {
            if (Color.alpha(p) < 200) continue
            Color.colorToHSV(p, hsv)
            val w = hsv[1] * hsv[2]
            if (hsv[1] < 0.25f || hsv[2] < 0.2f) continue
            val bin = ((hsv[0] / 30f).toInt()).coerceIn(0, 11)
            weight[bin] += w; sumH[bin] += hsv[0] * w; sumS[bin] += hsv[1] * w; sumV[bin] += hsv[2] * w
        }
        val best = weight.indices.maxByOrNull { weight[it] } ?: return NEUTRAL
        if (weight[best] < 8f) return NEUTRAL
        val w = weight[best]
        val h = sumH[best] / w
        val s = (sumS[best] / w).coerceAtLeast(0.55f)
        val v = (sumV[best] / w).coerceAtMost(0.72f)
        return Color.HSVToColor(floatArrayOf(h, s, v))
    }

    private val NEUTRAL = Color.parseColor("#6B6660")
}

/**
 * Style C icon: rounded square tinted with [colour] at 16%, glyph or letter in [colour].
 * Neutral icons (Settings, Inputs, All apps) use a grey tint and ink-coloured glyph.
 */
class GlyphIcon(
    colour: Int,
    pathData: String?,
    private val line: Boolean,
    private val letter: String?,
    val neutral: Boolean = false,
) : Drawable() {

    var colour: Int = colour
        set(value) { field = value; invalidateSelf() }

    private val glyph: Path? = pathData?.let { PathParser.createPathFromPathData(it) }?.apply { fillType = Path.FillType.EVEN_ODD }
    private val scaled = Path()
    private val matrix = Matrix()
    private val rect = RectF()
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val s = minOf(b.width(), b.height()).toFloat()
        rect.set(b.exactCenterX() - s / 2, b.exactCenterY() - s / 2, b.exactCenterX() + s / 2, b.exactCenterY() + s / 2)
        bg.color = if (neutral) Color.argb(31, 107, 102, 96) else (colour and 0x00FFFFFF) or (41 shl 24)
        canvas.drawRoundRect(rect, s * 0.3f, s * 0.3f, bg)

        fg.color = colour
        if (glyph != null) {
            // 24-unit glyph drawn at 64% of the icon, centred.
            val g = s * 0.64f / 24f
            matrix.setScale(g, g)
            matrix.postTranslate(rect.centerX() - 12 * g, rect.centerY() - 12 * g)
            glyph.transform(matrix, scaled)
            if (line) {
                fg.style = Paint.Style.STROKE
                fg.strokeWidth = 2.1f * g
            } else {
                fg.style = Paint.Style.FILL
            }
            canvas.drawPath(scaled, fg)
        } else if (letter != null) {
            fg.style = Paint.Style.FILL
            fg.textSize = s * 0.55f
            val y = rect.centerY() - (fg.descent() + fg.ascent()) / 2
            canvas.drawText(letter, rect.centerX(), y, fg)
        }
    }

    override fun setAlpha(alpha: Int) { fg.alpha = alpha; bg.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { fg.colorFilter = colorFilter; bg.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

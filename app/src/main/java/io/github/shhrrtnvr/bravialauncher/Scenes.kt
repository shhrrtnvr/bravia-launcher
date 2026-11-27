package io.github.shhrrtnvr.bravialauncher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader

/**
 * Paints each theme's background scene once into a bitmap. Drawn in the mockups'
 * 960x540 design space and scaled to the screen, so the paths match the designs 1:1.
 */
object Scenes {
    private const val W = 960f
    private const val H = 540f

    fun render(theme: Theme, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        c.scale(width / W, height / H)
        when (theme) {
            Theme.SAKURA -> sakura(c)
            Theme.YUKI -> yuki(c)
            Theme.UME -> ume(c)
        }
        return bitmap
    }

    // ---- Sakura: a cherry tree in full bloom, petals on the ground ----

    private fun sakura(c: Canvas) {
        sky(c, intArrayOf(0xFFFBF3F0.toInt(), 0xFFF7EAE6.toInt(), 0xFFEFE1D9.toInt()), floatArrayOf(0f, .55f, 1f))
        fill(c, "M0 455 C 150 420 300 440 470 430 C 630 420 770 448 960 428 L 960 540 L 0 540 Z", 0xFFEBDAD2.toInt())
        fill(c, "M0 492 C 210 470 420 488 640 478 C 790 472 880 486 960 482 L 960 540 L 0 540 Z", 0xFFE3CFC5.toInt())
        val bark = 0xFF6E524B.toInt()
        stroke(c, "M870 540 C 862 470 842 420 808 360 C 786 322 774 280 744 228", bark, 24f)
        stroke(c, "M796 342 C 724 300 642 252 562 204 C 502 168 442 124 380 92", bark, 11f)
        stroke(c, "M762 262 C 724 202 704 142 692 58", bark, 9f)
        stroke(c, "M748 236 C 804 192 864 152 960 120", bark, 8f)
        stroke(c, "M612 232 C 602 182 582 132 542 82", bark, 5f)
        stroke(c, "M472 146 C 432 130 382 140 322 160", bark, 4f)
        stroke(c, "M834 410 C 882 382 922 372 960 374", bark, 6f)

        val rnd = Lcg(11)
        val centers = arrayOf(
            380 to 92, 440 to 118, 500 to 148, 560 to 188, 622 to 230, 682 to 262, 542 to 82, 592 to 116,
            692 to 62, 704 to 128, 744 to 196, 802 to 160, 862 to 138, 930 to 116, 330 to 158, 902 to 362,
            952 to 378, 764 to 300, 642 to 170, 470 to 96, 820 to 90, 640 to 60,
        )
        val colours = intArrayOf(0xF7CDD5, 0xF1B6C3, 0xFBE0E5, 0xEBA5B5, 0xFFFFFF, 0xF4C0CB)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        for ((cx, cy) in centers) {
            repeat(26) {
                val r = 5 + rnd.next() * 11
                val x = cx + (rnd.next() - .5f) * 110
                val y = cy + (rnd.next() - .5f) * 70
                val rgb = colours[(rnd.next() * colours.size).toInt().coerceAtMost(colours.size - 1)]
                p.color = rgb or ((0.55f + rnd.next() * 0.4f) * 255).toInt().shl(24)
                c.drawCircle(x, y, r, p)
            }
        }
        // Fallen petals on the ground.
        repeat(46) {
            val x = rnd.next() * 950
            val y = 470 + rnd.next() * 64
            p.color = 0xEDB2BF or ((0.45f + rnd.next() * 0.5f) * 255).toInt().shl(24)
            c.save()
            c.rotate(rnd.next() * 180, x, y)
            c.drawOval(RectF(x - 4.5f, y - 2.5f, x + 4.5f, y + 2.5f), p)
            c.restore()
        }
    }

    // ---- Yuki: snowy mountains and snow-laden pines ----

    private fun yuki(c: Canvas) {
        sky(
            c,
            intArrayOf(0xFFC9D7E6.toInt(), 0xFFDCE6F0.toInt(), 0xFFEEF3F8.toInt(), 0xFFF7F9FB.toInt()),
            floatArrayOf(0f, .4f, .7f, 1f),
        )
        fill(c, "M0 330 L 90 270 L 170 300 L 260 240 L 330 280 L 380 262 L 480 150 L 520 140 L 560 152 L 650 250 L 720 230 L 800 280 L 880 250 L 960 290 L 960 540 L 0 540 Z", 0xFFB7C7D8.toInt())
        fill(c, "M430 206 L 480 150 L 520 140 L 560 152 L 606 204 L 588 210 L 570 196 L 554 214 L 536 198 L 518 216 L 500 198 L 484 214 L 466 200 L 450 212 Z", Color.WHITE)
        fill(c, "M90 270 L 170 300 L 130 298 L 110 286 Z M 260 240 L 330 280 L 296 274 L 276 262 Z M 720 230 L 800 280 L 760 268 L 742 254 Z", 0xFFF3F7FB.toInt())
        fill(c, "M0 400 C 120 360 260 380 380 368 C 520 354 640 386 780 370 C 860 362 920 372 960 368 L 960 540 L 0 540 Z", 0xFFE4ECF4.toInt())
        fill(c, "M0 450 C 160 428 330 446 500 438 C 680 430 820 452 960 440 L 960 540 L 0 540 Z", 0xFFF7FAFC.toInt())
        fill(c, "M0 500 C 200 486 420 500 640 492 C 780 488 880 498 960 494 L 960 540 L 0 540 Z", Color.WHITE)
        pine(c, 18f, 356f, 1.25f, 0xFF4F6674.toInt(), trunk = true)
        pine(c, 110f, 392f, 0.9f, 0xFF5A7180.toInt(), trunk = true)
        pine(c, 172f, 418f, 0.65f, 0xFF6A8090.toInt(), trunk = false)
        pine(c, 840f, 340f, 1.4f, 0xFF4F6674.toInt(), trunk = true)
        pine(c, 770f, 384f, 1f, 0xFF5A7180.toInt(), trunk = true)
        pine(c, 712f, 420f, 0.62f, 0xFF6A8090.toInt(), trunk = false)
    }

    private fun pine(c: Canvas, x: Float, y: Float, s: Float, colour: Int, trunk: Boolean) {
        c.save()
        c.translate(x, y)
        c.scale(s, s)
        fill(c, "M35 0 L 55 40 L 15 40 Z M 35 25 L 62 72 L 8 72 Z M 35 55 L 70 110 L 0 110 Z", colour)
        fill(c, "M35 0 L 44 18 L 26 18 Z M 35 25 L 47 46 L 23 46 Z M 35 55 L 50 80 L 20 80 Z", Color.WHITE)
        if (trunk) fill(c, "M31 108 L 39 108 L 39 120 L 31 120 Z", 0xFF5C4A42.toInt())
        c.restore()
    }

    // ---- Ume: ink-wash mountains in mist, red sun over water, a plum branch ----

    private fun ume(c: Canvas) {
        val paper = 0xFFEEEAE1.toInt()
        sky(c, intArrayOf(0xFFF4F1EA.toInt(), paper, 0xFFE6E1D6.toInt()), floatArrayOf(0f, .6f, 1f))
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0xD9C8392D.toInt()
        c.drawCircle(170f, 430f, 44f, p)
        fill(c, "M0 330 C 60 260 110 250 160 280 C 210 220 260 170 320 210 C 370 240 400 230 440 260 C 500 200 560 180 610 230 C 660 270 700 250 760 210 C 820 180 880 220 960 200 L 960 540 L 0 540 Z", 0x73A9A49B)
        mist(c, 330f, 400f, paper)
        fill(c, "M0 400 C 80 340 150 350 210 380 C 280 330 350 310 420 350 C 480 380 540 360 600 330 C 660 300 740 330 800 370 C 860 350 910 340 960 350 L 960 540 L 0 540 Z", 0x806E6961.toInt())
        mist(c, 400f, 470f, paper)
        fill(c, "M0 469 L 960 469 L 960 540 L 0 540 Z", 0xFFEFECE5.toInt())
        stroke(c, "M60 482 C 200 480 340 486 480 482 M 520 494 C 640 492 760 498 900 494 M 120 506 C 260 504 380 510 520 506", 0x808E8880.toInt(), 1.2f)
        fill(c, "M600 482 L 660 482 L 652 490 L 608 490 Z", 0xCC2E2B28.toInt())
        stroke(c, "M628 482 L 628 462 M 628 464 L 642 474", 0xCC2E2B28.toInt(), 1.5f)
        val ink = 0xFF2A2623.toInt()
        stroke(c, "M960 18 C 900 30 850 24 800 44 C 740 66 700 60 640 86 C 590 108 560 104 510 124", ink, 9f)
        stroke(c, "M800 44 C 790 76 770 100 740 120", ink, 5f)
        stroke(c, "M700 64 C 690 40 674 22 650 10", ink, 4f)
        stroke(c, "M600 102 C 592 126 574 146 550 160", ink, 3.5f)
        stroke(c, "M510 124 C 480 132 450 130 420 140", ink, 3f)
        stroke(c, "M880 28 C 890 54 900 70 920 84", ink, 3.5f)

        val rnd = Lcg(5)
        val spots = arrayOf(
            940 to 22, 905 to 28, 870 to 26, 838 to 36, 800 to 46, 770 to 58, 735 to 66, 700 to 66, 668 to 78,
            640 to 88, 610 to 100, 575 to 108, 545 to 118, 512 to 126, 480 to 130, 448 to 134, 425 to 140,
            792 to 78, 770 to 104, 742 to 120, 692 to 44, 668 to 22, 652 to 12, 596 to 126, 574 to 146,
            552 to 160, 886 to 52, 904 to 74, 920 to 84,
        )
        val reds = intArrayOf(0xFFC8392D.toInt(), 0xFFD24A3C.toInt(), 0xFFB52E24.toInt(), 0xFFE06A5A.toInt())
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = 0x40781410 }
        for ((cx, cy) in spots) {
            repeat(1 + (rnd.next() * 2).toInt()) {
                val r = 4 + rnd.next() * 5
                val x = cx + (rnd.next() - .5f) * 22
                val y = cy + (rnd.next() - .5f) * 18
                p.color = reds[(rnd.next() * reds.size).toInt().coerceAtMost(reds.size - 1)]
                c.drawCircle(x, y, r, p)
                c.drawCircle(x, y, r - 1f, ring)
            }
        }
    }

    // ---- helpers ----

    private fun sky(c: Canvas, colours: IntArray, stops: FloatArray) {
        val p = Paint().apply { shader = LinearGradient(0f, 0f, 0f, H, colours, stops, Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, W, H, p)
    }

    private fun mist(c: Canvas, top: Float, bottom: Float, colour: Int) {
        val clear = colour and 0x00FFFFFF
        val p = Paint().apply {
            shader = LinearGradient(0f, top, 0f, bottom, clear, (colour and 0x00FFFFFF) or 0xF2000000.toInt(), Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, top, W, bottom, p)
    }

    private fun fill(c: Canvas, d: String, colour: Int) {
        c.drawPath(SvgPath.parse(d), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colour })
    }

    private fun stroke(c: Canvas, d: String, colour: Int, width: Float) {
        c.drawPath(SvgPath.parse(d), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colour
            style = Paint.Style.STROKE
            strokeWidth = width
            strokeCap = Paint.Cap.ROUND
        })
    }

    /** Same pseudo-random sequence as the mockups (Park–Miller), so blossoms land in the same places. */
    private class Lcg(private var seed: Long) {
        fun next(): Float {
            seed = (seed * 16807) % 2147483647
            return (seed.toDouble() / 2147483647).toFloat()
        }
    }
}

/** Tiny SVG path parser: absolute M, L, C, Z only (all the scenes use). */
object SvgPath {
    private val token = Regex("[MLCZ]|-?\\d*\\.?\\d+")

    fun parse(d: String): Path {
        val path = Path()
        val t = token.findAll(d).map { it.value }.toList()
        var i = 0
        var cmd = 'M'
        fun n() = t[i++].toFloat()
        while (i < t.size) {
            if (t[i][0].isLetter()) {
                cmd = t[i++][0]
                if (cmd == 'Z') { path.close(); continue }
            }
            when (cmd) {
                'M' -> { path.moveTo(n(), n()); cmd = 'L' }
                'L' -> path.lineTo(n(), n())
                'C' -> path.cubicTo(n(), n(), n(), n(), n(), n())
                else -> i++
            }
        }
        return path
    }
}

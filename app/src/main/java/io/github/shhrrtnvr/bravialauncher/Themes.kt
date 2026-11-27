package io.github.shhrrtnvr.bravialauncher

import android.graphics.Color
import java.time.LocalDate

/**
 * Seasonal themes. The keys are always white frosted glass; the theme decides the scene
 * behind them, the particles, the focus accent and the text colours.
 */
enum class Theme(
    val accent: Int,
    val ink: Int,
    val dim: Int,
    /** Face colour of modifier keys (TV, Settings, Inputs, VPN, All apps). */
    val modFace: Int,
) {
    UME(
        accent = Color.parseColor("#C8392D"),
        ink = Color.parseColor("#2B2927"),
        dim = Color.parseColor("#6B6660"),
        modFace = Color.argb(140, 238, 235, 229),
    ),
    SAKURA(
        accent = Color.parseColor("#E2A6AE"),
        ink = Color.parseColor("#2E2A29"),
        dim = Color.parseColor("#6E6260"),
        modFace = Color.argb(140, 250, 238, 236),
    ),
    YUKI(
        accent = Color.parseColor("#8FB0D3"),
        ink = Color.parseColor("#26303B"),
        dim = Color.parseColor("#55606C"),
        modFace = Color.argb(140, 232, 239, 246),
    );

    companion object {
        /** Local seasons: cool months snowy mood, spring blossom, monsoon/autumn ink and mist. */
        fun forDate(date: LocalDate = LocalDate.now()): Theme = when (date.monthValue) {
            12, 1, 2 -> YUKI
            3, 4, 5 -> SAKURA
            else -> UME
        }

        fun byName(name: String?): Theme? = entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}

package io.github.shhrrtnvr.bravialauncher

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * A light glass pop-up with a title and a column of keycap options, matching home.
 * Back closes it. Used for the key menu, the VPN picker and the Inputs picker.
 */
class GlassMenu(context: Context, private val theme: Theme) {

    companion object {
        /** The menu on screen, if any (one at a time). */
        private var open: Dialog? = null

        /** Close whatever menu is open (Home pressed, Home left). */
        fun dismissOpen() {
            open?.dismiss()
            open = null
        }
    }

    /** Called when this menu closes, however it closes. */
    var onDismiss: (() -> Unit)? = null


    class Option(val label: String, val detail: String? = null, val enabled: Boolean = true, val onPick: () -> Unit)

    private val dialog = Dialog(context, android.R.style.Theme_DeviceDefault_Light_Dialog_NoActionBar)
    private val d = context.resources.displayMetrics.density

    /** One row of [showChoices]: a label and mutually exclusive options; [onPick] gets the index. */
    class Row(val label: String, val options: List<String>, val selected: Int, val onPick: (Int) -> Unit)

    /**
     * Rows of choices, like segmented controls: the chosen key in each row is marked. A pick
     * applies at once and the panel stays open (Back closes it), so several rows can change.
     */
    fun showChoices(title: String, rows: List<Row>, footer: String?) {
        val ctx = dialog.context
        val panel = panel(ctx, title)
        var first: GlassKey? = null
        val columns = rows.maxOf { it.options.size }
        rows.forEachIndexed { r, row ->
            val line = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                clipChildren = false
                gravity = Gravity.CENTER_VERTICAL
            }
            line.addView(TextView(ctx).apply {
                text = row.label
                textSize = 15f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(theme.dim)
            }, LinearLayout.LayoutParams((120 * d).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT))
            val keys = row.options.mapIndexed { i, option ->
                GlassKey(ctx).apply {
                    this.theme = this@GlassMenu.theme
                    radiusDp = 14f
                    isFocusable = true
                    isClickable = true
                    contentDescription = "${row.label}: $option"
                    marked = i == row.selected
                    addView(TextView(ctx).apply {
                        text = option
                        textSize = 15f
                        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                        setTextColor(theme.ink)
                    }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
                }
            }
            keys.forEachIndexed { i, key ->
                key.setOnClickListener {
                    keys.forEachIndexed { j, k -> k.marked = j == i }
                    row.onPick(i)
                }
                line.addView(key, LinearLayout.LayoutParams(0, (52 * d).toInt(), 1f).apply {
                    if (i > 0) marginStart = (10 * d).toInt()
                })
            }
            // Keep rows the same width so keys line up in columns.
            repeat(columns - keys.size) {
                line.addView(android.view.View(ctx), LinearLayout.LayoutParams(0, (52 * d).toInt(), 1f).apply { marginStart = (10 * d).toInt() })
            }
            if (r == 0) first = keys.getOrNull(row.selected)
            panel.addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                if (r > 0) topMargin = (12 * d).toInt()
            })
        }
        footer?.let {
            panel.addView(TextView(ctx).apply {
                text = it
                textSize = 13f
                setTextColor(theme.dim)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (18 * d).toInt()
                marginStart = (4 * d).toInt()
            })
        }
        present(panel, 120 + 150 * columns)
        first?.requestFocus()
    }

    private fun panel(ctx: Context, title: String): LinearLayout {
        val panel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            val pad = (22 * d).toInt()
            setPadding(pad, (18 * d).toInt(), pad, pad)
            background = GradientDrawable().apply {
                cornerRadius = 24 * d
                setColor(Color.argb(238, 250, 250, 249))
                setStroke((1 * d).toInt(), Color.WHITE)
            }
            elevation = 24 * d
        }
        panel.addView(TextView(ctx).apply {
            text = title
            textSize = 17f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(theme.ink)
            setPadding((4 * d).toInt(), 0, 0, (14 * d).toInt())
        })
        return panel
    }

    private fun present(panel: LinearLayout, widthDp: Int) {
        val ctx = dialog.context
        // Room for the focused key's grow + shadow.
        val frame = FrameLayout(ctx).apply {
            clipChildren = false
            val m = (24 * d).toInt()
            setPadding(m, m, m, m)
            addView(panel, FrameLayout.LayoutParams((widthDp * d).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        dialog.setContentView(frame)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.3f)
        }
        dialog.setCanceledOnTouchOutside(true)
        open?.dismiss()
        open = dialog
        dialog.setOnDismissListener {
            if (open === dialog) open = null
            onDismiss?.invoke()
        }
        dialog.show()
    }

    fun show(title: String, options: List<Option>, focusIndex: Int = 0, columns: Int = 1) {
        val ctx = dialog.context
        val panel = panel(ctx, title)
        val keys = options.map { option ->
            GlassKey(ctx).apply {
                this.theme = this@GlassMenu.theme
                radiusDp = 14f
                isFocusable = option.enabled
                isClickable = option.enabled
                alpha = if (option.enabled) 1f else 0.45f
                contentDescription = option.label
                setOnClickListener {
                    dialog.dismiss()
                    option.onPick()
                }
                addView(TextView(ctx).apply {
                    text = option.label
                    textSize = 15f
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    setTextColor(theme.ink)
                }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL or Gravity.START).apply {
                    marginStart = (16 * d).toInt()
                })
                option.detail?.let { detail ->
                    addView(TextView(ctx).apply {
                        text = detail
                        textSize = 13f
                        setTextColor(theme.dim)
                    }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL or Gravity.END).apply {
                        marginEnd = (16 * d).toInt()
                    })
                }
            }
        }
        // Lay the keys out in rows of [columns] (long lists like the 8 groups need 2 to fit the screen).
        keys.chunked(columns).forEachIndexed { r, rowKeys ->
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false }
            for (c in 0 until columns) {
                val key = rowKeys.getOrNull(c)
                row.addView(key ?: android.view.View(ctx), LinearLayout.LayoutParams(0, (52 * d).toInt(), 1f).apply {
                    if (c > 0) marginStart = (10 * d).toInt()
                })
            }
            panel.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                if (r > 0) topMargin = (10 * d).toInt()
            })
        }
        present(panel, if (columns > 1) 620 else 460)
        (keys.getOrNull(focusIndex)?.takeIf { it.isFocusable } ?: keys.firstOrNull { it.isFocusable })?.requestFocus()
    }
}

package io.github.shhrrtnvr.bravialauncher

import android.content.Context
import android.util.AttributeSet
import android.view.FocusFinder
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

/**
 * The All apps grid. D-pad Up from the top row must reach the search/sort bar above it, but
 * stock RecyclerView "scrolls the (unfocusable) group title into view" and keeps focus, so
 * once we're at the top we search the whole screen instead.
 */
class AllAppsGrid @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : RecyclerView(context, attrs) {

    override fun focusSearch(focused: View, direction: Int): View? {
        val result = super.focusSearch(focused, direction)
        if (direction == View.FOCUS_UP && (result == null || result === focused || !isInside(result)) && !canScrollVertically(-1)) {
            val root = rootView as? ViewGroup ?: return result
            return FocusFinder.getInstance().findNextFocus(root, focused, direction) ?: result
        }
        return result
    }

    private fun isInside(v: View): Boolean {
        var p: Any? = v.parent
        while (p is View) {
            if (p === this) return true
            p = p.parent
        }
        return false
    }
}

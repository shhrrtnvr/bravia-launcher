package io.github.shhrrtnvr.bravialauncher

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject

/**
 * What the family arranged: favourites in order, and apps hidden from the home screen
 * (still listed under All apps). Uninstalled favourites stay in the list, so they come
 * back in place if reinstalled.
 */
data class HomeLayout(
    val favourites: List<String>,
    val hidden: Set<String>,
    /** Apps moved to another group on the All apps page: package → [AppGroup] name. */
    val groups: Map<String, String> = emptyMap(),
    /** How the All apps page is ordered. */
    val sort: AppSort = AppSort.AZ,
) {
    companion object {
        const val MAX_FAVOURITES = 8
        val DEFAULT = HomeLayout(Apps.DEFAULT_FAVOURITES, emptySet())
    }
}

/**
 * Saved as a small JSON file with [AtomicFile] (write temp, fsync, rename): the TV never
 * shuts down cleanly (every restart is a kernel panic), so a half-written file must be
 * impossible. Anything unreadable means "use the defaults", never a crash.
 */
object LayoutStore {
    private fun file(context: Context) = AtomicFile(Storage.file(context, "layout.json"))

    fun load(context: Context): HomeLayout = try {
        val json = JSONObject(String(file(context).readFully(), Charsets.UTF_8))
        HomeLayout(
            favourites = json.getJSONArray("favourites").let { a -> List(a.length()) { a.getString(it) } }.distinct(),
            hidden = json.optJSONArray("hidden")?.let { a -> List(a.length()) { a.getString(it) } }.orEmpty().toSet(),
            groups = json.optJSONObject("groups")?.let { o -> o.keys().asSequence().associateWith { o.getString(it) } }.orEmpty(),
            sort = AppSort.byName(json.optString("sort")),
        )
    } catch (e: Exception) {
        HomeLayout.DEFAULT
    }

    fun save(context: Context, layout: HomeLayout) {
        val json = JSONObject()
            .put("version", 1)
            .put("favourites", JSONArray(layout.favourites))
            .put("hidden", JSONArray(layout.hidden.sorted()))
            .put("groups", JSONObject(layout.groups.toSortedMap() as Map<*, *>))
            .put("sort", layout.sort.name)
        val f = file(context)
        val out = f.startWrite()
        try {
            out.write(json.toString(2).toByteArray(Charsets.UTF_8))
            f.finishWrite(out) // fsyncs, then renames over the old file
        } catch (e: Exception) {
            f.failWrite(out)
            throw e
        }
    }
}

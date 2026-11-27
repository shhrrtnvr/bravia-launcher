package io.github.shhrrtnvr.bravialauncher

import android.content.Context
import org.json.JSONObject

/**
 * Which apps go through which VPN location (assets/vpn-routes.json, shared with
 * scripts/vpn-configs.py).
 *
 * Three kinds of app: groups with a fixed purpose and a choice of location (UK apps: London
 * or Manchester; YouTube: home, UK or US; US apps: US), and everything else, which is in no
 * tunnel and always uses the home connection.
 *
 * Android runs one VPN at a time, so one WireGuard tunnel = one server. A TV plays one thing
 * at a time, so we pick the tunnel for the app being opened ([tunnelFor]) and otherwise keep
 * the "resting" tunnel ([restingTunnel]: the UK apps' city, plus YouTube if it's set to UK).
 */
class VpnRoutes private constructor(
    val locations: Map<String, String>,
    val groups: List<Group>,
    private val tunnels: Map<String, Tunnel>,
) {
    class Group(val id: String, val title: String, val apps: Set<String>, val choices: List<String>)
    class Tunnel(val name: String, val location: String, val groups: Set<String>)

    fun groupOf(packageName: String): Group? = groups.firstOrNull { packageName in it.apps }

    private val ukGroup: Group get() = groups.first { it.id == UK_GROUP }

    /** A group's chosen location, with "uk" resolved to the UK apps' city. Null = home connection. */
    fun place(group: Group, prefs: Prefs): String? = when (val choice = prefs.choice(group)) {
        HOME -> null
        UK -> prefs.choice(ukGroup)
        else -> choice
    }

    /**
     * The best tunnel when [needed] must be routed as chosen: on its server (or, for the home
     * connection, the UK apps' server) and carrying it or not as chosen; among those, the one
     * that gets the most other groups right. With no [needed] group: the resting tunnel.
     */
    private fun best(needed: Group?, prefs: Prefs): Tunnel? {
        val server = needed?.let { place(it, prefs) } ?: place(ukGroup, prefs)
        return tunnels.values
            .filter { it.location == server }
            .filter { needed == null || (needed.id in it.groups) == (place(needed, prefs) != null) }
            .maxByOrNull { t -> groups.count { g -> (g.id in t.groups) == (place(g, prefs) == t.location) } }
    }

    /** The tunnel to keep up while nothing in particular is being watched (and at boot). */
    fun restingTunnel(prefs: Prefs): String? = best(null, prefs)?.name

    /** The tunnel [packageName] needs when it's opened; null for apps in no group (any tunnel will do). */
    fun tunnelFor(packageName: String, prefs: Prefs): String? =
        groupOf(packageName)?.let { best(it, prefs)?.name }

    /** Does [tunnel] carry [packageName]? */
    fun carries(tunnel: String, packageName: String): Boolean {
        val group = groupOf(packageName) ?: return false
        return tunnels[tunnel]?.groups?.contains(group.id) == true
    }

    /** The saved choice per group (device-protected prefs "vpn_routes"). */
    class Prefs(private val values: Map<String, String>) {
        fun choice(group: Group): String = values[group.id]?.takeIf { it in group.choices } ?: group.choices.first()
        fun with(group: Group, choice: String) = Prefs(values + (group.id to choice))
        val map: Map<String, String> get() = values
    }

    companion object {
        const val HOME = "home"
        const val UK = "uk"
        const val UK_GROUP = "uk"
        private const val PREFS = "vpn_routes"

        @Volatile private var cached: VpnRoutes? = null

        fun get(context: Context): VpnRoutes = cached ?: load(context).also { cached = it }

        private fun load(context: Context): VpnRoutes {
            val json = JSONObject(context.assets.open("vpn-routes.json").bufferedReader().use { it.readText() })
            val locs = json.getJSONObject("locations").let { o -> o.keys().asSequence().associateWith { o.getString(it) } }
            val groups = json.getJSONArray("groups").let { a ->
                (0 until a.length()).map { i ->
                    val g = a.getJSONObject(i)
                    Group(
                        g.getString("id"), g.getString("title"),
                        g.getJSONArray("apps").let { x -> (0 until x.length()).map { x.getString(it) }.toSet() },
                        g.getJSONArray("choices").let { x -> (0 until x.length()).map { x.getString(it) } },
                    )
                }
            }
            val tunnels = json.getJSONObject("tunnels").let { o ->
                o.keys().asSequence().associateWith { name ->
                    val t = o.getJSONObject(name)
                    Tunnel(name, t.getString("location"), t.getJSONArray("groups").let { x -> (0 until x.length()).map { x.getString(it) }.toSet() })
                }
            }
            return VpnRoutes(locs, groups, tunnels)
        }

        fun prefs(context: Context): Prefs {
            val p = Storage.prefs(context, PREFS)
            return Prefs(p.all.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap())
        }

        fun save(context: Context, prefs: Prefs) {
            val edit = Storage.prefs(context, PREFS).edit().clear()
            prefs.map.forEach { (k, v) -> edit.putString(k, v) }
            // commit(), not apply(): the TV loses recent writes when it crash-reboots.
            edit.commit()
        }
    }
}

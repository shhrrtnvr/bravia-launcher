package io.github.shhrrtnvr.bravialauncher

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors

class HomeActivity : Activity() {

    private val io = Executors.newSingleThreadExecutor()
    /** Slow network work (Xbox discovery) kept off [io] so it never delays the scene or app list. */
    private val net = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val d by lazy { resources.displayMetrics.density }

    private lateinit var root: FrameLayout
    private lateinit var sceneView: ImageView
    private lateinit var particles: ParticlesView
    private lateinit var home: LinearLayout
    private lateinit var favouritesBox: LinearLayout
    private lateinit var appsRow: LinearLayout
    private lateinit var allAppsPage: LinearLayout
    private lateinit var allApps: RecyclerView
    private lateinit var searchKey: GlassKey
    private lateinit var searchField: EditText
    private val sortKeys = mutableMapOf<AppSort, GlassKey>()
    private var query = ""
    private lateinit var vpnKey: GlassKey
    private lateinit var vpnState: TextView
    private lateinit var vpnLabel: TextView
    private lateinit var storageNote: TextView

    private var theme: Theme = Theme.forDate()
    /** Set with `am start ... --es theme sakura|yuki|ume` to preview a season. */
    private var themeOverride: Theme? = null
    private var glass: GlassSource? = null
    private var renderedFor: Triple<Theme, Int, Int>? = null

    private var apps: List<App> = emptyList()
    private var layout: HomeLayout = HomeLayout.DEFAULT
    private var favouriteKeys: List<View> = emptyList()
    private var loadedKey: List<String>? = null
    private var firstFavourite: View? = null
    private var allAppsKey: View? = null
    /** A VPN switch waiting for the WireGuard permission dialog. */
    private var pendingVpn: (() -> Unit)? = null
    private val routes by lazy { VpnRoutes.get(this) }
    private val allAdapter = AllAppsAdapter()

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            reloadApps(force = true)
            if (intent.action == Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE) {
                refreshTunnelFor(intent.getStringArrayExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST)?.toList().orEmpty())
            }
        }
    }

    /**
     * Apps on the USB volume appear ~15 s after boot, after always-on VPN has started the
     * tunnel, and WireGuard leaves out apps that weren't installed when a tunnel came up. When
     * one of the running tunnel's apps appears, restart the tunnel once so it's included.
     */
    private fun refreshTunnelFor(packages: List<String>) {
        val tunnel = VpnSwitch.lastTunnel(this) ?: return
        if (!VpnSwitch.hasPermission(this) || VpnSwitch.vpnNetwork(this) == null) return
        if (packages.none { routes.carries(tunnel, it) }) return
        VpnSwitch.restart(this, tunnel)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        Storage.migrateIfNeeded(this)
        layout = LayoutStore.load(this)
        root = findViewById(R.id.root)
        sceneView = findViewById(R.id.scene)
        particles = findViewById(R.id.particles)
        home = findViewById(R.id.home)
        favouritesBox = findViewById(R.id.favourites)
        appsRow = findViewById(R.id.apps_row)
        allAppsPage = findViewById(R.id.all_apps_page)
        allApps = findViewById(R.id.all_apps)
        vpnKey = findViewById(R.id.vpn)
        vpnState = findViewById(R.id.vpn_state)
        vpnLabel = findViewById(R.id.vpn_label)
        storageNote = findViewById(R.id.storage_note)
        findViewById<GlassKey>(R.id.clock_bar).panel = true
        vpnKey.modifier = true
        vpnKey.setOnClickListener { showVpnPanel() }
        // Not focusable until the favourites exist, so it can't grab focus at start-up.
        vpnKey.isFocusable = false
        root.viewTreeObserver.addOnGlobalFocusChangeListener { old, new -> guardVpnFocus(old, new) }

        allApps.layoutManager = GridLayoutManager(this, 6).apply { spanSizeLookup = allAdapter.spans }
        allApps.adapter = allAdapter
        buildAllAppsBar()
        allApps.itemAnimator = null
        // Keys move when the list scrolls; redraw so their glass matches what's behind.
        allApps.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) = GlassKey.invalidateAll(rv)
        })

        themeOverride = Theme.byName(intent.getStringExtra(EXTRA_THEME))
        root.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or_, ob ->
            if (r - l != or_ - ol || b - t != ob - ot) renderScene()
        }

        registerReceiver(packageReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        })
        // Apps on the adopted-storage volume appear late after boot.
        registerReceiver(packageReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE)
            addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE)
        })
        // We can start before the user is unlocked (direct boot); refresh once they are.
        registerReceiver(unlockReceiver, IntentFilter(Intent.ACTION_USER_UNLOCKED))
    }

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Storage.migrateIfNeeded(this@HomeActivity)
            layout = LayoutStore.load(this@HomeActivity)
            reloadApps(force = true)
            updateVpnKey()
        }
    }

    override fun onStart() {
        super.onStart()
        // The season may have changed since we were last on screen.
        renderScene()
        reloadApps(force = false)
        updateVpnKey()
        restTunnel()
        particles.start()
        nudgeIdle()
        // Learn the Xbox's Live ID while it happens to be awake, so the Xbox key can wake it later.
        if (XboxWake.knownLiveId(this) == null) {
            val ctx = applicationContext
            net.execute { XboxWake.discover(ctx) }
        }
    }

    override fun onStop() {
        GlassMenu.dismissOpen()
        main.removeCallbacks(idle)
        particles.stop()
        super.onStop()
    }

    /**
     * Left on Home with nobody pressing anything: stop the falling petals/snow so the TV isn't
     * redrawing at 30 fps for hours. (A screensaver wouldn't protect this panel: it's an LCD,
     * which doesn't burn in. Sony's Idle TV standby is what turns it off.) Any key brings them back.
     */
    private val idle = Runnable { particles.pause() }

    private fun nudgeIdle() {
        main.removeCallbacks(idle)
        main.postDelayed(idle, IDLE_MS)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            particles.start()
            nudgeIdle()
            lastKey = event.keyCode
            lastKeyAt = android.os.SystemClock.uptimeMillis()
        }
        return super.dispatchKeyEvent(event)
    }

    // ---- Focus ----

    private var lastKey = 0
    private var lastKeyAt = 0L
    private var vpnPanelClosedAt = 0L

    /**
     * The VPN card is a status card first: it only takes focus when someone presses Up onto it
     * (or right after its panel closes). Android otherwise hands it focus whenever the focused
     * key disappears (a list rebuild, a menu closing) because it's the first focusable view.
     */
    private fun guardVpnFocus(old: View?, new: View?) {
        if (new !== vpnKey) return
        val now = android.os.SystemClock.uptimeMillis()
        val pressedUp = lastKey == KeyEvent.KEYCODE_DPAD_UP && now - lastKeyAt < 500
        val panelJustClosed = now - vpnPanelClosedAt < 1000
        if (pressedUp || panelJustClosed) return
        val back = old?.takeIf { it !== vpnKey && it.isAttachedToWindow && it.isShown } ?: firstFavourite
        root.post { back?.requestFocus() }
    }

    /** Back from an app: start at the first favourite, not wherever focus was left. */
    override fun onRestart() {
        super.onRestart()
        if (home.visibility == View.VISIBLE) firstFavourite?.requestFocus()
    }

    /**
     * While another app is on screen and Android starts running short, give back the scene
     * (the bulk of our memory). onStart paints it again when Home comes back. The small
     * blurred copy the keys use stays, so the keys never flash.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_BACKGROUND && !hasWindowFocus()) {
            sceneView.setImageDrawable(null)
            renderedFor = null
        }
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        unregisterReceiver(unlockReceiver)
        io.shutdownNow()
        net.shutdownNow()
        super.onDestroy()
    }

    /** Home pressed while already home: back to the first favourite. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        GlassMenu.dismissOpen()
        Theme.byName(intent.getStringExtra(EXTRA_THEME))?.let { themeOverride = it; renderScene() }
        showHome()
        firstFavourite?.requestFocus()
    }

    @Deprecated("Activity.onBackPressed is fine for a plain Activity on API 26-34")
    override fun onBackPressed() {
        if (allAppsPage.visibility == View.VISIBLE) {
            // Back clears a search first, then leaves the page.
            if (query.isNotEmpty()) {
                searchField.setText("")
                searchField.requestFocus()
                return
            }
            showHome()
            allAppsKey?.requestFocus()
            return
        }
        // As the default home there's nothing behind us: ignore Back.
        // If another home is ever set, Back leaves as usual.
        if (!isDefaultHome()) {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    private fun isDefaultHome(): Boolean {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName == packageName
    }

    // ---- Scene and theme ----

    /** Paint the season's scene (off the main thread) whenever the theme or screen size changes. */
    private fun renderScene() {
        val wanted = themeOverride ?: Theme.forDate()
        val w = root.width
        val h = root.height
        if (w == 0 || h == 0) return
        val key = Triple(wanted, w, h)
        if (key == renderedFor) return
        renderedFor = key
        io.execute {
            val painted = Scenes.render(wanted, w, h)
            val source = GlassSource(painted, root)
            // Keep only the GPU copy of the full-screen scene (8 MB at 1080p): a hardware
            // bitmap lives in graphics memory, so we don't also hold the pixels on the heap.
            val bitmap = painted.copy(Bitmap.Config.HARDWARE, false)?.also { painted.recycle() } ?: painted
            main.post {
                if (isDestroyed || renderedFor != key) return@post
                theme = wanted
                sceneView.setImageBitmap(bitmap)
                glass = source
                particles.setTheme(wanted)
                applyTheme(root)
                updateVpnKey()
                allAdapter.notifyDataSetChanged()
            }
        }
    }

    /** Push the theme and glass into every key and text under [group]. */
    private fun applyTheme(group: ViewGroup) {
        if (group is GlassKey) {
            group.theme = theme
            group.glass = glass
        }
        for (i in 0 until group.childCount) {
            when (val child = group.getChildAt(i)) {
                is ViewGroup -> applyTheme(child)
                // Neutral symbols (Settings, Inputs, All apps) follow the season's ink.
                is ImageView -> (child.drawable as? GlyphIcon)?.takeIf { it.neutral }?.colour = theme.ink
                is TextClock -> child.setTextColor(if (child.id == R.id.date) theme.dim else theme.ink)
                is EditText -> { child.setTextColor(theme.ink); child.setHintTextColor(theme.dim) }
                is TextView -> child.setTextColor(if (child.id == R.id.vpn_label || child.id == R.id.storage_note) theme.dim else theme.ink)
            }
        }
    }

    // ---- Apps ----

    private fun reloadApps(force: Boolean) {
        io.execute {
            val all = Apps.load(this)
            val key = all.map { it.component.flattenToShortString() }
            main.post {
                if (isDestroyed || (!force && key == loadedKey)) return@post
                val first = loadedKey == null
                loadedKey = key
                apps = all
                buildHome()
                refreshAllApps()
                if (first) firstFavourite?.requestFocus()
            }
        }
    }

    private fun buildHome() {
        vpnKey.isFocusable = true
        updateStorageNote()
        updateVpnKey()
        val hadFocus = currentFocus
        val favourites = Apps.favourites(apps, layout.favourites)
        favouritesBox.removeAllViews()
        firstFavourite = null
        val favKeys = mutableListOf<View>()
        favourites.chunked(4).forEachIndexed { rowIndex, rowApps ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false }
            for (slot in 0 until 4) {
                val app = rowApps.getOrNull(slot)
                val view: View = if (app != null) {
                    key(app.label, big = true, modifier = app.packageName == LIVE_TV, icon = app.icon(), onLongClick = { showKeyMenu(app, Place.FAVOURITES) }) { launch(app) }
                        .also { favKeys += it }
                } else {
                    View(this) // keep the 4-column grid when a row isn't full
                }
                if (rowIndex == 0 && slot == 0) firstFavourite = view
                row.addView(view, LinearLayout.LayoutParams(0, (96 * d).toInt(), 1f).apply {
                    if (slot > 0) marginStart = (14 * d).toInt()
                })
            }
            favouritesBox.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                if (rowIndex > 0) topMargin = (14 * d).toInt()
            })
        }

        favouriteKeys = favKeys

        // Second row: Settings, Inputs, a fixed shortcut (live TV, or the Xbox if TV is a
        // favourite), two recent (or other) apps, All apps. Hidden apps never show here.
        appsRow.removeAllViews()
        val favouritePackages = favourites.map { it.packageName }.toSet()
        val shortcut = listOf(LIVE_TV, Apps.XBOX).firstOrNull { it !in favouritePackages }
            ?.let { pkg -> apps.firstOrNull { it.packageName == pkg } }
        val others = Recents.order(this, apps.filter {
            it.packageName !in favouritePackages && it.packageName !in layout.hidden && it !== shortcut
        }).take(if (shortcut == null) 3 else 2)
        val rowKeys = buildList {
            add(key(getString(R.string.settings), big = false, modifier = true, icon = AppIcons.symbol(AppIcons.GEAR, theme.ink)) { openSettings() })
            add(key(getString(R.string.inputs), big = false, modifier = true, icon = AppIcons.symbol(AppIcons.INPUT, theme.ink)) { showInputs() })
            shortcut?.let { app -> add(key(app.label, big = false, modifier = false, icon = app.icon(), onLongClick = { showKeyMenu(app, Place.ROW) }) { launch(app) }) }
            others.forEach { app -> add(key(app.label, big = false, modifier = false, icon = app.icon(), onLongClick = { showKeyMenu(app, Place.ROW) }) { launch(app) }) }
            add(key(getString(R.string.all_apps), big = false, modifier = true, icon = AppIcons.symbol(AppIcons.GRID, theme.ink)) { showAllApps() }.also { allAppsKey = it })
        }
        rowKeys.forEachIndexed { i, v ->
            appsRow.addView(v, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                if (i > 0) marginStart = (12 * d).toInt()
            })
        }
        applyTheme(home)
        if (hadFocus != null && hadFocus.parent == null) firstFavourite?.requestFocus()
    }

    /**
     * A glass keycap with one of our icons ([AppIcons]): big keys have the icon on the left
     * and the name beside it, small keys the icon above the name.
     * Holding OK runs [onLongClick] (the key menu).
     */
    private fun key(
        label: String,
        big: Boolean,
        modifier: Boolean,
        icon: Drawable? = null,
        onLongClick: (() -> Unit)? = null,
        onClick: () -> Unit,
    ): GlassKey =
        GlassKey(this).apply {
            isFocusable = true
            isClickable = true
            this.modifier = modifier
            radiusDp = if (big) 18f else 15f
            contentDescription = label
            setOnClickListener { onClick() }
            if (onLongClick != null) {
                setOnLongClickListener { onLongClick(); true }
                // The remote's menu/options button opens the same menu, no holding needed.
                setOnKeyListener { _, keyCode, event ->
                    if (keyCode != KeyEvent.KEYCODE_MENU) return@setOnKeyListener false
                    if (event.action == KeyEvent.ACTION_UP) onLongClick()
                    true
                }
            }
            // Big keys: icon left, name beside it. Small keys: icon above the name.
            // The 4 dp keycap lip at the bottom is balanced with extra bottom padding.
            val content = LinearLayout(context).apply {
                orientation = if (big) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                val side = ((if (big) 20 else 14) * d).toInt()
                setPadding(side, 0, side, (4 * d).toInt())
            }
            val iconSize = ((if (big) 52 else 26) * d).toInt()
            content.addView(ImageView(context).apply {
                tag = TAG_ICON
                scaleType = ImageView.ScaleType.FIT_CENTER
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                setKeyIcon(this, icon)
            }, LinearLayout.LayoutParams(iconSize, iconSize))
            content.addView(TextView(context).apply {
                tag = TAG_LABEL
                text = label
                textSize = if (big) 17f else 14f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                includeFontPadding = false
                setTextColor(theme.ink)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                if (big) marginStart = (16 * d).toInt() else topMargin = (8 * d).toInt()
            })
            addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }

    private fun setKeyIcon(view: ImageView, icon: Drawable?) {
        view.setImageDrawable(icon)
        view.visibility = if (icon != null) View.VISIBLE else View.GONE
    }

    /** Open [app], first switching to the VPN tunnel it needs (see [VpnRoutes]), if any. */
    private fun launch(app: App) {
        val tunnel = routes.tunnelFor(app.packageName, VpnRoutes.prefs(this))
        if (tunnel == null || !VpnSwitch.isWireGuardInstalled(this) || !VpnSwitch.hasPermission(this)) {
            start(app)
        } else {
            switchTunnel(tunnel) { start(app) }
        }
    }

    private fun start(app: App) {
        if (app.packageName == Apps.XBOX) {
            openXbox()
            Recents.note(this, app.packageName)
            return
        }
        try {
            startActivity(app.launchIntent())
            Recents.note(this, app.packageName)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, getString(R.string.cannot_open, app.label), Toast.LENGTH_SHORT).show()
            reloadApps(force = true)
        } catch (e: SecurityException) {
            Toast.makeText(this, getString(R.string.cannot_open, app.label), Toast.LENGTH_SHORT).show()
        }
    }

    // ---- Editing: hold OK on a key ----

    private enum class Place { FAVOURITES, ROW, ALL_APPS }

    private fun showKeyMenu(app: App, place: Place) {
        val favs = Apps.favourites(apps, layout.favourites).map { it.packageName }
        val index = favs.indexOf(app.packageName)
        val isFavourite = index >= 0
        val full = favs.size >= HomeLayout.MAX_FAVOURITES
        val hidden = app.packageName in layout.hidden
        val options = buildList {
            if (isFavourite) {
                add(GlassMenu.Option(getString(R.string.move_left), enabled = index > 0) { moveFavourite(app.packageName, -1) })
                add(GlassMenu.Option(getString(R.string.move_right), enabled = index < favs.size - 1) { moveFavourite(app.packageName, +1) })
                add(GlassMenu.Option(getString(R.string.remove_favourite)) { removeFavourite(app.packageName) })
            } else {
                add(GlassMenu.Option(getString(R.string.add_favourite), detail = if (full) getString(R.string.favourites_full) else null, enabled = !full) {
                    addFavourite(app.packageName, focus = place != Place.ALL_APPS)
                })
                if (place == Place.ALL_APPS && hidden) {
                    add(GlassMenu.Option(getString(R.string.show_on_home)) { setHidden(app.packageName, false) })
                } else {
                    add(GlassMenu.Option(getString(R.string.hide_from_home)) { setHidden(app.packageName, true) })
                }
            }
            if (app.onUsb) {
                add(GlassMenu.Option(getString(R.string.move_to_internal), detail = getString(R.string.on_usb_detail)) { openAppDetails(app.packageName) })
            }
            if (place == Place.ALL_APPS) {
                add(GlassMenu.Option(getString(R.string.move_to_group), detail = AppGroup.of(app, layout.groups).title) { showGroupPicker(app) })
            }
        }
        GlassMenu(this, theme).show(app.label, options)
    }

    // ---- Storage ----

    /**
     * Apps that must stay on internal storage: ourselves (home must start before the USB
     * volume mounts), WireGuard (the tunnel starts at boot), and the favourites (the apps
     * used most; internal storage is faster). Apps in tunnels can live on USB: see
     * [refreshTunnelFor]. The TV has "Force allow apps on external" on
     * so everything else can live on USB, but that also lets an update land any app on USB.
     * We can't move packages ourselves; we point at the app's settings page instead.
     */
    private fun misplacedApps(): List<String> {
        val favourites = Apps.favourites(apps, layout.favourites).filter { it.onUsb }.map { it.label }
        val others = listOf(packageName to getString(R.string.app_name), VpnSwitch.WG_PACKAGE to "WireGuard")
            .filter { (pkg, _) -> Storage.onUsb(this, pkg) }.map { it.second }
        return others + favourites
    }

    private fun updateStorageNote() {
        val misplaced = misplacedApps()
        storageNote.visibility = if (misplaced.isEmpty()) View.GONE else View.VISIBLE
        storageNote.text = getString(R.string.storage_note, misplaced.joinToString(", "))
    }

    private fun openAppDetails(pkg: String) {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", pkg, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Toast.makeText(this, R.string.storage_note_menu, Toast.LENGTH_LONG).show()
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, getString(R.string.cannot_open, getString(R.string.settings)), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showGroupPicker(app: App) {
        val current = AppGroup.of(app, layout.groups)
        val options = AppGroup.entries.map { group ->
            GlassMenu.Option(group.title, detail = if (group == current) getString(R.string.vpn_now) else null) {
                saveLayout(layout.copy(groups = layout.groups + (app.packageName to group.name)))
                focusApp(app.packageName)
            }
        }
        GlassMenu(this, theme).show(getString(R.string.group_title, app.label), options, focusIndex = current.ordinal, columns = 2)
    }

    private fun moveFavourite(pkg: String, delta: Int) {
        // Reorder among installed favourites; uninstalled ones keep their spot at the end.
        val installed = Apps.favourites(apps, layout.favourites).map { it.packageName }.toMutableList()
        val from = installed.indexOf(pkg)
        val to = from + delta
        if (from < 0 || to !in installed.indices) return
        installed.add(to, installed.removeAt(from))
        saveLayout(layout.copy(favourites = installed + layout.favourites.filter { it !in installed }))
        favouriteKeys.getOrNull(to)?.requestFocus()
    }

    private fun addFavourite(pkg: String, focus: Boolean) {
        saveLayout(layout.copy(favourites = layout.favourites.filter { it != pkg }.let {
            // Put it after the last installed favourite, before any uninstalled leftovers.
            val installed = Apps.favourites(apps, it).map { a -> a.packageName }
            installed + pkg + it.filter { p -> p !in installed }
        }, hidden = layout.hidden - pkg))
        if (focus) favouriteKeys.lastOrNull()?.requestFocus()
    }

    private fun removeFavourite(pkg: String) {
        val index = Apps.favourites(apps, layout.favourites).indexOfFirst { it.packageName == pkg }
        saveLayout(layout.copy(favourites = layout.favourites.filter { it != pkg }))
        // Focus the key that slid into its place (or the one before, if it was last).
        (favouriteKeys.getOrNull(minOf(index, favouriteKeys.size - 1)) ?: appsRow.getChildAt(0))?.requestFocus()
    }

    private fun setHidden(pkg: String, hide: Boolean) {
        saveLayout(layout.copy(hidden = if (hide) layout.hidden + pkg else layout.hidden - pkg))
        if (home.visibility == View.VISIBLE && hide) appsRow.getChildAt(0)?.requestFocus()
    }

    /** Save first, then redraw: a crash-reboot right after must not lose the change. */
    private fun saveLayout(new: HomeLayout) {
        layout = new
        try {
            LayoutStore.save(this, new)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.save_failed, Toast.LENGTH_SHORT).show()
        }
        buildHome()
        refreshAllApps()
    }

    private fun showHome() {
        allAppsPage.visibility = View.GONE
        home.visibility = View.VISIBLE
    }

    private fun showAllApps() {
        home.visibility = View.GONE
        allAppsPage.visibility = View.VISIBLE
        refreshAllApps()
        allApps.scrollToPosition(0)
        allApps.post { focusFirstApp() }
    }

    // ---- All apps page: search, sort, groups ----

    private fun buildAllAppsBar() {
        val bar = findViewById<LinearLayout>(R.id.all_apps_bar)
        searchField = EditText(this).apply {
            background = null
            hint = getString(R.string.search_apps)
            textSize = 16f
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            setPadding(0, 0, 0, 0)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    query = s?.toString().orEmpty()
                    refreshAllApps()
                    allApps.scrollToPosition(0)
                }
            })
            setOnEditorActionListener { _, _, _ ->
                // Done typing: close the keyboard and jump to the first result.
                getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(windowToken, 0)
                allApps.post { focusFirstApp() }
                true
            }
        }
        searchKey = GlassKey(this).apply {
            radiusDp = 16f
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((16 * d).toInt(), 0, (16 * d).toInt(), (3 * d).toInt())
                addView(ImageView(context).apply {
                    tag = TAG_ICON
                    setImageDrawable(AppIcons.symbol(AppIcons.SEARCH, theme.ink))
                }, LinearLayout.LayoutParams((26 * d).toInt(), (26 * d).toInt()))
                addView(searchField, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = (12 * d).toInt()
                })
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        searchField.setOnFocusChangeListener { _, has -> searchKey.lit = has }
        bar.addView(searchKey, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))

        for (sort in AppSort.entries) {
            val k = GlassKey(this).apply {
                isFocusable = true
                isClickable = true
                modifier = true
                radiusDp = 16f
                contentDescription = sort.title
                setOnClickListener {
                    if (layout.sort != sort) saveLayout(layout.copy(sort = sort))
                    allApps.scrollToPosition(0)
                }
                addView(TextView(context).apply {
                    text = sort.title
                    textSize = 15f
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    setPadding(0, 0, 0, (4 * d).toInt())
                }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
            sortKeys[sort] = k
            bar.addView(k, LinearLayout.LayoutParams((118 * d).toInt(), ViewGroup.LayoutParams.MATCH_PARENT).apply {
                marginStart = (12 * d).toInt()
            })
        }
    }

    /** Rebuild the All apps list from the search text, the sort and the groups. */
    private fun refreshAllApps() {
        sortKeys.forEach { (sort, key) -> key.marked = sort == layout.sort }
        val q = query.trim()
        val list = if (q.isEmpty()) apps else apps.filter { it.label.contains(q, ignoreCase = true) }
        val rows: List<Row> = when {
            list.isEmpty() && q.isNotEmpty() -> listOf(Row.Note(getString(R.string.no_matches, q)))
            layout.sort == AppSort.GROUPS -> list.groupBy { AppGroup.of(it, layout.groups) }
                .toSortedMap(compareBy { it.ordinal })
                .flatMap { (group, groupApps) -> listOf(Row.Title(group.title)) + groupApps.map { Row.Item(it) } }
            layout.sort == AppSort.RECENT -> Recents.order(this, list).map { Row.Item(it) }
            else -> list.map { Row.Item(it) }
        }
        allAdapter.submit(rows)
    }

    private fun focusFirstApp() {
        val pos = allAdapter.rows.indexOfFirst { it is Row.Item }
        if (pos < 0) { searchField.requestFocus(); return }
        (allApps.layoutManager?.findViewByPosition(pos) ?: allApps).requestFocus()
    }

    private fun focusApp(pkg: String) {
        val pos = allAdapter.rows.indexOfFirst { it is Row.Item && it.app.packageName == pkg }
        if (pos < 0) return
        allApps.scrollToPosition(pos)
        allApps.post { allApps.layoutManager?.findViewByPosition(pos)?.requestFocus() }
    }

    private fun openSettings() {
        try {
            startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, getString(R.string.cannot_open, getString(R.string.settings)), Toast.LENGTH_SHORT).show()
        }
    }

    /** HDMI inputs, by the names set on the TV; opens the chosen one full screen. */
    private fun showInputs() {
        val inputs = hdmiInputs()
        if (inputs.isEmpty()) {
            Toast.makeText(this, R.string.no_inputs, Toast.LENGTH_SHORT).show()
            return
        }
        val options = inputs.map { info ->
            val label = info.loadLabel(this).toString()
            GlassMenu.Option(label) { openInput(info, label) }
        }
        GlassMenu(this, theme).show(getString(R.string.inputs_title), options)
    }

    /** The TV's HDMI ports (not HDMI-CEC devices behind them), in port order: HDMI 1, 2, 3, 4. */
    private fun hdmiInputs(): List<TvInputInfo> =
        getSystemService(TvInputManager::class.java)?.tvInputList.orEmpty()
            .filter { it.type == TvInputInfo.TYPE_HDMI && it.parentId == null }
            .sortedBy { it.id }

    private fun openInput(info: TvInputInfo, label: String) {
        val uri = TvContract.buildChannelUriForPassthroughInput(info.id)
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, getString(R.string.cannot_open, label), Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * The Xbox is on HDMI 4: match the TV's own "HDMI 4" name, else the 4th HDMI port.
     * Waking it, two ways (both need a setting on the Xbox, see docs/plan.md):
     *  - HDMI-CEC: with CEC on, the Xbox shows up as its own input under HDMI 4. Opening that
     *    one (not the bare port) makes the TV "select" the device, which powers it on.
     *  - Network: the Xbox app's power-on packet (XboxWake), once we've seen the Xbox awake.
     */
    private fun openXbox() {
        val inputs = hdmiInputs()
        val hdmi4 = inputs.firstOrNull { it.loadLabel(this).toString().startsWith("HDMI 4") } ?: inputs.getOrNull(XBOX_HDMI_PORT - 1)
        if (hdmi4 == null) {
            Toast.makeText(this, getString(R.string.cannot_open, getString(R.string.xbox)), Toast.LENGTH_SHORT).show()
            return
        }
        val cecDevice = getSystemService(TvInputManager::class.java)?.tvInputList.orEmpty()
            .firstOrNull { it.parentId == hdmi4.id }
        val ctx = applicationContext
        net.execute {
            XboxWake.wake(ctx)
            // Learn (or refresh) its Live ID once it's up; it takes a few seconds to join the network.
            for (i in 0 until 8) {
                if (XboxWake.discover(ctx)) break
                Thread.sleep(4000)
            }
        }
        openInput(cecDevice ?: hdmi4, getString(R.string.xbox))
    }

    // ---- VPN ----

    /**
     * Groups that get a row in the VPN panel: those with a choice whose apps are installed
     * (Max or Disney+ appear by themselves once installed). UK apps always show.
     */
    private fun vpnRows(): List<VpnRoutes.Group> {
        val installed = apps.mapTo(HashSet()) { it.packageName }
        return routes.groups.filter { g ->
            g.choices.size > 1 && (g.id == VpnRoutes.UK_GROUP || g.apps.any { it in installed })
        }
    }

    private fun choiceLabel(choice: String) = if (choice == VpnRoutes.UK) "UK" else routes.locations[choice] ?: choice

    /**
     * The VPN key, two fixed short lines: the UK apps' city, then where everything else goes
     * ("Others  US", or "Others  Mixed" when they differ). The panel has the details.
     */
    private fun updateVpnKey() {
        if (!VpnSwitch.isWireGuardInstalled(this)) {
            vpnLabel.text = getString(R.string.vpn)
            vpnState.setText(R.string.vpn_missing)
            vpnKey.isEnabled = false
            return
        }
        vpnKey.isEnabled = true
        val prefs = VpnRoutes.prefs(this)
        fun line(entry: Pair<String, String>) = android.text.SpannableStringBuilder().apply {
            append(entry.first, android.text.style.ForegroundColorSpan(theme.dim), 0)
            append("  ")
            append(entry.second, android.text.style.ForegroundColorSpan(theme.ink), 0)
        }
        val rows = vpnRows()
        val uk = rows.first { it.id == VpnRoutes.UK_GROUP }
        vpnLabel.text = line(uk.title to choiceLabel(prefs.choice(uk)))
        // Never more than two short words on the second line: the panel has the details.
        val others = rows.filter { it.id != VpnRoutes.UK_GROUP }.map { prefs.choice(it) }.distinct()
        val summary = when (others.size) {
            0 -> null
            1 -> choiceLabel(others.single())
            else -> getString(R.string.vpn_mixed)
        }
        vpnState.text = when {
            switching -> getString(R.string.vpn_switching)
            summary == null -> ""
            else -> line(getString(R.string.vpn_others) to summary)
        }
    }

    private var switching = false

    /**
     * Two rows of choices (UK apps: London / Manchester; YouTube: Bangladesh / UK / US).
     * A pick applies straight away and the panel stays open; Back closes it.
     */
    private fun showVpnPanel() {
        val prefs = VpnRoutes.prefs(this)
        val rows = vpnRows().map { group ->
            GlassMenu.Row(
                group.title,
                group.choices.map { choiceLabel(it) },
                group.choices.indexOf(prefs.choice(group)),
            ) { index ->
                val updated = VpnRoutes.prefs(this).with(group, group.choices[index])
                VpnRoutes.save(this, updated)
                updateVpnKey()
                restTunnel()
            }
        }
        GlassMenu(this, theme).apply {
            onDismiss = { vpnPanelClosedAt = android.os.SystemClock.uptimeMillis() }
        }.showChoices(getString(R.string.vpn_title), rows, getString(R.string.vpn_footer))
    }

    /** Back on Home: put the resting tunnel back (UK apps' city, plus YouTube if it's set to UK). */
    private fun restTunnel() {
        if (!VpnSwitch.isWireGuardInstalled(this) || !VpnSwitch.hasPermission(this)) return
        val resting = routes.restingTunnel(VpnRoutes.prefs(this)) ?: return
        if (resting != VpnSwitch.lastTunnel(this) || VpnSwitch.vpnNetwork(this) == null) switchTunnel(resting, null)
    }

    /**
     * Bring [tunnel] up and run [then] once the new VPN network is there (or after 4 s anyway:
     * the app still opens, just maybe on the wrong connection for a moment).
     */
    private fun switchTunnel(tunnel: String, then: (() -> Unit)?) {
        if (!VpnSwitch.hasPermission(this)) {
            pendingVpn = { switchTunnel(tunnel, then) }
            requestPermissions(arrayOf(VpnSwitch.PERMISSION), REQUEST_VPN_PERMISSION)
            return
        }
        val before = VpnSwitch.vpnNetwork(this)
        if (tunnel == VpnSwitch.lastTunnel(this) && before != null) {
            then?.invoke()
            return
        }
        val previous = VpnSwitch.lastTunnel(this)
        VpnSwitch.up(this, tunnel)
        switching = true
        updateVpnKey()
        val started = android.os.SystemClock.uptimeMillis()
        val check = object : Runnable {
            override fun run() {
                val now = VpnSwitch.vpnNetwork(this@HomeActivity)
                val changed = now != null && now != before
                val waited = android.os.SystemClock.uptimeMillis() - started
                if (!changed && waited < SWITCH_TIMEOUT_MS) {
                    main.postDelayed(this, 150)
                    return
                }
                switching = false
                if (!changed && previous != null) {
                    // WireGuard ignored us (remote control off?) or the tunnel failed to start.
                    VpnSwitch.rememberTunnel(this@HomeActivity, previous)
                    Toast.makeText(this@HomeActivity, R.string.vpn_no_response, Toast.LENGTH_LONG).show()
                }
                if (!isDestroyed) updateVpnKey()
                then?.invoke()
            }
        }
        main.postDelayed(check, 150)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode != REQUEST_VPN_PERMISSION) return
        val action = pendingVpn
        pendingVpn = null
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED && action != null) {
            action()
        } else {
            Toast.makeText(this, R.string.vpn_permission_denied, Toast.LENGTH_LONG).show()
        }
    }

    // ---- All apps page ----

    /** A line on the All apps page: an app key, a group title, or a note ("No apps match…"). */
    private sealed class Row {
        class Item(val app: App) : Row()
        class Title(val text: String) : Row()
        class Note(val text: String) : Row()
    }

    private inner class AllAppsAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        var rows: List<Row> = emptyList()
            private set

        /** Titles and notes span the full width; apps take one of 6 columns. */
        val spans = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int) = if (rows.getOrNull(position) is Row.Item) 1 else 6
        }

        init { setHasStableIds(true) }

        fun submit(list: List<Row>) {
            rows = list
            notifyDataSetChanged()
        }

        override fun getItemCount() = rows.size
        override fun getItemViewType(position: Int) = if (rows[position] is Row.Item) TYPE_APP else TYPE_TEXT
        override fun getItemId(position: Int): Long = when (val r = rows[position]) {
            is Row.Item -> r.app.component.flattenToShortString().hashCode().toLong()
            is Row.Title -> ("title:" + r.text).hashCode().toLong()
            is Row.Note -> -1L
        }

        inner class AppHolder(val key: GlassKey) : RecyclerView.ViewHolder(key) {
            var app: App? = null
        }

        inner class TextHolder(val text: TextView) : RecyclerView.ViewHolder(text)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            if (viewType == TYPE_TEXT) {
                return TextHolder(TextView(parent.context).apply {
                    textSize = 15f
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    letterSpacing = 0.04f
                    setPadding((8 * d).toInt(), (16 * d).toInt(), 0, (4 * d).toInt())
                    layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                })
            }
            lateinit var holder: AppHolder
            val k = key("", big = false, modifier = false, onLongClick = { holder.app?.let { showKeyMenu(it, Place.ALL_APPS) } }) {
                holder.app?.let(::launch)
            }
            k.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (72 * d).toInt()).apply {
                val m = (6 * d).toInt()
                setMargins(m, m, m, m)
            }
            holder = AppHolder(k)
            return holder
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val row = rows[position]) {
                is Row.Title -> (holder as TextHolder).text.apply { text = row.text; setTextColor(theme.dim) }
                is Row.Note -> (holder as TextHolder).text.apply { text = row.text; setTextColor(theme.dim) }
                is Row.Item -> {
                    val h = holder as AppHolder
                    val app = row.app
                    h.app = app
                    h.key.contentDescription = app.label
                    h.key.theme = theme
                    h.key.glass = glass
                    h.key.findViewWithTag<TextView>(TAG_LABEL).apply {
                        text = app.label
                        setTextColor(theme.ink)
                    }
                    setKeyIcon(h.key.findViewWithTag(TAG_ICON), app.icon())
                }
            }
        }
    }

    private companion object {
        const val REQUEST_VPN_PERMISSION = 1
        const val EXTRA_THEME = "theme"
        const val LIVE_TV = "com.sony.dtv.tvx"
        const val XBOX_HDMI_PORT = 4
        const val IDLE_MS = 10 * 60 * 1000L
        const val SWITCH_TIMEOUT_MS = 4000L
        const val TAG_LABEL = "label"
        const val TAG_ICON = "icon"
        const val TYPE_APP = 0
        const val TYPE_TEXT = 1
    }
}

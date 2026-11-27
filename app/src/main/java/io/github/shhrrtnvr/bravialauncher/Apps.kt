package io.github.shhrrtnvr.bravialauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

/** One launchable TV app. */
class App(
    val component: ComponentName,
    val label: String,
    /** Colour for its icon in our set, taken from its real icon (see [AppIcons]). */
    val brand: Int,
    /** Came with the TV (Sony/Google built-ins), as opposed to installed by us. */
    val system: Boolean = false,
    /** The app's own declared category (ApplicationInfo.CATEGORY_*), used for grouping. */
    val category: Int = -1,
    /** Installed on the USB (adopted) storage rather than the TV's internal storage. */
    val onUsb: Boolean = false,
) {
    val packageName: String get() = component.packageName

    /** Our icon for this app; a new drawable per key (each key sizes its own). */
    fun icon(): Drawable = AppIcons.forApp(packageName, label, brand)

    fun launchIntent(): Intent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        .setComponent(component)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
}

object Apps {
    /**
     * Default favourites row, in order. Apps that aren't installed are skipped.
     * Milestone 2 makes this editable and saves it on the TV.
     */
    val DEFAULT_FAVOURITES = listOf(
        "com.google.android.youtube.tv",        // YouTube
        "com.stremio.one",                      // Stremio (Real-Debrid)
        Apps.XBOX,                              // Xbox on HDMI 4
        "com.viewlift.hoichoi",                 // Hoichoi
        "com.netflix.ninja",                    // Netflix
        "com.amazon.amazonvideo.livingroom",    // Prime Video
        "com.nvidia.bbciplayer",                // BBC iPlayer (via UK VPN)
        "air.ITVMobilePlayer",                  // ITVX (via UK VPN)
    )

    /**
     * The Xbox on HDMI 4, as an app-like entry so it can be a favourite, be moved, hidden
     * and found in All apps. Not a package: HomeActivity opens the HDMI input for it.
     */
    const val XBOX = "input.hdmi.xbox"

    fun xbox(label: String) = App(ComponentName(XBOX, "xbox"), label, brand = 0, system = true)

    /** All apps with a TV launcher entry (plus the Xbox), except ourselves, sorted by name. Call off the main thread. */
    fun load(context: Context): List<App> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val seen = HashSet<String>()
        // Both flags: list every app even in the few seconds after boot before the user is
        // unlocked (we start that early as a direct-boot aware home).
        val flags = PackageManager.MATCH_DIRECT_BOOT_AWARE or PackageManager.MATCH_DIRECT_BOOT_UNAWARE
        val colours = BrandColours(context)
        val apps = pm.queryIntentActivities(intent, flags)
            .asSequence()
            .map { it.activityInfo }
            .filter { it.packageName != context.packageName }
            // Some packages expose two TV entries (e.g. Sony Timers); keep the first.
            .filter { seen.add(it.packageName) }
            .map { info ->
                App(
                    component = ComponentName(info.packageName, info.name),
                    label = info.loadLabel(pm).toString().trim(),
                    brand = colours.of(info, pm),
                    system = info.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                    category = info.applicationInfo.category,
                    onUsb = Storage.onUsb(info.applicationInfo),
                )
            }
            .plus(xbox(context.getString(R.string.xbox)))
            .sortedBy { it.label.lowercase() }
            .toList()
        colours.save()
        return apps
    }

    /** The favourites that are installed, in the saved order. */
    fun favourites(all: List<App>, order: List<String>): List<App> {
        val byPackage = all.associateBy { it.packageName }
        return order.mapNotNull { byPackage[it] }
    }
}

/**
 * Each app's icon colour, worked out once per install/update and saved. Loading every real
 * icon on each trip home cost CPU and left the native heap full of decoded icons.
 */
private class BrandColours(context: Context) {
    private val prefs = Storage.prefs(context, "brand_colours")
    private val edit = prefs.edit()
    private var changed = false

    fun of(info: android.content.pm.ActivityInfo, pm: PackageManager): Int {
        if (AppIcons.hasOwnIcon(info.packageName)) return 0
        // The APK path changes on every install or update, so it doubles as a version stamp.
        val stamp = info.applicationInfo.sourceDir.orEmpty()
        prefs.getString(info.packageName, null)?.split('|', limit = 2)?.let { (colour, at) ->
            if (at == stamp) return colour.toInt()
        }
        val colour = AppIcons.brandColour(runCatching { info.loadIcon(pm) }.getOrNull())
        edit.putString(info.packageName, "$colour|$stamp")
        changed = true
        return colour
    }

    fun save() { if (changed) edit.apply() }
}

/**
 * Apps opened from the launcher, most recent first: home's second row (minus favourites)
 * and the All apps "Recent" order.
 */
object Recents {
    private const val PREFS = "recents"
    private const val KEY = "packages"
    private const val KEEP = 40

    private fun read(context: Context): List<String> =
        Storage.prefs(context, PREFS).getString(KEY, "")
            .orEmpty().split(',').filter { it.isNotBlank() }

    /**
     * Recently opened apps first (newest first); until there's history, apps we installed
     * ourselves before the TV's built-ins (BRAVIA notifications, Browser…).
     */
    fun order(context: Context, apps: List<App>): List<App> {
        val rank = read(context).withIndex().associate { (i, p) -> p to i }
        return apps.sortedWith(compareBy<App>({ rank[it.packageName] ?: Int.MAX_VALUE }, { it.system }))
    }

    fun note(context: Context, packageName: String) {
        val list = (listOf(packageName) + read(context).filter { it != packageName }).take(KEEP)
        // commit(), not apply(): the TV loses recent writes when it crash-reboots.
        Storage.prefs(context, PREFS).edit()
            .putString(KEY, list.joinToString(","))
            .commit()
    }
}

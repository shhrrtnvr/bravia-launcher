package io.github.shhrrtnvr.bravialauncher

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.storage.StorageManager
import android.os.UserManager
import java.io.File

/**
 * Everything we save lives in device-protected storage, readable from the first second of
 * boot. That lets Android start us directly as home (we're direct-boot aware) instead of
 * showing its blank FallbackHome for ~20 s until the user is unlocked. Nothing we store is
 * private: favourites order, hidden apps, recents, last VPN choice.
 */
object Storage {
    private const val MIGRATED = "migrated_from_ce"
    private val names = listOf("vpn", "recents")

    fun context(context: Context): Context =
        if (context.isDeviceProtectedStorage) context else context.createDeviceProtectedStorageContext()

    fun prefs(context: Context, name: String): SharedPreferences =
        context(context).getSharedPreferences(name, Context.MODE_PRIVATE)

    fun file(context: Context, name: String): File = File(context(context).filesDir, name)

    /** Installed on the adopted USB volume (mounts late at boot, slower) instead of internal storage. */
    fun onUsb(info: ApplicationInfo): Boolean = info.storageUuid != StorageManager.UUID_DEFAULT

    /** [onUsb] for a package that may have no TV launcher entry (WireGuard, ourselves). */
    fun onUsb(context: Context, packageName: String): Boolean = try {
        onUsb(context.packageManager.getApplicationInfo(packageName, 0))
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** One-time move of data saved before we were direct-boot aware. Needs the user unlocked. */
    fun migrateIfNeeded(context: Context) {
        val de = context(context)
        val marker = de.getSharedPreferences("storage", Context.MODE_PRIVATE)
        if (marker.getBoolean(MIGRATED, false)) return
        if (context.getSystemService(UserManager::class.java)?.isUserUnlocked != true) return
        val ce = context.applicationContext.let { if (it.isDeviceProtectedStorage) return else it }
        names.forEach { de.moveSharedPreferencesFrom(ce, it) }
        File(ce.filesDir, "layout.json").takeIf { it.exists() }?.let { old ->
            val target = File(de.filesDir, "layout.json")
            if (!target.exists()) old.copyTo(target)
            old.delete()
        }
        marker.edit().putBoolean(MIGRATED, true).commit()
    }
}

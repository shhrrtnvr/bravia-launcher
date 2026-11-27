package io.github.shhrrtnvr.bravialauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

/**
 * Switches the WireGuard app's tunnels through its documented remote-control intents.
 * We never run a VPN ourselves: WireGuard does, as the TV's always-on VPN. Which tunnel
 * to use when is [VpnRoutes]' job.
 *
 * Requirements on the TV:
 * - WireGuard installed, with the tunnels from scripts/vpn-configs.py imported.
 * - WireGuard → Settings → "Allow remote control apps" on (otherwise it ignores us silently).
 * - Our app holds [PERMISSION] (asked at first use, or `pm grant`).
 */
object VpnSwitch {
    const val WG_PACKAGE = "com.wireguard.android"
    const val PERMISSION = "com.wireguard.android.permission.CONTROL_TUNNELS"
    private const val ACTION_UP = "com.wireguard.android.action.SET_TUNNEL_UP"
    private const val ACTION_DOWN = "com.wireguard.android.action.SET_TUNNEL_DOWN"
    private val RECEIVER = ComponentName(WG_PACKAGE, "com.wireguard.android.model.TunnelManager\$IntentReceiver")

    private const val PREFS = "vpn"
    private const val KEY_TUNNEL = "tunnel"

    fun isWireGuardInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(WG_PACKAGE, 0)
    }.isSuccess

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** The VPN network, if one is up. (The tunnels don't carry our app, but it's still visible.) */
    fun vpnNetwork(context: Context): Network? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        @Suppress("DEPRECATION")
        return cm.allNetworks.firstOrNull { network ->
            cm.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }
    }

    /**
     * The tunnel we last brought up. Android hides the running tunnel's name from normal apps,
     * so this is what we asked for; if someone switches tunnels inside the WireGuard app, it's
     * stale until our next switch. Always-on VPN restarts WireGuard's last tunnel at boot.
     */
    fun lastTunnel(context: Context): String? = Storage.prefs(context, PREFS).getString(KEY_TUNNEL, null)

    fun rememberTunnel(context: Context, tunnel: String?) {
        // commit(), not apply(): the TV loses recent writes when it crash-reboots.
        Storage.prefs(context, PREFS).edit().putString(KEY_TUNNEL, tunnel).commit()
    }

    /** Take [tunnel] down and up again, so WireGuard re-reads which apps are installed. */
    fun restart(context: Context, tunnel: String) {
        context.sendBroadcast(Intent(ACTION_DOWN).setComponent(RECEIVER).putExtra("tunnel", tunnel))
        up(context, tunnel)
    }

    /** Bring [tunnel] up. WireGuard runs one at a time: this takes the current one down. */
    fun up(context: Context, tunnel: String) {
        context.sendBroadcast(Intent(ACTION_UP).setComponent(RECEIVER).putExtra("tunnel", tunnel))
        rememberTunnel(context, tunnel)
    }
}

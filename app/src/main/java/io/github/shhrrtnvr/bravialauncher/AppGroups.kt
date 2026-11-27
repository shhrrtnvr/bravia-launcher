package io.github.shhrrtnvr.bravialauncher

import android.content.pm.ApplicationInfo

/** Groups for the All apps page, in display order. */
enum class AppGroup(val title: String) {
    WATCH("Watch"),
    STREAMERS("Streamers"),
    PLAYERS("Players"),
    MUSIC("Music"),
    GAMES("Games"),
    SONY_TV("Sony TV"),
    TOOLS("Tools"),
    OTHER("Other");

    companion object {
        fun byName(name: String?) = entries.firstOrNull { it.name == name }

        /** First guesses for the apps on this TV; anyone can move an app with "Move to group…". */
        private val defaults = mapOf(
            // Official services and channels
            "com.google.android.youtube.tv" to WATCH,
            "com.netflix.ninja" to WATCH,
            "com.amazon.amazonvideo.livingroom" to WATCH,
            "com.viewlift.hoichoi" to WATCH,
            "com.nvidia.bbciplayer" to WATCH,
            "air.ITVMobilePlayer" to WATCH,
            "com.channel4.ondemand" to WATCH,
            "tv.stv.android.player" to WATCH,
            "tv.pluto.android" to WATCH,
            "tv.wuaki.apptv" to WATCH,
            "com.google.android.videos" to WATCH,
            "com.sonypicturescore" to WATCH,
            // Streaming apps that aren't a single service
            "com.stremio.one" to STREAMERS,
            "app.netmirror.newtv" to STREAMERS,
            "com.maertsni.tv" to STREAMERS,
            "ar.tvplayer.tv" to STREAMERS,
            "org.xbmc.kodi" to STREAMERS,
            "org.smarttube.stable" to STREAMERS,
            // Players and home media
            "com.plexapp.android" to PLAYERS,
            "com.github.damontecres.wholphin" to PLAYERS,
            "com.mxtech.videoplayer.ad" to PLAYERS,
            "com.sony.dtv.smartmediaapp" to PLAYERS,
            // Music
            "com.spotify.tv.android" to MUSIC,
            "com.google.android.youtube.tvmusic" to MUSIC,
            "com.sony.dtv.osat.music" to MUSIC,
            // Games
            "com.google.android.play.games" to GAMES,
            Apps.XBOX to GAMES,
            "io.github.shhrrtnvr.sprouts" to GAMES,
            // Tools
            "com.esaba.downloader" to TOOLS,
            "org.localsend.localsend_app" to TOOLS,
            "com.yablio.sendfilestotv" to TOOLS,
            "com.wireguard.android" to TOOLS,
            "com.vewd.core.integration.dia" to TOOLS,
            "com.android.vending" to TOOLS,
        )

        /** User's choice first, then our defaults, then the app's own category, then Sony/Other. */
        fun of(app: App, overrides: Map<String, String>): AppGroup {
            byName(overrides[app.packageName])?.let { return it }
            defaults[app.packageName]?.let { return it }
            return when (app.category) {
                ApplicationInfo.CATEGORY_VIDEO -> WATCH
                ApplicationInfo.CATEGORY_AUDIO -> MUSIC
                ApplicationInfo.CATEGORY_GAME -> GAMES
                ApplicationInfo.CATEGORY_PRODUCTIVITY -> TOOLS
                else -> if (app.packageName.startsWith("com.sony.")) SONY_TV else OTHER
            }
        }
    }
}

/** How the All apps page is ordered. */
enum class AppSort(val title: String) {
    AZ("A–Z"),
    GROUPS("Groups"),
    RECENT("Recent");

    companion object {
        fun byName(name: String?) = entries.firstOrNull { it.name == name } ?: AZ
    }
}

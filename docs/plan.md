# Sony BRAVIA TV — Custom Launcher Plan

Status: **milestone 4 done** (2026-11-27) — our launcher is the only home app; Projectivy uninstalled.

## Goal

Build our own lightweight home-screen app (launcher) for the living-room Sony BRAVIA so the TV
starts fast, shows our apps in our order, and never depends on Google's region rules.

**Focus**
- A fast, stable home screen on a 3 GB, 32-bit TV: nothing running in the background, small memory footprint.
- Our app order, stored on the TV only. No Google account sync, no recommendation rows, no region checks.
- Built for how the house uses the TV: YouTube, Stremio (Real-Debrid) for movies, plus TV inputs and settings one click away.
- Replaces Projectivy. The stock Google TV home stays disabled.

**Not in scope**
- Fixing the kernel crashes (Sony firmware issue).
- Content recommendations, ads, or any server or cloud component.
- A custom ROM or root. Everything works through a normal app install and adb.

## Target device

| Item | Value |
| --- | --- |
| Model | Sony BRAVIA KD-65X80J (`BRAVIA_VH2`, MediaTek MT5895) |
| OS | Android 12 (API 31), Google TV, firmware `STT2.230505.001.S136` |
| CPU | 4 cores, **32-bit only** (`armeabi-v7a`) |
| RAM | 3 GB, 100 MB swap |
| Storage | `/data` 4 GB, ~1.1 GB free |
| UI | 1920x1080 UI on 4K panel, density 320 |
| Current home | This launcher (Projectivy 4.68 uninstalled 2026-11-27) |
| Google TV home | `com.google.android.apps.tv.launcherx` disabled |
| adb | Network adb, port 5555 (legacy `adb tcpip`) |

## MVP

1. Registers as the TV's home screen (`HOME` intent).
2. One row of large favourite tiles: YouTube, Stremio, Live TV, then pinned apps.
3. Grid below with every launchable app (`LEANBACK_LAUNCHER`), hidden apps excluded.
4. Long-press / menu on a tile: move left/right, pin, hide.
5. Order and hidden list saved locally. Never reset, never synced.
6. Fixed shortcuts: TV inputs (HDMI 1–4, live TV), Settings, Wi-Fi.
7. Clock only. No content rows, no network calls, no background services.
8. Fully D-pad driven, clear focus highlight.

**Later:** kids mode (YouTube-only, PIN to leave) · wallpaper/solid colour · current input on home · layout backup/restore via adb.

## Technical approach

| Choice | Decision | Why |
| --- | --- | --- |
| Language | Kotlin | Standard for Android |
| UI | Plain Views + `RecyclerView` (not Compose, not Leanback) | Lowest memory, fastest cold start |
| Target | `minSdk 26`, `targetSdk 34`, tested on API 31 | This TV and most Android TV boxes |
| ABI | Pure Kotlin/Java | 32-bit userspace; no native libs |
| Storage | `SharedPreferences` or small JSON file | Order, pins, hidden apps |
| Permissions | `<queries>` for TV launcher apps (no `QUERY_ALL_PACKAGES`), `ACCESS_NETWORK_STATE`, `INTERNET`, WireGuard `CONTROL_TUNNELS` | `INTERNET` only for the Xbox wake packet on the home network (UDP 5050); nothing leaves the LAN |
| Install location | `internalOnly`, enforced by `scripts/install.sh` | Adopted storage mounts too late at boot for a home screen. The TV's developer option "Force allow apps on external" is on, which overrides `internalOnly`, so the script moves the package back after every install |
| Build | Gradle, `./gradlew assembleRelease`, own signing key | APK under 5 MB |

Install / switch:

```bash
./gradlew assembleRelease            # JDK 17, Android SDK in ~/Android/Sdk
adb connect <tv-ip>:5555         # TV=<tv-ip>:5555 for the script
scripts/install.sh                   # install -r, then pm move-package … internal, check, sync
adb shell pm grant io.github.shhrrtnvr.bravialauncher com.wireguard.android.permission.CONTROL_TUNNELS
adb shell sync                       # the TV loses recent writes if it crash-reboots
# only when ready to switch:
adb shell cmd package set-home-activity io.github.shhrrtnvr.bravialauncher/.HomeActivity
```

Release signing key: `~/.config/bravia-launcher/release.jks` + `keystore.properties` (outside the repo, mode 600). Back it up: an update signed with another key can't be installed over this one.

Fallback if the launcher ever breaks: Projectivy was uninstalled (2026-11-27), so either
install it again from the Play Store, or re-enable the stock Google TV home:

```bash
adb shell pm enable com.google.android.apps.tv.launcherx
adb shell cmd package set-home-activity com.google.android.apps.tv.launcherx/com.google.android.apps.tv.launcherx.home.HomeActivity
adb shell sync
```

## Milestones

1. ✅ Skeleton app that registers as home and lists all launchable apps (side by side with Projectivy) — plus the VPN switch.
2. ✅ Favourites row, reorder, hide, saved locally; TV input and Settings shortcuts.
3. ✅ Set as default home (2026-11-27); one week of daily use; fix what annoys.
4. ✅ Uninstall Projectivy (2026-11-27).
5. Optional: kids mode.

## Success criteria

- Home visible < 1 s after pressing Home (warm), < 3 s after `boot_completed` (cold).
- Launcher PSS < 40 MB.
- App order unchanged after reboots, updates and a week of use.
- YouTube and Stremio one click from the remote.

## Risks

| Risk | Mitigation |
| --- | --- |
| Update re-enables Google TV home or resets default | Re-run `set-home-activity` over adb |
| HDMI inputs need Sony-specific intents | Fall back to Sony's input picker (`com.sony.dtv.tvx`) |
| Kernel-panic reboots continue | Out of scope; firmware update / factory reset |
| Network adb unencrypted on Wi-Fi | Turn off ADB over network when not developing |

## Decisions (2026-11-27)

- Real-Debrid runs inside **Stremio**.
- Favourites row: YouTube, Stremio, Live TV, **Hoichoi**, Netflix, Prime Video, BBC iPlayer, the second UK app. Popular apps stay even if rarely used.
- Kids mode: **later**, not v1 (a launcher can't lock the user inside YouTube without screen pinning or device-owner mode).
- TV time zone set by hand (Settings → System → Date & time; automatic time zone off).

## Per-app VPN (split tunnel) — separate from the launcher

Goal: only the UK apps (BBC iPlayer + one other) go through a UK VPN; everything else, including Hoichoi, YouTube and Stremio, uses the local connection.

No kernel change needed (and not possible: locked bootloader, no root). Android's `VpnService` already supports per-app routing (`addAllowedApplication`), and VPN apps expose it in their settings:

| Option | How | Trade-off |
| --- | --- | --- |
| **WireGuard app + the VPN provider's WireGuard config (preferred)** | Generate a UK WireGuard config in the provider's dashboard, import it into the official WireGuard app, set tunnel → *Applications → Include only* iPlayer + UK app; turn on Always-on VPN | Lightest background footprint; no provider app needed |
| The provider's own app, split tunnelling | Pick the two UK apps | Already installed, but heavier app running in the background |

Notes:
- iPlayer blocks many commercial VPN IPs; if the provider's UK servers are blocked, try other UK locations or a dedicated/static IP.
- Do **not** enable "Block connections without VPN" (lockdown): it would cut the whole TV off when the tunnel is down.
- Building our own VPN into the launcher is out of scope: it would add native code (WireGuard's Go backend) and a background service, against the launcher's design goals.

### Status (2026-11-27)

- A WireGuard key pair registered with the VPN provider; the private key was generated locally and never sent anywhere.
- Configs on the Linux workstation in `~/.config/bravia-vpn/` (mode 600): `uk-london.conf`, `uk-manchester.conf` (iPlayer + ITVX only), `uk-london-yt.conf` (+ YouTube, SmartTube). Built from the provider's public server list (kept locally in `~/.config/bravia-vpn/servers.json`), with `IncludedApplications` set in the file.
- WireGuard 1.0.20260315 installed on **internal** storage (`pm install -f`, official APK from download.wireguard.com). Play Store installs land on the adopted storage volume, which loads too late at boot for always-on VPN.
- Tested once: London tunnel up and validated, carrying only iPlayer and ITVX (VPN `Uids` = those 2 apps); local internet unaffected.
- **Done (2026-11-27 03:20):** three tunnels imported; `uk-london` is the always-on VPN (`always_on_vpn_app = com.wireguard.android`, lockdown off). Reboot test: tunnel up within ~10 s of boot, handshake OK, carrying only iPlayer + ITVX; rest of the TV on the local connection. ITVX opens to the UK catalogue. The provider's app and X-plore uninstalled.
- Endpoints use **IP addresses**, not hostnames: at boot WireGuard starts before Wi-Fi is up, fails to resolve the hostname 10 times and gives up. If UK apps stop working, the IP was probably retired: re-run `scripts/vpn-configs.py` (re-resolves the names) and re-import.
- To change which apps use the VPN: switch tunnels in the WireGuard app (only one runs at a time; always-on restarts the last one used). Re-importing needs a file picker: WireGuard's TV screen can't import, so open its phone screen (`am start -n com.wireguard.android/.activity.MainActivity`) and use a file manager.

## Firmware update (2026-11-27)

Installed: `STT2.230505.001.S136` (built 2026-06-05). Checking in Settings → System software update found a newer release (Sony package dated 2026-11-04, about 1.9 GB). Because "Automatically check for update" is on, the check started the download and install in the background straight away. It's an A/B update: it writes to the inactive slot (`_a`; the TV runs `_b`) and switches over on the next restart, so a crash mid-install leaves the current system intact.

- 2026-11-27: BRAVIA Sync → **Auto TV on** turned off (`cec_setting tv_wake_on_one_touch_play = 0`), so HDMI devices no longer wake the TV. BRAVIA Sync control and Auto devices off stay on.
- [x] Installed 2026-11-27 05:51 during the home-switch restart test: now **`STT2.230505.001.S158`** (slot `_a`, built 2026-10-02). The OTA restart itself was clean (`reboot,ota`).
- [ ] `adb reboot` still ends in a kernel panic on S158. Watch for a day or two whether the twice-daily crash restarts stop (`adb shell getprop sys.boot.reason`, uptime).

## Finding: every reboot is a crash, and recent writes are lost

`adb reboot` and the daily restarts all end in a kernel panic (`sys.boot.reason = kernel_panic,sysrq_trigwarmd_crash`), so the TV never shuts down cleanly. Files written shortly before a restart don't reach the disk: two fresh WireGuard installs were corrupted ("Invalid file") because the TV rebooted within a minute of installing. `e2fsck` runs on every boot.

Practical rules:
- After installing or changing anything over adb, run `adb shell sync` before rebooting.
- **Launcher design:** never rely on a clean shutdown. Save layout changes immediately and atomically (write a temp file, `fsync`, rename; or `SharedPreferences.commit()`, not `apply()`), and treat a missing or corrupt layout file as "use defaults", never a crash.

## Hoichoi

2026-11-27: was not installed. Play Store offered it normally; installed `com.viewlift.hoichoi` 5.19.56 and it opens to the full catalogue on the local connection. No region block seen. Hoichoi must **not** go through the VPN.

- [x] Logged in with the existing subscription; plays fine.

## TV state checks (2026-11-27, adb from the Linux workstation)

- Time zone set by hand (auto time zone off).
- UK apps installed: BBC iPlayer (`com.nvidia.bbciplayer` 1.4.126, the patched Shield build), ITVX (`air.ITVMobilePlayer`), Channel 4 (`com.channel4.ondemand`), STV Player (`tv.stv.android.player`). None used this year per usage stats.
- The VPN provider's app installed; no always-on VPN set.

## Open questions

- [x] Second UK app: **ITVX** (`air.ITVMobilePlayer`).
- [x] VPN switch: **v1**. Built as a chip in the header (not a favourites tile), see Milestone 1 below.

## Milestone 1 (2026-11-27)

Package `io.github.shhrrtnvr.bravialauncher`, 148 KB signed APK, on internal storage. Launchable from Projectivy ("Home" tile); Projectivy stays the default home (pinned with `set-home-activity`, since a second home app otherwise makes the Home button show a chooser).

- Screen: clock + date, **VPN chip** top right, Favourites (4 per row: YouTube, Stremio, TV, Hoichoi, Netflix, Prime Video, BBC iPlayer, ITVX), then All apps (5 per row, A–Z). Banners, or icon + name for apps without one.
- Reloads the app list on every return to home and on package/adopted-storage broadcasts (apps on adopted storage appear late after boot).
- Back does nothing once it's the default home; Home while on home jumps to the first favourite.
- Measured on the TV: cold start 0.32 s (with the firmware update running in the background), PSS 38 MB.

VPN chip → picker: *UK apps — London* · *UK apps + YouTube — London* · *UK apps — Manchester* · *Off until next restart*. It sends WireGuard's `SET_TUNNEL_UP/DOWN` broadcasts; WireGuard swaps tunnels (and restores the old one if the new fails). Tested on the TV: London → London-YT (YouTube uid joined the tunnel) → London.
- WireGuard → Settings → **Allow remote control apps** is on (needed; otherwise it silently ignores us).
- Android hides the running tunnel's name from normal apps, so the chip shows on/off for real but the tunnel name is the last one picked here. Switching inside the WireGuard app makes the label stale until the next switch from the launcher.
- "Off" lasts until reboot: always-on VPN brings back the last tunnel.

Fixed during testing: D-pad Up couldn't leave the grid (RecyclerView swallowed it scrolling to the unfocusable section title) → `HomeGrid`.

## Look: seasonal glass keycaps (2026-11-27)

Picked after several rounds of mockups (`design/`): **minimal UI, rich scene**. Keys are white frosted-glass keycaps with the app name only (no banners, no Japanese text); the nature scene lives in the wallpaper behind them, Apple TV style.

| Months | Theme | Scene |
| --- | --- | --- |
| Dec–Feb | Yuki | snowy mountains, snow-laden pines, snowfall |
| Mar–May | Sakura | cherry tree in bloom, falling petals, petals on the ground |
| Jun–Nov | Ume | ink-wash mountains in mist, red sun over water, boat, plum branch |

- Layout: clock bar + VPN key; 8 favourite keys (4×2); one row: Settings, Inputs, 3 recent apps (installed-by-us apps until there's history), All apps → scrolling grid page.
- Scene painted once into a bitmap (`Scenes`, ported 1:1 from the mockups' SVG); a small box-blurred copy is the "glass" every key samples (`GlassSource`), so frosted keys cost nothing per frame.
- Petals/snow (`ParticlesView`) at ~30 fps only while home is visible: ~12% of one core (~3% of the TV) with 70 snowflakes after switching key corners to outline clipping. Stops when an app opens.
- Focus: key grows 6%, lifts with a soft shadow, diagonal sheen, thin accent ring.
- Inputs key: HDMI picker from `TvInputManager` (TV's own names), opens via `TvContract.buildChannelUriForPassthroughInput`. Not yet tested choosing an input.
- Preview a season: `adb shell am start -n io.github.shhrrtnvr.bravialauncher/.HomeActivity --es theme sakura|yuki|ume`.
- Memory: PSS ~47 MB at the time (the full-screen scene bitmap); fixed later, see "Memory" below.


## Milestone 2 (2026-11-27): editing on the TV

Hold OK on a key (or press the remote's menu/options button) for a glass menu:
- Favourite: **Move left / Move right / Remove from favourites**.
- Bottom-row app: **Add to favourites / Hide from home**.
- All apps page: **Add to favourites** (greyed out when 8 are set) and **Hide from home / Show on home**.

"Hidden" means never shown in home's bottom row; the app stays under All apps. Layout lives in `files/layout.json` via `AtomicFile` (write temp, fsync, rename) and is saved before the screen redraws; a missing or unreadable file falls back to the default favourites. Uninstalled favourites keep their place and come back if reinstalled.

VPN and Inputs pickers use the same light glass menu. Tested on the TV: move, hide, survive a force-stop, show again, menu button; HDMI 1 opens through Sony's TV app (`com.sony.dtv.tvx`), Home returns.

Testing note: `input keyevent --longpress` sometimes arrives as a short press while the TV is busy (it opened Stremio once); use `input keyevent MENU` to open the key menu in scripts.

## Milestone 3 (2026-11-27): default home

- `set-home-activity` to our launcher. The firmware update reset the Home choice (chooser shown); set again.
- **Projectivy disabled** (`pm disable-user`), not uninstalled: it lives on adopted storage, so at boot it was missing from the home candidates, Android discarded the saved Home choice as stale, and the chooser came back after every restart. With one home app the choice sticks (it's stored as the `android.app.role.HOME` holder).
- **ITVX moved to internal storage** (`pm move-package air.ITVMobilePlayer internal`, then `sync`): on adopted storage it wasn't installed yet when the always-on tunnel started at boot, so WireGuard left it out and ITVX went over the local connection. After the move the tunnel carries iPlayer + ITVX (`Uids 10205-10206`) from boot. Rule: anything that must be in the tunnel at boot must be on internal storage.
- Launcher is direct-boot aware and keeps its data in device-protected storage (one-time migration on unlock), lists all apps before unlock, refreshes on `USER_UNLOCKED`.
- Boot timing (restart, S158): boot animation ends ~19 s after kernel start, BOOT_COMPLETED +2 s, launcher on screen +14 s more. The gap is Android's blank FallbackHome waiting for the role service to restore the Home choice (it drops its cached copy at boot as "dangling"); not something the app can shorten. *Update (later that day): the launcher turned out to be on adopted storage (see "Install location"), which mounts late at boot; that alone explains a FallbackHome wait. Re-measure after the next restart now that it's on internal storage.* Waking from standby is instant (launcher already running).
- Verified after restart: launcher is home, all favourites present (incl. adopted-storage apps), VPN tunnel carries both UK apps.

## App icons on keys (2026-11-27)

Names alone were too hard for the kids. The apps' real icons clashed, so keys use **our own set (style C)**: a rounded square tinted with the app's brand colour and a simple symbol or letter in that colour (YouTube red play, Netflix red N, ITVX lime X, Stremio purple diamond…), generic shapes rather than the logos. Hand-picked for the family's apps in `AppIcons.kt`; every other app gets its first letter in the most vivid colour of its real icon (computed once when the app list loads). Settings, Inputs and All apps use neutral symbols in the season's ink. Favourites: 52 dp icon left of the name; small keys and All apps: 28 dp icon above the name. Screenshots: `design/screenshots/tv-ume-icons.png`, `tv-all-apps-icons.png`.

## Xbox key (2026-11-27)

The Xbox is on **HDMI 4**. Home's bottom row is now Settings, Inputs, **Xbox**, two recent apps, All apps. The Xbox key (Xbox green, game-controller symbol) opens HDMI 4 directly: it matches the TV's own "HDMI 4" input name, else the 4th HDMI port (on this TV `…ExternalTvInputService/HW5`, `hdmi_port=4`). Tested: switches to HW5 via Sony's TV app; Home returns.

**Waking the Xbox** (added later the same day). The key now also tries to turn the Xbox on, two ways; each needs one setting on the Xbox:

1. **HDMI-CEC.** Xbox: Settings > General > TV & display options > Device control > turn on HDMI-CEC. The Xbox then appears to the TV as a CEC device with its own input under HDMI 4 (`parentId` = HW5). The key opens that input instead of the bare port, and the TV's device-select (`HdmiCecLocalDeviceTv.deviceSelect`) powers a sleeping device on. On 2026-11-27 the TV saw no CEC devices at all (`dumpsys hdmi_control`: only the TV), so CEC is off on the Xbox today.
2. **Network (`XboxWake`).** What the Xbox phone app does: a UDP power-on packet (`0xDD02` + Live ID) to port 5050. Xbox: Settings > Devices & connections > Remote features on, power mode "Sleep". The Live ID is learnt automatically: while the Xbox is awake it answers a discovery broadcast (`0xDD00`) with its certificate, whose CN is the Live ID. The launcher sends discovery on every return Home until it has an ID, and for ~30 s after each Xbox key press; ID + IP are saved in device-protected prefs `xbox`. Not yet tested against the console (it was off and silent on 2026-11-27).

Either way the key switches to HDMI 4 straight away; a sleeping Xbox takes a few seconds to show a picture.

## All apps: search, sort, groups (2026-11-27)

A bar above the grid: **Search apps** (OK opens the TV keyboard; filters as you type; Back clears it, then leaves the page) and **A–Z / Groups / Recent** (current one marked with an accent bar; saved in `layout.json`).

- **Groups**, in order: Watch (official services and channels), Streamers (Stremio, NetMirrorTV, OnStream, TiviMate, Kodi, SmartTube), Players (Plex, Wholphin, MX Player, Media Player), Music, Games, Sony TV, Tools, Other. Defaults in `AppGroups.kt`; unknown apps fall back to their declared category, Sony packages to Sony TV, else Other.
- **Move to group…** in the All apps key menu overrides the group per app (saved in `layout.json` → `groups`). The picker is a 2-column glass menu.
- **Recent** orders by apps opened from the launcher (now recorded for every launch, last 40), then A–Z.
- D-pad: Up from the grid's top row reaches the bar (`AllAppsGrid`, same RecyclerView focus fix as before); Down from the bar enters the results.
- Screenshots: `design/screenshots/tv-all-apps-groups.png`, `tv-all-apps-search.png`.

## Memory (2026-11-27)

Measured with `dumpsys meminfo` / `dumpsys gfxinfo` on the TV:

| | Before | After |
| --- | --- | --- |
| Total PSS, Home after some use | 47–69 MB | ~25 MB |
| Native heap | 34 MB | 12–14 MB |
| Scene, GPU | 8 MB texture (plus the 8 MB heap copy) | 8 MB hardware bitmap only |

What changed:
- **Scene as a hardware bitmap.** Painted in software once, blurred for the glass, then copied to `Bitmap.Config.HARDWARE` and the software copy recycled. Before, the 1080p scene sat on the heap *and* as a GPU texture.
- **Icon colours cached.** `Apps.load` ran on every return Home and decoded every app's real icon just to pick its colour. Now the colour is saved per package with the APK path as the version stamp (`brand_colours` prefs), so icons are decoded once per install/update; hand-drawn icons are never decoded.
- **Give the scene back under pressure.** `onTrimMemory(TRIM_MEMORY_BACKGROUND+)` while another app is on screen drops the scene; `onStart` paints it again (tested with `am send-trim-memory`). The small blurred copy stays, so keys never flash.

## Google packages disabled (2026-11-27)

`pm disable-user --user 0` (reversible: `adb shell pm enable <package>`):

| Package | What it was | Lost |
| --- | --- | --- |
| `com.google.android.videos` | Google TV / Play Movies | — |
| `com.google.android.play.games` | Play Games | — |
| `com.google.android.apps.tv.dreamx` | Google TV ambient screensaver | Photo screensaver (screen just turns off) |
| `com.google.android.feedback` | Send feedback to Google | — |
| `com.google.android.syncadapters.calendar` | Calendar sync | — |
| `com.google.android.marvin.talkback` | TalkBack screen reader | — |
| `com.google.android.partnersetup` | OEM partner setup | — |

Already disabled before: `launcherx` (Google TV home), `tungsten.setupwraith` (setup).

Re-enabled the same day: `com.google.android.katniss` (Google Assistant / voice search, ~80 MB) — the family uses voice search.

Kept on purpose: Play Services/GSF (apps need them), Play Store (updates), `mediashell` (Chromecast), `tv.remote.service` (phone remote app), Gboard (the active keyboard), `tts` (speech recognition for apps' mic buttons), WebView, permission controller, ext services. YouTube and YouTube Music are apps the family may use, so they stay.

Sony's packages: see the next section.

## Projectivy uninstalled (2026-11-27)

`adb uninstall com.spocky.projengmenu`. Home stays `io.github.shhrrtnvr.bravialauncher/.HomeActivity`.

Also found that day: a second Android user (id 10, `new_user`, never started) blocked `pm move-package` (`Failure [-10]`, locked user). The launcher was removed for that user only (`pm uninstall --user 10 …`; it had never been opened there), after which the move to internal worked.

## Sony packages removed (2026-11-27)

`pm uninstall --user 0` — gone for the TV's user, survives restarts and updates; the APKs stay in
`/system` (no root), so a factory reset brings them back. Restore one with
`adb shell cmd package install-existing <package>`.

Rule used: never touch anything running as `android.uid.system` or marked persistent (removing
those can stop the TV booting, and network adb wouldn't come up to fix it), nor anything for the
tuner, inputs, HDMI-CEC (`braviasyncservice`, `devicecontrolservice`), audio, picture, light sensor
(`sensingfusionservice`), updates, live TV / HbbTV, recordings or the USB media player.

| Removed | What it was | RAM it used |
| --- | --- | --- |
| `com.uei.quicksetsdk.sony`, `irblaster`, `irbservice` | Universal remote for cable/satellite boxes over IR | ~70 MB |
| `com.sony.dtv.webapi.core` | IP control API (Sony phone apps, Home Assistant-style control) | ~43 MB |
| `seeds.iot`, `seeds.iot.remotestart` | Sony IoT / smart-speaker remote start | ~17 MB |
| `huey.dlna.module`, `huey.dlna.renderersettings`, `sonyericsson.dlna`, `homenetwork` | DLNA renderer / home-network media | ~22 MB |
| `b2b.hotelmode`, `b2b.noderuntime.*`, `b2b.noderuntimeextension.*`, `b2b.webappruntimedrm` | Hotel / professional mode | ~7 MB |
| `gestureapp`, `sensingcamera.*`, `distancealert`, `livingfit` | BRAVIA CAM features (this TV has no camera) | ~7 MB |
| `sonyselect`, `promos`, `android.autoinstalls.config.sony.bravia` | App promotions and auto-installs | ~6 MB |
| `demosupport`, `demosystemsupport`, `multiscreendemo` | Shop demo mode | — |
| `helpguidecontents`, `smarthelp`, `customersupport` | Help guide / support | — |
| `frame`, `launcherxcustomizer` | Photo frame; Google TV home customiser (that home is disabled) | — |

Checked afterwards: live TV, HDMI 4, Settings, Home, the UK tunnel (iPlayer + ITVX), HDMI-CEC still
enabled; no crashes in the crash log. `MemAvailable` idle on Home: ~1.28 GB (was ~0.97 GB earlier
that day, not a controlled comparison).

Kept, pending a decision: AirPlay + HomeKit (`airplayapp`, persistent, ~51 MB) — needed only for
casting from iPhones/Macs.

## Storage placement (2026-11-27)

The TV has 4 GB internal (`/data`, ~1.3 GB free) and a 28 GB USB drive adopted as internal
storage. Policy: **internal** for what must start at boot or is used most; **USB** for the rest.

| Internal | Why |
| --- | --- |
| This launcher | Home must start before the USB volume mounts |
| WireGuard, BBC iPlayer, ITVX | The always-on tunnel only includes apps installed when it starts at boot |
| Favourites (Stremio, Hoichoi, …; YouTube/Netflix/Prime/TV are system apps, always internal) | Most used; internal storage is faster |
| Channel 4, Downloader | Small; the Downloader move to USB was interrupted by a kernel panic, left as is |

**"Force allow apps on external" stays ON.** Tested with a throwaway APK and with the launcher:

- ON: new installs *and updates* land on the USB volume, even for apps moved to internal and
  for apps that declare `internalOnly`. Most apps can stay on USB.
- OFF: apps that don't declare an install location (most apps) can't go on USB, and an app
  already on USB moves to internal on its next update — the 17 apps on USB would gradually
  fill internal storage.

So with it ON, an update can move a must-be-internal app to USB. Handling:
- This launcher: `scripts/install.sh` moves it back after every install.
- Favourites and WireGuard: the launcher shows a note in the clock bar ("Stremio on USB storage
  · press Menu on it to move back"); the app's key menu gets **Move to internal storage…**,
  which opens its App info page (Storage used → Change → Internal shared storage). An app can't
  move packages itself (no permission), so the user taps through.
- From adb: `adb shell pm move-package <pkg> internal`, then `sync`.

**Boot:** with the launcher on internal storage, the launcher was on screen 24.0 s after power-on,
before the boot animation ended (restart at 07:13). The 12–15 s FallbackHome wait is gone — it
was the launcher sitting on the USB volume, not the Home role restore.

Second Android user removed: id 10, a Google TV profile (`com.android.tv.profile`), created
~167 days earlier and last used ~165 days earlier (`pm remove-user 10`).

## Idle on Home

The panel is LCD, which doesn't burn in, so a screensaver doesn't protect it (Google's ambient
screensaver `dreamx` stays disabled). What matters when the TV is left on Home is not redrawing:
after 10 minutes without a button press the falling petals/snow freeze in place (no redraws);
any key starts them again. To turn the TV off when idle, use Sony's Settings → Power & Energy →
Idle TV standby.

## What the "crashes" are (kernel log, 2026-11-27)

From `dumpsys dropbox --print SYSTEM_LAST_KMSG` (the kernel log saved from before each restart):
all three recorded restarts end the same way. The Android framework (`system_server`) dies and
`zygote` is killed; about 15 s later Sony's `warmd` writes to `/proc/sysrq-trigger`, a deliberate
kernel panic used as a hard reboot (`sysrq_trigwarmd_crash`). So every "crash" is Sony's reboot
after the Android framework restarted.

The 07:12 restart came ~2 s after resuming from standby (`PM: suspend exit`, the USB drive
re-enumerating, then `system_server` died at +2 s). The kernel log doesn't say why
`system_server` died, and the tombstones are empty. A normal app can't kill `system_server`;
this pattern was there before the launcher. Suspect, unproven: the adopted USB drive dropping
out and coming back on resume from standby while apps on it are in use.

Side effect seen afterwards: `com.android.statementservice` crashed on its missing data folder
("file system … in a bad state"); fixed with `pm clear com.android.statementservice`.

## VPN by app group (2026-11-27)

Replaces the old picker (UK / UK + YT / Manchester / Off: every combination as an option).

| Group | Apps | Location |
| --- | --- | --- |
| UK apps | iPlayer, ITVX, Channel 4, STV Player, Rakuten TV | London, Manchester, Edinburgh or Glasgow (the provider's UK cities) |
| YouTube | YouTube, SmartTube | Bangladesh (direct), UK, US |
| US apps | Pluto TV | US (New York), fixed |
| Netflix | row | Bangladesh, UK, US, Japan (anime), Canada (large catalogue) |
| Prime Video | row | Bangladesh, UK, US (catalogue follows the Amazon account more than the IP) |
| Max | row once installed | Bangladesh, US, Spain (has Max; the UK gets HBO via Sky/NOW) |
| Disney+ | row once installed | Bangladesh, UK, US, Japan |
| Everything else | Hoichoi, Stremio, NetMirror, Netflix, … | Home connection, never in a tunnel |

Source of truth: `app/src/main/assets/vpn-routes.json` (groups, choices, tunnels). The launcher
reads it; `scripts/vpn-configs.py` turns it into WireGuard files in `~/.config/bravia-vpn/`.
YouTube beyond UK/US: YouTube is essentially the same everywhere, so more countries add
nothing. Pluto via the US has by far the most channels; Rakuten is a UK/EU service.

**One VPN at a time.** Android allows one VPN, WireGuard one server per tunnel. The launcher
switches on launch instead: it picks the tunnel the opened app needs, brings it up, waits for
the new VPN network (≤ 4 s, usually < 1 s), then opens the app. Back on Home it puts the
*resting* tunnel back: the UK apps' city, plus YouTube when YouTube is set to UK. Tunnels:
`uk-lon`, `uk-man`, `uk-edi`, `uk-gla`, each also `-yt` and `-all` (UK apps + YouTube + the four streaming services, used when one of those is set to UK and opened), and `us` (Pluto, YouTube, the four streaming services). WireGuard starts a tunnel fine when an included app isn't installed (tested with Max/Disney+).
Caveat: YouTube opened outside the launcher (cast, voice) uses whatever tunnel is up — fine
for UK/Bangladesh, direct when YouTube is set to US.

**UI.** The VPN key shows "UK apps  London / YouTube  Bangladesh". It opens one panel with a
row per group (keys like segmented buttons, chosen one marked); picks apply at once.

Tested 2026-11-27: `uk-lon` carries the 5 UK apps; YouTube → US then opening YouTube switched
to `us` before YouTube started (validated); Home put `uk-lon` back. Left at London + Bangladesh.

Storage: apps that can be in a resting tunnel (UK apps, YouTube, SmartTube) must be on internal
storage (STV, Rakuten, SmartTube moved). Pluto can stay on USB (only in a launch-time tunnel).

Importing tunnels: the TV has no system file picker; WireGuard's TV screen uses Cx File Explorer
(installed 2026-11-27, all-files access granted). Zip the `.conf` files, push to
`/sdcard/Download`, WireGuard → + → Cx → Downloads → zip → Select; then delete the zip (it holds
the private key). Old tunnels `uk-london`, `uk-london-yt`, `uk-manchester` deleted.

**VPN key, short form:** two fixed lines that never grow: "UK apps  London" and
"Others  Bangladesh/UK/US" when everything else goes the same way, else "Others  Mixed".
The panel has a row per installed group with the details.
Tested: Netflix → US, opening Netflix switched to `us` (validated, Netflix's uid in it); Home put
the resting tunnel back. Netflix blocks screenshots, so whether it showed the US catalogue or a
proxy error still needs checking by eye.

**Time zone / location per app:** not possible here. Android has one system time zone; changing
it needs `SET_TIME_ZONE` (system apps only) and would move the TV's clock for everything, and
per-app spoofing needs root (Xposed-style hooks). Not needed either: Netflix & co. decide by IP
address and DNS, and the tunnels use the provider's DNS. When they refuse, it's because the IP is a
known VPN address. Fix for that, if it happens: a dedicated IP in the UK/US.

**More countries (2026-11-27):** tunnels `jp` (Tokyo: Netflix, Disney+), `ca` (Toronto: Netflix),
`es` (Madrid: Max). Only countries whose catalogue really differs for that app; Australia left
out (Netflix/Disney+ there ≈ UK; its own free apps like ABC iview would be a separate group if
installed). Tested: Netflix → Japan, opening Netflix switched to `jp` (validated). The
`uk-*-all` tunnels on the TV still list Max (imported before Max lost the UK choice); harmless.

## Xbox as a favourite (2026-11-27)

The Xbox is now an app-like entry (`Apps.XBOX`, not a real package; opening it switches to
HDMI 4 and wakes the Xbox as before). So it can be a favourite, moved, hidden, and found in All
apps (Games). Home: Xbox took TV's place in the favourites (3rd key); the bottom row's fixed
shortcut is now TV (live TV), or the Xbox if TV is ever made a favourite again. Done on the TV
through the launcher's own key menu (remove TV, add Xbox from All apps, move left ×5).
Default favourites for a fresh install updated the same way.

## Storage, round 2 (2026-11-27, 09:20)

The launcher now restarts the running tunnel once when one of its apps appears on the USB
volume (`EXTERNAL_APPLICATIONS_AVAILABLE`, at boot or after a move), so apps in tunnels no longer
need internal storage. Seen working: WireGuard "Device started" right after STV moved.

Moved to USB: STV, Rakuten, SmartTube, Channel 4, Downloader. Uninstalled: the Google TV app
(`com.google.android.videos`, a downloaded copy, already disabled). Netflix and Prime Video are
Sony built-ins with Play updates on top (`SYSTEM|UPDATED_SYSTEM_APP`); Android can't move those.

Left on internal: launcher, WireGuard (both needed before USB mounts), favourites Stremio,
Hoichoi, iPlayer, ITVX (speed), plus Android and the built-in apps' updates. `/data`: 65% used,
1.4 GB free (was 71%).

`scripts/install.sh` only moves the launcher when the install landed on USB (moving an app that
is already internal fails with -6).

## Focus rules on Home (2026-11-27)

- The VPN card is a status card: it takes focus only when Up is pressed onto it (or right
  after its panel closes). Android used to hand it focus at start-up (it's the first focusable
  view before the favourites load) and whenever the focused key vanished in a rebuild.
- Home pressed with a menu or the VPN panel open closes it (`GlassMenu.dismissOpen`).
- Coming back from an app (onRestart) or pressing Home focuses the first favourite.

## Puzzle Sprouts on the TV (2026-11-27)

The family's puzzle site (shhrrtnvr/puzzle-sprouts) now has a TV mode (`/tv.js`) and a small
Android TV app (`puzzle-sprouts/tv-app`, package `io.github.shhrrtnvr.sprouts`, ~50 KB):
remote / game controller play for all ~55 games, the TV's own voice for read-aloud, progress
kept on the TV, offline after the first visit. Details in that repo (GAME_GUIDE.md "TV",
tv-app/README.md). In the launcher it has a green sprout icon and sits in the Games group; it
isn't in a VPN group (home connection).

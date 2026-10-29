# tv-home

**A lightweight, customizable home screen for Android TV.**

This fork adds Sony BRAVIA controls, local notifications, on-TV customization,
stronger night appearance, and smaller decoded app artwork to the
[original tv-home project](https://github.com/mihirgupta0900/tv-home).

**Make a slow, ad-filled Android TV fast and clean again.** Debloat it over Wi-Fi and give it a
tiny Apple TV-style home screen with rotating nature wallpapers. You can hand the whole thing to
Claude Code with one prompt, or do it by hand with the scripts here.

![Before: Google TV home with a sponsored hero. After: clean dock over a Lake Louise wallpaper](before-after.png)

On a 2 GB TCL Google TV this disabled 25 bloat apps and cut the home screen's memory use from
192 MB to 83 MB. The debloat workflow disables packages rather than uninstalling them; keep a snapshot
of each package's original enabled state for restoration.

## Do it with Claude Code (easiest)

1. On the TV: Settings → System → About → press OK on **Build** 7 times → back → Developer options
   → turn on **USB debugging**. Keep the TV on, on the same Wi-Fi as your computer.
2. Clone this repo, open Claude Code inside it (so it can reuse the launcher and scripts), and paste:

   ```text
   My Android TV is slow and full of ads. Find it on my Wi-Fi, connect over ADB, and debloat it:
   disable the manufacturer's ads, telemetry and junk (reversibly, never uninstall), asking me first
   about apps and features I might use. Then build and install a fast, minimal Apple TV-style home
   screen with a dock of my favourite apps and rotating nature wallpapers. Back up before changing
   anything, check every step with screenshots from the TV, and give me a restore script.

   Heads-up from people who've done this: use the adb-shell Python package if macOS blocks adb; on
   Google TV also disable com.google.android.tungsten.setupwraith or it hijacks the Home button; and
   test the Home button from inside an app.
   ```

3. Accept "Allow USB debugging?" on the TV when asked, then answer a few questions: which apps you
   use, which go in the dock, whether you want wallpapers.

The prompt is short on purpose; Claude works out the details. The gotchas below are there in case it gets stuck.

## Do it by hand (macOS)

```bash
./setup.sh                                                  # Python ADB client, JDK 17, Android SDK
tools/find-tv.sh                                            # finds the TV, saves its IP
tools/venv/bin/python -I tools/tv.py --probe                # model, Android version
tools/disable.sh examples/tcl-google-tv-g07/debloat.txt     # or your own list, see examples/
launcher/build-and-install.sh                               # build + install the launcher
```

Undo everything with `tools/restore.sh <the same list>`.

## What's here

| Path | What it is |
|---|---|
| [PROMPT.md](PROMPT.md) | The prompt for Claude Code / any coding agent |
| [launcher/](launcher/) | The home screen. 4.7 MB including the wallpapers; plain Android, no network; optional local notification listener |
| [launcher/res/values/config.xml](launcher/res/values/config.xml) | Dock apps, hidden apps, Live TV app, grid columns |
| [tools/tv.py](tools/tv.py) | Pure-Python ADB client: shell, batch, screenshots, chunked APK push |
| [tools/find-tv.sh](tools/find-tv.sh) | Finds Android TVs on your Wi-Fi |
| [tools/type.sh](tools/type.sh) | Type into the TV's on-screen keyboard from your laptop (`tools/type.sh` alone prompts hidden, for passwords) |
| [tools/disable.sh](tools/disable.sh), [tools/restore.sh](tools/restore.sh) | Apply / undo a debloat list |
| [examples/](examples/) | Debloat lists per TV model |

## The launcher

- Clock, greeting and date top-left. Header buttons for **TV**, **Inputs**,
  **Notifications**, **Customize**, the network, **Controls**, and **TV settings**.
  The remote's Menu key also opens Inputs.
- A dock of your favourite apps. The focused app's name appears above it over a glow coloured from
  its banner. Press Down for every other app in a grid.
- 12 nature photos crossfade every 10 minutes, with a scrim so text stays readable. Swap them in
  `launcher/assets/wallpapers/` (1920×1080 JPEGs, credits in `launcher/WALLPAPER-CREDITS.md`).
- Deterministic D-pad focus, remembers the last tile, picks up newly installed apps on return.

On this Sony build, use **Customize** on Home (or long-press OK on an app) to
edit the dock and hide grid apps. Choose a wallpaper, rotation interval, extra
background dimming, or reduced motion without rebuilding. Defaults still come from
`config.xml`. **TV** opens Sony TV mode directly; **Inputs** selects a source.
**Notifications** opens active app notifications (requires notification access) or
BRAVIA notices. Ongoing status notifications are hidden by default. **Show all**
reveals them, and **Notification filters** or **Mute in Home** controls individual
senders. Open a notification and choose **Clear** to dismiss it, or use
**Clear visible notifications** at the end of the list. Bulk clearing asks first and
only includes clearable items in the current view. Ongoing/foreground-service alerts
are preserved. **More** contains Open and Mute/Unmute. Apps can post new alerts later.
Filters only affect Home's list; Clear dismisses the actual Android notification
and cannot be undone by restoring the launcher.
Notification contents remain local and are not stored by Home; filter choices are saved.

**Controls** opens Sony picture, sound, audio-output and sleep-timer panels, plus all
TV settings. A separate **TV settings** button opens all settings directly.
**Customize → Night appearance** dims the wallpaper and ambient glow
(default 20:00–07:00, 60%; adjustable 10–70%) using the TV's local clock.
It shows whether night appearance is active and offers 20-second day/night previews.
Previews end automatically, on Home, or when leaving the launcher; saved settings
are unchanged. Existing installations retain their saved dimming strength.
App cards and text stay readable; this does not change TV brightness or video playback. **Larger text** increases Home
and its menus by 20% without changing other apps. App artwork is decoded near tile
size and reused across dock/grid edits; full-resolution nature photos are preserved.

This checkout is configured for a Sony BRAVIA 4K VH22 on Android 12, with MovieBox,
YouTube, v2rayTun, Telewebion and Hiddify in dock order. Sony Live TV is available in
Inputs. See [the Sony setup notes](examples/sony-vh22/README.md) for the staged install,
backup and restore workflow; do not apply the TCL debloat list to this TV.

## Gotchas we hit

- **macOS blocks the `adb` binary** on the local network ("No route to host"). `tools/tv.py` uses
  the `adb-shell` Python package instead.
- **Google TV hijacks the Home button.** With the stock launcher disabled, the setup app
  (`com.google.android.tungsten.setupwraith`) has a higher HOME priority than any third-party
  launcher. The install script disables it; `restore.sh` re-enables it.
- **Home opened the launcher behind the current app**, because of a stale home task from switching
  launchers. Force-stopping the launcher and `com.android.tv.settings` and pressing Home fixes it.
  The install script does this.
- **Chained `pm` commands** (`pm disable a; pm disable b`) can silently stop after the first. The
  scripts run one per shell call.
- **Apps installed from the Play Store didn't appear**, because the package broadcast was missed
  while another app was in front. The launcher re-checks with `getChangedPackages` on every return.
- **Unsplash blocks scripted downloads** with a bot check. The wallpapers come from Wikimedia
  Commons Featured Pictures instead.

## Supported TVs

Android TV and Google TV: TCL, Xiaomi/Redmi, Hisense, Sony, OnePlus, Realme, Nvidia Shield,
Chromecast with Google TV and most others. Not Samsung (Tizen), LG (webOS) or Fire TV.
Tested on: TCL Google TV (Android 11, upstream), Sony BRAVIA 4K VH22
(Android 12, this fork). Sony shortcuts may be unavailable on other brands.

## Credits

Inspired by [Gathin23/tv-vibehack](https://github.com/Gathin23/tv-vibehack). Wallpaper photos are
from Wikimedia Commons under their own licences, see
[launcher/WALLPAPER-CREDITS.md](launcher/WALLPAPER-CREDITS.md). Code is MIT, see [LICENSE](LICENSE).

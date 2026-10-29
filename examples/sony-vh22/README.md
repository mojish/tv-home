# Sony BRAVIA 4K VH22 (Android 12)

This fork is configured for MovieBox (`com.codegraphi.win`), YouTube, v2rayTun,
Telewebion and Hiddify. Edit `launcher/res/values/config.xml` for another TV.
Installed apps missing from the dock remain in the grid. Customize on Home can
change one to six favourites, their order, and hidden grid tiles without rebuilding.
Hiding tiles never disables or uninstalls apps.

## Build and staged installation

Use JDK 17 or 21 and an Android SDK with platform 34 and build-tools 34.0.0.
The build preserves Java parameter names for D8 compatibility with JDK 21.
Keep `launcher/debug.keystore` privately: subsequent APK updates must use the same
signing key. The script generates it on the first build; it must never be committed.

```sh
JAVA_HOME=/path/to/jdk ANDROID_HOME=/path/to/android-sdk \
  bash launcher/build-and-install.sh --build
/path/to/android-sdk/build-tools/34.0.0/apksigner verify launcher/build/tvhome.apk
adb -s TV_IP:5555 install --no-incremental -r launcher/build/tvhome.apk
adb -s TV_IP:5555 shell am start -n com.mihir.tvhome/.MainActivity
```

Back up before installation. Inspect Home and test every favourite, the grid,
Inputs and TV Settings before selecting it as default Home or disabling stock Home.
The combined installer changes Home and disables Google packages without taking a
TV-specific backup; use `--build` and individual, checked ADB commands here.
The minimum Android API is 30. Location permission is optional for showing the Wi-Fi
SSID; the network shortcut works without it. VPN/Ethernet/Wi-Fi labels describe the
active transport, not a guarantee that streaming or Internet access works.

## TV controls and appearance

- **TV** opens `com.sony.dtv.tvx`; Sony restores the previous channel/input.
- **Inputs** selects a source; the remote Menu key opens this menu too.
- **Controls** opens Sony picture, sound, audio-output and Timers & Clock panels.
  Choose Sleep timer within the latter. Opening a panel does not change its values.
- **TV settings** opens Android's main TV settings directly.
- **Customize** changes dock/grid, one of 12 bundled photos, rotation (off or
  1/5/10/30/60 minutes), background dimming (0–50%), reduced motion and 20% larger
  text. Text scaling applies only to Home and its menus.
- **Night appearance** dims the wallpaper and glow on the TV's local schedule,
  default 20:00–07:00 at 60% (10–70% available). Saved older strengths are preserved.
  Start is inclusive and end exclusive; a stronger manual dim setting takes priority.
  The menu shows Active now/Day now and offers temporary day/night previews.
  Previews last 20 seconds and end on Home or leaving Home. Cards and text retain
  contrast; TV picture settings and playback brightness are unchanged.
- Up/Down focus movement handled by Home's ScrollView plays Android's matching
  navigation sound, respecting system sound-effects settings. Horizontal navigation
  retains Android's normal sound handling. No extra sound is added at focus edges.

## Notifications

Enable Home under TV Settings → Apps → Special app access → Notification access,
or follow Home's access prompt. This optional listener has no network permission;
Home does not persist notification contents. Filter choices are stored locally.

**Notifications → App notifications** shows current alerts, with ongoing status
hidden by default. **Show all** reveals hidden items. Filters and per-notification
**More → Mute in Home** hide senders only in Home; they do not stop apps or silence
system alerts. **BRAVIA notices** opens Sony's separate notice center.

Open a clearable alert and choose **Clear**. **Clear visible notifications** at the
end of the list asks for confirmation and dismisses only clearable entries shown in
that view. Hidden entries, ongoing alerts and foreground-service notifications are
preserved. Clear removes the Android notification, so an APK rollback cannot restore
it. An app can post another alert later. **More → Open** follows its content action;
auto-cancel alerts are dismissed after a successful action.

Android contracts: [notification dismissal](https://developer.android.com/reference/android/service/notification/NotificationListenerService#cancelNotifications(java.lang.String[])),
[clearable status](https://developer.android.com/reference/android/service/notification/StatusBarNotification#isClearable()),
and [navigation sound effects](https://developer.android.com/reference/android/view/SoundEffectConstants).

## Device-specific debloat and rollback

The original Sony pass disabled these six packages for user 0, after replacement
Home testing: `tv.samba.ssm`, `com.sony.dtv.promos`, `com.sony.dtv.sonyselect`,
`com.sony.dtv.sonytab`, `com.google.android.apps.tv.launcherx`, and
`com.google.android.tungsten.setupwraith`. Treat this as a tested example for this
model, not an automatic list for every Sony TV. Preserve TV/input, casting, voice,
updates and picture/sound services; ask the owner about apps they use.

Before any change, save the resolved Home component, package enabled states
(including default versus explicitly enabled), relevant settings, input configuration,
the installed Home APK and screenshots under ignored `backup/`. A configuration
snapshot is not a full app-data/account backup. Save APK checksums and a restore
script for that exact TV and baseline. Keep addresses, accounts, screenshots, APK
backups and signing keys out of Git.

For a launcher-only rollback, verify the saved APK checksum and model, then install
the prior APK with `adb -s TV_IP:5555 install --no-incremental -r PREVIOUS.apk` and
press Home. The APK must have the same signature. Versions 1.1–1.4 retain versionCode
1 to allow the tested in-place rollback workflow. Preferences and notification access
remain; set night dimming back to its earlier value when reverting to an older build.

For full debloat rollback, restore each package's exact prior state with the
appropriate `pm default-state`, `pm enable` or `pm disable-user` command, select the
saved stock Home component, and test Home from inside an app. Do not blindly enable
all packages: some may have already been disabled before this project.
Private per-device restore scripts and historical evidence stay in `backup/`.

## Validation

Build/signature and manual QA use a real VH22 on Android 12. Screenshot evidence,
operation logs and device-specific results stay in private backups. Recheck after
firmware updates or using a different TV:

1. Navigate dock, header and grid with D-pad; check directional sound on the TV.
2. Open TV Settings and an app, then press Home from each.
3. Compare day/night previews on the same photo; wait for preview expiry and check
   that leaving Home cancels it. Check the header with larger text enabled.
4. Post disposable test notifications, clear one, then bulk-clear the visible tests.
   Confirm hidden and ongoing notifications remain. Check bulk Cancel, filters and
   unavailable/reconnecting listener behavior.
5. Verify disabled-package states and default Home are unchanged by APK upgrades.
6. Check HDMI picture/audio and casting with real sources when available; activity
   launches and service presence alone cannot establish successful playback.

Artwork is decoded near tile size and cached across customization edits. A previous
controlled three-sample cold-Home comparison measured median PSS 33.2 MiB before
that optimization and 25.6 MiB after it; those are idle snapshots, not a memory ceiling
or a whole-TV speed benchmark. Interaction can raise memory usage.

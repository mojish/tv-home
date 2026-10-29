package com.mihir.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.Intent;
import android.provider.Settings;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Remote-friendly, local-only launcher preferences. Never changes installed apps. */
final class HomeSettings {
    private final Activity activity;
    private final SharedPreferences prefs;
    private final List<MainActivity.App> apps;
    private final String[] wallpapers;
    private final Consumer<String> changed;
    private AlertDialog dialog;

    HomeSettings(Activity activity, SharedPreferences prefs, List<MainActivity.App> apps,
                 String[] wallpapers, Consumer<String> changed) {
        this.activity = activity;
        this.prefs = prefs;
        this.apps = apps;
        this.wallpapers = wallpapers;
        this.changed = changed;
    }

    static String[] dock(SharedPreferences prefs, String[] defaults) {
        String saved = prefs.getString("dock_order", null);
        return saved == null ? defaults : saved.isEmpty() ? new String[0] : saved.split(",");
    }

    private List<String> dock() {
        List<String> result = new ArrayList<>(Arrays.asList(dock(prefs,
                activity.getResources().getStringArray(R.array.dock_apps))));
        result.removeIf(p -> apps.stream().noneMatch(a -> a.pkg.equals(p)));
        return result;
    }

    private String label(String pkg) {
        for (MainActivity.App a : apps) if (a.pkg.equals(pkg)) return a.label;
        return pkg;
    }

    void dismiss() {
        if (dialog != null) { dialog.dismiss(); dialog = null; }
    }

    private void display(AlertDialog.Builder builder) {
        dismiss();
        dialog = builder.create();
        dialog.show();
        if (dialog.getListView() != null) {
            android.widget.ListView list = dialog.getListView();
            android.view.ViewGroup.LayoutParams layout = list.getLayoutParams();
            float density = activity.getResources().getDisplayMetrics().density;
            layout.height = Math.min((int) (list.getCount() * 48 * density),
                    (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.60f));
            list.setLayoutParams(layout);
            list.requestFocus();
        }
    }

    void show() {
        String[] options = {"Dock apps", "Hide apps from grid", "Choose wallpaper",
                "Wallpaper rotation", "Background dimming", "Reduced motion: " +
                (prefs.getBoolean("reduced_motion", false) ? "On" : "Off"), "Night appearance",
                "Larger text: " + (prefs.getBoolean("large_text", false) ? "On" : "Off"), "Reset customization"};
        display(new AlertDialog.Builder(activity).setTitle("Customize Home")
                .setItems(options, (d, which) -> {
                    switch (which) {
                        case 0: showDock(); break;
                        case 1: showHidden(); break;
                        case 2: showWallpapers(); break;
                        case 3: showRotation(); break;
                        case 4: showDimming(); break;
                        case 5:
                            prefs.edit().putBoolean("reduced_motion", !prefs.getBoolean("reduced_motion", false)).apply();
                            changed.accept("motion"); show(); break;
                        case 6: showNight(); break;
                        case 7:
                            prefs.edit().putBoolean("large_text", !prefs.getBoolean("large_text", false)).apply();
                            dismiss(); changed.accept("text"); break;
                        case 8: confirmReset(); break;
                    }
                }).setNegativeButton("Close", null));
    }

    void showControls() {
        String[] names = {"Picture settings", "Sound settings", "Audio output", "Sleep timer", "All TV settings"};
        String[] actions = {"com.sony.dtv.base.picturesettings", "com.sony.dtv.base.soundsettings",
                "com.sony.dtv.base.audiosystemsettings", "com.sony.dtv.timers.intent.action.SLEEP_TIMER", Settings.ACTION_SETTINGS};
        display(new AlertDialog.Builder(activity).setTitle("Quick controls")
                .setItems(names, (d, which) -> {
                    Intent intent = new Intent(actions[which]);
                    if (which < 3) intent.setPackage("com.sony.dtv.configsettings");
                    else if (which == 3) intent.setPackage("com.sony.dtv.timers");
                    try { activity.startActivity(intent); }
                    catch (RuntimeException e) {
                        Toast.makeText(activity, "Shortcut unavailable; opening TV settings", Toast.LENGTH_LONG).show();
                        try { activity.startActivity(new Intent(Settings.ACTION_SETTINGS)); }
                        catch (RuntimeException unavailable) { Toast.makeText(activity, "TV settings are unavailable", Toast.LENGTH_LONG).show(); }
                    }
                }).setNegativeButton("Close", null));
    }

    private void saveDock(List<String> order) {
        prefs.edit().putString("dock_order", String.join(",", order)).apply();
        changed.accept("dock");
    }

    private void showDock() {
        List<String> order = dock();
        List<String> names = new ArrayList<>();
        for (int i = 0; i < order.size(); i++) names.add((i + 1) + ". " + label(order.get(i)));
        names.add("Add app to dock…");
        display(new AlertDialog.Builder(activity).setTitle("Dock order · up to 6 apps")
                .setItems(names.toArray(new String[0]), (d, which) -> {
                    if (which == order.size()) showAdd();
                    else for (MainActivity.App app : apps) if (app.pkg.equals(order.get(which))) { showApp(app); break; }
                }).setNegativeButton("Back", (d, w) -> show()));
    }

    void showApp(MainActivity.App app) {
        List<String> order = dock();
        int index = order.indexOf(app.pkg);
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        if (index >= 0) {
            if (index > 0) {
                labels.add("Move left"); actions.add(() -> { Collections.swap(order, index, index - 1); saveDock(order); showDock(); });
            }
            if (index < order.size() - 1) {
                labels.add("Move right"); actions.add(() -> { Collections.swap(order, index, index + 1); saveDock(order); showDock(); });
            }
            if (order.size() > 1) {
                labels.add("Remove from dock"); actions.add(() -> { order.remove(app.pkg); saveDock(order); showDock(); });
            }
        } else {
            if (order.size() < 6) {
                labels.add("Add to dock"); actions.add(() -> { order.add(app.pkg); saveDock(order); showDock(); });
            }
            Set<String> hidden = new HashSet<>(prefs.getStringSet("hidden_grid", Collections.emptySet()));
            boolean isHidden = hidden.contains(app.pkg);
            labels.add(isHidden ? "Show in app grid" : "Hide from app grid");
            actions.add(() -> {
                if (isHidden) hidden.remove(app.pkg); else hidden.add(app.pkg);
                prefs.edit().putStringSet("hidden_grid", hidden).apply(); changed.accept("hidden");
            });
        }
        display(new AlertDialog.Builder(activity).setTitle(app.label)
                .setItems(labels.toArray(new String[0]), (d, which) -> actions.get(which).run())
                .setNegativeButton("Back", (d, w) -> showDock()));
    }

    private void showAdd() {
        List<String> order = dock();
        if (order.size() >= 6) {
            Toast.makeText(activity, "Remove a dock app first (maximum 6)", Toast.LENGTH_LONG).show();
            showDock(); return;
        }
        List<MainActivity.App> available = new ArrayList<>();
        for (MainActivity.App a : apps) if (!order.contains(a.pkg)) available.add(a);
        String[] names = available.stream().map(a -> a.label).toArray(String[]::new);
        display(new AlertDialog.Builder(activity).setTitle("Add app to dock")
                .setItems(names, (d, which) -> {
                    order.add(available.get(which).pkg); saveDock(order); showDock();
                }).setNegativeButton("Back", (d, w) -> showDock()));
    }

    private void showHidden() {
        List<String> order = dock();
        List<MainActivity.App> candidates = new ArrayList<>();
        for (MainActivity.App app : apps) if (!order.contains(app.pkg)) candidates.add(app);
        Set<String> hidden = new HashSet<>(prefs.getStringSet("hidden_grid", Collections.emptySet()));
        String[] names = candidates.stream().map(a -> a.label).toArray(String[]::new);
        boolean[] checked = new boolean[names.length];
        for (int i = 0; i < checked.length; i++) checked[i] = hidden.contains(candidates.get(i).pkg);
        display(new AlertDialog.Builder(activity).setTitle("Checked apps are hidden from grid")
                .setMultiChoiceItems(names, checked, (d, which, value) -> {
                    String pkg = candidates.get(which).pkg;
                    if (value) hidden.add(pkg); else hidden.remove(pkg);
                }).setPositiveButton("Save", (d, which) -> {
                    prefs.edit().putStringSet("hidden_grid", hidden).apply(); changed.accept("hidden");
                }).setNegativeButton("Cancel", null));
    }

    private void showWallpapers() {
        if (wallpapers.length == 0) return;
        String[] names = new String[wallpapers.length];
        for (int i = 0; i < names.length; i++) names[i] = "Nature photo " + (i + 1);
        int current = Math.floorMod(prefs.getInt("wallpaper", 0), wallpapers.length);
        display(new AlertDialog.Builder(activity).setTitle("Choose wallpaper")
                .setSingleChoiceItems(names, current, (d, which) -> {
                    prefs.edit().putInt("wallpaper", which).apply(); changed.accept("wallpaper"); dismiss();
                }).setNegativeButton("Cancel", null));
    }

    private void showRotation() {
        int[] values = {0, 1, 5, 10, 30, 60};
        String[] labels = {"Off · keep selected photo", "Every minute", "Every 5 minutes", "Every 10 minutes", "Every 30 minutes", "Every hour"};
        choice("Wallpaper rotation", labels, values, "rotation_minutes", 10, "rotation");
    }

    private void showDimming() {
        choice("Extra background dimming", new String[]{"None", "10%", "20%", "30%", "40%", "50%"},
                new int[]{0, 10, 20, 30, 40, 50}, "wallpaper_dim", 0, "dim");
    }

    private void showNight() {
        String[] labels = {"Automatic night dimming: " + (prefs.getBoolean("night_enabled", true) ? "On" : "Off"),
                String.format(java.util.Locale.ROOT, "Starts at %02d:00", prefs.getInt("night_start", 20)),
                String.format(java.util.Locale.ROOT, "Ends at %02d:00", prefs.getInt("night_end", 7)),
                "Night dimming: " + prefs.getInt("night_dim", 60) + "%",
                "Preview night · 20 seconds", "Preview day · 20 seconds"};
        boolean active = prefs.getBoolean("night_enabled", true) && HomeAppearance.isNightHour(
                java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY),
                prefs.getInt("night_start", 20), prefs.getInt("night_end", 7));
        display(new AlertDialog.Builder(activity).setTitle("Night appearance · " + (active ? "Active now" : "Day now"))
                .setItems(labels, (d, which) -> {
                    if (which == 0) {
                        prefs.edit().putBoolean("night_enabled", !prefs.getBoolean("night_enabled", true)).apply();
                        changed.accept("night"); showNight();
                    } else if (which == 1 || which == 2) showNightHour(which == 1);
                    else if (which == 3) choice("Night background dimming", new String[]{"10%", "20%", "30%", "40%", "50%", "60%", "70%"},
                            new int[]{10, 20, 30, 40, 50, 60, 70}, "night_dim", 60, "night");
                    else { dismiss(); changed.accept(which == 4 ? "preview_night" : "preview_day"); }
                }).setNegativeButton("Back", (d, w) -> show()));
    }

    private void showNightHour(boolean start) {
        String key = start ? "night_start" : "night_end";
        String other = start ? "night_end" : "night_start";
        String[] labels = new String[24];
        for (int i = 0; i < 24; i++) labels[i] = String.format(java.util.Locale.ROOT, "%02d:00", i);
        display(new AlertDialog.Builder(activity).setTitle(start ? "Night starts at" : "Night ends at")
                .setSingleChoiceItems(labels, prefs.getInt(key, start ? 20 : 7), (d, which) -> {
                    if (which == prefs.getInt(other, start ? 7 : 20)) {
                        Toast.makeText(activity, "Start and end must be different", Toast.LENGTH_SHORT).show();
                        showNightHour(start); return;
                    }
                    prefs.edit().putInt(key, which).apply(); changed.accept("night"); showNight();
                }).setNegativeButton("Cancel", (d, w) -> showNight()));
    }

    private void choice(String title, String[] labels, int[] values, String key, int fallback, String event) {
        int current = prefs.getInt(key, fallback), selected = 0;
        for (int i = 0; i < values.length; i++) if (values[i] == current) selected = i;
        display(new AlertDialog.Builder(activity).setTitle(title)
                .setSingleChoiceItems(labels, selected, (d, which) -> {
                    prefs.edit().putInt(key, values[which]).apply(); changed.accept(event); dismiss();
                }).setNegativeButton("Cancel", null));
    }

    private void confirmReset() {
        display(new AlertDialog.Builder(activity).setTitle("Reset Home customization?")
                .setMessage("Restore the original five favourites, show hidden grid apps and reset wallpaper and motion options. Installed apps and TV settings will not change.")
                .setPositiveButton("Reset", (d, w) -> {
                    prefs.edit().remove("dock_order").remove("hidden_grid").remove("wallpaper")
                            .remove("rotation_minutes").remove("wallpaper_dim").remove("reduced_motion")
                            .remove("night_enabled").remove("night_start").remove("night_end").remove("night_dim")
                            .remove("large_text").apply();
                    changed.accept("reset");
                }).setNegativeButton("Cancel", null));
    }
}

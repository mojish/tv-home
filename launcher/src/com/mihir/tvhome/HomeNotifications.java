package com.mihir.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.widget.Toast;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.lang.ref.WeakReference;

/** Local active notifications. Stores filter preferences, never notification contents. */
public final class HomeNotifications extends NotificationListenerService {
    private static volatile HomeNotifications connected;
    private static WeakReference<AlertDialog> visible = new WeakReference<>(null);
    private static int screenGeneration;

    static void dismiss() {
        screenGeneration++;
        AlertDialog dialog = visible.get();
        if (dialog != null) dialog.dismiss();
        visible.clear();
    }

    private static void display(AlertDialog.Builder builder) {
        dismiss();
        AlertDialog dialog = builder.create();
        visible = new WeakReference<>(dialog);
        dialog.show();
        if (dialog.getListView() != null) {
            android.widget.ListView list = dialog.getListView();
            android.view.ViewGroup.LayoutParams layout = list.getLayoutParams();
            float density = dialog.getContext().getResources().getDisplayMetrics().density;
            layout.height = Math.min((int) (list.getCount() * 48 * density),
                    (int) (dialog.getContext().getResources().getDisplayMetrics().heightPixels * 0.60f));
            list.setLayoutParams(layout);
            list.requestFocus();
        }
    }

    @Override public void onListenerConnected() { connected = this; }
    @Override public void onListenerDisconnected() { if (connected == this) connected = null; }
    @Override public void onDestroy() { if (connected == this) connected = null; super.onDestroy(); }

    static void show(Activity activity) {
        display(new AlertDialog.Builder(activity).setTitle("Notifications")
                .setItems(new String[]{"App notifications", "Notification filters", "BRAVIA notices"}, (d, which) -> {
                    if (which == 0) showApps(activity, false);
                    else if (which == 1) showFilters(activity);
                    else try {
                        activity.startActivity(new Intent("com.sony.dtv.intent.action.SHOW_NOTIFICATIONCENTER")
                                .setPackage("com.sony.dtv.notificationcenter"));
                    } catch (RuntimeException e) {
                        Toast.makeText(activity, "BRAVIA notification center is unavailable", Toast.LENGTH_LONG).show();
                    }
                }).setNegativeButton("Close", null));
    }

    private static SharedPreferences preferences(Activity activity) {
        return activity.getSharedPreferences("notification_filters", MODE_PRIVATE);
    }

    /** Null means an access/error dialog was displayed, not an empty notification list. */
    private static StatusBarNotification[] activeNotifications(Activity activity) {
        HomeNotifications listener = connected;
        if (listener == null) {
            display(new AlertDialog.Builder(activity).setTitle("Enable notification access")
                    .setMessage("Allow Home in Notification access to see active app notifications here. They stay on this TV; Home does not save or send their contents. If already enabled, wait a moment and reopen this menu.")
                    .setPositiveButton("Open notification access", (d, w) -> {
                        try { activity.startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); }
                        catch (RuntimeException e) { Toast.makeText(activity, "Open TV Settings → Apps → Special app access → Notification access", Toast.LENGTH_LONG).show(); }
                    }).setNegativeButton("Cancel", null));
            return null;
        }
        StatusBarNotification[] active;
        try { active = listener.getActiveNotifications(); }
        catch (RuntimeException e) {
            display(new AlertDialog.Builder(activity).setTitle("Notifications unavailable")
                    .setMessage("Notification access is reconnecting. Please reopen this menu in a moment.")
                    .setPositiveButton("Close", null));
            return null;
        }
        if (active == null) active = new StatusBarNotification[0];
        Arrays.sort(active, (a, b) -> Long.compare(b.getPostTime(), a.getPostTime()));
        return active;
    }

    private static void showApps(Activity activity, boolean showAll) {
        StatusBarNotification[] active = activeNotifications(activity);
        if (active == null) return;
        SharedPreferences prefs = preferences(activity);
        Set<String> muted = prefs.getStringSet("muted_apps", Collections.emptySet());
        boolean hideOngoing = prefs.getBoolean("hide_ongoing", true);
        List<StatusBarNotification> items = new ArrayList<>();
        for (StatusBarNotification item : active) {
            boolean ongoing = (item.getNotification().flags &
                    (Notification.FLAG_ONGOING_EVENT | Notification.FLAG_FOREGROUND_SERVICE)) != 0;
            if (showAll || (!muted.contains(item.getPackageName()) && !(hideOngoing && ongoing))) items.add(item);
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(showAll ? "All notifications · " + active.length :
                        "App notifications · " + items.size() + " of " + active.length)
                .setNegativeButton("Back", (d, w) -> show(activity))
                .setNeutralButton("Filters", (d, w) -> showFilters(activity));
        if (showAll) builder.setPositiveButton("Filtered view", (d, w) -> showApps(activity, false));
        else if (items.size() < active.length)
            builder.setPositiveButton("Show all", (d, w) -> showApps(activity, true));
        if (items.isEmpty()) builder.setMessage(active.length == 0 ? "No active app notifications." :
                "All " + active.length + " notifications are hidden by your filters. Show all to review them.");
        else {
            List<StatusBarNotification> clearable = new ArrayList<>();
            for (StatusBarNotification item : items) if (canClear(item)) clearable.add(item);
            String[] labels = new String[items.size() + (clearable.isEmpty() ? 0 : 1)];
            for (int i = 0; i < items.size(); i++) labels[i] = appLabel(activity, items.get(i).getPackageName()) + " · " +
                    items.get(i).getNotification().extras.getCharSequence(Notification.EXTRA_TITLE, "Notification");
            if (!clearable.isEmpty()) labels[items.size()] = "Clear visible notifications · " + clearable.size();
            builder.setItems(labels, (d, which) -> {
                if (which < items.size()) showDetail(activity, items.get(which), showAll);
                else display(new AlertDialog.Builder(activity).setTitle("Clear " + clearable.size() + " notifications?")
                        .setMessage("Dismiss clearable notifications from this list. Hidden and ongoing notifications stay. Apps can send new notifications later.")
                        .setPositiveButton("Clear", (dialog, w) -> clear(activity, clearable, showAll))
                        .setNegativeButton("Cancel", (dialog, w) -> showApps(activity, showAll)));
            });
        }
        display(builder);
    }

    private static void showFilters(Activity activity) {
        SharedPreferences prefs = preferences(activity);
        boolean hide = prefs.getBoolean("hide_ongoing", true);
        int count = prefs.getStringSet("muted_apps", Collections.emptySet()).size();
        display(new AlertDialog.Builder(activity).setTitle("Filters for Home only")
                .setItems(new String[]{"Hide ongoing status: " + (hide ? "On" : "Off"), "Muted apps · " + count}, (d, which) -> {
                    if (which == 0) {
                        prefs.edit().putBoolean("hide_ongoing", !hide).apply();
                        showFilters(activity);
                    } else showMutedApps(activity);
                }).setNegativeButton("Back", (d, w) -> show(activity))
                .setPositiveButton("View notifications", (d, w) -> showApps(activity, false)));
    }

    private static void showMutedApps(Activity activity) {
        SharedPreferences prefs = preferences(activity);
        Set<String> muted = new HashSet<>(prefs.getStringSet("muted_apps", Collections.emptySet()));
        Set<String> packages = new HashSet<>(muted);
        StatusBarNotification[] active = activeNotifications(activity);
        if (active == null) return;
        for (StatusBarNotification item : active) packages.add(item.getPackageName());
        List<String> apps = new ArrayList<>(packages);
        Collections.sort(apps, (a, b) -> appLabel(activity, a).compareToIgnoreCase(appLabel(activity, b)));
        AlertDialog.Builder builder = new AlertDialog.Builder(activity).setTitle("Checked apps are muted in Home")
                .setNegativeButton("Cancel", (d, w) -> showFilters(activity));
        if (apps.isEmpty()) builder.setMessage("No active notification senders or muted apps yet.");
        else {
            String[] labels = new String[apps.size()];
            boolean[] checked = new boolean[apps.size()];
            for (int i = 0; i < apps.size(); i++) {
                labels[i] = appLabel(activity, apps.get(i)); checked[i] = muted.contains(apps.get(i));
            }
            builder.setMultiChoiceItems(labels, checked, (d, which, value) -> {
                if (value) muted.add(apps.get(which)); else muted.remove(apps.get(which));
            }).setPositiveButton("Save", (d, w) -> {
                prefs.edit().putStringSet("muted_apps", muted).apply(); showApps(activity, false);
            });
        }
        display(builder);
    }

    private static String appLabel(Activity activity, String pkg) {
        try { return activity.getPackageManager().getApplicationLabel(
                activity.getPackageManager().getApplicationInfo(pkg, 0)).toString(); }
        catch (Exception e) { return pkg; }
    }

    private static void showDetail(Activity activity, StatusBarNotification item, boolean showAll) {
        Notification n = item.getNotification();
        CharSequence body = n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        if (body == null) body = n.extras.getCharSequence(Notification.EXTRA_TEXT, "No text supplied by this app.");
        AlertDialog.Builder b = new AlertDialog.Builder(activity)
                .setTitle(appLabel(activity, item.getPackageName()) + " · " + n.extras.getCharSequence(Notification.EXTRA_TITLE, "Notification"))
                .setMessage(body).setNegativeButton("Back", (d, w) -> showApps(activity, showAll))
                .setNeutralButton("More", (d, w) -> showActions(activity, item, showAll));
        if (canClear(item)) b.setPositiveButton("Clear", (d, w) -> clear(activity, Collections.singletonList(item), showAll));
        display(b);
    }

    private static void showActions(Activity activity, StatusBarNotification item, boolean showAll) {
        Notification n = item.getNotification();
        boolean muted = preferences(activity).getStringSet("muted_apps", Collections.emptySet()).contains(item.getPackageName());
        List<String> actions = new ArrayList<>();
        if (n.contentIntent != null) actions.add("Open");
        actions.add(muted ? "Unmute in Home" : "Mute in Home");
        display(new AlertDialog.Builder(activity).setTitle("Notification actions")
                .setItems(actions.toArray(new String[0]), (d, which) -> {
            if (n.contentIntent != null && which == 0) {
                try {
                    n.contentIntent.send();
                    HomeNotifications listener = connected;
                    if (listener != null && canClear(item) && (n.flags & Notification.FLAG_AUTO_CANCEL) != 0)
                        listener.cancelNotification(item.getKey());
                } catch (PendingIntent.CanceledException | RuntimeException e) {
                    Toast.makeText(activity, "This notification is no longer available", Toast.LENGTH_SHORT).show();
                }
                return;
            }
            if (muted) setMuted(activity, item.getPackageName(), false);
            else display(new AlertDialog.Builder(activity).setTitle("Mute " + appLabel(activity, item.getPackageName()) + " in Home?")
                    .setMessage("Hide this app's notifications in Home. The app keeps running and its system alerts are unchanged. Undo under Notification filters → Muted apps.")
                    .setPositiveButton("Mute", (dialog, w) -> setMuted(activity, item.getPackageName(), true))
                    .setNegativeButton("Cancel", (dialog, w) -> showDetail(activity, item, showAll)));
        }).setNegativeButton("Back", (d, w) -> showDetail(activity, item, showAll)));
    }

    private static boolean canClear(StatusBarNotification item) {
        return item.isClearable() && (item.getNotification().flags & Notification.FLAG_FOREGROUND_SERVICE) == 0;
    }

    private static void clear(Activity activity, List<StatusBarNotification> selected, boolean showAll) {
        HomeNotifications listener = connected;
        if (listener == null) { activeNotifications(activity); return; }
        Set<String> selectedKeys = new HashSet<>();
        for (StatusBarNotification item : selected) selectedKeys.add(item.getKey());
        StatusBarNotification[] current = activeNotifications(activity);
        if (current == null) return;
        List<String> keys = new ArrayList<>();
        for (StatusBarNotification item : current)
            if (selectedKeys.contains(item.getKey()) && canClear(item)) keys.add(item.getKey());
        try {
            // Never pass null: that would dismiss notifications outside the user's selection.
            if (!keys.isEmpty()) listener.cancelNotifications(keys.toArray(new String[0]));
        } catch (RuntimeException e) {
            Toast.makeText(activity, "Could not clear notifications. Try again shortly.", Toast.LENGTH_LONG).show();
        }
        dismiss();
        int generation = screenGeneration;
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (generation == screenGeneration && !activity.isFinishing() && !activity.isDestroyed() && activity.hasWindowFocus())
                showApps(activity, showAll);
        }, 300);
    }

    private static void setMuted(Activity activity, String pkg, boolean mute) {
        SharedPreferences prefs = preferences(activity);
        Set<String> muted = new HashSet<>(prefs.getStringSet("muted_apps", Collections.emptySet()));
        if (mute) muted.add(pkg); else muted.remove(pkg);
        prefs.edit().putStringSet("muted_apps", muted).apply();
        showApps(activity, false);
    }
}

package com.mihir.tvhome;

/** Local-clock schedule math, independent of Android views. */
final class HomeAppearance {
    private HomeAppearance() { }

    static boolean isNightHour(int hour, int start, int end) {
        if (start == end) return false;
        return start < end ? hour >= start && hour < end : hour >= start || hour < end;
    }

    static float dimAmount(int manual, boolean nightActive, int night) {
        int percent = nightActive ? Math.max(manual, night) : manual;
        return Math.max(0, Math.min(70, percent)) / 100f;
    }
}

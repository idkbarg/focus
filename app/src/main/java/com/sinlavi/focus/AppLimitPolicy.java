package com.sinlavi.focus;

import android.content.SharedPreferences;
import android.content.Context;

final class AppLimitPolicy {
    static final String ACTION_BLOCK = "block";
    static final String ACTION_WARN = "warn";
    static final String PERIOD_DAY = "day";
    static final String PERIOD_WEEK = "week";
    static final String UNLOCK_BREAK = "break";
    static final String UNLOCK_ALLOWANCE = "allowance";
    static final String UNLOCK_PIN = "pin";
    static final String UNLOCK_NONE = "none";

    private AppLimitPolicy() { }

    static String key(String packageName, String name) {
        return "app_limit:" + packageName + ":" + name;
    }

    static boolean enabled(SharedPreferences prefs, String packageName) {
        return prefs.getBoolean(key(packageName, "enabled"), false)
                && prefs.getInt(key(packageName, "minutes"), 0) > 0;
    }

    static long limitMillis(SharedPreferences prefs, String packageName) {
        return prefs.getInt(key(packageName, "minutes"), 0) * 60_000L;
    }

    static String period(SharedPreferences prefs, String packageName) {
        return prefs.getString(key(packageName, "period"), PERIOD_DAY);
    }

    static String action(SharedPreferences prefs, String packageName) {
        return prefs.getString(key(packageName, "action"), ACTION_BLOCK);
    }

    static String unlock(SharedPreferences prefs, String packageName) {
        return prefs.getString(key(packageName, "unlock"), UNLOCK_BREAK);
    }

    static long periodStart(SharedPreferences prefs, String packageName, long now) {
        if (PERIOD_WEEK.equals(period(prefs, packageName))) {
            java.util.Calendar calendar = java.util.Calendar.getInstance();
            calendar.setTimeInMillis(now);
            calendar.set(java.util.Calendar.DAY_OF_WEEK, calendar.getFirstDayOfWeek());
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 0);
            calendar.set(java.util.Calendar.MINUTE, 0);
            calendar.set(java.util.Calendar.SECOND, 0);
            calendar.set(java.util.Calendar.MILLISECOND, 0);
            return calendar.getTimeInMillis();
        }
        return AppUsageStats.startOfDay(now);
    }

    static boolean temporarilyAllowed(SharedPreferences prefs, String packageName,
                                      long usedMillis, long now) {
        String unlock = unlock(prefs, packageName);
        if (UNLOCK_BREAK.equals(unlock) || UNLOCK_PIN.equals(unlock)) {
            return prefs.getLong(key(packageName, "unlock_until"), 0) > now;
        }
        if (UNLOCK_ALLOWANCE.equals(unlock)) {
            int allowanceMinutes = prefs.getInt(key(packageName, "allowance_minutes"), 10);
            long startUsage = prefs.getLong(key(packageName, "allowance_start_usage"), -1);
            if (startUsage >= 0 && usedMillis >= startUsage
                    && usedMillis - startUsage < allowanceMinutes * 60_000L) return true;
            prefs.edit().remove(key(packageName, "allowance_start_usage")).apply();
        }
        return false;
    }

    static long usageSincePeriod(Context context, SharedPreferences prefs,
                                 String packageName, long now) {
        long start = periodStart(prefs, packageName, now);
        return AppUsageStats.query(context, start, now).getOrDefault(packageName, 0L);
    }
}

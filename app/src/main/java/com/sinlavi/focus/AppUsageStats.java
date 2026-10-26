package com.sinlavi.focus;

import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Process;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class AppUsageStats {
    private AppUsageStats() { }

    static boolean hasAccess(Context context) {
        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        if (appOps == null) return false;
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), context.getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    static Map<String, Long> query(Context context, long from, long to) {
        if (!hasAccess(context)) throw new SecurityException("Usage access is not granted.");
        UsageStatsManager manager = (UsageStatsManager)
                context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (manager == null) throw new IllegalStateException("Usage stats service is unavailable.");
        List<UsageStats> stats = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, from, to);
        if (stats == null) throw new IllegalStateException("Usage stats are unavailable.");
        Map<String, Long> results = new HashMap<>();
        for (UsageStats item : stats) {
            if (item.getPackageName() == null) continue;
            results.merge(item.getPackageName(), item.getTotalTimeInForeground(), Long::sum);
        }
        return results;
    }

    static long todayUsage(Context context, String packageName) {
        long now = System.currentTimeMillis();
        java.util.Calendar start = java.util.Calendar.getInstance();
        start.set(java.util.Calendar.HOUR_OF_DAY, 0);
        start.set(java.util.Calendar.MINUTE, 0);
        start.set(java.util.Calendar.SECOND, 0);
        start.set(java.util.Calendar.MILLISECOND, 0);
        return query(context, start.getTimeInMillis(), now).getOrDefault(packageName, 0L);
    }

    static long startOfDay(long time) {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.setTimeInMillis(time);
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0);
        calendar.set(java.util.Calendar.MINUTE, 0);
        calendar.set(java.util.Calendar.SECOND, 0);
        calendar.set(java.util.Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    static String duration(long millis) {
        long minutes = Math.max(0, millis) / 60_000L;
        long hours = minutes / 60;
        long remainder = minutes % 60;
        return hours > 0
                ? CalendarDateUtils.digits(hours) + " ساعت " + CalendarDateUtils.digits(remainder) + " دقیقه"
                : CalendarDateUtils.digits(remainder) + " دقیقه";
    }
}

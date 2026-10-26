package com.sinlavi.focus;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;

import java.util.Set;

public class FocusGuardService extends AccessibilityService {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String currentPackage = "";
    private long usageCacheAt;
    private long usageCacheValue;
    private String usageCachePackage = "";
    private long lastRedirect;
    private String homePackage;
    private final Runnable limitCheck = new Runnable() {
        @Override public void run() {
            if (!currentPackage.isEmpty()) enforceCurrentPackage();
        }
    };

    @Override
    protected void onServiceConnected() {
        ResolveInfo home = getPackageManager().resolveActivity(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                PackageManager.MATCH_DEFAULT_ONLY);
        homePackage = home == null || home.activityInfo == null ? "" : home.activityInfo.packageName;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        String packageName = String.valueOf(event.getPackageName());
        if (packageName.equals(getPackageName()) || packageName.equals("android")
                || packageName.equals("com.android.settings") || packageName.equals("com.android.systemui")
                || packageName.equals(homePackage)) {
            currentPackage = "";
            handler.removeCallbacks(limitCheck);
            return;
        }
        currentPackage = packageName;
        enforceCurrentPackage();
        handler.removeCallbacks(limitCheck);
        handler.postDelayed(limitCheck, 10_000);
    }

    private void enforceCurrentPackage() {
        String packageName = currentPackage;
        if (packageName.isEmpty()) return;
        android.content.SharedPreferences prefs = getSharedPreferences("focus_data", MODE_PRIVATE);
        boolean focusGuardActive = prefs.getBoolean("focus_guard_active", false)
                && prefs.getLong("timer_end_wall", 0) > System.currentTimeMillis();
        Set<String> blocked = prefs.getStringSet("blocked_apps", java.util.Collections.emptySet());
        boolean pomodoroBlock = focusGuardActive && blocked.contains(packageName);
        boolean timeLimitBlock = false;
        if (!pomodoroBlock && AppLimitPolicy.enabled(prefs, packageName)
                && AppLimitPolicy.ACTION_BLOCK.equals(AppLimitPolicy.action(prefs, packageName))
                && AppUsageStats.hasAccess(this)) {
            try {
                long now = System.currentTimeMillis();
                if (now - usageCacheAt > 10_000 || !packageName.equals(usageCachePackage)) {
                    usageCacheValue = AppLimitPolicy.usageSincePeriod(this, prefs, packageName, now);
                    usageCacheAt = now;
                    usageCachePackage = packageName;
                }
                boolean unlocked = AppLimitPolicy.temporarilyAllowed(prefs, packageName,
                        usageCacheValue, now);
                timeLimitBlock = !unlocked
                        && usageCacheValue >= AppLimitPolicy.limitMillis(prefs, packageName);
            } catch (RuntimeException e) {
                android.util.Log.e("FocusGuard", "Unable to check screen-time limit for "
                        + packageName, e);
            }
        }
        if ((pomodoroBlock || timeLimitBlock) && System.currentTimeMillis() - lastRedirect > 900) {
            lastRedirect = System.currentTimeMillis();
            performGlobalAction(GLOBAL_ACTION_HOME);
        }
    }

    @Override
    public void onInterrupt() { }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(limitCheck);
        super.onDestroy();
    }
}

package com.sinlavi.focus;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.view.accessibility.AccessibilityEvent;

import java.util.Set;

public class FocusGuardService extends AccessibilityService {
    private long lastRedirect;
    private String homePackage;

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
                || packageName.equals(homePackage)) return;
        android.content.SharedPreferences prefs = getSharedPreferences("focus_data", MODE_PRIVATE);
        if (!prefs.getBoolean("focus_guard_active", false)
                || prefs.getLong("timer_end_wall", 0) <= System.currentTimeMillis()) return;
        Set<String> blocked = prefs.getStringSet("blocked_apps", java.util.Collections.emptySet());
        if (blocked.contains(packageName) && System.currentTimeMillis() - lastRedirect > 900) {
            lastRedirect = System.currentTimeMillis();
            performGlobalAction(GLOBAL_ACTION_HOME);
        }
    }

    @Override
    public void onInterrupt() { }
}

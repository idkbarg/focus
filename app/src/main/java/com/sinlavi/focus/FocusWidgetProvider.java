package com.sinlavi.focus;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;
import android.widget.RemoteViews;

import androidx.core.content.ContextCompat;

import java.util.Locale;
import java.text.SimpleDateFormat;
import java.util.Date;

public class FocusWidgetProvider extends AppWidgetProvider {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_DATE_CHANGED.equals(action)
                || "android.intent.action.TIME_SET".equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)) {
            updateAll(context);
            return;
        }
        super.onReceive(context, intent);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) updateWidget(context, appWidgetManager, appWidgetId);
    }

    static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, FocusWidgetProvider.class));
        if (ids.length == 0) return;
        for (int id : ids) updateWidget(context, manager, id);
    }

    private static void updateWidget(Context context, AppWidgetManager manager, int widgetId) {
        SharedPreferences prefs = context.getSharedPreferences("focus_data", Context.MODE_PRIVATE);
        long timerEndWall = prefs.getLong("timer_end_wall", 0);
        boolean running = timerEndWall > System.currentTimeMillis();
        boolean expired = timerEndWall > 0 && !running;
        long duration = prefs.getLong("timer_duration", prefs.getInt("work_minutes", 25) * 60_000L);
        long remaining = running
                ? Math.max(0, timerEndWall - System.currentTimeMillis())
                : expired ? 0 : prefs.getLong("timer_remaining", duration);
        long seconds = (remaining + 999) / 1000;
        String phase = prefs.getString("timer_phase", "focus");

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.focus_widget);
        if (running && Build.VERSION.SDK_INT >= 24) {
            views.setChronometer(R.id.widget_time, SystemClock.elapsedRealtime() + remaining,
                    "%s", true);
            views.setChronometerCountDown(R.id.widget_time, true);
        } else {
            views.setTextViewText(R.id.widget_time, String.format(Locale.getDefault(), "%02d:%02d",
                    seconds / 60, seconds % 60));
        }
        views.setTextViewText(R.id.widget_phase, phaseLabel(phase));
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        int sessions = prefs.getInt("stats_count:" + today, 0);
        long studyMinutes = prefs.getLong("stats_study:" + today, 0) / 60_000L;
        views.setTextViewText(R.id.widget_stats, "امروز · "
                + CalendarDateUtils.digits(sessions) + " جلسه · "
                + CalendarDateUtils.digits(studyMinutes) + " دقیقه");
        views.setImageViewResource(R.id.widget_toggle,
                running ? R.drawable.ic_pause : R.drawable.ic_play_arrow);
        views.setInt(R.id.widget_toggle, "setColorFilter",
                ContextCompat.getColor(context, R.color.focus_on_primary));
        views.setContentDescription(R.id.widget_toggle,
                running ? "توقف موقت زمان‌سنج تمرکز" : "شروع زمان‌سنج تمرکز");

        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        open.putExtra(MainActivity.EXTRA_WIDGET_ACTION, MainActivity.WIDGET_ACTION_OPEN_APP);
        views.setOnClickPendingIntent(R.id.widget_open, PendingIntent.getActivity(context, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        Intent toggle = new Intent(context, MainActivity.class);
        toggle.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        toggle.putExtra(MainActivity.EXTRA_WIDGET_ACTION, MainActivity.WIDGET_ACTION_TOGGLE_TIMER);
        views.setOnClickPendingIntent(R.id.widget_toggle, PendingIntent.getActivity(context, 2, toggle,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        manager.updateAppWidget(widgetId, views);
    }

    private static String phaseLabel(String phase) {
        if ("short_break".equals(phase)) return "استراحت کوتاه";
        if ("long_break".equals(phase)) return "استراحت بلند";
        return "جلسهٔ تمرکز";
    }
}

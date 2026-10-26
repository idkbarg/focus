package com.sinlavi.focus;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

public class CalendarClockWidgetProvider extends AppWidgetProvider {
    static final String ACTION_CYCLE_CALENDAR = "com.sinlavi.focus.CYCLE_CALENDAR";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_CYCLE_CALENDAR.equals(intent.getAction())) {
            SharedPreferences prefs = context.getSharedPreferences("focus_data", Context.MODE_PRIVATE);
            int mode = Math.max(CalendarDateUtils.JALALI, Math.min(CalendarDateUtils.HIJRI,
                    prefs.getInt("clock_calendar_mode", CalendarDateUtils.JALALI)));
            prefs.edit().putInt("clock_calendar_mode", (mode + 1) % 3).apply();
            updateAll(context);
            return;
        }
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
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) updateWidget(context, manager, id);
    }

    static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, CalendarClockWidgetProvider.class));
        for (int id : ids) updateWidget(context, manager, id);
    }

    private static void updateWidget(Context context, AppWidgetManager manager, int id) {
        SharedPreferences prefs = context.getSharedPreferences("focus_data", Context.MODE_PRIVATE);
        int mode = Math.max(CalendarDateUtils.JALALI, Math.min(CalendarDateUtils.HIJRI,
                prefs.getInt("clock_calendar_mode", CalendarDateUtils.JALALI)));
        String[] dates = CalendarDateUtils.allDates(System.currentTimeMillis());
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.calendar_clock_widget);
        views.setTextViewText(R.id.clock_widget_jalali, "شمسی  ·  " + dates[CalendarDateUtils.JALALI]);
        views.setTextViewText(R.id.clock_widget_gregorian, "میلادی  ·  " + dates[CalendarDateUtils.GREGORIAN]);
        views.setTextViewText(R.id.clock_widget_hijri, "قمری محاسباتی  ·  " + dates[CalendarDateUtils.HIJRI]);
        int selected = android.graphics.Color.rgb(60, 180, 230);
        int muted = android.graphics.Color.rgb(178, 182, 193);
        views.setTextColor(R.id.clock_widget_jalali, mode == CalendarDateUtils.JALALI ? selected : muted);
        views.setTextColor(R.id.clock_widget_gregorian, mode == CalendarDateUtils.GREGORIAN ? selected : muted);
        views.setTextColor(R.id.clock_widget_hijri, mode == CalendarDateUtils.HIJRI ? selected : muted);
        views.setTextViewText(R.id.clock_widget_cycle,
                new String[]{"تقویم شمسی", "تقویم میلادی", "تقویم قمری"}[mode]);

        Intent open = new Intent(context, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPending = PendingIntent.getActivity(context, 31, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.clock_widget_open, openPending);

        Intent cycle = new Intent(context, CalendarClockWidgetProvider.class)
                .setAction(ACTION_CYCLE_CALENDAR);
        PendingIntent cyclePending = PendingIntent.getBroadcast(context, 32, cycle,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.clock_widget_cycle, cyclePending);
        manager.updateAppWidget(id, views);
    }
}

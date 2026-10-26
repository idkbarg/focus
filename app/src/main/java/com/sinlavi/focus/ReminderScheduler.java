package com.sinlavi.focus;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;

final class ReminderScheduler {
    private static final String TAG = "ReminderScheduler";
    private static final String PREFS = "focus_data";

    private ReminderScheduler() { }

    static void schedule(Context context, String id, String title, String message, long atMillis) {
        if (atMillis <= System.currentTimeMillis()) return;
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            Log.e(TAG, "Alarm service is unavailable; reminder was not scheduled.");
            return;
        }
        Intent intent = new Intent(context, ReminderReceiver.class)
                .setAction(ReminderReceiver.ACTION_SHOW_REMINDER)
                .setData(Uri.parse("focus://reminder/" + Uri.encode(id)))
                .putExtra(ReminderReceiver.EXTRA_ID, id)
                .putExtra(ReminderReceiver.EXTRA_TITLE, title)
                .putExtra(ReminderReceiver.EXTRA_MESSAGE, message);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pendingIntent);
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, atMillis, pendingIntent);
        }
    }

    static void cancel(Context context, String id) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        Intent intent = new Intent(context, ReminderReceiver.class)
                .setAction(ReminderReceiver.ACTION_SHOW_REMINDER)
                .setData(Uri.parse("focus://reminder/" + Uri.encode(id)));
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
        }
    }

    static void rescheduleAll(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            if (!entry.getKey().startsWith("tasks:") || !(entry.getValue() instanceof String)) continue;
            try {
                JSONArray tasks = new JSONArray((String) entry.getValue());
                for (int i = 0; i < tasks.length(); i++) {
                    JSONObject task = tasks.getJSONObject(i);
                    if (task.optBoolean("done")) continue;
                    String id = task.optString("id");
                    String title = task.optString("title");
                    long dueAt = task.optLong("dueAt");
                    long remindAt = task.optLong("remindAt");
                    schedule(context, id + ":deadline", "مهلت کار تمام شد", title, dueAt);
                    schedule(context, id + ":reminder", "یادآوری کار", title, remindAt);
                }
            } catch (JSONException e) {
                Log.e(TAG, "Could not restore task reminders from " + entry.getKey(), e);
            }
        }
        try {
            JSONArray reminders = new JSONArray(prefs.getString("saved_reminders", "[]"));
            for (int i = 0; i < reminders.length(); i++) {
                JSONObject reminder = reminders.getJSONObject(i);
                schedule(context, reminder.optString("id"), reminder.optString("title"),
                        reminder.optString("details"), reminder.optLong("at"));
            }
        } catch (JSONException e) {
            Log.e(TAG, "Could not restore saved reminders.", e);
        }
        long examEnd = prefs.getLong("exam_end_wall", 0);
        if (examEnd > System.currentTimeMillis()) {
            schedule(context, "exam_timer", "زمان آزمون تمام شد",
                    prefs.getString("exam_title", "آزمون"), examEnd);
        }
    }
}

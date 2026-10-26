package com.sinlavi.focus;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ReminderReceiver extends BroadcastReceiver {
    static final String ACTION_SHOW_REMINDER = "com.sinlavi.focus.SHOW_REMINDER";
    static final String EXTRA_ID = "reminder_id";
    static final String EXTRA_TITLE = "reminder_title";
    static final String EXTRA_MESSAGE = "reminder_message";
    private static final String CHANNEL_ID = "focus_reminders";
    private static final String TAG = "ReminderReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            ReminderScheduler.rescheduleAll(context);
            return;
        }
        if (ACTION_SHOW_REMINDER.equals(intent.getAction())) {
            removeDeliveredReminder(context, intent.getStringExtra(EXTRA_ID));
            showNotification(context, intent.getStringExtra(EXTRA_TITLE),
                    intent.getStringExtra(EXTRA_MESSAGE));
        }
    }

    private void removeDeliveredReminder(Context context, String id) {
        if (id == null || id.contains(":") || "exam_timer".equals(id)) return;
        android.content.SharedPreferences prefs =
                context.getSharedPreferences("focus_data", Context.MODE_PRIVATE);
        try {
            JSONArray saved = new JSONArray(prefs.getString("saved_reminders", "[]"));
            JSONArray remaining = new JSONArray();
            for (int i = 0; i < saved.length(); i++) {
                JSONObject item = saved.optJSONObject(i);
                if (item != null && !id.equals(item.optString("id"))) remaining.put(item);
            }
            prefs.edit().putString("saved_reminders", remaining.toString()).apply();
        } catch (JSONException e) {
            Log.e(TAG, "Unable to remove delivered reminder from storage.", e);
        }
    }

    static void showNotification(Context context, String title, String message) {
        if (Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Notification permission is not granted; reminder cannot be displayed.");
            return;
        }
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            Log.e(TAG, "Notification service is unavailable.");
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(new NotificationChannel(CHANNEL_ID,
                    "یادآورها و کارها", NotificationManager.IMPORTANCE_HIGH));
        }
        Intent open = new Intent(context, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_OPEN_REMINDERS, true);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);
        builder.setSmallIcon(R.drawable.ic_focus)
                .setContentTitle(title == null ? "یادآوری" : title)
                .setContentText(message == null ? "" : message)
                .setStyle(new Notification.BigTextStyle().bigText(message == null ? "" : message))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setPriority(Notification.PRIORITY_HIGH);
        try {
            manager.notify((int) (System.currentTimeMillis() & 0x7fffffff), builder.build());
        } catch (SecurityException e) {
            Log.w(TAG, "Notification delivery was blocked by the system.", e);
        }
    }
}

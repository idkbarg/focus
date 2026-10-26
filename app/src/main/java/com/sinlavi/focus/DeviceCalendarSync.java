package com.sinlavi.focus;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.provider.CalendarContract;

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

final class DeviceCalendarSync {
    static final class CalendarInfo {
        final long id;
        final String name;
        final String account;

        CalendarInfo(long id, String name, String account) {
            this.id = id;
            this.name = name == null || name.trim().isEmpty() ? "تقویم" : name;
            this.account = account == null ? "" : account;
        }

        @Override public String toString() {
            return account.isEmpty() ? name : name + " · " + account;
        }
    }

    private DeviceCalendarSync() { }

    static List<CalendarInfo> writableCalendars(Context context) {
        List<CalendarInfo> calendars = new ArrayList<>();
        String[] projection = {
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME
        };
        String selection = CalendarContract.Calendars.VISIBLE + " = 1 AND "
                + CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL + " >= ?";
        String[] selectionArgs = {
                String.valueOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR)
        };
        try (Cursor cursor = context.getContentResolver().query(
                CalendarContract.Calendars.CONTENT_URI, projection, selection, selectionArgs,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME + " ASC")) {
            if (cursor != null) {
                int idColumn = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID);
                int nameColumn = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME);
                int accountColumn = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME);
                while (cursor.moveToNext()) {
                    calendars.add(new CalendarInfo(cursor.getLong(idColumn),
                            cursor.getString(nameColumn), cursor.getString(accountColumn)));
                }
            }
        }
        return calendars;
    }

    static long upsertTask(Context context, long calendarId, long eventId, String title,
                           String description, long dueAt, long remindAt, boolean done) {
        ContentValues values = new ContentValues();
        values.put(CalendarContract.Events.CALENDAR_ID, calendarId);
        values.put(CalendarContract.Events.TITLE, (done ? "✓ " : "") + title);
        values.put(CalendarContract.Events.DESCRIPTION, description);
        values.put(CalendarContract.Events.DTSTART, dueAt);
        values.put(CalendarContract.Events.DTEND, dueAt + 5 * 60_000L);
        values.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());
        values.put(CalendarContract.Events.HAS_ALARM, remindAt > 0 ? 1 : 0);
        values.put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY);

        android.net.Uri eventUri;
        boolean created = false;
        if (eventId > 0) {
            eventUri = CalendarContract.Events.CONTENT_URI.buildUpon()
                    .appendPath(String.valueOf(eventId)).build();
            if (context.getContentResolver().update(eventUri, values, null, null) == 0) {
                eventId = 0;
            }
        }
        if (eventId <= 0) {
            eventUri = context.getContentResolver().insert(CalendarContract.Events.CONTENT_URI, values);
            if (eventUri == null) throw new IllegalStateException("Calendar did not return an event URI.");
            eventId = Long.parseLong(eventUri.getLastPathSegment());
            created = true;
        }

        try {
            android.net.Uri remindersUri = CalendarContract.Reminders.CONTENT_URI;
            context.getContentResolver().delete(remindersUri,
                    CalendarContract.Reminders.EVENT_ID + " = ?", new String[]{String.valueOf(eventId)});
            if (remindAt > 0 && remindAt <= dueAt) {
                ContentValues reminder = new ContentValues();
                reminder.put(CalendarContract.Reminders.EVENT_ID, eventId);
                reminder.put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT);
                reminder.put(CalendarContract.Reminders.MINUTES,
                        Math.max(0, (int) ((dueAt - remindAt) / 60_000L)));
                android.net.Uri reminderUri = context.getContentResolver().insert(remindersUri, reminder);
                if (reminderUri == null) {
                    throw new IllegalStateException("Calendar did not accept the reminder.");
                }
            }
        } catch (RuntimeException e) {
            if (created) {
                try {
                    deleteEvent(context, eventId);
                } catch (RuntimeException cleanupError) {
                    e.addSuppressed(cleanupError);
                }
            }
            throw e;
        }
        return eventId;
    }

    static void deleteEvent(Context context, long eventId) {
        if (eventId <= 0) return;
        android.net.Uri eventUri = CalendarContract.Events.CONTENT_URI.buildUpon()
                .appendPath(String.valueOf(eventId)).build();
        context.getContentResolver().delete(eventUri, null, null);
    }
}

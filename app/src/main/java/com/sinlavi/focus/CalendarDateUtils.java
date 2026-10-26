package com.sinlavi.focus;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

final class CalendarDateUtils {
    static final int JALALI = 0;
    static final int GREGORIAN = 1;
    static final int HIJRI = 2;

    private static final String[] JALALI_MONTHS = {
            "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
            "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    };
    private static final String[] HIJRI_MONTHS = {
            "محرم", "صفر", "ربیع‌الاول", "ربیع‌الثانی", "جمادی‌الاول", "جمادی‌الثانی",
            "رجب", "شعبان", "رمضان", "شوال", "ذی‌القعده", "ذی‌الحجه"
    };

    private CalendarDateUtils() { }

    static String[] allDates(long timeMillis) {
        Calendar date = Calendar.getInstance();
        date.setTimeInMillis(timeMillis);
        int year = date.get(Calendar.YEAR);
        int month = date.get(Calendar.MONTH) + 1;
        int day = date.get(Calendar.DAY_OF_MONTH);
        int[] jalali = gregorianToJalali(year, month, day);
        int[] hijri = gregorianToHijri(year, month, day);
        String weekday = date.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG,
                new Locale("fa", "IR"));
        String gregorian = new SimpleDateFormat("d MMMM yyyy", Locale.US)
                .format(new Date(timeMillis));
        return new String[]{
                weekday + "، " + digits(jalali[2]) + " " + JALALI_MONTHS[jalali[1] - 1]
                        + " " + digits(jalali[0]),
                digits(gregorian),
                digits(hijri[2]) + " " + HIJRI_MONTHS[hijri[1] - 1] + " "
                        + digits(hijri[0]) + " هـ"
        };
    }

    static String[] datesForDay(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month - 1, day, 12, 0);
        return allDates(calendar.getTimeInMillis());
    }

    static String dateForMode(long timeMillis, int mode) {
        String[] dates = allDates(timeMillis);
        return dates[Math.max(JALALI, Math.min(HIJRI, mode))];
    }

    static String digits(Object value) {
        return String.valueOf(value).replace('0', '۰').replace('1', '۱').replace('2', '۲')
                .replace('3', '۳').replace('4', '۴').replace('5', '۵')
                .replace('6', '۶').replace('7', '۷').replace('8', '۸').replace('9', '۹');
    }

    private static int[] gregorianToJalali(int year, int month, int day) {
        int[] gregorianMonthDays = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
        int[] jalaliMonthDays = {31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29};
        int gy = year - 1600;
        int gm = month - 1;
        int gd = day - 1;
        int dayNumber = 365 * gy + (gy + 3) / 4 - (gy + 99) / 100 + (gy + 399) / 400;
        for (int i = 0; i < gm; i++) dayNumber += gregorianMonthDays[i];
        if (gm > 1 && (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0))) dayNumber++;
        dayNumber += gd;
        int jalaliDayNumber = dayNumber - 79;
        int jalaliYear = 979 + 33 * (jalaliDayNumber / 12053);
        jalaliDayNumber %= 12053;
        jalaliYear += 4 * (jalaliDayNumber / 1461);
        jalaliDayNumber %= 1461;
        if (jalaliDayNumber >= 366) {
            jalaliYear += (jalaliDayNumber - 1) / 365;
            jalaliDayNumber = (jalaliDayNumber - 1) % 365;
        }
        int jalaliMonth = 0;
        while (jalaliMonth < 11 && jalaliDayNumber >= jalaliMonthDays[jalaliMonth]) {
            jalaliDayNumber -= jalaliMonthDays[jalaliMonth++];
        }
        return new int[]{jalaliYear, jalaliMonth + 1, jalaliDayNumber + 1};
    }

    private static int[] gregorianToHijri(int year, int month, int day) {
        int a = (14 - month) / 12;
        int y = year + 4800 - a;
        int m = month + 12 * a - 3;
        int julianDay = day + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045;
        int hijriYear = (30 * (julianDay - 1948440) + 10646) / 10631;
        int firstOfYear = hijriToJulianDay(hijriYear, 1, 1);
        int hijriMonth = Math.min(12, Math.max(1, (int) Math.ceil((julianDay - 29 - firstOfYear) / 29.5) + 1));
        int hijriDay = julianDay - hijriToJulianDay(hijriYear, hijriMonth, 1) + 1;
        return new int[]{hijriYear, hijriMonth, hijriDay};
    }

    private static int hijriToJulianDay(int year, int month, int day) {
        return day + (int) Math.ceil(29.5 * (month - 1)) + (year - 1) * 354
                + (3 + 11 * year) / 30 + 1948440;
    }
}

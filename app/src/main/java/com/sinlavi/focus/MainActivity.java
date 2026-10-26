package com.sinlavi.focus;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfRenderer;
import android.app.NotificationManager;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.TextClock;
import android.widget.HorizontalScrollView;
import android.widget.GridLayout;
import android.widget.PopupMenu;
import android.widget.Toast;
import android.widget.CheckBox;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.HashMap;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final int PDF_PAGE_SIZE = 30;
    private static final int APP_DRAWER_PAGE_SIZE = 48;
    private static final int REQUEST_PDF = 11;
    private static final int REQUEST_PDF_FOLDER = 13;
    private static final int REQUEST_HOME_ROLE = 16;
    private static final int REQUEST_CALENDAR_ACCESS = 17;
    private static final int REQUEST_APP_WIDGET_PICK = 18;
    private static final int REQUEST_APP_WIDGET_CONFIGURE = 19;
    private static final int REQUEST_USAGE_ACCESS = 20;
    private static final int APP_WIDGET_HOST_ID = 0xF062;
    private static final String PREFS = "focus_data";
    public static final String EXTRA_WIDGET_ACTION = "widget_action";
    public static final String WIDGET_ACTION_TOGGLE_TIMER = "toggle_timer";
    public static final String WIDGET_ACTION_OPEN_APP = "open_app";
    public static final String EXTRA_OPEN_REMINDERS = "open_reminders";
    private static final String PDF_LIST = "pdf_library";
    private static final String BLOCKED_APPS = "blocked_apps";
    private static final long TICK_MS = 1000;
    private static final int ICON_SIZE_DP = 20;
    private static final int ICON_TARGET_DP = 48;
    private static final String HOME_APP_SHORTCUTS = "home_app_shortcuts";
    private static final String SAVED_REMINDERS = "saved_reminders";
    private static final String TASKS_PREFIX = "tasks:";
    private static final String EXAM_REMINDER_ID = "exam_timer";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<PdfItem> pdfs = new ArrayList<>();
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService pdfExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService calendarExecutor = Executors.newSingleThreadExecutor();
    private AppWidgetHost appWidgetHost;
    private AppWidgetManager appWidgetManager;
    private boolean widgetHostListening;
    private final Map<String, Bitmap> pdfCoverCache = new HashMap<>();
    private final Set<String> pdfCoverLoads = new HashSet<>();
    private android.content.SharedPreferences prefs;
    private LinearLayout screen;
    private TextView timerText;
    private TextView timerStatus;
    private MaterialButton timerButton;
    private TimerProgressView timerProgress;
    private TextView statsSummary;
    private Uri pendingExternalPdf;
    private boolean overlayReceiverRegistered;
    private MaterialButton appDrawerMore;
    private final BroadcastReceiver overlayControlReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String control = intent.getStringExtra(PdfCompanionOverlayService.EXTRA_CONTROL);
            if (PdfCompanionOverlayService.CONTROL_TIMER.equals(control)) toggleTimer();
        }
    };
    private String activeScreen = "home";
    private Runnable backAction;
    private int pdfVisibleLimit = PDF_PAGE_SIZE;
    private long durationMillis = 25 * 60_000L;
    private long remainingMillis = durationMillis;
    private long endAtElapsed;
    private boolean timerRunning;
    private String timerPhase = "focus";
    private boolean scanningPdfs;
    private boolean isHome = true;
    private Typeface appTypeface;
    private long examDurationMillis = 90 * 60_000L;
    private long examRemainingMillis = examDurationMillis;
    private long examEndAtElapsed;
    private boolean examRunning;
    private String examTitle = "آزمون";
    private TextView examTimeText;
    private TextView examHomeSummary;
    private MaterialButton examStartButton;
    private TimerProgressView examProgress;

    private final Runnable examTick = new Runnable() {
        @Override
        public void run() {
            if (!examRunning) return;
            examRemainingMillis = Math.max(0, examEndAtElapsed - SystemClock.elapsedRealtime());
            updateExamDisplay();
            if (examRemainingMillis == 0) {
                finishExamTimer();
                return;
            }
            handler.postDelayed(this, TICK_MS);
        }
    };

    private final Runnable timerTick = new Runnable() {
        @Override
        public void run() {
            if (!timerRunning) return;
            remainingMillis = Math.max(0, endAtElapsed - SystemClock.elapsedRealtime());
            updateTimerDisplay();
            if (remainingMillis == 0) {
                finishTimerPhase();
                return;
            }
            handler.postDelayed(this, TICK_MS);
        }
    };

    @Override
    protected void attachBaseContext(Context base) {
        Locale.setDefault(new Locale("fa", "IR"));
        android.content.res.Configuration configuration =
                new android.content.res.Configuration(base.getResources().getConfiguration());
        configuration.setLocale(new Locale("fa", "IR"));
        configuration.setLayoutDirection(new Locale("fa", "IR"));
        super.attachBaseContext(base.createConfigurationContext(configuration));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppDiagnostics.initialize(this);
        appTypeface = ResourcesCompat.getFont(this, R.font.vazirmatn_regular);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        appWidgetManager = AppWidgetManager.getInstance(this);
        appWidgetHost = new AppWidgetHost(this, APP_WIDGET_HOST_ID);
        ContextCompat.registerReceiver(this, overlayControlReceiver,
                new IntentFilter(PdfCompanionOverlayService.ACTION_CONTROL),
                ContextCompat.RECEIVER_NOT_EXPORTED);
        overlayReceiverRegistered = true;
        createReminderChannel();
        timerPhase = prefs.getString("timer_phase", "focus");
        durationMillis = prefs.getLong("timer_duration", durationForPhase(timerPhase));
        remainingMillis = prefs.getLong("timer_remaining", durationMillis);
        long savedEnd = prefs.getLong("timer_end_wall", 0);
        if (savedEnd > 0) {
            remainingMillis = savedEnd - System.currentTimeMillis();
            if (remainingMillis > 0) {
                endAtElapsed = SystemClock.elapsedRealtime() + remainingMillis;
                timerRunning = true;
            } else {
                remainingMillis = 0;
                prefs.edit().remove("timer_end_wall").putLong("timer_remaining", 0)
                        .putBoolean("focus_guard_active", false).apply();
                if ("focus".equals(timerPhase)) recordCompletedFocus();
                timerPhase = nextPhase(timerPhase);
                durationMillis = durationForPhase(timerPhase);
                remainingMillis = durationMillis;
                prefs.edit().putString("timer_phase", timerPhase).putLong("timer_duration", durationMillis)
                        .putLong("timer_remaining", remainingMillis).apply();
            }
        }
        restoreExamTimer();
        ReminderScheduler.rescheduleAll(this);
        buildHome();
        handleWidgetAction(getIntent());
        if (timerRunning) handler.post(timerTick);
        if (examRunning) handler.post(examTick);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (prefs != null && prefs.getBoolean("external_pdf_active", false)) {
            prefs.edit().putBoolean("external_pdf_active", false).apply();
            stopService(new Intent(this, PdfCompanionOverlayService.class));
        }
        if (pendingExternalPdf != null) {
            Uri pdfUri = pendingExternalPdf;
            pendingExternalPdf = null;
            if (canDrawOverlays()) {
                openPdfInSystemViewer(pdfUri, true);
            } else {
                new MaterialAlertDialogBuilder(this)
                        .setMessage("مجوز پنل شناور داده نشد. PDF بدون پنل باز شود؟")
                        .setNegativeButton("لغو", null)
                        .setPositiveButton("بازکردن PDF", (dialog, which) ->
                                openPdfInSystemViewer(pdfUri, false))
                        .show();
            }
        }
        if (prefs != null && timerRunning) {
            prefs.edit().putBoolean("focus_guard_active", true).apply();
        }
        if ("Focus guard".equals(activeScreen)) showFocusGuard();
        else if ("Screen time".equals(activeScreen)) showScreenTimeDashboard();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (appWidgetHost != null && !widgetHostListening) {
            appWidgetHost.startListening();
            widgetHostListening = true;
        }
    }

    @Override
    protected void onStop() {
        if (appWidgetHost != null && widgetHostListening) {
            appWidgetHost.stopListening();
            widgetHostListening = false;
        }
        super.onStop();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleWidgetAction(intent);
    }

    private void handleWidgetAction(Intent intent) {
        if (intent == null) return;
        if (intent.getBooleanExtra(EXTRA_OPEN_REMINDERS, false)) {
            intent.removeExtra(EXTRA_OPEN_REMINDERS);
            showReminders();
            return;
        }
        if (!WIDGET_ACTION_TOGGLE_TIMER.equals(intent.getStringExtra(EXTRA_WIDGET_ACTION))) return;
        intent.removeExtra(EXTRA_WIDGET_ACTION);
        toggleTimer();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (timerRunning) {
            remainingMillis = Math.max(0, endAtElapsed - SystemClock.elapsedRealtime());
            prefs.edit().putLong("timer_end_wall", System.currentTimeMillis() + remainingMillis).apply();
        } else if (prefs != null) {
            prefs.edit().putLong("timer_remaining", remainingMillis).apply();
        }
        if (examRunning) {
            examRemainingMillis = Math.max(0, examEndAtElapsed - SystemClock.elapsedRealtime());
            prefs.edit().putLong("exam_end_wall", System.currentTimeMillis() + examRemainingMillis)
                    .putLong("exam_remaining", examRemainingMillis).apply();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(timerTick);
        handler.removeCallbacks(examTick);
        ioExecutor.shutdownNow();
        if (overlayReceiverRegistered) {
            unregisterReceiver(overlayControlReceiver);
            overlayReceiverRegistered = false;
        }
        pdfExecutor.shutdown();
        calendarExecutor.shutdown();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (!isHome) {
            navigateBack();
            return;
        }
        super.onBackPressed();
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private TextView text(String value, int size, int textColor) {
        TextView view = new TextView(this);
        view.setText(fa(value));
        view.setTextSize(size);
        view.setTextColor(textColor);
        if (appTypeface != null) view.setTypeface(appTypeface);
        return view;
    }

    private MaterialButton button(String label) {
        MaterialButton button = new MaterialButton(this);
        button.setText(fa(label));
        button.setTextSize(14);
        button.setCornerRadius(dp(14));
        if (appTypeface != null) button.setTypeface(appTypeface);
        return button;
    }

    private String fa(String value) {
        if (value == null) return "";
        switch (value) {
            case "Timer": return "تمرکز";
            case "Notes": return "یادداشت‌ها";
            case "Screen time": return "زمان استفاده";
            case "Note editor": return "ویرایش یادداشت";
            case "Reminders": return "یادآورها";
            case "Exam timer": return "زمان‌سنج آزمون";
            case "PDFs": return "PDF";
            case "PDF library": return "کتابخانهٔ PDF";
            case "Pomodoro settings": return "تنظیمات تمرکز";
            case "Make room for what matters.": return "برای کارهای مهم وقت بساز.";
            case "Focus guard": return "محافظ تمرکز";
            case "Start": return "شروع";
            case "Pause": return "توقف موقت";
            case "Resume": return "ادامه";
            case "Pause timer": return "توقف موقت زمان‌سنج";
            case "Start or resume timer": return "شروع یا ادامهٔ زمان‌سنج";
            case "Reset": return "بازنشانی";
            case "Skip": return "ردکردن";
            case "LAST PDF": return "آخرین PDF";
            case "Browse": return "مرور";
            case "Open": return "بازکردن";
            case "Page": return "صفحه";
            case "Rotate page": return "چرخاندن صفحه";
            case "Reset view": return "بازنشانی نما";
            case "Add five minutes": return "افزودن پنج دقیقه";
            case "More PDF options": return "گزینه‌های بیشتر PDF";
            case "Hide reader controls": return "پنهان‌کردن نوار ابزار";
            case "All apps": return "همهٔ برنامه‌ها";
            case "More apps": return "برنامه‌های بیشتر";
            case "Load more": return "نمایش بیشتر";
            case "No apps found": return "برنامه‌ای پیدا نشد";
            case "Search apps": return "جست‌وجوی برنامه‌ها";
            case "Add PDF": return "افزودن PDF";
            case "Scan folder": return "اسکن پوشه";
            case "Next": return "بعدی";
            case "Previous": return "قبلی";
            case "Add files": return "افزودن فایل";
            case "Add an app shortcut": return "افزودن میان‌بر برنامه";
            case "Your PDF library is empty. Add a document to start reading.":
                return "کتابخانهٔ PDF خالی است. برای شروع مطالعه یک سند اضافه کنید.";
            case "Set your study and rest rhythm. Changes apply to the next timer.":
                return "زمان تمرکز و استراحت را تنظیم کنید؛ تغییرات در نوبت بعد اعمال می‌شوند.";
            case "Save timer settings": return "ذخیرهٔ تنظیمات زمان‌سنج";
            case "Focus guard · choose distracting apps": return "محافظ تمرکز · انتخاب برنامه‌های مزاحم";
            case "During-focus system controls": return "کنترل‌های هنگام تمرکز";
            case "Session behavior": return "رفتار جلسه";
            case "Manage Accessibility service": return "مدیریت سرویس دسترس‌پذیری";
            case "No more apps are available to add.": return "برنامهٔ دیگری برای افزودن پیدا نشد.";
            case "This app is no longer available.": return "این برنامه دیگر در دسترس نیست.";
            case "This app could not be opened.": return "بازکردن این برنامه ممکن نشد.";
            case "Remove this app from Quick access?": return "این برنامه از دسترسی سریع حذف شود؟";
            case "Cancel": return "انصراف";
            case "Remove": return "حذف";
            case "Delete": return "پاک‌کردن";
            case "Reconnect this PDF": return "اتصال دوباره به PDF";
            case "This cannot be undone.": return "این کار برگشت‌پذیر نیست.";
            case "Delete this day's note?": return "یادداشت این روز پاک شود؟";
            case "Open note": return "بازکردن یادداشت";
            case "Delete note": return "پاک‌کردن یادداشت";
            case "Timer settings saved.": return "تنظیمات زمان‌سنج ذخیره شد.";
            case "The PDF file on your device will not be deleted.":
                return "فایل PDF از دستگاه شما پاک نمی‌شود.";
            case "Remove from library?": return "از کتابخانه حذف شود؟";
            case "Enter a valid page number": return "شمارهٔ صفحه معتبر نیست.";
            case "PDF": return "PDF";
            case "Do Not Disturb": return "مزاحم نشوید";
            case "Airplane mode": return "حالت پرواز";
            case "Auto-start breaks": return "شروع خودکار استراحت";
            case "Auto-start focus": return "شروع خودکار تمرکز";
            case "Gentle end chime": return "صدای پایان ملایم";
            case "Study time (minutes)": return "مدت تمرکز (دقیقه)";
            case "Short break (minutes)": return "استراحت کوتاه (دقیقه)";
            case "Long break (minutes)": return "استراحت بلند (دقیقه)";
            case "Long break after how many study sessions": return "پس از چند جلسه استراحت بلند باشد";
            case "Manage Android notification access": return "مدیریت دسترسی اعلان‌های اندروید";
            case "Start break timers without a confirmation": return "استراحت بدون تأیید شروع شود";
            case "Continue automatically after a break": return "پس از استراحت تمرکز خودکار شروع شود";
            case "Play a short cue when a phase ends": return "پایان هر مرحله با یک صدای کوتاه اعلام شود";
            case "This file provider does not allow persistent access after reopening.":
                return "دسترسی پایدار به این فایل پس از بستن برنامه ممکن نیست.";
            case "Added 5 minutes to your focus timer.": return "۵ دقیقه به زمان‌سنج تمرکز اضافه شد.";
            case "Choose a page from 1 to ": return "شمارهٔ صفحه را بین ۱ تا وارد کنید: ";
            case "Enter a page from 1 to ": return "شمارهٔ صفحه را از ۱ تا ";
            case "Folder access could not be saved.": return "ذخیرهٔ دسترسی پوشه ممکن نشد.";
            case "Could not scan this folder. Choose a folder that Focus can access.":
                return "اسکن پوشه انجام نشد؛ پوشه‌ای را انتخاب کنید که برنامه به آن دسترسی دارد.";
            case "Add PDF document": return "افزودن سند PDF";
            case "Unable to render this PDF page.": return "نمایش این صفحهٔ PDF ممکن نشد.";
            case "Do Not Disturb controls require Android 6 or newer.":
                return "کنترل حالت مزاحم‌نشوید به اندروید ۶ یا جدیدتر نیاز دارد.";
            case "Time remaining in focus session": return "زمان باقی‌مانده از جلسهٔ تمرکز";
            case "Continue reading ": return "ادامهٔ مطالعهٔ ";
            case "Open PDF ": return "بازکردن PDF ";
            case "Play": return "پخش";
            case "Reset timer": return "بازنشانی زمان‌سنج";
            case "Choose an app": return "انتخاب برنامه";
            case "Choose PDF": return "انتخاب PDF";
            case "Later": return "بعداً";
            case "Choose distracting apps to return to your home screen during an active Pomodoro. Enable the Accessibility service below to use this optional feature.":
                return "برنامه‌های مزاحم را انتخاب کنید تا هنگام تمرکز به صفحهٔ خانه بازگردید. برای استفاده، سرویس دسترس‌پذیری را فعال کنید.";
            case "Service enabled": return "سرویس فعال است";
            case "Service not enabled": return "سرویس فعال نیست";
            case "Block these apps while your timer is running": return "هنگام اجرای زمان‌سنج این برنامه‌ها مسدود شوند";
            case "This PDF could not be opened.": return "بازکردن PDF ممکن نشد.";
            case "Study session": return "جلسهٔ تمرکز";
            case "Short break": return "استراحت کوتاه";
            case "Long break": return "استراحت بلند";
            default:
                return value;
        }
    }

    private MaterialButton iconButton(int icon, String description, Runnable action) {
        MaterialButton button = new MaterialButton(this);
        button.setText("");
        if (appTypeface != null) button.setTypeface(appTypeface);
        button.setIconResource(icon);
        button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        button.setIconTint(android.content.res.ColorStateList.valueOf(Color.WHITE));
        button.setIconSize(dp(ICON_SIZE_DP));
        button.setIconPadding(0);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setMinWidth(dp(ICON_TARGET_DP));
        button.setMinimumWidth(dp(ICON_TARGET_DP));
        button.setMinHeight(dp(ICON_TARGET_DP));
        button.setMinimumHeight(dp(ICON_TARGET_DP));
        button.setGravity(Gravity.CENTER);
        button.setPadding(0, 0, 0, 0);
        button.setCornerRadius(dp(ICON_TARGET_DP / 2f));
        button.setContentDescription(fa(description));
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(47, 51, 62)));
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private MaterialCardView card() {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(14));
        card.setCardElevation(dp(1));
        card.setCardBackgroundColor(Color.rgb(29, 32, 40));
        card.setStrokeColor(Color.rgb(47, 51, 62));
        card.setStrokeWidth(dp(1));
        return card;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void showScreen(String title, View body) {
        isHome = false;
        activeScreen = title;
        if ("Note editor".equals(title)) backAction = this::showNotes;
        else if ("Focus guard".equals(title)) backAction = this::showPomodoroSettings;
        else backAction = this::buildHome;
        screen = column();
        screen.setBackgroundColor(Color.rgb(17, 19, 24));
        screen.setPadding(dp(16), dp(8), dp(16), dp(12));
        LinearLayout header = row();
        MaterialButton back = iconButton(R.drawable.ic_arrow_back, "Back", this::navigateBack);
        header.addView(back);
        TextView heading = text(title, 22, Color.WHITE);
        heading.setTypeface(appTypeface, Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1);
        titleParams.leftMargin = dp(12);
        header.addView(heading, titleParams);
        screen.addView(header);
        if (body instanceof ScrollView) {
            screen.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        } else {
            LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, 0, 1);
            bodyParams.topMargin = dp(12);
            screen.addView(body, bodyParams);
        }
        setScreenContent(screen);
    }

    private void navigateBack() {
        if (backAction != null) {
            backAction.run();
        } else {
            buildHome();
        }
    }

    private void setScreenContent(View root) {
        setScreenContent(root, true);
    }

    private void setScreenContent(View root, boolean withNavigation) {
        int left = root.getPaddingLeft();
        int top = root.getPaddingTop();
        int right = root.getPaddingRight();
        int bottom = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(left + systemBars.left, top + systemBars.top,
                    right + systemBars.right, bottom + systemBars.bottom);
            return insets;
        });
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        if (!withNavigation) {
            setContentView(root);
            return;
        }
        FrameLayout shell = new FrameLayout(this);
        FrameLayout.LayoutParams contentParams = new FrameLayout.LayoutParams(-1, -1);
        contentParams.bottomMargin = dp(72);
        shell.addView(root, contentParams);
        shell.addView(bottomNavigation(), new FrameLayout.LayoutParams(-1, dp(72), Gravity.BOTTOM));
        setContentView(shell);
    }

    private LinearLayout bottomNavigation() {
        LinearLayout navigation = row();
        navigation.setGravity(Gravity.CENTER);
        navigation.setBackgroundColor(Color.rgb(24, 26, 33));
        navigation.setElevation(dp(8));
        navigation.setMinimumHeight(dp(72));
        addNavigationItem(navigation, R.drawable.ic_focus, "تمرکز", "home", this::buildHome);
        addNavigationItem(navigation, R.drawable.ic_edit, "یادداشت", "Notes", this::showNotes);
        addNavigationItem(navigation, R.drawable.ic_notifications, "یادآورها", "Reminders", this::showReminders);
        addNavigationItem(navigation, R.drawable.ic_picture_as_pdf, "PDF", "PDF library", this::showPdfLibrary);
        addNavigationItem(navigation, R.drawable.ic_music_note, "موسیقی", "Music", this::showMusic);
        return navigation;
    }

    private void addNavigationItem(LinearLayout parent, int icon, String label, String destination,
                                   Runnable action) {
        LinearLayout item = column();
        item.setGravity(Gravity.CENTER);
        item.setMinimumHeight(dp(72));
        item.setPadding(dp(4), 0, dp(4), 0);
        item.setContentDescription(fa(label));
        item.setClickable(true);
        item.setFocusable(true);
        item.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        if (Build.VERSION.SDK_INT >= 28) item.setAccessibilityPaneTitle(label);
        boolean selected = activeScreen.equals(destination)
                || "home".equals(destination) && activeScreen.equals("home");
        item.setBackgroundColor(selected ? Color.rgb(38, 40, 51) : Color.TRANSPARENT);
        ImageView iconView = new ImageView(this);
        iconView.setImageResource(icon);
        int foreground = selected ? Color.rgb(60, 180, 230) : Color.rgb(178, 182, 193);
        iconView.setImageTintList(android.content.res.ColorStateList.valueOf(foreground));
        item.addView(iconView, new LinearLayout.LayoutParams(dp(24), dp(24)));
        TextView text = text(label, 11, foreground);
        text.setGravity(Gravity.CENTER);
        text.setSingleLine(true);
        text.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, dp(20));
        labelParams.topMargin = dp(3);
        item.addView(text, labelParams);
        item.setOnClickListener(v -> action.run());
        parent.addView(item, new LinearLayout.LayoutParams(0, dp(72), 1));
    }

    private ScrollView scroll(View child) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(child);
        return scroll;
    }

    private void buildHome() {
        isHome = true;
        activeScreen = "home";
        backAction = null;
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        LinearLayout content = column();
        content.setPadding(dp(20), dp(20), dp(20), dp(20));
        LinearLayout brand = row();
        TextView greeting = text("تمرکز", 13, Color.rgb(60, 180, 230));
        greeting.setLetterSpacing(0.18f);
        brand.addView(greeting, new LinearLayout.LayoutParams(0, -2, 1));
        MaterialButton settings = iconButton(R.drawable.ic_settings,
                "Pomodoro and focus guard settings", this::showPomodoroSettings);
        brand.addView(settings);
        MaterialButton calendarOptions = iconButton(R.drawable.ic_more_vert,
                "Clock and calendar options", this::showClockCalendarOptions);
        LinearLayout.LayoutParams calendarOptionsParams = new LinearLayout.LayoutParams(-2, -2);
        calendarOptionsParams.leftMargin = dp(6);
        brand.addView(calendarOptions, calendarOptionsParams);
        MaterialButton apps = iconButton(R.drawable.ic_apps, "All apps", () -> showAppDrawer(""));
        LinearLayout.LayoutParams appsParams = new LinearLayout.LayoutParams(-2, -2);
        appsParams.leftMargin = dp(6);
        brand.addView(apps, appsParams);
        content.addView(brand);
        TextView title = text("برای کارهای مهم وقت بساز.", 22, Color.WHITE);
        title.setTypeface(appTypeface, Typeface.BOLD);
        LinearLayout.LayoutParams titleMargin = new LinearLayout.LayoutParams(-1, -2);
        titleMargin.topMargin = dp(6);
        titleMargin.bottomMargin = dp(14);
        content.addView(title, titleMargin);
        if (prefs.getBoolean("home_clock_calendar_visible", true)) {
            MaterialCardView clockCalendarCard = card();
            LinearLayout clockCalendarContent = column();
            clockCalendarContent.setPadding(dp(16), dp(12), dp(16), dp(12));
            LinearLayout clockCalendarHeader = row();
            TextClock digitalClock = new TextClock(this);
            digitalClock.setFormat24Hour("HH:mm");
            digitalClock.setFormat12Hour("h:mm");
            digitalClock.setTextColor(Color.WHITE);
            digitalClock.setTextSize(32);
            digitalClock.setTypeface(appTypeface, Typeface.BOLD);
            digitalClock.setContentDescription("ساعت");
            clockCalendarHeader.addView(digitalClock, new LinearLayout.LayoutParams(0, -2, 1));
            TextView today = text("امروز", 11, Color.LTGRAY);
            clockCalendarHeader.addView(today);
            clockCalendarContent.addView(clockCalendarHeader);

            String[] dateLabels = {"شمسی", "میلادی", "قمری محاسباتی"};
            String[] dateValues = CalendarDateUtils.allDates(System.currentTimeMillis());
            int calendarMode = Math.max(CalendarDateUtils.JALALI, Math.min(CalendarDateUtils.HIJRI,
                    prefs.getInt("clock_calendar_mode", CalendarDateUtils.JALALI)));
            for (int i = 0; i < dateLabels.length; i++) {
                LinearLayout dateRow = row();
                TextView kind = text(dateLabels[i], 11,
                        i == calendarMode ? Color.rgb(60, 180, 230) : Color.LTGRAY);
                dateRow.addView(kind, new LinearLayout.LayoutParams(dp(76), -2));
                TextView dateValue = text(dateValues[i], 12,
                        i == calendarMode ? Color.WHITE : Color.LTGRAY);
                dateRow.addView(dateValue, new LinearLayout.LayoutParams(0, -2, 1));
                dateRow.setPadding(0, dp(2), 0, dp(2));
                dateRow.setClickable(true);
                dateRow.setOnClickListener(v -> showDatePickerForNotes());
                clockCalendarContent.addView(dateRow);
            }
            clockCalendarCard.addView(clockCalendarContent);
            clockCalendarCard.setContentDescription("ساعت و تقویم‌های شمسی، میلادی و قمری");
            clockCalendarCard.setOnLongClickListener(v -> {
                showClockCalendarOptions();
                return true;
            });
            LinearLayout.LayoutParams clockCalendarParams = new LinearLayout.LayoutParams(-1, -2);
            clockCalendarParams.bottomMargin = dp(14);
            content.addView(clockCalendarCard, clockCalendarParams);
        }
        addHomeWidgetsSection(content);
        if (!isDefaultLauncher()) {
            MaterialCardView setupCard = card();
            LinearLayout setupRow = row();
            setupRow.setPadding(dp(12), dp(8), dp(12), dp(8));
            TextView setupLabel = text("فوکِس را به لانچر پیش‌فرض برای صفحهٔ اصلی تبدیل کنید.", 12, Color.WHITE);
            setupRow.addView(setupLabel, new LinearLayout.LayoutParams(0, -2, 1));
            MaterialButton setup = button("فعال‌سازی");
            setup.setMinHeight(dp(40));
            setup.setOnClickListener(v -> requestDefaultLauncher());
            setupRow.addView(setup);
            setupCard.addView(setupRow);
            LinearLayout.LayoutParams setupParams = new LinearLayout.LayoutParams(-1, -2);
            setupParams.bottomMargin = dp(12);
            content.addView(setupCard, setupParams);
        }

        MaterialCardView timerCard = card();
        LinearLayout timerContent = column();
        timerContent.setGravity(Gravity.CENTER);
        timerContent.setPadding(dp(14), dp(12), dp(14), dp(14));
        timerStatus = text(phaseTitle(timerPhase).toUpperCase(Locale.getDefault()), 12, Color.LTGRAY);
        timerStatus.setLetterSpacing(0.08f);
        timerContent.addView(timerStatus);
        FrameLayout clock = new FrameLayout(this);
        timerProgress = new TimerProgressView();
        clock.addView(timerProgress, new FrameLayout.LayoutParams(dp(176), dp(176), Gravity.CENTER));
        timerText = text("", 46, Color.WHITE);
        timerText.setTypeface(appTypeface, Typeface.NORMAL);
        timerText.setGravity(Gravity.CENTER);
        timerText.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        timerText.setContentDescription(fa("Time remaining in focus session"));
        clock.addView(timerText, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams clockParams = new LinearLayout.LayoutParams(-1, dp(166));
        clockParams.topMargin = dp(2);
        timerContent.addView(clock, clockParams);
        TextView duration = text("Pomodoro settings", 13, Color.LTGRAY);
        timerContent.addView(duration);
        duration.setOnClickListener(v -> showPomodoroSettings());
        timerContent.setOnClickListener(v -> showPomodoroSettings());
        timerButton = button("");
        timerButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(34, 158, 217)));
        LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(dp(170), dp(52));
        startParams.topMargin = dp(10);
        timerContent.addView(timerButton, startParams);
        timerButton.setOnClickListener(v -> toggleTimer());
        LinearLayout timerActions = row();
        timerActions.setGravity(Gravity.CENTER);
        timerActions.setPadding(0, dp(8), 0, 0);
        MaterialButton resetTimer = button("Reset");
        resetTimer.setIconResource(R.drawable.ic_rotate_right);
        resetTimer.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        resetTimer.setIconTint(android.content.res.ColorStateList.valueOf(Color.WHITE));
        resetTimer.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(47, 51, 62)));
        resetTimer.setOnClickListener(v -> resetTimer());
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(dp(112), dp(44));
        resetParams.rightMargin = dp(8);
        resetTimer.setMinHeight(dp(48));
        timerActions.addView(resetTimer, resetParams);
        MaterialButton skipTimer = button("Skip");
        skipTimer.setIconResource(R.drawable.ic_skip_next);
        skipTimer.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        skipTimer.setIconTint(android.content.res.ColorStateList.valueOf(Color.WHITE));
        skipTimer.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(47, 51, 62)));
        skipTimer.setOnClickListener(v -> skipTimerPhase());
        skipTimer.setMinHeight(dp(48));
        timerActions.addView(skipTimer, new LinearLayout.LayoutParams(dp(112), dp(48)));
        timerContent.addView(timerActions);
        timerCard.addView(timerContent);
        LinearLayout.LayoutParams timerParams = new LinearLayout.LayoutParams(-1, -2);
        timerParams.bottomMargin = dp(16);
        content.addView(timerCard, timerParams);
        updateTimerDisplay();

        MaterialCardView stats = card();
        LinearLayout statsRow = row();
        statsRow.setPadding(dp(12), dp(10), dp(12), dp(10));
        statsSummary = text(todayStatsText(), 13, Color.WHITE);
        statsSummary.setGravity(Gravity.CENTER);
        statsRow.addView(statsSummary, new LinearLayout.LayoutParams(-1, -2));
        stats.addView(statsRow);
        stats.setClickable(true);
        stats.setFocusable(true);
        stats.setOnClickListener(v -> showScreenTimeDashboard());
        stats.setOnLongClickListener(v -> {
            showScreenTimeDashboard();
            return true;
        });
        LinearLayout.LayoutParams statsParams = new LinearLayout.LayoutParams(-1, -2);
        statsParams.bottomMargin = dp(12);
        content.addView(stats, statsParams);
        addExamTimerCard(content);
        addUpcomingTasks(content);

        TextView shortcutsTitle = text("دسترسی سریع", 12, Color.rgb(60, 180, 230));
        shortcutsTitle.setTypeface(appTypeface, Typeface.BOLD);
        shortcutsTitle.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams shortcutsTitleParams = new LinearLayout.LayoutParams(-1, -2);
        shortcutsTitleParams.bottomMargin = dp(8);
        content.addView(shortcutsTitle, shortcutsTitleParams);
        GridLayout actions = new GridLayout(this);
        actions.setColumnCount(2);
        actions.setUseDefaultMargins(false);
        for (HomeShortcut shortcut : homeShortcuts()) addAction(actions, shortcut);
        addAction(actions, new HomeShortcut("یادآورها", R.drawable.ic_notifications, this::showReminders, null));
        addAction(actions, new HomeShortcut("تایمر آزمون", R.drawable.ic_focus, this::showExamTimer, null));
        addAction(actions, new HomeShortcut("Add shortcut", R.drawable.ic_add, null, null));
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(-1, -2);
        actionsParams.bottomMargin = dp(16);
        content.addView(actions, actionsParams);

        LinearLayout bottom = column();
        MaterialCardView recent = card();
        LinearLayout recentContent = row();
        recentContent.setPadding(dp(15), dp(13), dp(15), dp(13));
        ImageView pdfIcon = new ImageView(this);
        pdfIcon.setImageResource(R.drawable.ic_picture_as_pdf);
        pdfIcon.setImageTintList(android.content.res.ColorStateList.valueOf(Color.rgb(60, 180, 230)));
        pdfIcon.setContentDescription("PDF");
        pdfIcon.setLayoutParams(new LinearLayout.LayoutParams(dp(24), dp(24)));
        recentContent.addView(pdfIcon);
        LinearLayout recentDetails = column();
        recentDetails.addView(text("LAST PDF", 11, Color.LTGRAY));
        TextView recentTitle = text(lastPdfTitle(), 14, Color.WHITE);
        recentTitle.setMaxLines(1);
        recentTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        String lastPdfStatus = lastPdfContinueLabel();
        LinearLayout.LayoutParams recentTitleParams = new LinearLayout.LayoutParams(-1, -2);
        recentTitleParams.topMargin = dp(3);
        recentDetails.addView(recentTitle, recentTitleParams);
        LinearLayout.LayoutParams recentDetailsParams = new LinearLayout.LayoutParams(0, -2, 1);
        recentDetailsParams.leftMargin = dp(12);
        recentContent.addView(recentDetails, recentDetailsParams);
        recentContent.addView(text(lastPdfStatus, 12, Color.rgb(60, 180, 230)));
        recent.addView(recentContent);
        recent.setMinimumHeight(dp(72));
        recent.setClickable(true);
        recent.setFocusable(true);
        recent.setContentDescription("Continue reading " + recentTitle.getText() + ", " + lastPdfStatus);
        recent.setOnClickListener(v -> openLastPdf());
        bottom.addView(recent);

        MaterialCardView musicCard = card();
        LinearLayout musicRow = row();
        musicRow.setPadding(dp(14), dp(12), dp(12), dp(12));
        ImageView musicIcon = new ImageView(this);
        musicIcon.setImageResource(R.drawable.ic_music_note);
        musicIcon.setImageTintList(android.content.res.ColorStateList.valueOf(Color.rgb(60, 180, 230)));
        musicRow.addView(musicIcon, new LinearLayout.LayoutParams(dp(28), dp(28)));
        LinearLayout musicDetails = column();
        musicDetails.setGravity(Gravity.CENTER_VERTICAL);
        musicDetails.addView(text("پخش‌کنندهٔ موسیقی", 14, Color.WHITE));
        TextView musicHint = text("بازکردن برنامهٔ موسیقی دستگاه", 11, Color.LTGRAY);
        LinearLayout.LayoutParams musicHintParams = new LinearLayout.LayoutParams(-1, -2);
        musicHintParams.topMargin = dp(3);
        musicDetails.addView(musicHint, musicHintParams);
        LinearLayout.LayoutParams musicDetailsParams = new LinearLayout.LayoutParams(0, -2, 1);
        musicDetailsParams.leftMargin = dp(12);
        musicRow.addView(musicDetails, musicDetailsParams);
        ImageView musicChevron = new ImageView(this);
        musicChevron.setImageResource(R.drawable.ic_chevron_right);
        musicChevron.setImageTintList(android.content.res.ColorStateList.valueOf(Color.LTGRAY));
        musicRow.addView(musicChevron, new LinearLayout.LayoutParams(dp(24), dp(24)));
        musicCard.addView(musicRow);
        musicCard.setClickable(true);
        musicCard.setFocusable(true);
        musicCard.setContentDescription("بازکردن پخش‌کنندهٔ موسیقی دستگاه");
        musicCard.setOnClickListener(v -> showMusic());
        LinearLayout.LayoutParams musicParams = new LinearLayout.LayoutParams(-1, -2);
        musicParams.topMargin = dp(8);
        bottom.addView(musicCard, musicParams);
        LinearLayout.LayoutParams bottomParams = new LinearLayout.LayoutParams(-1, -2);
        bottomParams.topMargin = dp(4);
        content.addView(bottom, bottomParams);

        ScrollView scroll = scroll(content);
        scroll.setBackgroundColor(Color.rgb(17, 19, 24));
        setScreenContent(scroll);
        loadSavedPdfs();
    }

    private void showAppDrawer(String initialQuery) {
        List<ResolveInfo> discovered = getPackageManager().queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);
        Collections.sort(discovered, (left, right) ->
                String.valueOf(left.loadLabel(getPackageManager()))
                        .compareToIgnoreCase(String.valueOf(right.loadLabel(getPackageManager()))));
        List<ResolveInfo> apps = new ArrayList<>();
        Set<String> seenPackages = new HashSet<>();
        for (ResolveInfo info : discovered) {
            if (info.activityInfo != null
                    && !getPackageName().equals(info.activityInfo.packageName)
                    && seenPackages.add(info.activityInfo.packageName)) apps.add(info);
        }

        LinearLayout content = column();
        content.setPadding(0, dp(4), 0, dp(12));
        TextInputEditText search = new TextInputEditText(this);
        search.setSingleLine(true);
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(Color.LTGRAY);
        search.setHint(fa("Search apps"));
        search.setCompoundDrawablePadding(dp(8));
        search.setPadding(dp(12), dp(4), dp(12), dp(4));
        search.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(60, 65, 78)));
        content.addView(search, new LinearLayout.LayoutParams(-1, dp(48)));
        TextView resultCount = text("", 11, Color.LTGRAY);
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(-1, -2);
        countParams.topMargin = dp(8);
        content.addView(resultCount, countParams);
        LinearLayout appRows = column();
        LinearLayout.LayoutParams rowsParams = new LinearLayout.LayoutParams(-1, -2);
        rowsParams.topMargin = dp(4);
        content.addView(appRows, rowsParams);
        int[] visibleLimit = {APP_DRAWER_PAGE_SIZE};
        Runnable render = () -> renderAppDrawerRows(appRows, resultCount, apps,
                String.valueOf(search.getText()), visibleLimit[0]);
        MaterialButton more = button("More apps");
        more.setOnClickListener(v -> {
            visibleLimit[0] += APP_DRAWER_PAGE_SIZE;
            render.run();
        });
        appDrawerMore = more;
        content.addView(more, new LinearLayout.LayoutParams(-1, dp(48)));
        more.setVisibility(View.GONE);
        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                visibleLimit[0] = APP_DRAWER_PAGE_SIZE;
                render.run();
            }
            @Override public void afterTextChanged(android.text.Editable s) { }
        });
        render.run();
        showScreen("All apps", scroll(content));
        search.setText(initialQuery);
        render.run();
    }

    private void showMusic() {
        Intent musicIntent = new Intent(Intent.ACTION_MAIN);
        musicIntent.addCategory(Intent.CATEGORY_APP_MUSIC);
        try {
            if (musicIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(musicIntent);
            } else {
                Toast.makeText(this, "برنامهٔ پخش موسیقی روی دستگاه پیدا نشد.", Toast.LENGTH_LONG).show();
            }
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, "بازکردن پخش‌کنندهٔ موسیقی ممکن نشد.", Toast.LENGTH_LONG).show();
            android.util.Log.w("FocusMusic", "No music app could handle the music intent.", e);
        }
    }

    private void showClockCalendarOptions() {
        String[] modes = {"تقویم اصلی: شمسی", "تقویم اصلی: میلادی", "تقویم اصلی: قمری"};
        int mode = prefs.getInt("clock_calendar_mode", CalendarDateUtils.JALALI);
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("ساعت و تقویم صفحهٔ اصلی")
                .setMessage("برای افزودن ویجت مستقل، صفحهٔ اصلی گوشی را نگه دارید و از بخش ویجت‌ها، ساعت و تقویم یا پومودورو را اضافه کنید.")
                .setSingleChoiceItems(modes, mode, (choiceDialog, which) -> {
                    prefs.edit().putInt("clock_calendar_mode", which).apply();
                    updateClockCalendarWidgets();
                    choiceDialog.dismiss();
                    if (isHome) buildHome();
                })
                .setNegativeButton("بستن", null)
                .setPositiveButton("نمایش کارت", (choiceDialog, which) -> {
                    prefs.edit().putBoolean("home_clock_calendar_visible", true).apply();
                    buildHome();
                })
                .setNeutralButton("حذف از صفحهٔ اصلی", (choiceDialog, which) -> {
                    prefs.edit().putBoolean("home_clock_calendar_visible", false).apply();
                    buildHome();
                });
        dialog.show();
    }

    private void updateClockCalendarWidgets() {
        CalendarClockWidgetProvider.updateAll(this);
        FocusWidgetProvider.updateAll(this);
    }

    private void addHomeWidgetsSection(LinearLayout parent) {
        LinearLayout section = column();
        LinearLayout header = row();
        TextView title = text("ویجت‌های صفحهٔ اصلی", 14, Color.WHITE);
        title.setTypeface(appTypeface, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        MaterialButton addWidget = button("+ ویجت");
        addWidget.setMinHeight(dp(40));
        addWidget.setOnClickListener(v -> pickHomeWidget());
        header.addView(addWidget);
        section.addView(header);

        List<Integer> widgetIds = loadHomeWidgetIds();
        if (widgetIds.isEmpty()) {
            TextView empty = text("ویجت ساعت و پومودورو را از اینجا به صفحه اضافه کنید.", 11, Color.LTGRAY);
            LinearLayout.LayoutParams emptyParams = new LinearLayout.LayoutParams(-1, -2);
            emptyParams.topMargin = dp(4);
            section.addView(empty, emptyParams);
        }
        List<Integer> validIds = new ArrayList<>();
        for (int widgetId : widgetIds) {
            AppWidgetProviderInfo info = appWidgetManager.getAppWidgetInfo(widgetId);
            if (info == null) {
                android.util.Log.w("FocusWidgets", "Removing unavailable home widget " + widgetId);
                appWidgetHost.deleteAppWidgetId(widgetId);
                continue;
            }
            validIds.add(widgetId);
            MaterialCardView widgetCard = card();
            LinearLayout widgetContent = column();
            widgetContent.setPadding(dp(8), dp(4), dp(8), dp(8));
            LinearLayout widgetHeader = row();
            CharSequence label = info.loadLabel(getPackageManager());
            widgetHeader.addView(text(String.valueOf(label), 11, Color.LTGRAY),
                    new LinearLayout.LayoutParams(0, dp(40), 1));
            MaterialButton menu = iconButton(R.drawable.ic_more_vert, "گزینه‌های ویجت",
                    () -> showHomeWidgetMenu(widgetId, label));
            widgetHeader.addView(menu);
            widgetContent.addView(widgetHeader);
            AppWidgetHostView hostView = appWidgetHost.createView(this, widgetId, info);
            hostView.setAppWidget(widgetId, info);
            widgetContent.addView(hostView, new LinearLayout.LayoutParams(-1, -2));
            widgetCard.addView(widgetContent);
            LinearLayout.LayoutParams widgetParams = new LinearLayout.LayoutParams(-1, -2);
            widgetParams.topMargin = dp(8);
            section.addView(widgetCard, widgetParams);
        }
        if (validIds.size() != widgetIds.size()) saveHomeWidgetIds(validIds);
        LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(-1, -2);
        sectionParams.bottomMargin = dp(14);
        parent.addView(section, sectionParams);
    }

    private void pickHomeWidget() {
        int widgetId = appWidgetHost.allocateAppWidgetId();
        prefs.edit().putInt("pending_home_widget_id", widgetId).apply();
        Intent pick = new Intent(AppWidgetManager.ACTION_APPWIDGET_PICK)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        try {
            startActivityForResult(pick, REQUEST_APP_WIDGET_PICK);
        } catch (android.content.ActivityNotFoundException | SecurityException e) {
            prefs.edit().remove("pending_home_widget_id").apply();
            appWidgetHost.deleteAppWidgetId(widgetId);
            android.util.Log.e("FocusWidgets", "Android widget picker is unavailable.", e);
            Toast.makeText(this, "فهرست ویجت‌های اندروید باز نشد.", Toast.LENGTH_LONG).show();
        }
    }

    private List<Integer> loadHomeWidgetIds() {
        List<Integer> ids = new ArrayList<>();
        try {
            JSONArray stored = new JSONArray(prefs.getString("home_widget_ids", "[]"));
            for (int i = 0; i < stored.length(); i++) ids.add(stored.getInt(i));
        } catch (JSONException e) {
            android.util.Log.e("FocusWidgets", "Unable to read saved home widgets.", e);
        }
        return ids;
    }

    private void saveHomeWidgetIds(List<Integer> ids) {
        JSONArray stored = new JSONArray();
        for (int id : ids) stored.put(id);
        prefs.edit().putString("home_widget_ids", stored.toString()).apply();
    }

    private void showHomeWidgetMenu(int widgetId, CharSequence label) {
        new MaterialAlertDialogBuilder(this).setTitle(String.valueOf(label))
                .setItems(new String[]{"حذف ویجت"}, (dialog, which) -> {
                    List<Integer> ids = loadHomeWidgetIds();
                    ids.remove(Integer.valueOf(widgetId));
                    saveHomeWidgetIds(ids);
                    appWidgetHost.deleteAppWidgetId(widgetId);
                    buildHome();
                }).setNegativeButton("انصراف", null).show();
    }

    private void finishHomeWidgetPick(int widgetId) {
        AppWidgetProviderInfo info = appWidgetManager.getAppWidgetInfo(widgetId);
        if (info == null) {
            appWidgetHost.deleteAppWidgetId(widgetId);
            prefs.edit().remove("pending_home_widget_id").apply();
            Toast.makeText(this, "اطلاعات این ویجت پیدا نشد.", Toast.LENGTH_LONG).show();
            buildHome();
            return;
        }
        if (info.configure != null) {
            Intent configure = new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                    .setComponent(info.configure)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
            try {
                startActivityForResult(configure, REQUEST_APP_WIDGET_CONFIGURE);
            } catch (android.content.ActivityNotFoundException | SecurityException e) {
                appWidgetHost.deleteAppWidgetId(widgetId);
                prefs.edit().remove("pending_home_widget_id").apply();
                android.util.Log.e("FocusWidgets", "Widget configuration could not be opened.", e);
                Toast.makeText(this, "تنظیمات این ویجت باز نشد.", Toast.LENGTH_LONG).show();
            }
            return;
        }
        attachHomeWidget(widgetId);
    }

    private void attachHomeWidget(int widgetId) {
        List<Integer> ids = loadHomeWidgetIds();
        if (!ids.contains(widgetId)) ids.add(widgetId);
        saveHomeWidgetIds(ids);
        prefs.edit().remove("pending_home_widget_id").apply();
        buildHome();
    }

    private boolean isDefaultLauncher() {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo current = getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        return current != null && current.activityInfo != null
                && getPackageName().equals(current.activityInfo.packageName);
    }

    private void requestDefaultLauncher() {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                android.app.role.RoleManager roles = getSystemService(android.app.role.RoleManager.class);
                if (roles != null && roles.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)) {
                    startActivityForResult(roles.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME),
                            REQUEST_HOME_ROLE);
                    return;
                }
            }
            startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
        } catch (android.content.ActivityNotFoundException | SecurityException e) {
            android.util.Log.e("FocusLauncher", "Could not open default home app settings.", e);
            Toast.makeText(this, "انتخاب لانچر پیش‌فرض در تنظیمات دستگاه در دسترس نیست.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void renderAppDrawerRows(LinearLayout target, TextView resultCount,
                                    List<ResolveInfo> apps, String query, int limit) {
        target.removeAllViews();
        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<ResolveInfo> matches = new ArrayList<>();
        for (ResolveInfo info : apps) {
            String label = String.valueOf(info.loadLabel(getPackageManager()));
            if (label.toLowerCase(Locale.ROOT).contains(needle)) matches.add(info);
        }
        resultCount.setText(getString(R.string.installed_app_count, persianDigits(matches.size())));
        int shown = Math.min(limit, matches.size());
        if (shown == 0) {
            target.addView(text("No apps found", 13, Color.LTGRAY));
        }
        LinearLayout appRow = null;
        for (int i = 0; i < shown; i++) {
            if (i % 4 == 0) {
                appRow = row();
                appRow.setGravity(Gravity.TOP);
                target.addView(appRow, new LinearLayout.LayoutParams(-1, -2));
            }
            ResolveInfo info = matches.get(i);
            LinearLayout cell = column();
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(dp(4), dp(8), dp(4), dp(8));
            cell.setClickable(true);
            cell.setFocusable(true);
            ImageView icon = new ImageView(this);
            icon.setImageDrawable(info.loadIcon(getPackageManager()));
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            cell.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));
            TextView label = text(String.valueOf(info.loadLabel(getPackageManager())), 10, Color.WHITE);
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(2);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, dp(34));
            labelParams.topMargin = dp(4);
            cell.addView(label, labelParams);
            appRow.addView(cell, new LinearLayout.LayoutParams(0, dp(92), 1f));
            cell.setOnClickListener(v -> launchDrawerApp(info));
            cell.setOnLongClickListener(v -> {
                showAppContextMenu(v, info);
                return true;
            });
        }
        if (appDrawerMore != null) appDrawerMore.setVisibility(shown < matches.size() ? View.VISIBLE : View.GONE);
    }

    private void showAppContextMenu(View anchor, ResolveInfo app) {
        if (app.activityInfo == null) return;
        String packageName = app.activityInfo.packageName;
        CharSequence label = app.loadLabel(getPackageManager());
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, "مدیریت زمان و آمار");
        menu.getMenu().add(0, 2, 1, "اطلاعات برنامه");
        boolean systemApp = (app.activityInfo.applicationInfo.flags
                & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0;
        if (!systemApp) menu.getMenu().add(0, 3, 2, "حذف نصب");
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                showAppTimeSettings(packageName, String.valueOf(label));
                return true;
            }
            if (item.getItemId() == 2) {
                openPackageSettings(packageName);
                return true;
            }
            if (item.getItemId() == 3) {
                uninstallPackage(packageName);
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void openPackageSettings(String packageName) {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + packageName)));
        } catch (android.content.ActivityNotFoundException | SecurityException e) {
            android.util.Log.e("FocusLauncher", "Unable to open app settings for " + packageName, e);
            Toast.makeText(this, "اطلاعات برنامه باز نشد.", Toast.LENGTH_LONG).show();
        }
    }

    private void uninstallPackage(String packageName) {
        try {
            Intent uninstall = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + packageName));
            startActivity(uninstall);
        } catch (android.content.ActivityNotFoundException | SecurityException e) {
            android.util.Log.e("FocusLauncher", "Unable to request uninstall for " + packageName, e);
            Toast.makeText(this, "حذف نصب این برنامه انجام نشد.", Toast.LENGTH_LONG).show();
        }
    }

    private void launchDrawerApp(ResolveInfo app) {
        if (app.activityInfo == null) return;
        Intent launch = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(app.activityInfo.packageName, app.activityInfo.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(launch);
        } catch (android.content.ActivityNotFoundException | SecurityException e) {
            android.util.Log.w("FocusLauncher", "Unable to open selected app.", e);
            Toast.makeText(this, fa("This app could not be opened."), Toast.LENGTH_SHORT).show();
        }
    }

    private List<HomeShortcut> homeShortcuts() {
        List<HomeShortcut> shortcuts = new ArrayList<>();
        shortcuts.add(new HomeShortcut("ماشین‌حساب", R.drawable.ic_calculator, this::launchCalculator, null));
        shortcuts.add(new HomeShortcut("یادداشت‌ها", R.drawable.ic_edit, this::showNotes, null));
        shortcuts.add(new HomeShortcut("موسیقی", R.drawable.ic_music_note, this::showMusic, null));
        shortcuts.add(new HomeShortcut("کتابخانهٔ PDF", R.drawable.ic_picture_as_pdf, this::showPdfLibrary, null));
        shortcuts.add(new HomeShortcut("زمان صفحه", R.drawable.ic_graphic_eq,
                this::showScreenTimeDashboard, null));
        String calculatorPackage = findCalculatorPackage();
        Set<String> installedPackages = new LinkedHashSet<>(
                prefs.getStringSet(HOME_APP_SHORTCUTS, Collections.emptySet()));
        Set<String> unavailablePackages = new HashSet<>();
        for (String packageName : installedPackages) {
            if (packageName.equals(calculatorPackage)) continue;
            Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);
            if (launchIntent == null) {
                unavailablePackages.add(packageName);
                continue;
            }
            try {
                shortcuts.add(new HomeShortcut(
                        String.valueOf(getPackageManager().getApplicationLabel(
                                getPackageManager().getApplicationInfo(packageName, 0))),
                        0, () -> launchShortcut(packageName), packageName));
            } catch (PackageManager.NameNotFoundException ignored) {
                unavailablePackages.add(packageName);
            }
        }
        if (!unavailablePackages.isEmpty()) {
            installedPackages.removeAll(unavailablePackages);
            prefs.edit().putStringSet(HOME_APP_SHORTCUTS, installedPackages).apply();
        }
        return shortcuts;
    }

    @Nullable
    private String findCalculatorPackage() {
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo app : getPackageManager().queryIntentActivities(intent, 0)) {
            String label = String.valueOf(app.loadLabel(getPackageManager())).toLowerCase(Locale.ROOT);
            if (label.contains("calculator") || label.contains("calc") || label.contains("ماشین")
                    || label.contains("حسابگر") || label.contains("الحاسبة")) {
                return app.activityInfo.packageName;
            }
        }
        return null;
    }

    private void launchCalculator() {
        String packageName = findCalculatorPackage();
        if (packageName != null) {
            launchShortcut(packageName);
            return;
        }
        Intent calculator = new Intent(Intent.ACTION_MAIN).addCategory("android.intent.category.APP_CALCULATOR");
        ResolveInfo handler = getPackageManager().resolveActivity(calculator, PackageManager.MATCH_DEFAULT_ONLY);
        if (handler != null) {
            try {
                startActivity(calculator);
                return;
            } catch (android.content.ActivityNotFoundException | SecurityException e) {
                android.util.Log.w("FocusShortcuts", "Unable to launch the system calculator.", e);
            }
        }
        Toast.makeText(this, "ماشین‌حسابی روی دستگاه پیدا نشد.", Toast.LENGTH_SHORT).show();
    }

    private void addAction(GridLayout parent, HomeShortcut shortcut) {
        MaterialCardView item = new MaterialCardView(this);
        item.setRadius(dp(16));
        item.setCardElevation(0);
        item.setStrokeWidth(dp(1));
        item.setStrokeColor(Color.rgb(47, 51, 62));
        item.setCardBackgroundColor(Color.rgb(29, 32, 40));
        item.setClickable(true);
        item.setFocusable(true);
        item.setContentDescription(fa(shortcut.label));
        item.setMinimumHeight(dp(92));

        LinearLayout content = column();
        content.setGravity(Gravity.CENTER);
        content.setPadding(dp(8), dp(14), dp(8), dp(12));
        ImageView glyph = new ImageView(this);
        if (shortcut.packageName == null) {
            glyph.setImageResource(shortcut.icon);
        } else {
            try {
                glyph.setImageDrawable(getPackageManager().getApplicationIcon(shortcut.packageName));
            } catch (PackageManager.NameNotFoundException ignored) {
                glyph.setImageResource(R.drawable.ic_focus);
            }
        }
        if (shortcut.packageName == null) {
            glyph.setImageTintList(android.content.res.ColorStateList.valueOf(Color.rgb(60, 180, 230)));
        }
        glyph.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        content.addView(glyph, new LinearLayout.LayoutParams(dp(22), dp(22)));
        TextView caption = text(shortcut.label, 12, Color.WHITE);
        caption.setMaxLines(1);
        caption.setEllipsize(android.text.TextUtils.TruncateAt.END);
        caption.setGravity(Gravity.CENTER);
        caption.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams captionParams = new LinearLayout.LayoutParams(-1, -2);
        captionParams.topMargin = dp(8);
        content.addView(caption, captionParams);
        item.addView(content);
        item.setOnClickListener(v -> {
            if (shortcut.action == null) chooseHomeAppShortcut();
            else shortcut.action.run();
        });
        if (shortcut.packageName != null) {
            item.setOnLongClickListener(v -> {
                new MaterialAlertDialogBuilder(this)
                        .setTitle(shortcut.label)
                        .setMessage("Remove this app from Quick access?")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Remove", (dialog, which) -> {
                            Set<String> packages = new LinkedHashSet<>(
                                    prefs.getStringSet(HOME_APP_SHORTCUTS, Collections.emptySet()));
                            packages.remove(shortcut.packageName);
                            prefs.edit().putStringSet(HOME_APP_SHORTCUTS, packages).apply();
                            buildHome();
                        })
                        .show();
                return true;
            });
        }

        GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1f),
                GridLayout.spec(GridLayout.UNDEFINED, 1f));
        params.width = 0;
        params.height = GridLayout.LayoutParams.WRAP_CONTENT;
        params.setMargins(0, 0, dp(8), dp(8));
        parent.addView(item, params);
    }

    private void chooseHomeAppShortcut() {
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> launchableApps = getPackageManager().queryIntentActivities(launcherIntent, 0);
        Collections.sort(launchableApps, (first, second) ->
                String.valueOf(first.loadLabel(getPackageManager()))
                        .compareToIgnoreCase(String.valueOf(second.loadLabel(getPackageManager()))));
        Set<String> addedPackages = new HashSet<>(
                prefs.getStringSet(HOME_APP_SHORTCUTS, Collections.emptySet()));
        Set<String> seenPackages = new HashSet<>();
        List<ResolveInfo> choices = new ArrayList<>();
        for (ResolveInfo app : launchableApps) {
            String packageName = app.activityInfo.packageName;
            if (packageName.equals(getPackageName()) || addedPackages.contains(packageName)
                    || !seenPackages.add(packageName)) continue;
            choices.add(app);
        }
        if (choices.isEmpty()) {
            Toast.makeText(this, "No more apps are available to add.", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[choices.size()];
        for (int i = 0; i < choices.size(); i++) {
            labels[i] = String.valueOf(choices.get(i).loadLabel(getPackageManager()));
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Add an app shortcut")
                .setItems(labels, (dialog, which) -> {
                    String packageName = choices.get(which).activityInfo.packageName;
                    Set<String> packages = new LinkedHashSet<>(
                            prefs.getStringSet(HOME_APP_SHORTCUTS, Collections.emptySet()));
                    packages.add(packageName);
                    prefs.edit().putStringSet(HOME_APP_SHORTCUTS, packages).apply();
                    buildHome();
                })
                .show();
    }

    private void launchShortcut(String packageName) {
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);
        if (launchIntent == null) {
            Toast.makeText(this, "This app is no longer available.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(launchIntent);
        } catch (android.content.ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, "This app could not be opened.", Toast.LENGTH_SHORT).show();
        }
    }

    private static final class HomeShortcut {
        final String label;
        final int icon;
        final Runnable action;
        final String packageName;

        HomeShortcut(String label, int icon, Runnable action, String packageName) {
            this.label = label;
            this.icon = icon;
            this.action = action;
            this.packageName = packageName;
        }
    }

    private android.graphics.drawable.Drawable makeThumbnailBackground() {
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(34, 158, 217), Color.rgb(55, 177, 161)});
        drawable.setCornerRadius(dp(8));
        return drawable;
    }

    private void updateTimerDisplay() {
        if (timerText == null || timerButton == null || timerProgress == null) return;
        long seconds = Math.max(0, (remainingMillis + 999) / 1000);
        timerText.setText(persianDigits(String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60)));
        timerProgress.setProgress(remainingMillis, durationMillis);
        timerButton.setText(fa(timerRunning ? "Pause" : remainingMillis < durationMillis ? "Resume" : "Start"));
        timerButton.setContentDescription(fa(timerRunning ? "Pause timer" : "Start or resume timer"));
        if (timerStatus != null) timerStatus.setText(phaseTitle(timerPhase).toUpperCase(Locale.getDefault()));
        if (statsSummary != null) statsSummary.setText(todayStatsText());
    }

    private void updateWidget() {
        FocusWidgetProvider.updateAll(this);
    }

    private void playTimerChime() {
        android.media.ToneGenerator tone = new android.media.ToneGenerator(
                android.media.AudioManager.STREAM_NOTIFICATION, 55);
        tone.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 180);
        handler.postDelayed(tone::release, 250);
    }

    private void resetTimer() {
        handler.removeCallbacks(timerTick);
        timerRunning = false;
        remainingMillis = durationMillis;
        endAtElapsed = 0;
        prefs.edit().remove("timer_end_wall").putLong("timer_remaining", remainingMillis)
                .putBoolean("focus_guard_active", false).apply();
        updateTimerDisplay();
        updateWidget();
        Toast.makeText(this, phaseTitle(timerPhase) + " timer reset", Toast.LENGTH_SHORT).show();
    }

    private void skipTimerPhase() {
        handler.removeCallbacks(timerTick);
        timerRunning = false;
        timerPhase = nextPhase(timerPhase);
        durationMillis = durationForPhase(timerPhase);
        remainingMillis = durationMillis;
        endAtElapsed = 0;
        prefs.edit().putString("timer_phase", timerPhase).putLong("timer_duration", durationMillis)
                .putLong("timer_remaining", remainingMillis).remove("timer_end_wall")
                .putBoolean("focus_guard_active", false).apply();
        updateTimerDisplay();
        updateWidget();
        Toast.makeText(this, "Skipped to " + phaseTitle(timerPhase).toLowerCase(Locale.getDefault()),
                Toast.LENGTH_SHORT).show();
    }

    private void toggleTimer() {
        if (timerRunning) {
            remainingMillis = Math.max(0, endAtElapsed - SystemClock.elapsedRealtime());
            timerRunning = false;
            handler.removeCallbacks(timerTick);
            prefs.edit().remove("timer_end_wall").putLong("timer_remaining", remainingMillis)
                    .putBoolean("focus_guard_active", false).apply();
        } else {
            if (remainingMillis <= 0) remainingMillis = durationMillis;
            endAtElapsed = SystemClock.elapsedRealtime() + remainingMillis;
            timerRunning = true;
            prefs.edit().putLong("timer_end_wall", System.currentTimeMillis() + remainingMillis)
                    .putLong("timer_remaining", remainingMillis).putBoolean("focus_guard_active", true).apply();
            handler.removeCallbacks(timerTick);
            handler.post(timerTick);
        }
        updateTimerDisplay();
        updateWidget();
    }

    private void showDurationDialog() {
        showPomodoroSettings();
    }

    private void showPomodoroSettings() {
        LinearLayout body = column();
        body.setPadding(0, dp(8), 0, dp(12));
        TextView caption = text("Set your study and rest rhythm. Changes apply to the next timer.", 13, Color.LTGRAY);
        body.addView(caption);
        TextInputEditText work = settingsField("Study time (minutes)", prefs.getInt("work_minutes", 25));
        TextInputEditText shortBreak = settingsField("Short break (minutes)", prefs.getInt("short_break_minutes", 5));
        TextInputEditText longBreak = settingsField("Long break (minutes)", prefs.getInt("long_break_minutes", 20));
        TextInputEditText interval = settingsField("Long break after how many study sessions", prefs.getInt("long_break_interval", 4));
        addSettingsField(body, work);
        addSettingsField(body, shortBreak);
        addSettingsField(body, longBreak);
        addSettingsField(body, interval);
        MaterialButton save = button("Save timer settings");
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(-1, dp(48));
        saveParams.topMargin = dp(8);
        body.addView(save, saveParams);
        save.setOnClickListener(v -> {
            try {
                int workMinutes = parseSetting(work, 1, 180);
                int shortMinutes = parseSetting(shortBreak, 1, 180);
                int longMinutes = parseSetting(longBreak, 1, 180);
                int breakInterval = parseSetting(interval, 2, 12);
                prefs.edit().putInt("work_minutes", workMinutes).putInt("short_break_minutes", shortMinutes)
                        .putInt("long_break_minutes", longMinutes).putInt("long_break_interval", breakInterval).apply();
                if (!timerRunning) {
                    durationMillis = durationForPhase(timerPhase);
                    remainingMillis = durationMillis;
                    prefs.edit().putLong("timer_duration", durationMillis).putLong("timer_remaining", remainingMillis).apply();
                }
                Toast.makeText(this, "Timer settings saved.", Toast.LENGTH_SHORT).show();
                buildHome();
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Check the values: duration 1–180 minutes, interval 2–12.", Toast.LENGTH_LONG).show();
            }
        });
        MaterialButton blocker = button("Focus guard · choose distracting apps");
        LinearLayout.LayoutParams blockerParams = new LinearLayout.LayoutParams(-1, dp(48));
        blockerParams.topMargin = dp(8);
        body.addView(blocker, blockerParams);
        blocker.setOnClickListener(v -> showFocusGuard());
        LinearLayout diagnosticsActions = row();
        LinearLayout.LayoutParams diagnosticsParams = new LinearLayout.LayoutParams(-1, dp(48));
        diagnosticsParams.topMargin = dp(8);
        MaterialButton copyDiagnostics = button("کپی گزارش خطا");
        diagnosticsActions.addView(copyDiagnostics, new LinearLayout.LayoutParams(0, -1, 1));
        copyDiagnostics.setOnClickListener(v -> copyDiagnostics());
        MaterialButton shareDiagnostics = button("ارسال گزارش");
        LinearLayout.LayoutParams shareParams = new LinearLayout.LayoutParams(0, -1, 1);
        shareParams.leftMargin = dp(8);
        diagnosticsActions.addView(shareDiagnostics, shareParams);
        shareDiagnostics.setOnClickListener(v -> shareDiagnostics());
        body.addView(diagnosticsActions, diagnosticsParams);
        TextView quietLabel = text("During-focus system controls", 15, Color.WHITE);
        LinearLayout.LayoutParams quietParams = new LinearLayout.LayoutParams(-1, -2);
        quietParams.topMargin = dp(18);
        body.addView(quietLabel, quietParams);
        body.addView(distractionSwitch("Do Not Disturb", "Manage Android notification access",
                () -> openDoNotDisturbSettings(), () -> isDndAllowed()));
        body.addView(distractionSwitch("Airplane mode", "Open Android network settings",
                () -> startActivity(new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)),
                () -> false));
        TextView behaviorLabel = text("Session behavior", 15, Color.WHITE);
        LinearLayout.LayoutParams behaviorParams = new LinearLayout.LayoutParams(-1, -2);
        behaviorParams.topMargin = dp(18);
        body.addView(behaviorLabel, behaviorParams);
        body.addView(preferenceSwitch("Auto-start breaks", "Start break timers without a confirmation", "auto_start_break", false));
        body.addView(preferenceSwitch("Auto-start focus", "Continue automatically after a break", "auto_start_focus", false));
        body.addView(preferenceSwitch("Gentle end chime", "Play a short cue when a phase ends", "session_chime", true));
        showScreen("Pomodoro settings", scroll(body));
    }

    private void shareDiagnostics() {
        File report = AppDiagnostics.getLogFile();
        if (report == null || !report.isFile()) {
            Toast.makeText(this, "هنوز گزارشی برای ارسال ثبت نشده است.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Uri reportUri = androidx.core.content.FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", report);
            Intent share = new Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, "Focus app diagnostics")
                    .putExtra(Intent.EXTRA_TEXT, "گزارش فنی برنامهٔ Focus پیوست شده است.")
                    .putExtra(Intent.EXTRA_STREAM, reportUri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            share.setClipData(android.content.ClipData.newUri(
                    getContentResolver(), "Focus diagnostics", reportUri));
            startActivity(Intent.createChooser(share, "ارسال گزارش خطا"));
        } catch (RuntimeException e) {
            android.util.Log.e("FocusDiagnostics", "Unable to share diagnostics report.", e);
            Toast.makeText(this, "آماده‌سازی گزارش خطا ممکن نشد.", Toast.LENGTH_LONG).show();
        }
    }

    private void copyDiagnostics() {
        File report = AppDiagnostics.getLogFile();
        if (report == null || !report.isFile()) {
            Toast.makeText(this, "هنوز گزارشی برای کپی‌کردن ثبت نشده است.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            java.io.ByteArrayOutputStream contentsBuffer = new java.io.ByteArrayOutputStream();
            try (java.io.FileInputStream input = new java.io.FileInputStream(report)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) contentsBuffer.write(buffer, 0, count);
            }
            String contents = new String(contentsBuffer.toByteArray(), "UTF-8");
            android.content.ClipboardManager clipboard =
                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard == null) {
                Toast.makeText(this, "دسترسی به کلیپ‌بورد ممکن نشد.", Toast.LENGTH_LONG).show();
                return;
            }
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText(
                    "Focus diagnostics", contents));
            Toast.makeText(this, "گزارش خطا کپی شد؛ آن را اینجا جای‌گذاری کنید.",
                    Toast.LENGTH_LONG).show();
        } catch (IOException | RuntimeException e) {
            android.util.Log.e("FocusDiagnostics", "Unable to copy diagnostics report.", e);
            Toast.makeText(this, "کپی گزارش خطا ممکن نشد.", Toast.LENGTH_LONG).show();
        }
    }

    private LinearLayout preferenceSwitch(String title, String description, String key, boolean defaultValue) {
        return settingSwitch(title, description, prefs.getBoolean(key, defaultValue), checked ->
                prefs.edit().putBoolean(key, checked).apply());
    }

    private interface BooleanChangedListener {
        void onChanged(boolean checked);
    }

    private LinearLayout settingSwitch(String title, String description, boolean initialValue,
                                       BooleanChangedListener onChanged) {
        return settingSwitch(title, description, initialValue, onChanged, null);
    }

    private LinearLayout distractionSwitch(String title, String description, Runnable action,
                                          java.util.concurrent.Callable<Boolean> status) {
        boolean initialValue;
        try {
            initialValue = status.call();
        } catch (Exception ignored) {
            initialValue = false;
        }
        return settingSwitch(title, description, initialValue, checked -> { }, action);
    }

    private LinearLayout settingSwitch(String title, String description, boolean initialValue,
                                       BooleanChangedListener onChanged, Runnable action) {
        LinearLayout row = row();
        row.setPadding(0, dp(8), 0, dp(8));
        LinearLayout labels = column();
        labels.addView(text(title, 14, Color.WHITE));
        labels.addView(text(description, 11, Color.LTGRAY));
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        android.widget.Switch control = new android.widget.Switch(this);
        control.setShowText(false);
        control.setChecked(initialValue);
        control.setContentDescription(title);
        control.setOnCheckedChangeListener((button, checked) -> {
            onChanged.onChanged(checked);
            if (action != null) {
                action.run();
                button.postDelayed(() -> button.setChecked(isDndAllowed()), 700);
            }
        });
        row.addView(control);
        return row;
    }

    private void openDoNotDisturbSettings() {
        if (Build.VERSION.SDK_INT < 23) {
            Toast.makeText(this, "Do Not Disturb controls require Android 6 or newer.", Toast.LENGTH_LONG).show();
            return;
        }
        startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
    }

    private boolean isDndAllowed() {
        if (Build.VERSION.SDK_INT < 23) return false;
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        return manager != null && manager.isNotificationPolicyAccessGranted()
                && manager.getCurrentInterruptionFilter() != NotificationManager.INTERRUPTION_FILTER_ALL;
    }

    private TextInputEditText settingsField(String hint, int value) {
        TextInputEditText input = new TextInputEditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setSingleLine(true);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.GRAY);
        input.setHint(fa(hint));
        input.setText(String.valueOf(value));
        return input;
    }

    private void addSettingsField(LinearLayout body, TextInputEditText field) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(54));
        params.topMargin = dp(6);
        body.addView(field, params);
    }

    private int parseSetting(TextInputEditText field, int minimum, int maximum) {
        int value = Integer.parseInt(String.valueOf(field.getText()));
        if (value < minimum || value > maximum) throw new NumberFormatException();
        return value;
    }

    private long durationForPhase(String phase) {
        String key = "focus".equals(phase) ? "work_minutes"
                : "long_break".equals(phase) ? "long_break_minutes" : "short_break_minutes";
        int fallback = "focus".equals(phase) ? 25 : "long_break".equals(phase) ? 20 : 5;
        int minutes = prefs == null ? fallback : prefs.getInt(key, fallback);
        return minutes * 60_000L;
    }

    private String phaseTitle(String phase) {
        if ("long_break".equals(phase)) return "استراحت بلند";
        if ("short_break".equals(phase)) return "استراحت کوتاه";
        return "جلسهٔ تمرکز";
    }

    private String nextPhase(String completedPhase) {
        if (!"focus".equals(completedPhase)) return "focus";
        int interval = prefs.getInt("long_break_interval", 4);
        long total = prefs.getLong("focus_sessions_total", 0);
        return interval > 0 && total % interval == 0 ? "long_break" : "short_break";
    }

    private void finishTimerPhase() {
        String completedPhase = timerPhase;
        timerRunning = false;
        handler.removeCallbacks(timerTick);
        if ("focus".equals(completedPhase)) recordCompletedFocus();
        timerPhase = nextPhase(completedPhase);
        durationMillis = durationForPhase(timerPhase);
        remainingMillis = durationMillis;
        endAtElapsed = 0;
        boolean autoStart = "focus".equals(timerPhase)
                ? prefs.getBoolean("auto_start_focus", false)
                : prefs.getBoolean("auto_start_break", false);
        prefs.edit().putString("timer_phase", timerPhase).putLong("timer_duration", durationMillis)
                .putLong("timer_remaining", remainingMillis).remove("timer_end_wall")
                .putBoolean("focus_guard_active", false).apply();
        updateTimerDisplay();
        if (autoStart) {
            toggleTimer();
        } else {
            if (prefs.getBoolean("session_chime", true)) playTimerChime();
        }
        Toast.makeText(this, phaseTitle(completedPhase) + " complete · " + phaseTitle(timerPhase) + " is ready",
                Toast.LENGTH_LONG).show();
    }

    private void recordCompletedFocus() {
        String day = todayKey();
        String countKey = "stats_count:" + day;
        String studyKey = "stats_study:" + day;
        int completedFocusSessions = prefs.getInt(countKey, 0) + 1;
        prefs.edit().putInt(countKey, completedFocusSessions)
                .putLong(studyKey, prefs.getLong(studyKey, 0) + durationMillis)
                .putLong("focus_sessions_total", prefs.getLong("focus_sessions_total", 0) + 1).apply();
        appendPomodoroNote();
        updateWidget();
    }

    private String todayStatsText() {
        String day = todayKey();
        int sessions = prefs.getInt("stats_count:" + day, 0);
        long totalMinutes = prefs.getLong("stats_study:" + day, 0) / 60_000L;
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        int goal = prefs.getInt("long_break_interval", 4);
        return "امروز · " + persianDigits(sessions) + "/" + persianDigits(goal)
                + " جلسه · " + (hours > 0 ? persianDigits(hours) + "ساعت " : "")
                + persianDigits(minutes) + " دقیقه تمرکز";
    }

    private void appendPomodoroNote() {
        String key = noteKey(todayKey());
        String old = prefs.getString(key, "");
        String entry = "• جلسهٔ تمرکز کامل شد — "
                + persianDigits(new SimpleDateFormat("HH:mm", Locale.US).format(new Date()));
        prefs.edit().putString(key, old.trim().isEmpty() ? entry : old + "\n" + entry).apply();
    }

    private String todayKey() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private String noteKey(String date) {
        return "note:" + date;
    }

    private void showNotes() {
        LinearLayout list = column();
        list.setPadding(0, dp(8), 0, dp(12));
        LinearLayout notesToolbar = row();
        TextView noteHeading = text("یادداشت‌های روزانه", 17, Color.WHITE);
        notesToolbar.addView(noteHeading, new LinearLayout.LayoutParams(0, -2, 1));
        MaterialButton addToday = button("یادداشت امروز");
        addToday.setMinHeight(dp(48));
        addToday.setOnClickListener(v -> showNoteEditor(todayKey()));
        notesToolbar.addView(addToday);
        list.addView(notesToolbar);
        TextView hint = text("متن کامل هر یادداشت و کارهای همان روز را همین‌جا ببینید.", 12, Color.LTGRAY);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2);
        hintParams.topMargin = dp(4);
        hintParams.bottomMargin = dp(10);
        list.addView(hint, hintParams);
        List<String> dates = new ArrayList<>();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            if (entry.getKey().startsWith("note:")) dates.add(entry.getKey().substring(5));
            else if (entry.getKey().startsWith(TASKS_PREFIX)) {
                dates.add(entry.getKey().substring(TASKS_PREFIX.length()));
            }
        }
        JSONArray savedReminders = loadReminders();
        SimpleDateFormat dayFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        for (int i = 0; i < savedReminders.length(); i++) {
            JSONObject reminder = savedReminders.optJSONObject(i);
            if (reminder == null) continue;
            dates.add(dayFormat.format(new Date(reminder.optLong("at"))));
        }
        if (!dates.contains(todayKey())) dates.add(todayKey());
        Collections.sort(dates, Collections.reverseOrder());
        Set<String> uniqueDates = new LinkedHashSet<>(dates);
        for (String date : dates) {
            if (!uniqueDates.remove(date)) continue;
            MaterialCardView noteCard = card();
            LinearLayout noteContent = column();
            noteContent.setPadding(dp(16), dp(14), dp(16), dp(14));
            TextView dateTitle = text(prettyDate(date), 14, Color.rgb(60, 180, 230));
            dateTitle.setTypeface(appTypeface, Typeface.BOLD);
            noteContent.addView(dateTitle);
            String note = prefs.getString(noteKey(date), "");
            TextView noteBody = text(note.trim().isEmpty() ? "هنوز یادداشتی برای این روز ثبت نشده است." : note,
                    14, note.trim().isEmpty() ? Color.GRAY : Color.WHITE);
            noteBody.setLineSpacing(dp(3), 1f);
            noteBody.setPadding(0, dp(10), 0, dp(10));
            noteBody.setOnClickListener(v -> showNoteEditor(date));
            noteContent.addView(noteBody);
            int completedSessions = prefs.getInt("stats_count:" + date, 0);
            long focusMinutes = prefs.getLong("stats_study:" + date, 0) / 60_000L;
            int unlocks = prefs.getInt("screen_time_unlocks:" + date, 0);
            noteContent.addView(text("تمرکز روز · " + persianDigits(completedSessions)
                    + " جلسه · " + persianDigits(focusMinutes) + " دقیقه · "
                    + persianDigits(unlocks) + " بازکردن موقت", 11, Color.LTGRAY));
            LinearLayout taskHeader = row();
            TextView taskTitle = text("کارهای این روز", 13, Color.WHITE);
            taskTitle.setTypeface(appTypeface, Typeface.BOLD);
            taskHeader.addView(taskTitle, new LinearLayout.LayoutParams(0, -2, 1));
            MaterialButton addTask = button("+ کار");
            addTask.setMinHeight(dp(40));
            addTask.setOnClickListener(v -> showTaskEditor(date, null));
            taskHeader.addView(addTask);
            MaterialButton addAlarm = button("+ یادآور");
            addAlarm.setMinHeight(dp(40));
            LinearLayout.LayoutParams addAlarmParams = new LinearLayout.LayoutParams(-2, dp(40));
            addAlarmParams.leftMargin = dp(4);
            addAlarm.setOnClickListener(v -> showReminderEditor(date));
            taskHeader.addView(addAlarm, addAlarmParams);
            noteContent.addView(taskHeader);
            addTaskRows(noteContent, loadTasks(date), date, false);
            addReminderRows(noteContent, date);
            noteCard.addView(noteContent);
            noteCard.setContentDescription(prettyDate(date) + ". " + noteBody.getText());
            noteCard.setOnLongClickListener(v -> {
                new MaterialAlertDialogBuilder(this).setTitle(prettyDate(date))
                        .setItems(new String[]{"ویرایش یادداشت", "پاک‌کردن یادداشت"}, (dialog, which) -> {
                            if (which == 0) showNoteEditor(date);
                            else new MaterialAlertDialogBuilder(this).setTitle("Delete this day's note?")
                                    .setMessage("This cannot be undone.")
                                    .setNegativeButton("Cancel", null)
                                    .setPositiveButton("Delete", (d, w) -> {
                                        prefs.edit().remove(noteKey(date)).apply();
                                        showNotes();
                                    }).show();
                        }).show();
                return true;
            });
            noteBody.setOnLongClickListener(v -> {
                new MaterialAlertDialogBuilder(this).setTitle(prettyDate(date))
                        .setItems(new String[]{"ویرایش یادداشت", "پاک‌کردن یادداشت"}, (dialog, which) -> {
                            if (which == 0) showNoteEditor(date);
                            else new MaterialAlertDialogBuilder(this)
                                    .setTitle("یادداشت پاک شود؟")
                                    .setNegativeButton("انصراف", null)
                                    .setPositiveButton("پاک‌کردن", (d, w) -> {
                                        prefs.edit().remove(noteKey(date)).apply();
                                        showNotes();
                                    }).show();
                        }).show();
                return true;
            });
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
            cardParams.bottomMargin = dp(12);
            list.addView(noteCard, cardParams);
        }
        showScreen("Notes", scroll(list));
    }

    private void addReminderRows(LinearLayout parent, String date) {
        JSONArray reminders = loadReminders();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        int count = 0;
        for (int i = 0; i < reminders.length(); i++) {
            JSONObject reminder = reminders.optJSONObject(i);
            if (reminder == null || !date.equals(dateFormat.format(new Date(reminder.optLong("at"))))) {
                continue;
            }
            if (count++ == 0) {
                TextView heading = text("یادآورهای این روز", 12, Color.rgb(60, 180, 230));
                LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(-1, -2);
                headingParams.topMargin = dp(8);
                parent.addView(heading, headingParams);
            }
            String id = reminder.optString("id");
            LinearLayout row = row();
            row.setPadding(0, dp(2), 0, dp(2));
            TextView reminderText = text("یادآور · " + reminder.optString("title") + " · "
                    + persianDigits(new SimpleDateFormat("HH:mm", Locale.US)
                    .format(new Date(reminder.optLong("at")))), 12, Color.WHITE);
            row.addView(reminderText, new LinearLayout.LayoutParams(0, -2, 1));
            MaterialButton remove = iconButton(R.drawable.ic_close, "حذف یادآور",
                    () -> new MaterialAlertDialogBuilder(this)
                            .setTitle("حذف یادآور؟")
                            .setNegativeButton("انصراف", null)
                            .setPositiveButton("حذف", (dialog, which) -> {
                                removeReminder(id);
                                showNotes();
                            }).show());
            row.addView(remove);
            parent.addView(row);
        }
    }

    private void removeReminder(String id) {
        JSONArray reminders = loadReminders();
        JSONArray remaining = new JSONArray();
        List<Long> calendarEventIds = new ArrayList<>();
        for (int i = 0; i < reminders.length(); i++) {
            JSONObject item = reminders.optJSONObject(i);
            if (item == null || id.equals(item.optString("id"))) {
                if (item != null) {
                    long eventId = item.optLong("calendarEventId");
                    if (eventId > 0 && hasCalendarPermissions()) {
                        calendarEventIds.add(eventId);
                    }
                }
                continue;
            }
            remaining.put(item);
        }
        prefs.edit().putString(SAVED_REMINDERS, remaining.toString()).apply();
        ReminderScheduler.cancel(this, id);
        if (!calendarEventIds.isEmpty()) {
            calendarExecutor.execute(() -> {
                for (long eventId : calendarEventIds) {
                    try {
                        DeviceCalendarSync.deleteEvent(this, eventId);
                    } catch (RuntimeException e) {
                        android.util.Log.e("FocusCalendar", "Unable to remove reminder from calendar.", e);
                    }
                }
            });
        }
    }

    private Date parseDay(String value) {
        try {
            Date date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(value);
            return date == null ? new Date() : date;
        } catch (Exception ignored) {
            return new Date();
        }
    }

    private void showNoteEditor(String date) {
        LinearLayout editorScreen = column();
        editorScreen.setPadding(dp(4), dp(8), dp(4), dp(12));
        TextView dateLabel = text(prettyDate(date), 13, Color.rgb(60, 180, 230));
        editorScreen.addView(dateLabel);
        TextInputEditText editor = new TextInputEditText(this);
        editor.setTextColor(Color.WHITE);
        editor.setHintTextColor(Color.GRAY);
        editor.setHint("برنامه، ایده یا یادداشت روزتان را بنویسید…");
        if (appTypeface != null) editor.setTypeface(appTypeface);
        editor.setMinLines(8);
        editor.setGravity(Gravity.TOP);
        editor.setPadding(dp(14), dp(14), dp(14), dp(14));
        editor.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(34, 158, 217)));
        editor.setText(prefs.getString(noteKey(date), ""));
        LinearLayout.LayoutParams editorParams = new LinearLayout.LayoutParams(-1, 0, 1);
        editorParams.topMargin = dp(8);
        editorScreen.addView(editor, editorParams);
        TextView savedStatus = text("تغییرات به‌صورت خودکار ذخیره می‌شوند", 12, Color.LTGRAY);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
        statusParams.topMargin = dp(8);
        editorScreen.addView(savedStatus, statusParams);
        editor.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                prefs.edit().putString(noteKey(date), s.toString()).apply();
                savedStatus.setText(getResources().getQuantityString(
                        R.plurals.note_saved_characters, s.length(), s.length()));
            }
            @Override public void afterTextChanged(android.text.Editable s) { }
        });
        showScreen("Note editor", editorScreen);
    }

    private String prettyDate(String value) {
        try {
            Date date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(value);
            if (date == null) return value;
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(date);
            String[] dates = CalendarDateUtils.datesForDay(calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH));
            return dates[CalendarDateUtils.JALALI] + "\n" + dates[CalendarDateUtils.GREGORIAN];
        } catch (java.text.ParseException ignored) {
            return value;
        }
    }

    private String todayDateLabel() {
        return prettyDate(todayKey());
    }

    private void showDatePickerForNotes() {
        Calendar today = Calendar.getInstance();
        new DatePickerDialog(this, (picker, year, month, day) -> {
            Calendar selected = Calendar.getInstance();
            selected.clear();
            selected.set(year, month, day);
            showNoteEditor(new SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    .format(selected.getTime()));
        }, today.get(Calendar.YEAR), today.get(Calendar.MONTH),
                today.get(Calendar.DAY_OF_MONTH)).show();
    }

    private String formatDateTime(long timeMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timeMillis);
        String date = prettyDate(new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.getTime()));
        return date + " · " + persianDigits(new SimpleDateFormat("HH:mm", Locale.US).format(calendar.getTime()));
    }

    private String persianDigits(Object value) {
        return String.valueOf(value).replace('0', '۰').replace('1', '۱').replace('2', '۲')
                .replace('3', '۳').replace('4', '۴').replace('5', '۵')
                .replace('6', '۶').replace('7', '۷').replace('8', '۸').replace('9', '۹');
    }

    private String tasksKey(String date) {
        return TASKS_PREFIX + date;
    }

    private List<PlannerTask> loadTasks(String date) {
        List<PlannerTask> tasks = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs.getString(tasksKey(date), "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                PlannerTask task = new PlannerTask(item.optString("id", UUID.randomUUID().toString()),
                        item.optString("title"), item.optString("details"), item.optLong("dueAt"),
                        item.optLong("remindAt"), item.optBoolean("done", false),
                        item.optLong("calendarEventId"));
                task.noteDate = date;
                tasks.add(task);
            }
        } catch (JSONException e) {
            android.util.Log.e("FocusTasks", "Unable to read tasks for " + date, e);
        }
        Collections.sort(tasks, (first, second) -> Long.compare(first.dueAt, second.dueAt));
        return tasks;
    }

    private List<PlannerTask> loadAllTasks() {
        List<PlannerTask> tasks = new ArrayList<>();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            if (!entry.getKey().startsWith(TASKS_PREFIX) || !(entry.getValue() instanceof String)) continue;
            String date = entry.getKey().substring(TASKS_PREFIX.length());
            for (PlannerTask task : loadTasks(date)) {
                task.noteDate = date;
                tasks.add(task);
            }
        }
        Collections.sort(tasks, (first, second) -> Long.compare(first.dueAt, second.dueAt));
        return tasks;
    }

    private void saveTasks(String date, List<PlannerTask> tasks) {
        boolean syncCalendar = calendarSyncEnabled() && hasCalendarPermissions();
        List<PlannerTask> previousTasks = syncCalendar ? loadTasks(date) : new ArrayList<>();
        JSONArray array = new JSONArray();
        try {
            for (PlannerTask task : tasks) {
                JSONObject item = new JSONObject();
                item.put("id", task.id);
                item.put("title", task.title);
                item.put("details", task.details);
                item.put("dueAt", task.dueAt);
                item.put("remindAt", task.remindAt);
                item.put("done", task.done);
                item.put("calendarEventId", task.calendarEventId);
                array.put(item);
            }
        } catch (JSONException e) {
            android.util.Log.e("FocusTasks", "Unable to save tasks for " + date, e);
            Toast.makeText(this, "ذخیرهٔ کارها انجام نشد.", Toast.LENGTH_LONG).show();
            return;
        }
        prefs.edit().putString(tasksKey(date), array.toString()).apply();
        if (syncCalendar) {
            List<PlannerTask> updatedTasks = new ArrayList<>(tasks);
            calendarExecutor.execute(() -> {
                if (syncTaskChanges(date, previousTasks, updatedTasks)) {
                    handler.post(() -> {
                        if (!isFinishing() && !isDestroyed()) {
                            Toast.makeText(this, "کارها ذخیره شدند، اما همگام‌سازی با تقویم کامل نشد.",
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                }
            });
        }
    }

    private boolean syncTaskChanges(String date, List<PlannerTask> previousTasks,
                                    List<PlannerTask> updatedTasks) {
        if (!calendarSyncEnabled() || !hasCalendarPermissions()) return false;
        long calendarId = prefs.getLong("calendar_sync_id", -1);
        boolean failed = false;
        for (PlannerTask previous : previousTasks) {
            boolean retained = false;
            for (PlannerTask task : updatedTasks) {
                if (previous.id.equals(task.id)) {
                    retained = true;
                    break;
                }
            }
            if (!retained) {
                long eventId = prefs.getLong(taskCalendarEventKey(previous.id),
                        previous.calendarEventId);
                try {
                    DeviceCalendarSync.deleteEvent(this, eventId);
                    prefs.edit().remove(taskCalendarEventKey(previous.id)).apply();
                } catch (RuntimeException e) {
                    failed = true;
                    android.util.Log.e("FocusCalendar", "Unable to delete a removed task event.", e);
                }
            }
        }
        for (PlannerTask task : updatedTasks) {
            try {
                long existingEventId = prefs.getLong(taskCalendarEventKey(task.id), task.calendarEventId);
                long eventId = DeviceCalendarSync.upsertTask(this, calendarId, existingEventId,
                        task.title, task.details + (task.done ? "\nوضعیت: انجام‌شده" : ""),
                        task.dueAt, task.remindAt, task.done);
                prefs.edit().putLong(taskCalendarEventKey(task.id), eventId).apply();
                persistTaskCalendarEventId(date, task.id, eventId);
            } catch (RuntimeException e) {
                failed = true;
                android.util.Log.e("FocusCalendar", "Unable to synchronize task " + task.id, e);
            }
        }
        return failed;
    }

    private String taskCalendarEventKey(String taskId) {
        return "calendar_event_task:" + taskId;
    }

    private void persistTaskCalendarEventId(String date, String taskId, long eventId) {
        String key = tasksKey(date);
        try {
            JSONArray current = new JSONArray(prefs.getString(key, "[]"));
            for (int i = 0; i < current.length(); i++) {
                JSONObject task = current.optJSONObject(i);
                if (task != null && taskId.equals(task.optString("id"))) {
                    task.put("calendarEventId", eventId);
                    prefs.edit().putString(key, current.toString()).apply();
                    return;
                }
            }
        } catch (JSONException e) {
            android.util.Log.e("FocusCalendar", "Unable to save the task calendar link.", e);
        }
    }

    private void scheduleTaskReminders(PlannerTask task) {
        ReminderScheduler.cancel(this, task.id + ":deadline");
        ReminderScheduler.cancel(this, task.id + ":reminder");
        if (task.done) return;
        ReminderScheduler.schedule(this, task.id + ":deadline", "مهلت کار تمام شد",
                task.title, task.dueAt);
        if (task.remindAt > 0 && task.remindAt < task.dueAt) {
            ReminderScheduler.schedule(this, task.id + ":reminder", "یادآوری کار",
                    task.title, task.remindAt);
        }
    }

    private void showTaskEditor(String date, @Nullable PlannerTask existing) {
        LinearLayout form = column();
        form.setPadding(dp(4), dp(8), dp(4), 0);
        TextInputEditText title = new TextInputEditText(this);
        title.setSingleLine(true);
        title.setHint("عنوان کار");
        title.setTextColor(Color.WHITE);
        title.setHintTextColor(Color.GRAY);
        if (appTypeface != null) title.setTypeface(appTypeface);
        if (existing != null) title.setText(existing.title);
        form.addView(title, new LinearLayout.LayoutParams(-1, dp(56)));
        TextInputEditText details = new TextInputEditText(this);
        details.setHint("توضیحات (اختیاری)");
        details.setTextColor(Color.WHITE);
        details.setHintTextColor(Color.GRAY);
        details.setMinLines(2);
        if (appTypeface != null) details.setTypeface(appTypeface);
        if (existing != null) details.setText(existing.details);
        form.addView(details, new LinearLayout.LayoutParams(-1, dp(76)));

        Calendar due = Calendar.getInstance();
        due.add(Calendar.HOUR_OF_DAY, 1);
        if (existing != null && existing.dueAt > 0) due.setTimeInMillis(existing.dueAt);
        TextView dueLabel = text("", 12, Color.LTGRAY);
        MaterialButton dueButton = button("انتخاب زمان پایان");
        dueButton.setOnClickListener(v -> pickDateTime(due, "مهلت انجام", dueLabel));
        form.addView(dueButton);
        form.addView(dueLabel);
        dueLabel.setText(formatDateTime(due.getTimeInMillis()));

        int[] reminderOffsets = {-1, 0, 5, 10, 30, 60};
        String[] reminderOptions = {"بدون یادآوری", "در زمان پایان", "۵ دقیقه قبل",
                "۱۰ دقیقه قبل", "۳۰ دقیقه قبل", "۱ ساعت قبل"};
        int initialOffset = existing == null ? 10 : existing.remindAt <= 0 ? -1
                : (int) Math.max(0, (existing.dueAt - existing.remindAt) / 60_000L);
        int selectedIndex = 0;
        for (int i = 0; i < reminderOffsets.length; i++) {
            if (reminderOffsets[i] == initialOffset) selectedIndex = i;
        }
        final int[] selectedReminder = {selectedIndex};
        MaterialButton reminderButton = button(reminderOptions[selectedIndex]);
        reminderButton.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle("زمان یادآوری")
                .setSingleChoiceItems(reminderOptions, selectedReminder[0], (dialog, which) -> {
                    selectedReminder[0] = which;
                    reminderButton.setText(reminderOptions[which]);
                    dialog.dismiss();
                }).show());
        form.addView(reminderButton);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(existing == null ? "کار جدید" : "ویرایش کار")
                .setView(form)
                .setNegativeButton("انصراف", null)
                .setPositiveButton("ذخیره", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String taskTitle = String.valueOf(title.getText()).trim();
                    if (taskTitle.isEmpty()) {
                        title.setError("عنوان کار را وارد کنید.");
                        return;
                    }
                    long dueAt = due.getTimeInMillis();
                    int offset = reminderOffsets[selectedReminder[0]];
                    long remindAt = offset < 0 ? 0 : dueAt - offset * 60_000L;
                    PlannerTask task = existing == null
                            ? new PlannerTask(UUID.randomUUID().toString(), taskTitle,
                            String.valueOf(details.getText()).trim(), dueAt, remindAt, false, 0)
                            : existing.withValues(taskTitle, String.valueOf(details.getText()).trim(),
                            dueAt, remindAt);
                    List<PlannerTask> tasks = loadTasks(date);
                    if (existing == null) tasks.add(task);
                    else {
                        for (int i = 0; i < tasks.size(); i++) {
                            if (tasks.get(i).id.equals(existing.id)) {
                                tasks.set(i, task);
                                break;
                            }
                        }
                    }
                    saveTasks(date, tasks);
                    requestNotificationPermission();
                    scheduleTaskReminders(task);
                    dialog.dismiss();
                    if ("Reminders".equals(activeScreen)) showReminders();
                    else if (isHome) buildHome();
                    else showNotes();
                }));
        dialog.show();
    }

    private void pickDateTime(Calendar calendar, String title, TextView preview) {
        new DatePickerDialog(this, (picker, year, month, day) -> {
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, month);
            calendar.set(Calendar.DAY_OF_MONTH, day);
            new TimePickerDialog(this, (timePicker, hour, minute) -> {
                calendar.set(Calendar.HOUR_OF_DAY, hour);
                calendar.set(Calendar.MINUTE, minute);
                calendar.set(Calendar.SECOND, 0);
                calendar.set(Calendar.MILLISECOND, 0);
                preview.setText(title + ": " + formatDateTime(calendar.getTimeInMillis()));
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show();
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void addTaskRows(LinearLayout parent, List<PlannerTask> tasks,
                             String noteDate, boolean compact) {
        if (tasks.isEmpty()) {
            parent.addView(text("هنوز کاری برای این یادداشت ثبت نشده است.", 12, Color.GRAY));
            return;
        }
        for (PlannerTask task : tasks) {
            String taskDate = task.noteDate == null ? noteDate : task.noteDate;
            LinearLayout item = row();
            CheckBox checkBox = new CheckBox(this);
            checkBox.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.rgb(60, 180, 230)));
            checkBox.setChecked(task.done);
            item.addView(checkBox, new LinearLayout.LayoutParams(dp(42), dp(48)));
            LinearLayout details = column();
            TextView taskName = text(task.title, 13, task.done ? Color.GRAY : Color.WHITE);
            if (task.done) taskName.setPaintFlags(taskName.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            details.addView(taskName);
            if (!task.details.isEmpty()) {
                TextView description = text(task.details, 11, Color.LTGRAY);
                details.addView(description);
            }
            String dueText = task.dueAt <= System.currentTimeMillis() && !task.done
                    ? "عقب‌افتاده · " + formatDateTime(task.dueAt)
                    : "مهلت · " + formatDateTime(task.dueAt);
            details.addView(text(dueText, 11, task.dueAt < System.currentTimeMillis()
                    && !task.done ? Color.rgb(255, 130, 110) : Color.LTGRAY));
            item.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
            checkBox.setOnCheckedChangeListener((button, checked) -> {
                task.done = checked;
                List<PlannerTask> current = loadTasks(taskDate);
                for (PlannerTask itemTask : current) {
                    if (itemTask.id.equals(task.id)) itemTask.done = checked;
                }
                saveTasks(taskDate, current);
                scheduleTaskReminders(task);
                if ("Reminders".equals(activeScreen)) showReminders();
                else if (isHome) buildHome();
                else showNotes();
            });
            item.setOnClickListener(v -> showTaskEditor(taskDate, task));
            item.setOnLongClickListener(v -> {
                new MaterialAlertDialogBuilder(this).setTitle(task.title)
                        .setMessage("این کار و یادآورهای آن پاک شوند؟")
                        .setNegativeButton("انصراف", null)
                        .setPositiveButton("پاک‌کردن", (dialog, which) -> {
                            List<PlannerTask> current = loadTasks(taskDate);
                            for (int i = current.size() - 1; i >= 0; i--) {
                                if (current.get(i).id.equals(task.id)) {
                                    current.remove(i);
                                    break;
                                }
                            }
                            saveTasks(taskDate, current);
                            ReminderScheduler.cancel(this, task.id + ":deadline");
                            ReminderScheduler.cancel(this, task.id + ":reminder");
                            if ("Reminders".equals(activeScreen)) showReminders();
                            else if (isHome) buildHome();
                            else showNotes();
                        }).show();
                return true;
            });
            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(-1, -2);
            itemParams.topMargin = compact ? dp(2) : dp(4);
            parent.addView(item, itemParams);
        }
    }

    private void addUpcomingTasks(LinearLayout parent) {
        List<PlannerTask> tasks = new ArrayList<>();
        for (PlannerTask task : loadAllTasks()) if (!task.done) tasks.add(task);
        Collections.sort(tasks, (first, second) -> Long.compare(first.dueAt, second.dueAt));
        TextView heading = text("کارهای پیشِ‌رو", 15, Color.WHITE);
        heading.setTypeface(appTypeface, Typeface.BOLD);
        heading.setOnClickListener(v -> showReminders());
        heading.setClickable(true);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(-1, -2);
        headingParams.bottomMargin = dp(6);
        parent.addView(heading, headingParams);
        MaterialCardView taskCard = card();
        LinearLayout taskList = column();
        taskList.setPadding(dp(10), dp(6), dp(10), dp(6));
        if (tasks.isEmpty()) {
            TextView empty = text("هنوز کاری ثبت نشده؛ تکلیف بعدی را همین‌جا برنامه‌ریزی کنید.", 12, Color.LTGRAY);
            taskList.addView(empty);
        } else {
            List<PlannerTask> nextTasks = tasks.subList(0, Math.min(3, tasks.size()));
            for (PlannerTask task : nextTasks) {
                String date = task.noteDate == null ? todayKey() : task.noteDate;
                addTaskRows(taskList, Collections.singletonList(task), date, true);
            }
        }
        MaterialButton addTask = button("افزودن تکلیف");
        addTask.setIconResource(R.drawable.ic_add);
        addTask.setIconTint(android.content.res.ColorStateList.valueOf(Color.WHITE));
        addTask.setMinHeight(dp(44));
        LinearLayout.LayoutParams addTaskParams = new LinearLayout.LayoutParams(-1, dp(44));
        addTaskParams.topMargin = dp(4);
        taskList.addView(addTask, addTaskParams);
        addTask.setOnClickListener(v -> showTaskEditor(todayKey(), null));
        taskCard.addView(taskList);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.bottomMargin = dp(14);
        parent.addView(taskCard, cardParams);
    }

    private void restoreExamTimer() {
        examTitle = prefs.getString("exam_title", "آزمون");
        examDurationMillis = Math.max(60_000L, prefs.getLong("exam_duration", 90 * 60_000L));
        long endAtWall = prefs.getLong("exam_end_wall", 0);
        if (endAtWall > System.currentTimeMillis()) {
            examRemainingMillis = endAtWall - System.currentTimeMillis();
            examEndAtElapsed = SystemClock.elapsedRealtime() + examRemainingMillis;
            examRunning = true;
        } else if (endAtWall > 0) {
            examRemainingMillis = 0;
            prefs.edit().remove("exam_end_wall").putLong("exam_remaining", 0).apply();
            ReminderScheduler.cancel(this, EXAM_REMINDER_ID);
            ReminderReceiver.showNotification(this, "زمان آزمون تمام شد", examTitle);
        } else {
            examRemainingMillis = Math.max(0, prefs.getLong("exam_remaining", examDurationMillis));
        }
    }

    private void addExamTimerCard(LinearLayout parent) {
        MaterialCardView card = card();
        LinearLayout content = row();
        content.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout details = column();
        TextView heading = text("تایمر آزمون", 14, Color.WHITE);
        heading.setTypeface(appTypeface, Typeface.BOLD);
        details.addView(heading);
        examHomeSummary = text(examTitle + " · " + formatDuration(examRemainingMillis),
                12, Color.LTGRAY);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(-1, -2);
        summaryParams.topMargin = dp(3);
        details.addView(examHomeSummary, summaryParams);
        content.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
        MaterialButton open = button(examRunning ? "مدیریت" : "تنظیم");
        open.setMinHeight(dp(42));
        open.setOnClickListener(v -> showExamTimer());
        content.addView(open);
        card.addView(content);
        card.setOnClickListener(v -> showExamTimer());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(14);
        parent.addView(card, params);
    }

    private String formatDuration(long millis) {
        long totalSeconds = Math.max(0, (millis + 999) / 1000);
        return persianDigits(String.format(Locale.US, "%02d:%02d:%02d",
                totalSeconds / 3600, totalSeconds / 60 % 60, totalSeconds % 60));
    }

    private void showExamTimer() {
        LinearLayout content = column();
        content.setPadding(0, dp(12), 0, dp(12));
        TextView description = text("مدت آزمون را تنظیم کنید؛ با پایان زمان، اعلان دریافت می‌کنید.",
                13, Color.LTGRAY);
        content.addView(description);
        TextInputEditText title = new TextInputEditText(this);
        title.setSingleLine(true);
        title.setTextColor(Color.WHITE);
        title.setHintTextColor(Color.GRAY);
        title.setHint("نام آزمون");
        title.setText(examTitle);
        if (appTypeface != null) title.setTypeface(appTypeface);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, dp(54));
        titleParams.topMargin = dp(12);
        content.addView(title, titleParams);
        TextInputEditText duration = new TextInputEditText(this);
        duration.setSingleLine(true);
        duration.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        duration.setTextColor(Color.WHITE);
        duration.setHintTextColor(Color.GRAY);
        duration.setHint("مدت آزمون به دقیقه");
        duration.setText(String.valueOf(examDurationMillis / 60_000L));
        if (appTypeface != null) duration.setTypeface(appTypeface);
        LinearLayout.LayoutParams durationParams = new LinearLayout.LayoutParams(-1, dp(54));
        durationParams.topMargin = dp(8);
        content.addView(duration, durationParams);
        FrameLayout clock = new FrameLayout(this);
        examProgress = new TimerProgressView();
        clock.addView(examProgress, new FrameLayout.LayoutParams(dp(220), dp(220), Gravity.CENTER));
        examTimeText = text(formatDuration(examRemainingMillis), 32, Color.WHITE);
        examTimeText.setGravity(Gravity.CENTER);
        clock.addView(examTimeText, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams clockParams = new LinearLayout.LayoutParams(-1, dp(230));
        clockParams.topMargin = dp(12);
        content.addView(clock, clockParams);
        LinearLayout controls = row();
        controls.setGravity(Gravity.CENTER);
        examStartButton = button(examRunning ? "توقف موقت" : "شروع آزمون");
        examStartButton.setOnClickListener(v -> {
            if (!examRunning) {
                String examName = String.valueOf(title.getText()).trim();
                if (!examName.isEmpty()) examTitle = examName;
                try {
                    long minutes = Long.parseLong(String.valueOf(duration.getText()).trim());
                    if (minutes < 1 || minutes > 720) throw new NumberFormatException();
                    if (!examRunning && examRemainingMillis == examDurationMillis) {
                        examDurationMillis = minutes * 60_000L;
                        examRemainingMillis = examDurationMillis;
                    } else if (!examRunning && examRemainingMillis <= 0) {
                        examDurationMillis = minutes * 60_000L;
                        examRemainingMillis = examDurationMillis;
                    }
                } catch (NumberFormatException e) {
                    duration.setError("مدت آزمون باید بین ۱ تا ۷۲۰ دقیقه باشد.");
                    return;
                }
                prefs.edit().putString("exam_title", examTitle)
                        .putLong("exam_duration", examDurationMillis).apply();
            }
            toggleExamTimer();
        });
        controls.addView(examStartButton);
        MaterialButton reset = button("شروع دوباره");
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(-2, dp(48));
        resetParams.leftMargin = dp(8);
        reset.setLayoutParams(resetParams);
        reset.setOnClickListener(v -> {
            if (!examRunning) {
                try {
                    long minutes = Long.parseLong(String.valueOf(duration.getText()).trim());
                    if (minutes < 1 || minutes > 720) throw new NumberFormatException();
                    examDurationMillis = minutes * 60_000L;
                    String examName = String.valueOf(title.getText()).trim();
                    if (!examName.isEmpty()) examTitle = examName;
                    prefs.edit().putString("exam_title", examTitle)
                            .putLong("exam_duration", examDurationMillis).apply();
                } catch (NumberFormatException e) {
                    duration.setError("مدت آزمون باید بین ۱ تا ۷۲۰ دقیقه باشد.");
                    return;
                }
            }
            resetExamTimer();
        });
        controls.addView(reset);
        LinearLayout.LayoutParams controlsParams = new LinearLayout.LayoutParams(-1, -2);
        controlsParams.topMargin = dp(12);
        content.addView(controls, controlsParams);
        updateExamDisplay();
        showScreen("Exam timer", scroll(content));
    }

    private void toggleExamTimer() {
        if (examRunning) {
            examRemainingMillis = Math.max(0, examEndAtElapsed - SystemClock.elapsedRealtime());
            examRunning = false;
            handler.removeCallbacks(examTick);
            prefs.edit().remove("exam_end_wall").putLong("exam_remaining", examRemainingMillis).apply();
            ReminderScheduler.cancel(this, EXAM_REMINDER_ID);
        } else {
            requestNotificationPermission();
            if (examRemainingMillis <= 0) examRemainingMillis = examDurationMillis;
            examEndAtElapsed = SystemClock.elapsedRealtime() + examRemainingMillis;
            examRunning = true;
            long endAtWall = System.currentTimeMillis() + examRemainingMillis;
            prefs.edit().putLong("exam_end_wall", endAtWall)
                    .putLong("exam_remaining", examRemainingMillis).apply();
            ReminderScheduler.schedule(this, EXAM_REMINDER_ID, "زمان آزمون تمام شد",
                    examTitle, endAtWall);
            handler.removeCallbacks(examTick);
            handler.post(examTick);
        }
        updateExamDisplay();
    }

    private void resetExamTimer() {
        examRunning = false;
        handler.removeCallbacks(examTick);
        ReminderScheduler.cancel(this, EXAM_REMINDER_ID);
        examRemainingMillis = examDurationMillis;
        examEndAtElapsed = 0;
        prefs.edit().remove("exam_end_wall").putLong("exam_remaining", examRemainingMillis).apply();
        updateExamDisplay();
    }

    private void finishExamTimer() {
        examRunning = false;
        handler.removeCallbacks(examTick);
        examRemainingMillis = 0;
        examEndAtElapsed = 0;
        prefs.edit().remove("exam_end_wall").putLong("exam_remaining", 0).apply();
        ReminderScheduler.cancel(this, EXAM_REMINDER_ID);
        ReminderReceiver.showNotification(this, "زمان آزمون تمام شد", examTitle);
        updateExamDisplay();
    }

    private void updateExamDisplay() {
        String remaining = formatDuration(examRemainingMillis);
        if (examTimeText != null) examTimeText.setText(remaining);
        if (examProgress != null) examProgress.setProgress(examRemainingMillis, examDurationMillis);
        if (examStartButton != null) examStartButton.setText(examRunning ? "توقف موقت" : "شروع آزمون");
        if (examHomeSummary != null) {
            examHomeSummary.setText(examTitle + " · " + remaining);
        }
    }

    private void showReminders() {
        LinearLayout content = column();
        content.setPadding(0, dp(8), 0, dp(12));
        LinearLayout actions = row();
        MaterialButton addReminder = button("یادآوری جدید");
        addReminder.setOnClickListener(v -> showReminderEditor());
        actions.addView(addReminder, new LinearLayout.LayoutParams(0, dp(48), 1));
        MaterialButton addTask = button("کار جدید");
        LinearLayout.LayoutParams addTaskParams = new LinearLayout.LayoutParams(0, dp(48), 1);
        addTaskParams.leftMargin = dp(8);
        addTask.setOnClickListener(v -> showTaskEditor(todayKey(), null));
        actions.addView(addTask, addTaskParams);
        content.addView(actions);
        addCalendarSyncCard(content);

        TextView taskHeading = text("کارها و مهلت‌ها", 16, Color.WHITE);
        taskHeading.setTypeface(appTypeface, Typeface.BOLD);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(-1, -2);
        headingParams.topMargin = dp(16);
        headingParams.bottomMargin = dp(6);
        content.addView(taskHeading, headingParams);
        List<PlannerTask> tasks = loadAllTasks();
        for (int i = tasks.size() - 1; i >= 0; i--) {
            if (tasks.get(i).done) tasks.remove(i);
        }
        addTaskRows(content, tasks, null, false);

        TextView reminderHeading = text("یادآورهای زمان‌بندی‌شده", 16, Color.WHITE);
        reminderHeading.setTypeface(appTypeface, Typeface.BOLD);
        LinearLayout.LayoutParams reminderHeadingParams = new LinearLayout.LayoutParams(-1, -2);
        reminderHeadingParams.topMargin = dp(20);
        reminderHeadingParams.bottomMargin = dp(6);
        content.addView(reminderHeading, reminderHeadingParams);
        JSONArray reminders = loadReminders();
        if (reminders.length() == 0) {
            content.addView(text("یادآور مستقلی ثبت نشده است.", 12, Color.GRAY));
        }
        for (int i = 0; i < reminders.length(); i++) {
            JSONObject reminder = reminders.optJSONObject(i);
            if (reminder == null) continue;
            String id = reminder.optString("id");
            MaterialCardView card = card();
            LinearLayout row = row();
            row.setPadding(dp(12), dp(10), dp(8), dp(10));
            LinearLayout details = column();
            details.addView(text(reminder.optString("title"), 14, Color.WHITE));
            String description = reminder.optString("details");
            if (!description.isEmpty()) details.addView(text(description, 12, Color.LTGRAY));
            details.addView(text(formatDateTime(reminder.optLong("at")), 11, Color.rgb(60, 180, 230)));
            row.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
            MaterialButton remove = iconButton(R.drawable.ic_close, "حذف یادآور", () -> {
                removeReminder(id);
                showReminders();
            });
            row.addView(remove);
            card.addView(row);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
            cardParams.topMargin = dp(5);
            content.addView(card, cardParams);
        }
        showScreen("Reminders", scroll(content));
        requestNotificationPermission();
    }

    private boolean calendarSyncEnabled() {
        return prefs.getBoolean("calendar_sync_enabled", false)
                && prefs.getLong("calendar_sync_id", -1) > 0;
    }

    private boolean hasCalendarPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR)
                == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALENDAR)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void addCalendarSyncCard(LinearLayout parent) {
        MaterialCardView syncCard = card();
        LinearLayout details = column();
        details.setPadding(dp(14), dp(12), dp(14), dp(12));
        boolean connected = calendarSyncEnabled();
        details.addView(text("تقویم گوشی", 14, Color.WHITE));
        String calendarName = prefs.getString("calendar_sync_name", "");
        details.addView(text(connected
                ? "همگام‌سازی یک‌طرفه با " + calendarName
                : "ثبت خودکار کارها و یادآورها در تقویم انتخابی", 11, Color.LTGRAY));
        MaterialButton action = button(connected ? "همگام‌سازی اکنون" : "اتصال تقویم");
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(-1, dp(44));
        actionParams.topMargin = dp(8);
        details.addView(action, actionParams);
        action.setOnClickListener(v -> {
            if (connected) syncAllCalendarItems();
            else requestCalendarAccess();
        });
        if (connected) {
            MaterialButton disconnect = button("غیرفعال‌کردن همگام‌سازی");
            disconnect.setMinHeight(dp(40));
            disconnect.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(47, 51, 62)));
            LinearLayout.LayoutParams disconnectParams = new LinearLayout.LayoutParams(-1, dp(40));
            disconnectParams.topMargin = dp(4);
            details.addView(disconnect, disconnectParams);
            disconnect.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                    .setTitle("غیرفعال‌کردن همگام‌سازی؟")
                    .setMessage("رویدادهای موجود در تقویم گوشی باقی می‌مانند.")
                    .setNegativeButton("انصراف", null)
                    .setPositiveButton("غیرفعال‌کردن", (dialog, which) -> {
                        prefs.edit().putBoolean("calendar_sync_enabled", false).apply();
                        showReminders();
                    }).show());
        }
        syncCard.addView(details);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.topMargin = dp(10);
        parent.addView(syncCard, cardParams);
    }

    private void requestCalendarAccess() {
        if (hasCalendarPermissions()) {
            showCalendarChooser();
            return;
        }
        ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR
        }, REQUEST_CALENDAR_ACCESS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_CALENDAR_ACCESS) return;
        if (hasCalendarPermissions()) {
            showCalendarChooser();
        } else {
            Toast.makeText(this, "بدون اجازهٔ تقویم، همگام‌سازی فعال نمی‌شود.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void showCalendarChooser() {
        try {
            List<DeviceCalendarSync.CalendarInfo> calendars =
                    DeviceCalendarSync.writableCalendars(this);
            if (calendars.isEmpty()) {
                Toast.makeText(this, "تقویم قابل‌نوشتنی روی دستگاه پیدا نشد.",
                        Toast.LENGTH_LONG).show();
                return;
            }
            CharSequence[] labels = new CharSequence[calendars.size()];
            for (int i = 0; i < calendars.size(); i++) labels[i] = calendars.get(i).toString();
            new MaterialAlertDialogBuilder(this)
                    .setTitle("انتخاب تقویم")
                    .setItems(labels, (dialog, which) -> {
                        DeviceCalendarSync.CalendarInfo selected = calendars.get(which);
                        prefs.edit().putLong("calendar_sync_id", selected.id)
                                .putString("calendar_sync_name", selected.toString())
                                .putBoolean("calendar_sync_enabled", true).apply();
                        syncAllCalendarItems();
                    }).setNegativeButton("انصراف", null).show();
        } catch (RuntimeException e) {
            android.util.Log.e("FocusCalendar", "Unable to load writable calendars.", e);
            Toast.makeText(this, "فهرست تقویم گوشی خوانده نشد.", Toast.LENGTH_LONG).show();
        }
    }

    private void syncAllCalendarItems() {
        if (!hasCalendarPermissions()) {
            prefs.edit().putBoolean("calendar_sync_enabled", false).apply();
            Toast.makeText(this, "دسترسی تقویم لغو شده است؛ دوباره متصلش کنید.",
                    Toast.LENGTH_LONG).show();
            showReminders();
            return;
        }
        calendarExecutor.execute(() -> {
            boolean failed = false;
            Set<String> dates = new LinkedHashSet<>();
            for (PlannerTask task : loadAllTasks()) {
                if (task.noteDate != null) dates.add(task.noteDate);
            }
            for (String date : dates) {
                failed |= syncTaskChanges(date, new ArrayList<>(), loadTasks(date));
            }
            JSONArray reminders = loadReminders();
            for (int i = 0; i < reminders.length(); i++) {
                JSONObject reminder = reminders.optJSONObject(i);
                if (reminder == null) continue;
                JSONObject current = findReminder(reminder.optString("id"));
                if (current == null) continue;
                try {
                    long eventId = DeviceCalendarSync.upsertTask(this,
                            prefs.getLong("calendar_sync_id", -1),
                            current.optLong("calendarEventId"), "یادآوری: " + current.optString("title"),
                            current.optString("details"), current.optLong("at"),
                            current.optLong("at"), false);
                    persistReminderCalendarEventId(current.optString("id"), eventId);
                } catch (RuntimeException e) {
                    failed = true;
                    android.util.Log.e("FocusCalendar", "Unable to synchronize reminder "
                            + current.optString("id"), e);
                }
            }
            boolean syncFailed = failed;
            handler.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(this, syncFailed
                        ? "برخی موارد با تقویم همگام نشدند؛ گزارش خطا ثبت شد."
                        : "کارها و یادآورها با تقویم گوشی همگام شدند.",
                        Toast.LENGTH_LONG).show();
                if ("Reminders".equals(activeScreen)) showReminders();
            });
        });
    }

    private JSONObject findReminder(String id) {
        JSONArray reminders = loadReminders();
        for (int i = 0; i < reminders.length(); i++) {
            JSONObject reminder = reminders.optJSONObject(i);
            if (reminder != null && id.equals(reminder.optString("id"))) return reminder;
        }
        return null;
    }

    private void persistReminderCalendarEventId(String id, long eventId) {
        JSONArray reminders = loadReminders();
        for (int i = 0; i < reminders.length(); i++) {
            JSONObject reminder = reminders.optJSONObject(i);
            if (reminder == null || !id.equals(reminder.optString("id"))) continue;
            try {
                reminder.put("calendarEventId", eventId);
                prefs.edit().putString(SAVED_REMINDERS, reminders.toString()).apply();
            } catch (JSONException e) {
                android.util.Log.e("FocusCalendar", "Unable to save the reminder calendar link.", e);
            }
            return;
        }
    }

    private void syncReminderToCalendar(String id) {
        if (!calendarSyncEnabled() || !hasCalendarPermissions()) return;
        JSONObject reminder = findReminder(id);
        if (reminder == null) return;
        try {
            long eventId = DeviceCalendarSync.upsertTask(this,
                    prefs.getLong("calendar_sync_id", -1), reminder.optLong("calendarEventId"),
                    "یادآوری: " + reminder.optString("title"), reminder.optString("details"),
                    reminder.optLong("at"), reminder.optLong("at"), false);
            persistReminderCalendarEventId(id, eventId);
        } catch (RuntimeException e) {
            android.util.Log.e("FocusCalendar", "Unable to synchronize reminder " + id, e);
            handler.post(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    Toast.makeText(this, "یادآور ذخیره شد اما در تقویم ثبت نشد.",
                            Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private JSONArray loadReminders() {
        try {
            return new JSONArray(prefs.getString(SAVED_REMINDERS, "[]"));
        } catch (JSONException e) {
            android.util.Log.e("FocusReminders", "Unable to read saved reminders.", e);
            return new JSONArray();
        }
    }

    private void showReminderEditor() {
        showReminderEditor(todayKey());
    }

    private void showReminderEditor(String date) {
        LinearLayout form = column();
        form.setPadding(dp(4), dp(8), dp(4), 0);
        TextInputEditText title = new TextInputEditText(this);
        title.setSingleLine(true);
        title.setHint("عنوان یادآوری");
        title.setTextColor(Color.WHITE);
        title.setHintTextColor(Color.GRAY);
        if (appTypeface != null) title.setTypeface(appTypeface);
        form.addView(title, new LinearLayout.LayoutParams(-1, dp(56)));
        TextInputEditText details = new TextInputEditText(this);
        details.setHint("توضیحات");
        details.setTextColor(Color.WHITE);
        details.setHintTextColor(Color.GRAY);
        details.setMinLines(2);
        if (appTypeface != null) details.setTypeface(appTypeface);
        form.addView(details, new LinearLayout.LayoutParams(-1, dp(76)));
        Calendar when = Calendar.getInstance();
        Calendar selectedDate = Calendar.getInstance();
        selectedDate.setTime(parseDay(date));
        if (new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()).equals(date)) {
            when.add(Calendar.HOUR_OF_DAY, 1);
        } else {
            when.set(Calendar.YEAR, selectedDate.get(Calendar.YEAR));
            when.set(Calendar.MONTH, selectedDate.get(Calendar.MONTH));
            when.set(Calendar.DAY_OF_MONTH, selectedDate.get(Calendar.DAY_OF_MONTH));
            when.set(Calendar.HOUR_OF_DAY, 9);
            when.set(Calendar.MINUTE, 0);
        }
        TextView whenLabel = text(formatDateTime(when.getTimeInMillis()), 12, Color.LTGRAY);
        MaterialButton chooseTime = button("انتخاب تاریخ و ساعت");
        chooseTime.setOnClickListener(v -> pickDateTime(when, "زمان یادآوری", whenLabel));
        form.addView(chooseTime);
        form.addView(whenLabel);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("یادآوری جدید")
                .setView(form)
                .setNegativeButton("انصراف", null)
                .setPositiveButton("ذخیره", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String reminderTitle = String.valueOf(title.getText()).trim();
                    if (reminderTitle.isEmpty()) {
                        title.setError("عنوان یادآوری را وارد کنید.");
                        return;
                    }
                    String id = UUID.randomUUID().toString();
                    long at = when.getTimeInMillis();
                    if (at <= System.currentTimeMillis()) {
                        Toast.makeText(this, "زمان یادآوری باید در آینده باشد.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    try {
                        JSONObject item = new JSONObject();
                        item.put("id", id);
                        item.put("title", reminderTitle);
                        item.put("details", String.valueOf(details.getText()).trim());
                        item.put("at", at);
                        JSONArray saved = loadReminders();
                        saved.put(item);
                        prefs.edit().putString(SAVED_REMINDERS, saved.toString()).apply();
                    } catch (JSONException e) {
                        android.util.Log.e("FocusReminders", "Unable to save reminder.", e);
                        Toast.makeText(this, "ذخیرهٔ یادآوری انجام نشد.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    ReminderScheduler.schedule(this, id, reminderTitle,
                            String.valueOf(details.getText()).trim(), at);
                    if (calendarSyncEnabled()) {
                        calendarExecutor.execute(() -> syncReminderToCalendar(id));
                    }
                    dialog.dismiss();
                    showReminders();
                }));
        dialog.show();
    }

    private void createReminderChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) manager.createNotificationChannel(new android.app.NotificationChannel(
                    "focus_reminders", "یادآورها و کارها", NotificationManager.IMPORTANCE_HIGH));
        }
    }





    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33
                || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) return;
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.POST_NOTIFICATIONS}, 15);
    }




    private File bitmapCacheFile(String category, String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder name = new StringBuilder(digest.length * 2);
            for (byte value : digest) name.append(String.format(Locale.ROOT, "%02x", value & 0xff));
            File directory = new File(getCacheDir(), category);
            if (!directory.isDirectory() && !directory.mkdirs()) {
                android.util.Log.w("FocusCache", "Unable to create cache directory: " + directory);
                return null;
            }
            return new File(directory, name + ".png");
        } catch (NoSuchAlgorithmException e) {
            android.util.Log.e("FocusCache", "SHA-256 is unavailable; cache entry was skipped.", e);
            return null;
        }
    }

    private Bitmap readBitmapCache(String category, String key) {
        File file = bitmapCacheFile(category, key);
        if (file == null || !file.isFile()) return null;
        try {
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
            if (bitmap == null && !file.delete()) {
                android.util.Log.w("FocusCache", "Unable to remove invalid cache entry: " + file);
            }
            return bitmap;
        } catch (RuntimeException | OutOfMemoryError e) {
            android.util.Log.w("FocusCache", "Unable to load cached bitmap: " + file, e);
            if (!file.delete()) android.util.Log.w("FocusCache", "Unable to remove invalid cache entry: " + file);
            return null;
        }
    }

    private void writeBitmapCache(String category, String key, Bitmap bitmap) {
        File file = bitmapCacheFile(category, key);
        if (file == null) return;
        File temporary = new File(file.getParentFile(), file.getName() + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                android.util.Log.w("FocusCache", "Bitmap compression failed for " + file);
                if (!temporary.delete()) android.util.Log.w("FocusCache", "Unable to remove temporary cache file.");
                return;
            }
            output.flush();
            if (!temporary.renameTo(file)) {
                android.util.Log.w("FocusCache", "Unable to commit cached bitmap: " + file);
                if (!temporary.delete()) android.util.Log.w("FocusCache", "Unable to remove temporary cache file.");
            }
        } catch (IOException e) {
            android.util.Log.w("FocusCache", "Unable to write bitmap cache: " + file, e);
            if (temporary.exists() && !temporary.delete()) {
                android.util.Log.w("FocusCache", "Unable to remove temporary cache file: " + temporary);
            }
        }
    }


    private int sampleSize(BitmapFactory.Options options, int targetSize) {
        int sample = 1;
        while (options.outWidth / sample > targetSize * 2 || options.outHeight / sample > targetSize * 2) sample *= 2;
        return sample;
    }

    private void showPdfLibrary() {
        loadSavedPdfs();
        LinearLayout list = column();
        list.setPadding(0, dp(4), 0, dp(12));
        LinearLayout actions = row();
        MaterialButton add = button("Add PDF");
        add.setIconResource(R.drawable.ic_add);
        add.setIconTint(android.content.res.ColorStateList.valueOf(Color.WHITE));
        add.setMinHeight(dp(48));
        add.setContentDescription("Add PDF document");
        add.setOnClickListener(v -> pickPdf());
        actions.addView(add, new LinearLayout.LayoutParams(0, dp(42), 1));
        MaterialButton scan = button(scanningPdfs ? "Scanning…" : "Scan folder");
        scan.setMinHeight(dp(48));
        scan.setEnabled(!scanningPdfs);
        LinearLayout.LayoutParams scanParams = new LinearLayout.LayoutParams(0, dp(42), 1);
        scanParams.leftMargin = dp(8);
        actions.addView(scan, scanParams);
        scan.setOnClickListener(v -> {
            String folder = prefs.getString("pdf_scan_folder", "");
            if (!folder.isEmpty()) scanPdfFolder(Uri.parse(folder));
            else pickPdfFolder();
        });
        list.addView(actions);
        TextView libraryStatus = text(pdfs.size() + " documents · long press for options", 11, Color.LTGRAY);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
        statusParams.topMargin = dp(9);
        list.addView(libraryStatus, statusParams);
        if (pdfs.isEmpty()) {
            TextView empty = text("Your PDF library is empty. Add a document to start reading.", 14, Color.LTGRAY);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.topMargin = dp(18);
            list.addView(empty, params);
        }
        int pdfCount = Math.min(pdfs.size(), pdfVisibleLimit);
        for (int pdfIndex = 0; pdfIndex < pdfCount; pdfIndex++) {
            PdfItem pdf = pdfs.get(pdfIndex);
            MaterialCardView item = card();
            LinearLayout details = row();
            details.setPadding(dp(10), dp(9), dp(8), dp(9));
            ImageView thumbnail = new ImageView(this);
            thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumbnail.setBackground(makeThumbnailBackground());
            thumbnail.setTag(pdf.uri);
            LinearLayout.LayoutParams thumbnailParams = new LinearLayout.LayoutParams(dp(42), dp(54));
            details.addView(thumbnail, thumbnailParams);
            TextView name = text(pdf.title, 13, Color.WHITE);
            name.setMaxLines(2);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout textDetails = column();
            textDetails.setGravity(Gravity.CENTER_VERTICAL);
            textDetails.addView(name);
            TextView progress = text("برای بازکردن در نمایشگر PDF دستگاه لمس کنید.", 11, Color.LTGRAY);
            LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(-1, -2);
            progressParams.topMargin = dp(4);
            textDetails.addView(progress, progressParams);
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, -2, 1);
            textParams.leftMargin = dp(10);
            details.addView(textDetails, textParams);
            ImageView chevron = new ImageView(this);
            chevron.setImageResource(R.drawable.ic_chevron_right);
            chevron.setImageTintList(android.content.res.ColorStateList.valueOf(Color.LTGRAY));
            details.addView(chevron, new LinearLayout.LayoutParams(dp(24), dp(24)));
            item.addView(details);
            item.setMinimumHeight(dp(72));
            item.setClickable(true);
            item.setFocusable(true);
            item.setContentDescription("Open PDF " + pdf.title);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.topMargin = dp(5);
            list.addView(item, params);
            requestPdfThumbnail(pdf, thumbnail);
            item.setOnClickListener(v -> openPdf(pdf));
            item.setOnLongClickListener(v -> {
                new MaterialAlertDialogBuilder(this).setTitle(pdf.title)
                        .setItems(new String[]{"Open", "Remove from library"}, (dialog, choice) -> {
                            if (choice == 0) openPdf(pdf);
                            else new MaterialAlertDialogBuilder(this).setTitle("Remove from library?")
                                    .setMessage("The PDF file on your device will not be deleted.")
                                    .setNegativeButton("Cancel", null)
                                    .setPositiveButton("Remove", (d, w) -> {
                                        pdfs.remove(pdf);
                                        prefs.edit().remove("pdf_page:" + pdf.uri)
                                                .remove("pdf_pages:" + pdf.uri).apply();
                                        savePdfList();
                                        showPdfLibrary();
                                    }).show();
                        }).show();
                return true;
            });
        }
        MaterialButton more = button("Load more");
        more.setVisibility(pdfVisibleLimit < pdfs.size() ? View.VISIBLE : View.GONE);
        LinearLayout.LayoutParams moreParams = new LinearLayout.LayoutParams(-1, dp(46));
        moreParams.topMargin = dp(8);
        list.addView(more, moreParams);
        more.setOnClickListener(v -> {
            pdfVisibleLimit += PDF_PAGE_SIZE;
            showPdfLibrary();
        });
        showScreen("PDF library", scroll(list));
    }

    private void pickPdf() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_PDF);
    }

    private void pickPdfFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_PDF_FOLDER);
    }

    private Bitmap renderPdfThumbnail(PdfItem pdf) {
        String key = pdf.uri.toString();
        Bitmap cached = readBitmapCache("pdf-covers", key);
        if (cached != null) return cached;
        File stagedFile = null;
        try {
            stagedFile = copyPdfToCache(pdf.uri);
            try (ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(
                    stagedFile, ParcelFileDescriptor.MODE_READ_ONLY);
                 PdfRenderer thumbnailRenderer = new PdfRenderer(descriptor)) {
                if (thumbnailRenderer.getPageCount() == 0) {
                    android.util.Log.w("FocusPdf", "PDF has no pages: " + pdf.uri);
                    return null;
                }
                Bitmap bitmap;
                try (PdfRenderer.Page page = thumbnailRenderer.openPage(0)) {
                    float scale = Math.min(120f / page.getWidth(), 160f / page.getHeight());
                    int width = Math.max(1, Math.round(page.getWidth() * scale));
                    int height = Math.max(1, Math.round(page.getHeight() * scale));
                    bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    bitmap.eraseColor(Color.WHITE);
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                }
                writeBitmapCache("pdf-covers", key, bitmap);
                return bitmap;
            }
        } catch (IOException | RuntimeException | OutOfMemoryError e) {
            android.util.Log.w("FocusPdf", "Unable to generate PDF cover: " + pdf.uri, e);
            return null;
        } finally {
            deleteStagedPdf(stagedFile);
        }
    }

    private File copyPdfToCache(Uri uri) throws IOException {
        File directory = new File(getCacheDir(), "pdf-staging");
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Unable to create PDF staging directory");
        }
        File stagedFile = File.createTempFile("focus-", ".pdf", directory);
        boolean copied = false;
        try (java.io.InputStream input = getContentResolver().openInputStream(uri);
             java.io.OutputStream output = new java.io.BufferedOutputStream(
                     new FileOutputStream(stagedFile))) {
            if (input == null) throw new IOException("PDF provider returned no input stream");
            byte[] buffer = new byte[32 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            output.flush();
            copied = true;
            return stagedFile;
        } finally {
            if (!copied) deleteStagedPdf(stagedFile);
        }
    }

    private void deleteStagedPdf(@Nullable File file) {
        if (file != null && file.exists() && !file.delete()) {
            android.util.Log.w("FocusPdf", "Unable to delete staged PDF file: " + file);
        }
    }

    private void requestPdfThumbnail(PdfItem pdf, ImageView target) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1) return;
        String key = pdf.uri.toString();
        target.setTag(key);
        Bitmap cached = pdfCoverCache.get(key);
        if (cached != null && !cached.isRecycled()) {
            target.setImageBitmap(cached);
            target.setBackground(null);
            return;
        }
        if (!pdfCoverLoads.add(key)) return;
        pdfExecutor.execute(() -> {
            Bitmap cover = renderPdfThumbnail(pdf);
            runOnUiThread(() -> {
                pdfCoverLoads.remove(key);
                if (isFinishing() || isDestroyed()) {
                    if (cover != null) cover.recycle();
                    return;
                }
                if (cover == null) return;
                if (pdfCoverCache.size() >= 32) {
                    String oldestKey = pdfCoverCache.keySet().iterator().next();
                    pdfCoverCache.remove(oldestKey);
                }
                pdfCoverCache.put(key, cover);
                if (key.equals(target.getTag())) {
                    target.setImageBitmap(cover);
                    target.setBackground(null);
                }
            });
        });
    }

    private void scanPdfFolder(Uri treeUri) {
        if (scanningPdfs) return;
        scanningPdfs = true;
        showPdfLibrary();
        ioExecutor.execute(() -> {
            List<PdfItem> found = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            try {
                String rootId = DocumentsContract.getTreeDocumentId(treeUri);
                scanPdfChildren(treeUri, rootId, found, seen, 0);
                runOnUiThread(() -> {
                    int added = 0;
                    for (PdfItem pdf : found) {
                        boolean exists = false;
                        for (PdfItem saved : pdfs) if (saved.uri.equals(pdf.uri)) {
                            exists = true;
                            break;
                        }
                        if (!exists) {
                            pdfs.add(pdf);
                            added++;
                        }
                    }
                    savePdfList();
                    scanningPdfs = false;
                    Toast.makeText(this, found.size() + " PDFs found · " + added + " added", Toast.LENGTH_LONG).show();
                    if (!isFinishing() && "PDF library".equals(activeScreen)) showPdfLibrary();
                });
            } catch (IOException | RuntimeException e) {
                runOnUiThread(() -> {
                    scanningPdfs = false;
                    Toast.makeText(this, "Could not scan this folder. Choose a folder that Focus can access.", Toast.LENGTH_LONG).show();
                    if (!isFinishing() && "PDF library".equals(activeScreen)) showPdfLibrary();
                });
            }
        });
    }

    private void scanPdfChildren(Uri treeUri, String parentId, List<PdfItem> results,
                                 Set<String> seen, int depth) throws IOException {
        if (depth > 8 || results.size() >= 1000) return;
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId);
        String[] projection = {DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE};
        try (Cursor cursor = getContentResolver().query(children, projection, null, null,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME + " COLLATE NOCASE")) {
            if (cursor == null) return;
            int idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
            int nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            int mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE);
            while (cursor.moveToNext() && results.size() < 1000) {
                String documentId = cursor.getString(idColumn);
                String name = cursor.getString(nameColumn);
                String mime = cursor.getString(mimeColumn);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    scanPdfChildren(treeUri, documentId, results, seen, depth + 1);
                } else if ((name != null && name.toLowerCase(Locale.ROOT).endsWith(".pdf"))
                        || "application/pdf".equalsIgnoreCase(mime)) {
                    Uri document = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId);
                    if (seen.add(document.toString())) results.add(new PdfItem(name == null ? "PDF document" : name, document));
                }
            }
        }
    }

    private void loadSavedPdfs() {
        pdfs.clear();
        String raw = prefs.getString(PDF_LIST, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                pdfs.add(new PdfItem(item.getString("title"), Uri.parse(item.getString("uri"))));
            }
        } catch (JSONException ignored) {
            prefs.edit().remove(PDF_LIST).apply();
        }
    }

    private void savePdfList() {
        JSONArray array = new JSONArray();
        for (PdfItem pdf : pdfs) {
            JSONObject item = new JSONObject();
            try {
                item.put("title", pdf.title);
                item.put("uri", pdf.uri.toString());
                array.put(item);
            } catch (JSONException ignored) { }
        }
        prefs.edit().putString(PDF_LIST, array.toString()).apply();
    }

    private String getDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
        } catch (RuntimeException ignored) { }
        return "File";
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_APP_WIDGET_PICK) {
            int widgetId = data == null
                    ? prefs.getInt("pending_home_widget_id", -1)
                    : data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                    prefs.getInt("pending_home_widget_id", -1));
            if (resultCode == RESULT_OK && widgetId >= 0) {
                finishHomeWidgetPick(widgetId);
            } else {
                if (widgetId >= 0) appWidgetHost.deleteAppWidgetId(widgetId);
                prefs.edit().remove("pending_home_widget_id").apply();
                buildHome();
            }
            return;
        }
        if (requestCode == REQUEST_APP_WIDGET_CONFIGURE) {
            int widgetId = data == null
                    ? prefs.getInt("pending_home_widget_id", -1)
                    : data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                    prefs.getInt("pending_home_widget_id", -1));
            if (resultCode == RESULT_OK && widgetId >= 0) attachHomeWidget(widgetId);
            else {
                if (widgetId >= 0) appWidgetHost.deleteAppWidgetId(widgetId);
                prefs.edit().remove("pending_home_widget_id").apply();
                buildHome();
            }
            return;
        }
        if (requestCode == REQUEST_HOME_ROLE) {
            buildHome();
            return;
        }
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQUEST_PDF) {
            persistReadPermission(uri);
            for (PdfItem pdf : pdfs) if (pdf.uri.equals(uri)) {
                openPdf(pdf);
                return;
            }
            PdfItem pdf = new PdfItem(getDisplayName(uri), uri);
            pdfs.add(0, pdf);
            savePdfList();
            openPdf(pdf);
        } else if (requestCode == REQUEST_PDF_FOLDER) {
            try {
                if ((data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) {
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    prefs.edit().putString("pdf_scan_folder", uri.toString()).apply();
                }
            } catch (SecurityException e) {
                Toast.makeText(this, "Folder access could not be saved.", Toast.LENGTH_LONG).show();
            }
            scanPdfFolder(uri);
        }
    }

    private void openLastPdf() {
        loadSavedPdfs();
        if (pdfs.isEmpty()) showPdfLibrary();
        else {
            String lastUri = prefs.getString("last_pdf_uri", "");
            for (PdfItem pdf : pdfs) {
                if (pdf.uri.toString().equals(lastUri)) {
                    openPdf(pdf);
                    return;
                }
            }
            openPdf(pdfs.get(0));
        }
    }

    private String lastPdfTitle() {
        loadSavedPdfs();
        if (pdfs.isEmpty()) return "Add a PDF to get started";
        String lastUri = prefs.getString("last_pdf_uri", "");
        for (PdfItem pdf : pdfs) if (pdf.uri.toString().equals(lastUri)) return pdf.title;
        return pdfs.get(0).title;
    }

    private String lastPdfContinueLabel() {
        loadSavedPdfs();
        return pdfs.isEmpty() ? "Browse" : "Open";
    }

    private void openPdf(PdfItem pdf) {
        if (Build.VERSION.SDK_INT >= 23 && !canDrawOverlays()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("پنل شناور مطالعه")
                    .setMessage("برای نمایش دکمهٔ چشم روی PDF و دسترسی سریع به پومودورو، مجوز نمایش روی برنامه‌های دیگر لازم است.")
                    .setNegativeButton("بدون پنل باز کن", (dialog, which) ->
                            openPdfInSystemViewer(pdf.uri, false))
                    .setPositiveButton("دادن مجوز", (dialog, which) -> {
                        pendingExternalPdf = pdf.uri;
                        Intent permission = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName()));
                        try {
                            startActivity(permission);
                        } catch (android.content.ActivityNotFoundException | SecurityException e) {
                            pendingExternalPdf = null;
                            android.util.Log.e("FocusOverlay", "Overlay permission settings are unavailable.", e);
                            Toast.makeText(this, "تنظیم مجوز پنل شناور باز نشد.", Toast.LENGTH_LONG).show();
                        }
                    })
                    .show();
            return;
        }
        openPdfInSystemViewer(pdf.uri, Build.VERSION.SDK_INT < 23 || canDrawOverlays());
    }

    private boolean canDrawOverlays() {
        return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this);
    }

    private void openPdfInSystemViewer(Uri uri, boolean showCompanion) {
        Intent view = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/pdf")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (view.resolveActivity(getPackageManager()) == null) {
            Toast.makeText(this, "برنامه‌ای برای نمایش PDF نصب نیست. یک نمایشگر PDF نصب کنید.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        prefs.edit().putString("last_pdf_uri", uri.toString()).apply();
        if (showCompanion) {
            try {
                prefs.edit().putBoolean("external_pdf_active", true).apply();
                Intent service = new Intent(this, PdfCompanionOverlayService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(this, service);
                } else {
                    startService(service);
                }
            } catch (SecurityException | IllegalStateException e) {
                prefs.edit().putBoolean("external_pdf_active", false).apply();
                android.util.Log.e("FocusOverlay", "Could not start PDF companion controls.", e);
                Toast.makeText(this, "پنل شناور شروع نشد؛ PDF بدون پنل باز می‌شود.",
                        Toast.LENGTH_LONG).show();
            }
        }
        try {
            startActivity(Intent.createChooser(view, "بازکردن PDF"));
        } catch (android.content.ActivityNotFoundException | SecurityException e) {
            prefs.edit().putBoolean("external_pdf_active", false).apply();
            stopService(new Intent(this, PdfCompanionOverlayService.class));
            android.util.Log.e("FocusPdf", "No application could open the selected PDF.", e);
            Toast.makeText(this, "بازکردن PDF ممکن نشد.", Toast.LENGTH_LONG).show();
        }
    }




    private void persistReadPermission(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            Toast.makeText(this, "This file provider does not allow persistent access after reopening.",
                    Toast.LENGTH_LONG).show();
        }
    }








    private void showFocusGuard() {
        LinearLayout body = column();
        body.setPadding(0, dp(8), 0, dp(12));
        TextView description = text("Choose distracting apps to return to your home screen during an active Pomodoro. Enable the Accessibility service below to use this optional feature.", 14, Color.LTGRAY);
        body.addView(description);
        boolean serviceEnabled = isGuardEnabled();
        TextView status = text(serviceEnabled ? "Service enabled" : "Service not enabled", 14,
                serviceEnabled ? Color.rgb(85, 196, 161) : Color.LTGRAY);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
        statusParams.topMargin = dp(12);
        body.addView(status, statusParams);
        MaterialButton settings = button("Manage Accessibility service");
        LinearLayout.LayoutParams settingsParams = new LinearLayout.LayoutParams(-1, dp(48));
        settingsParams.topMargin = dp(8);
        body.addView(settings, settingsParams);
        settings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        TextView label = text("Block these apps while your timer is running", 15, Color.WHITE);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, -2);
        labelParams.topMargin = dp(18);
        labelParams.bottomMargin = dp(8);
        body.addView(label, labelParams);
        List<ResolveInfo> apps = getPackageManager().queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);
        Collections.sort(apps, (a, b) -> String.valueOf(a.loadLabel(getPackageManager()))
                .compareToIgnoreCase(String.valueOf(b.loadLabel(getPackageManager()))));
        java.util.Set<String> selected = new java.util.HashSet<>(prefs.getStringSet(BLOCKED_APPS, Collections.emptySet()));
        java.util.Set<String> shownPackages = new java.util.HashSet<>();
        ResolveInfo home = getPackageManager().resolveActivity(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                PackageManager.MATCH_DEFAULT_ONLY);
        String homePackage = home == null || home.activityInfo == null ? "" : home.activityInfo.packageName;
        for (ResolveInfo app : apps) {
            String packageName = app.activityInfo.packageName;
            if (packageName.equals(getPackageName()) || packageName.equals(homePackage)
                    || packageName.equals("com.android.settings") || packageName.equals("com.android.systemui")
                    || !shownPackages.add(packageName)) continue;
            android.widget.CheckBox checkBox = new android.widget.CheckBox(this);
            checkBox.setText(app.loadLabel(getPackageManager()));
            checkBox.setTextColor(Color.WHITE);
            checkBox.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.rgb(60, 180, 230)));
            checkBox.setChecked(selected.contains(packageName));
            checkBox.setOnCheckedChangeListener((buttonView, checked) -> {
                java.util.Set<String> updated = new java.util.HashSet<>(prefs.getStringSet(BLOCKED_APPS, Collections.emptySet()));
                if (checked) updated.add(packageName); else updated.remove(packageName);
                prefs.edit().putStringSet(BLOCKED_APPS, updated).apply();
            });
            body.addView(checkBox);
        }
        showScreen("Focus guard", scroll(body));
    }

        private void showScreenTimeDashboard() {
            LinearLayout body = column();
            body.setPadding(0, dp(8), 0, dp(12));
            body.addView(text("زمان استفاده از برنامه‌ها", 18, Color.WHITE));
            TextView description = text("آمار از Android Usage Access خوانده می‌شود. برای ثبت دقیق و اجرای محدودیت‌ها، دسترسی ویژه را فعال کنید.",
                    12, Color.LTGRAY);
            LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(-1, -2);
            descriptionParams.topMargin = dp(4);
            body.addView(description, descriptionParams);
            if (!AppUsageStats.hasAccess(this)) {
                MaterialButton enable = button("فعال‌سازی دسترسی آمار مصرف");
                LinearLayout.LayoutParams enableParams = new LinearLayout.LayoutParams(-1, dp(48));
                enableParams.topMargin = dp(12);
                body.addView(enable, enableParams);
                enable.setOnClickListener(v -> requestUsageAccess());
                body.addView(text("این دسترسی فقط برای محاسبهٔ زمان استفاده و اعمال سقف‌هایی است که خودتان تنظیم می‌کنید.",
                        11, Color.LTGRAY));
            } else {
                TextView loading = text("در حال خواندن آمار…", 12, Color.LTGRAY);
                LinearLayout.LayoutParams loadingParams = new LinearLayout.LayoutParams(-1, dp(48));
                loadingParams.topMargin = dp(12);
                body.addView(loading, loadingParams);
                calendarExecutor.execute(() -> {
                    try {
                        long now = System.currentTimeMillis();
                        long dayStart = AppUsageStats.startOfDay(now);
                        Calendar week = Calendar.getInstance();
                        week.setTimeInMillis(now);
                        week.set(Calendar.DAY_OF_WEEK, week.getFirstDayOfWeek());
                        week.set(Calendar.HOUR_OF_DAY, 0);
                        week.set(Calendar.MINUTE, 0);
                        week.set(Calendar.SECOND, 0);
                        week.set(Calendar.MILLISECOND, 0);
                        Map<String, Long> today = AppUsageStats.query(this, dayStart, now);
                        Map<String, Long> weekly = AppUsageStats.query(this, week.getTimeInMillis(), now);
                        persistUsageSnapshot(today, now);
                        handler.post(() -> {
                            if (isFinishing() || isDestroyed() || !"Screen time".equals(activeScreen)) return;
                            body.removeView(loading);
                            addUsageSummary(body, today, weekly);
                            addUsageRows(body, today, weekly);
                        });
                    } catch (RuntimeException e) {
                        android.util.Log.e("FocusUsage", "Unable to read app usage statistics.", e);
                        handler.post(() -> {
                            if (!isFinishing() && !isDestroyed()) {
                                loading.setText("آمار مصرف خوانده نشد. مجوز Usage Access را بررسی و دوباره تلاش کنید.");
                            }
                        });
                    }
                });
            }
            showScreen("Screen time", scroll(body));
        }

        private void requestUsageAccess() {
            try {
                startActivityForResult(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), REQUEST_USAGE_ACCESS);
            } catch (android.content.ActivityNotFoundException | SecurityException e) {
                android.util.Log.e("FocusUsage", "Usage access settings are unavailable.", e);
                Toast.makeText(this, "تنظیمات دسترسی آمار مصرف باز نشد.", Toast.LENGTH_LONG).show();
            }
        }

        private void addUsageSummary(LinearLayout parent, Map<String, Long> today,
                                     Map<String, Long> weekly) {
            long todayTotal = 0;
            long weekTotal = 0;
            for (long value : today.values()) todayTotal += value;
            for (long value : weekly.values()) weekTotal += value;
            MaterialCardView summary = card();
            LinearLayout values = row();
            values.setPadding(dp(10), dp(14), dp(10), dp(14));
            values.addView(usageStatColumn("امروز", AppUsageStats.duration(todayTotal)),
                    new LinearLayout.LayoutParams(0, -2, 1));
            values.addView(usageStatColumn("این هفته", AppUsageStats.duration(weekTotal)),
                    new LinearLayout.LayoutParams(0, -2, 1));
            summary.addView(values);
            LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(-1, -2);
            summaryParams.topMargin = dp(12);
            parent.addView(summary, summaryParams);
            TextView heading = text("بیشترین استفاده", 15, Color.WHITE);
            heading.setTypeface(appTypeface, Typeface.BOLD);
            LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(-1, -2);
            headingParams.topMargin = dp(16);
            parent.addView(heading, headingParams);
        }

        private LinearLayout usageStatColumn(String title, String value) {
            LinearLayout column = column();
            column.setGravity(Gravity.CENTER);
            column.addView(text(title, 11, Color.LTGRAY));
            TextView amount = text(value, 15, Color.WHITE);
            amount.setTypeface(appTypeface, Typeface.BOLD);
            LinearLayout.LayoutParams amountParams = new LinearLayout.LayoutParams(-2, -2);
            amountParams.topMargin = dp(4);
            column.addView(amount, amountParams);
            return column;
        }

        private void addUsageRows(LinearLayout parent, Map<String, Long> today,
                                  Map<String, Long> weekly) {
            List<Map.Entry<String, Long>> sorted = new ArrayList<>(today.entrySet());
            Collections.sort(sorted, (left, right) -> Long.compare(right.getValue(), left.getValue()));
            int shown = 0;
            for (Map.Entry<String, Long> entry : sorted) {
                String packageName = entry.getKey();
                if (packageName.equals(getPackageName()) || entry.getValue() <= 0) continue;
                CharSequence appName;
                android.graphics.drawable.Drawable icon;
                try {
                    android.content.pm.ApplicationInfo application =
                            getPackageManager().getApplicationInfo(packageName, 0);
                    appName = getPackageManager().getApplicationLabel(application);
                    icon = getPackageManager().getApplicationIcon(application);
                } catch (PackageManager.NameNotFoundException e) {
                    continue;
                }
                long todayMillis = entry.getValue();
                long weekMillis = weekly.getOrDefault(packageName, 0L);
                MaterialCardView item = card();
                LinearLayout row = row();
                row.setPadding(dp(12), dp(8), dp(8), dp(8));
                ImageView appIcon = new ImageView(this);
                appIcon.setImageDrawable(icon);
                row.addView(appIcon, new LinearLayout.LayoutParams(dp(36), dp(36)));
                LinearLayout info = column();
                info.addView(text(String.valueOf(appName), 13, Color.WHITE));
                info.addView(text(AppUsageStats.duration(todayMillis) + " امروز · "
                        + AppUsageStats.duration(weekMillis) + " این هفته", 10, Color.LTGRAY));
                LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(0, -2, 1);
                infoParams.leftMargin = dp(10);
                row.addView(info, infoParams);
                MaterialButton settings = iconButton(R.drawable.ic_more_vert, "مدیریت زمان برنامه",
                        () -> showAppTimeSettings(packageName, String.valueOf(appName)));
                row.addView(settings);
                item.addView(row);
                item.setOnClickListener(v -> showAppTimeSettings(packageName, String.valueOf(appName)));
                LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(-1, -2);
                itemParams.topMargin = dp(5);
                parent.addView(item, itemParams);
                if (++shown >= 30) break;
            }
            if (shown == 0) parent.addView(text("امروز مصرفی برای برنامه‌ها ثبت نشده است.", 12, Color.LTGRAY));
        }

        private void persistUsageSnapshot(Map<String, Long> usage, long time) {
            String day = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(time));
            JSONObject snapshot = new JSONObject();
            try {
                for (Map.Entry<String, Long> entry : usage.entrySet()) snapshot.put(entry.getKey(), entry.getValue());
                prefs.edit().putString("screen_usage_snapshot:" + day, snapshot.toString()).apply();
            } catch (JSONException e) {
                android.util.Log.e("FocusUsage", "Unable to save usage snapshot.", e);
            }
        }

        private void showAppTimeSettings(String packageName, String appName) {
            LinearLayout body = column();
            body.setPadding(dp(4), dp(8), dp(4), dp(8));
            TextView status = text("در حال خواندن زمان مصرف…", 12, Color.LTGRAY);
            body.addView(status);
            if (!isGuardEnabled()) {
                TextView guardStatus = text("برای اجرای مسدودسازی، سرویس محافظ تمرکز باید فعال باشد.",
                        11, Color.rgb(255, 190, 100));
                LinearLayout.LayoutParams guardStatusParams = new LinearLayout.LayoutParams(-1, -2);
                guardStatusParams.topMargin = dp(8);
                body.addView(guardStatus, guardStatusParams);
                MaterialButton enableGuard = button("فعال‌سازی سرویس محافظ");
                LinearLayout.LayoutParams guardButtonParams = new LinearLayout.LayoutParams(-1, dp(42));
                guardButtonParams.topMargin = dp(4);
                body.addView(enableGuard, guardButtonParams);
                enableGuard.setOnClickListener(v -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    } catch (android.content.ActivityNotFoundException | SecurityException e) {
                        android.util.Log.e("FocusUsage", "Accessibility settings are unavailable.", e);
                        Toast.makeText(this, "تنظیمات دسترس‌پذیری باز نشد.", Toast.LENGTH_LONG).show();
                    }
                });
            }
            if (AppUsageStats.hasAccess(this)) {
                calendarExecutor.execute(() -> {
                    try {
                        long now = System.currentTimeMillis();
                        long dayStart = AppUsageStats.startOfDay(now);
                        long today = AppUsageStats.query(this, dayStart, now).getOrDefault(packageName, 0L);
                        Calendar weekCalendar = Calendar.getInstance();
                        weekCalendar.setTimeInMillis(now);
                        weekCalendar.set(Calendar.DAY_OF_WEEK, weekCalendar.getFirstDayOfWeek());
                        weekCalendar.set(Calendar.HOUR_OF_DAY, 0);
                        weekCalendar.set(Calendar.MINUTE, 0);
                        weekCalendar.set(Calendar.SECOND, 0);
                        weekCalendar.set(Calendar.MILLISECOND, 0);
                        long week = AppUsageStats.query(this, weekCalendar.getTimeInMillis(), now)
                                .getOrDefault(packageName, 0L);
                        long periodUsed = AppLimitPolicy.usageSincePeriod(this, prefs, packageName, now);
                        boolean enabled = AppLimitPolicy.enabled(prefs, packageName);
                        int warningMinutes = prefs.getInt(
                                AppLimitPolicy.key(packageName, "warning_minutes"), 10);
                        long limit = AppLimitPolicy.limitMillis(prefs, packageName);
                        boolean nearLimit = enabled && AppLimitPolicy.ACTION_WARN.equals(
                                AppLimitPolicy.action(prefs, packageName))
                                && periodUsed >= Math.max(0, limit - warningMinutes * 60_000L);
                        handler.post(() -> status.setText("امروز: " + AppUsageStats.duration(today)
                                + "  ·  این هفته: " + AppUsageStats.duration(week)
                                + (nearLimit ? "\nبه سقف مصرف نزدیک شده‌اید." : "")));
                    } catch (RuntimeException e) {
                        android.util.Log.e("FocusUsage", "Unable to read usage for " + packageName, e);
                        handler.post(() -> status.setText("آمار برنامه خوانده نشد."));
                    }
                });
            } else {
                status.setText("برای نمایش زمان مصرف، Usage Access را فعال کنید.");
            }

            TextInputEditText limitInput = settingsField("سقف مصرف (دقیقه)", Math.max(1,
                    prefs.getInt(AppLimitPolicy.key(packageName, "minutes"), 60)));
            addSettingsField(body, limitInput);
            TextInputEditText warningInput = settingsField("هشدار پیش از سقف (دقیقه)", Math.max(0,
                    prefs.getInt(AppLimitPolicy.key(packageName, "warning_minutes"), 10)));
            addSettingsField(body, warningInput);
            TextInputEditText breakInput = settingsField("مدت بازکردن موقت (دقیقه)", Math.max(1,
                    prefs.getInt(AppLimitPolicy.key(packageName, "break_minutes"), 5)));
            addSettingsField(body, breakInput);
            TextInputEditText allowanceInput = settingsField("سهمیهٔ اضطراری (دقیقه)", Math.max(1,
                    prefs.getInt(AppLimitPolicy.key(packageName, "allowance_minutes"), 10)));
            addSettingsField(body, allowanceInput);

            String[] periods = {"روزانه", "هفتگی"};
            String[] periodValues = {AppLimitPolicy.PERIOD_DAY, AppLimitPolicy.PERIOD_WEEK};
            String[] actions = {"فقط هشدار", "مسدودکردن بعد از سقف"};
            String[] actionValues = {AppLimitPolicy.ACTION_WARN, AppLimitPolicy.ACTION_BLOCK};
            String[] unlocks = {"وقفهٔ کوتاه", "سهمیهٔ اضطراری روزانه", "PIN", "بدون بازکردن"};
            String[] unlockValues = {AppLimitPolicy.UNLOCK_BREAK, AppLimitPolicy.UNLOCK_ALLOWANCE,
                    AppLimitPolicy.UNLOCK_PIN, AppLimitPolicy.UNLOCK_NONE};
            int periodIndex = AppLimitPolicy.PERIOD_WEEK.equals(
                    prefs.getString(AppLimitPolicy.key(packageName, "period"), AppLimitPolicy.PERIOD_DAY)) ? 1 : 0;
            int actionIndex = AppLimitPolicy.ACTION_WARN.equals(
                    prefs.getString(AppLimitPolicy.key(packageName, "action"), AppLimitPolicy.ACTION_BLOCK)) ? 0 : 1;
            String unlockValue = prefs.getString(AppLimitPolicy.key(packageName, "unlock"),
                    AppLimitPolicy.UNLOCK_BREAK);
            int unlockIndex = 0;
            for (int i = 0; i < unlockValues.length; i++) if (unlockValues[i].equals(unlockValue)) unlockIndex = i;
            int[] selectedPeriod = {periodIndex};
            int[] selectedAction = {actionIndex};
            int[] selectedUnlock = {unlockIndex};

            MaterialButton periodButton = button(periods[periodIndex]);
            periodButton.setOnClickListener(v -> choosePolicy(periodButton, "بازهٔ سقف", periods,
                    selectedPeriod));
            body.addView(periodButton);
            MaterialButton actionButton = button(actions[actionIndex]);
            actionButton.setOnClickListener(v -> choosePolicy(actionButton, "رفتار پس از سقف", actions,
                    selectedAction));
            body.addView(actionButton);
            MaterialButton unlockButton = button(unlocks[unlockIndex]);
            unlockButton.setOnClickListener(v -> choosePolicy(unlockButton, "روش بازکردن", unlocks,
                    selectedUnlock));
            body.addView(unlockButton);
            CheckBox enabled = new CheckBox(this);
            enabled.setText("فعال‌کردن محدودیت برای این برنامه");
            enabled.setTextColor(Color.WHITE);
            enabled.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.rgb(60, 180, 230)));
            enabled.setChecked(prefs.getBoolean(AppLimitPolicy.key(packageName, "enabled"), false));
            body.addView(enabled);
            body.addView(text("محدودیت دستگاهی است و با دسترسی Usage Access و سرویس محافظ تمرکز اعمال می‌شود. در حالت هشدار، برنامه مسدود نمی‌شود.",
                    10, Color.LTGRAY));
            if (enabled.isChecked()
                    && AppLimitPolicy.ACTION_BLOCK.equals(AppLimitPolicy.action(prefs, packageName))) {
                MaterialButton unlockNow = button("بازکردن موقت این برنامه");
                LinearLayout.LayoutParams unlockParams = new LinearLayout.LayoutParams(-1, dp(44));
                unlockParams.topMargin = dp(8);
                body.addView(unlockNow, unlockParams);
                unlockNow.setOnClickListener(v -> requestTemporaryUnlock(packageName));
            }

            androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                    .setTitle("زمان استفاده · " + appName)
                    .setView(body)
                    .setNegativeButton("بستن", null)
                    .setNeutralButton("آمار همهٔ برنامه‌ها", (d, w) -> showScreenTimeDashboard())
                    .setPositiveButton("ذخیره", null)
                    .create();
            dialog.setOnShowListener(ignored -> dialog.getButton(
                    androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                try {
                    int minutes = parseSetting(limitInput, 1, 1440);
                    int warning = Integer.parseInt(String.valueOf(warningInput.getText()));
                    int breakMinutes = parseSetting(breakInput, 1, 60);
                    int allowance = parseSetting(allowanceInput, 1, 120);
                    if (warning < 0 || warning > minutes) throw new NumberFormatException();
                    if (enabled.isChecked() && !AppUsageStats.hasAccess(this)) {
                        dialog.dismiss();
                        new MaterialAlertDialogBuilder(this)
                                .setMessage("برای آمار و اجرای سقف برنامه باید دسترسی Usage Access را فعال کنید.")
                                .setNegativeButton("بعداً", null)
                                .setPositiveButton("فعال‌سازی", (d, w) -> requestUsageAccess()).show();
                        return;
                    }
                    String selectedUnlockValue = unlockValues[selectedUnlock[0]];
                    if (AppLimitPolicy.UNLOCK_PIN.equals(selectedUnlockValue)
                            && !prefs.contains("screen_time_pin_hash")) {
                        setScreenTimePin(() -> saveAppLimit(packageName, minutes, warning,
                                breakMinutes, allowance, periodValues[selectedPeriod[0]],
                                actionValues[selectedAction[0]], selectedUnlockValue, enabled.isChecked(),
                                dialog));
                        return;
                    }
                    saveAppLimit(packageName, minutes, warning, breakMinutes, allowance,
                            periodValues[selectedPeriod[0]], actionValues[selectedAction[0]],
                            selectedUnlockValue, enabled.isChecked(), dialog);
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "مقدارها را بررسی کنید؛ سقف ۱ تا ۱۴۴۰ و هشدار حداکثر برابر سقف باشد.",
                            Toast.LENGTH_LONG).show();
                }
            }));
            dialog.show();
        }

        private void choosePolicy(MaterialButton button, String title, String[] options,
                                  int[] selected) {
            new MaterialAlertDialogBuilder(this).setTitle(title)
                    .setSingleChoiceItems(options, selected[0], (dialog, which) -> {
                        selected[0] = which;
                        button.setText(options[which]);
                        dialog.dismiss();
                    }).setNegativeButton("انصراف", null).show();
        }

        private void requestTemporaryUnlock(String packageName) {
                    if (!AppUsageStats.hasAccess(this)) {
                        requestUsageAccess();
                        return;
                    }
                    String unlock = AppLimitPolicy.unlock(prefs, packageName);
                    if (AppLimitPolicy.UNLOCK_NONE.equals(unlock)) {
                        Toast.makeText(this, "برای این برنامه بازکردن موقت غیرفعال است.", Toast.LENGTH_LONG).show();
                    } else if (AppLimitPolicy.UNLOCK_PIN.equals(unlock)) {
                        verifyScreenTimePin(packageName);
                    } else {
                        calendarExecutor.execute(() -> {
                            try {
                                long now = System.currentTimeMillis();
                                long usage = AppLimitPolicy.usageSincePeriod(this, prefs, packageName, now);
                                if (AppLimitPolicy.UNLOCK_BREAK.equals(unlock)) {
                                    int minutes = prefs.getInt(AppLimitPolicy.key(packageName, "break_minutes"), 5);
                                    prefs.edit().putLong(AppLimitPolicy.key(packageName, "unlock_until"),
                                            now + minutes * 60_000L).apply();
                                    recordScreenTimeUnlock();
                                    handler.post(() -> Toast.makeText(this,
                                            "برنامه برای " + persianDigits(minutes) + " دقیقه باز شد.",
                                            Toast.LENGTH_LONG).show());
                                } else {
                                    prefs.edit().putLong(AppLimitPolicy.key(packageName, "allowance_start_usage"),
                                            usage).apply();
                                    recordScreenTimeUnlock();
                                    handler.post(() -> Toast.makeText(this, "سهمیهٔ اضطراری فعال شد.",
                                            Toast.LENGTH_LONG).show());
                                }
                            } catch (RuntimeException e) {
                                android.util.Log.e("FocusUsage", "Unable to unlock " + packageName, e);
                                handler.post(() -> Toast.makeText(this, "مصرف برنامه خوانده نشد؛ بازکردن انجام نشد.",
                                        Toast.LENGTH_LONG).show());
                            }
                        });
                    }
        }

        private void verifyScreenTimePin(String packageName) {
                    TextInputEditText pin = new TextInputEditText(this);
                    pin.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                            | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
                    pin.setHint("PIN");
                    pin.setTextColor(Color.WHITE);
                    new MaterialAlertDialogBuilder(this).setTitle("بازکردن موقت با PIN")
                            .setView(pin).setNegativeButton("انصراف", null)
                            .setPositiveButton("تأیید", (dialog, which) -> {
                                String saltString = prefs.getString("screen_time_pin_salt", "");
                                String expected = prefs.getString("screen_time_pin_hash", "");
                                try {
                                    byte[] salt = android.util.Base64.decode(saltString, android.util.Base64.NO_WRAP);
                                    if (!expected.isEmpty() && expected.equals(hashPin(
                                            String.valueOf(pin.getText()), salt))) {
                                        int minutes = prefs.getInt(AppLimitPolicy.key(packageName, "break_minutes"), 5);
                                        prefs.edit().putLong(AppLimitPolicy.key(packageName, "unlock_until"),
                                                System.currentTimeMillis() + minutes * 60_000L).apply();
                                        recordScreenTimeUnlock();
                                        Toast.makeText(this, "برنامه برای " + persianDigits(minutes)
                                                + " دقیقه باز شد.", Toast.LENGTH_LONG).show();
                                    } else {
                                        Toast.makeText(this, "PIN درست نیست.", Toast.LENGTH_LONG).show();
                                    }
                                } catch (IllegalArgumentException e) {
                                    android.util.Log.e("FocusUsage", "Stored screen-time PIN is invalid.", e);
                                    Toast.makeText(this, "PIN ذخیره‌شده معتبر نیست.", Toast.LENGTH_LONG).show();
                                }
                            }).show();
        }

        private void recordScreenTimeUnlock() {
                    String today = todayKey();
                    String key = "screen_time_unlocks:" + today;
                    prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply();
        }

        private void saveAppLimit(String packageName, int minutes, int warning, int breakMinutes,
                                  int allowance, String period, String action, String unlock,
                                  boolean enabled, androidx.appcompat.app.AlertDialog dialog) {
            prefs.edit()
                    .putInt(AppLimitPolicy.key(packageName, "minutes"), minutes)
                    .putInt(AppLimitPolicy.key(packageName, "warning_minutes"), warning)
                    .putInt(AppLimitPolicy.key(packageName, "break_minutes"), breakMinutes)
                    .putInt(AppLimitPolicy.key(packageName, "allowance_minutes"), allowance)
                    .putString(AppLimitPolicy.key(packageName, "period"), period)
                    .putString(AppLimitPolicy.key(packageName, "action"), action)
                    .putString(AppLimitPolicy.key(packageName, "unlock"), unlock)
                    .putBoolean(AppLimitPolicy.key(packageName, "enabled"), enabled)
                    .apply();
            dialog.dismiss();
            Toast.makeText(this, enabled ? "محدودیت ذخیره شد." : "محدودیت غیرفعال شد.",
                    Toast.LENGTH_SHORT).show();
            showAppTimeSettings(packageName, getAppLabel(packageName));
        }

        private String getAppLabel(String packageName) {
            try {
                return String.valueOf(getPackageManager().getApplicationLabel(
                        getPackageManager().getApplicationInfo(packageName, 0)));
            } catch (PackageManager.NameNotFoundException e) {
                return packageName;
            }
        }

        private void setScreenTimePin(Runnable onSaved) {
            TextInputEditText pin = new TextInputEditText(this);
            pin.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                    | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
            pin.setHint("PIN حداقل ۴ رقمی");
            pin.setTextColor(Color.WHITE);
            new MaterialAlertDialogBuilder(this).setTitle("تنظیم PIN بازکردن")
                    .setView(pin).setNegativeButton("انصراف", null)
                    .setPositiveButton("ذخیره", (dialog, which) -> {
                        String value = String.valueOf(pin.getText());
                        if (value.length() < 4) {
                            Toast.makeText(this, "PIN باید حداقل ۴ رقم باشد.", Toast.LENGTH_LONG).show();
                            return;
                        }
                        byte[] salt = UUID.randomUUID().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                        prefs.edit().putString("screen_time_pin_salt", android.util.Base64.encodeToString(
                                        salt, android.util.Base64.NO_WRAP))
                                .putString("screen_time_pin_hash", hashPin(value, salt)).apply();
                        onSaved.run();
                    }).show();
        }

        private String hashPin(String pin, byte[] salt) {
            try {
                javax.crypto.SecretKeyFactory factory =
                        javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
                javax.crypto.spec.PBEKeySpec spec = new javax.crypto.spec.PBEKeySpec(
                        pin.toCharArray(), salt, 40_000, 256);
                byte[] derived = factory.generateSecret(spec).getEncoded();
                spec.clearPassword();
                return android.util.Base64.encodeToString(derived, android.util.Base64.NO_WRAP);
            } catch (java.security.GeneralSecurityException e) {
                throw new IllegalStateException("PIN protection is unavailable.", e);
            }
        }
    private boolean isGuardEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        ComponentName service = new ComponentName(this, FocusGuardService.class);
        if (enabled == null) return false;
        for (String component : enabled.split(":")) {
            if (service.equals(ComponentName.unflattenFromString(component))) return true;
        }
        return false;
    }



    private static class PdfItem {
        final String title;
        final Uri uri;
        PdfItem(String title, Uri uri) { this.title = title; this.uri = uri; }
    }

    private static class PlannerTask {
        final String id;
        final String title;
        final String details;
        final long dueAt;
        final long remindAt;
        boolean done;
        String noteDate;
        long calendarEventId;

        PlannerTask(String id, String title, String details, long dueAt, long remindAt,
                    boolean done, long calendarEventId) {
            this.id = id;
            this.title = title;
            this.details = details == null ? "" : details;
            this.dueAt = dueAt;
            this.remindAt = remindAt;
            this.done = done;
            this.calendarEventId = calendarEventId;
        }

        PlannerTask withValues(String newTitle, String newDetails, long newDueAt, long newRemindAt) {
            PlannerTask updated = new PlannerTask(id, newTitle, newDetails, newDueAt,
                    newRemindAt, done, calendarEventId);
            updated.noteDate = noteDate;
            return updated;
        }
    }


    private final class TimerProgressView extends View {
        private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF bounds = new RectF();
        private float progress;

        TimerProgressView() {
            super(MainActivity.this);
            trackPaint.setStyle(Paint.Style.STROKE);
            trackPaint.setStrokeCap(Paint.Cap.ROUND);
            trackPaint.setStrokeWidth(dp(5));
            trackPaint.setColor(Color.rgb(47, 51, 62));
            progressPaint.setStyle(Paint.Style.STROKE);
            progressPaint.setStrokeCap(Paint.Cap.ROUND);
            progressPaint.setStrokeWidth(dp(5));
            progressPaint.setColor(Color.rgb(34, 158, 217));
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        void setProgress(long remaining, long duration) {
            progress = duration <= 0 ? 0 : Math.max(0, Math.min(1, (float) (duration - remaining) / duration));
            invalidate();
        }

        @Override
        protected void onDraw(android.graphics.Canvas canvas) {
            super.onDraw(canvas);
            float inset = trackPaint.getStrokeWidth();
            bounds.set(inset, inset, getWidth() - inset, getHeight() - inset);
            canvas.drawArc(bounds, -90, 360, false, trackPaint);
            if (progress > 0) canvas.drawArc(bounds, -90, progress * 360, false, progressPaint);
        }
    }
}

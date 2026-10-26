package com.sinlavi.focus;

import android.annotation.SuppressLint;
import android.Manifest;
import android.content.ContentUris;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfRenderer;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.MediaMetadataRetriever;
import android.app.NotificationManager;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
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
import android.provider.MediaStore;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.HorizontalScrollView;
import android.widget.GridLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
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
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_AUDIO_PERMISSION = 10;
    private static final int REQUEST_PDF = 11;
    private static final int REQUEST_AUDIO = 12;
    private static final int REQUEST_PDF_FOLDER = 13;
    private static final int REQUEST_PDF_RECONNECT = 14;
    private static final String PREFS = "focus_data";
    public static final String EXTRA_WIDGET_ACTION = "widget_action";
    public static final String WIDGET_ACTION_TOGGLE_TIMER = "toggle_timer";
    public static final String WIDGET_ACTION_OPEN_APP = "open_app";
    public static final String EXTRA_OPEN_REMINDERS = "open_reminders";
    private static final String PDF_LIST = "pdf_library";
    private static final String AUDIO_LIST = "audio_library";
    private static final String BLOCKED_APPS = "blocked_apps";
    private static final long TICK_MS = 1000;
    private static final int LIBRARY_PAGE_SIZE = 40;
    private static final int ICON_SIZE_DP = 20;
    private static final int ICON_TARGET_DP = 48;
    private static final String HOME_APP_SHORTCUTS = "home_app_shortcuts";
    private static final String SAVED_REMINDERS = "saved_reminders";
    private static final String TASKS_PREFIX = "tasks:";
    private static final String EXAM_REMINDER_ID = "exam_timer";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Track> tracks = new ArrayList<>();
    private final List<Integer> queue = new ArrayList<>();
    private final List<PdfItem> pdfs = new ArrayList<>();
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService persistenceExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService pdfExecutor = Executors.newSingleThreadExecutor();
    private final Map<String, Bitmap> artworkCache = new HashMap<>();
    private final Map<String, Bitmap> pdfCoverCache = new HashMap<>();
    private final Set<String> artworkLoads = new HashSet<>();
    private final Set<String> pdfCoverLoads = new HashSet<>();
    private android.content.SharedPreferences prefs;
    private LinearLayout screen;
    private TextView timerText;
    private TextView timerStatus;
    private MaterialButton timerButton;
    private TimerProgressView timerProgress;
    private TextView playerTitle;
    private TextView playerArtist;
    private ImageView playerArtwork;
    private MaterialButton playerPlayButton;
    private TextView statsSummary;
    private TextView readerTimerLabel;
    private TextView readerPlayerLabel;
    private TextView readerPageLabel;
    private MaterialButton readerTimerButton;
    private MaterialButton readerPlayerButton;
    private MaterialButton readerPreviousButton;
    private MaterialButton readerNextButton;
    private SeekBar readerPageSeekBar;
    private TextInputEditText musicSearch;
    private TextView miniPlayerTitle;
    private TextView miniPlayerArtist;
    private ImageView miniPlayerArtwork;
    private MaterialButton miniPlayerPlayButton;
    private String activeScreen = "home";
    private Runnable backAction;
    private MediaPlayer mediaPlayer;
    private AudioTrack noisePlayer;
    private int playingTrack = -1;
    private boolean noisePlaying;
    private long durationMillis = 25 * 60_000L;
    private long remainingMillis = durationMillis;
    private long endAtElapsed;
    private boolean timerRunning;
    private String timerPhase = "focus";
    private boolean scanningPdfs;
    private boolean loadingTracks;
    private boolean loadingSavedTracks;
    private boolean tracksLoaded;
    private volatile PdfRenderer renderer;
    private volatile ParcelFileDescriptor pdfDescriptor;
    private volatile File activePdfFile;
    private final Object pdfLock = new Object();
    private volatile int pdfPageCount;
    private final AtomicInteger pdfRenderRequestId = new AtomicInteger();
    private PdfItem activePdf;
    private int currentPage;
    private int visibleTrackCount = LIBRARY_PAGE_SIZE;
    private int visiblePdfCount = LIBRARY_PAGE_SIZE;
    private boolean pageRotated = true;
    private boolean readerControlsVisible = true;
    private boolean isHome = true;
    private LinearLayout readerControls;
    private HorizontalScrollView readerNavbar;
    private FrameLayout readerRoot;
    private PdfPageView pdfImage;
    private ProgressBar pdfLoading;
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
        createReminderChannel();
        loadSavedTracks();
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
        if (prefs != null && timerRunning) {
            prefs.edit().putBoolean("focus_guard_active", true).apply();
        }
        if ("Focus guard".equals(activeScreen)) showFocusGuard();
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
        saveQueue();
        ioExecutor.shutdownNow();
        persistenceExecutor.shutdown();
        releaseAudio();
        closePdf();
        pdfExecutor.shutdown();
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
            case "Note editor": return "ویرایش یادداشت";
            case "Reminders": return "یادآورها";
            case "Exam timer": return "زمان‌سنج آزمون";
            case "PDFs": return "PDF";
            case "PDF library": return "کتابخانهٔ PDF";
            case "Music": return "موسیقی";
            case "Queue": return "صف پخش";
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
            case "Load more": return "نمایش موارد بیشتر";
            case "Nothing playing": return "چیزی در حال پخش نیست";
            case "Choose a track": return "یک آهنگ انتخاب کنید";
            case "Open music player": return "بازکردن پخش‌کنندهٔ موسیقی";
            case "Page": return "صفحه";
            case "Add PDF": return "افزودن PDF";
            case "Scan folder": return "اسکن پوشه";
            case "Next": return "بعدی";
            case "Previous": return "قبلی";
            case "Add files": return "افزودن فایل";
            case "Add an app shortcut": return "افزودن میان‌بر برنامه";
            case "Your PDF library is empty. Add a document to start reading.":
                return "کتابخانهٔ PDF خالی است. برای شروع مطالعه یک سند اضافه کنید.";
            case "Your queue is empty. Add tracks with + in the music list.":
                return "صف پخش خالی است. از فهرست موسیقی آهنگ اضافه کنید.";
            case "White noise": return "صدای سفید";
            case "Built-in · study ambience": return "صدای داخلی · مناسب مطالعه";
            case "+ Add files": return "افزودن فایل";
            case "Load device music library": return "بارگذاری موسیقی دستگاه";
            case "Search tracks, artists, albums": return "جست‌وجوی آهنگ، هنرمند یا آلبوم";
            case "Loading your music library…": return "در حال بارگذاری موسیقی…";
            case "No matching tracks.": return "آهنگی پیدا نشد.";
            case "No tracks found. Add audio files from your device.":
                return "آهنگی پیدا نشد. فایل صوتی اضافه کنید.";
            case "Allow music access to scan your library, or add individual audio files.":
                return "برای اسکن کتابخانه اجازه دهید یا فایل صوتی جداگانه اضافه کنید.";
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
            case "Continue music": return "ادامهٔ پخش موسیقی";
            case "Auto-start breaks": return "شروع خودکار استراحت";
            case "Auto-start focus": return "شروع خودکار تمرکز";
            case "Gentle end chime": return "صدای پایان ملایم";
            case "Study time (minutes)": return "مدت تمرکز (دقیقه)";
            case "Short break (minutes)": return "استراحت کوتاه (دقیقه)";
            case "Long break (minutes)": return "استراحت بلند (دقیقه)";
            case "Long break after how many study sessions": return "پس از چند جلسه استراحت بلند باشد";
            case "Manage Android notification access": return "مدیریت دسترسی اعلان‌های اندروید";
            case "Keep playback running when a session ends": return "پس از پایان جلسه موسیقی ادامه یابد";
            case "Start break timers without a confirmation": return "استراحت بدون تأیید شروع شود";
            case "Continue automatically after a break": return "پس از استراحت تمرکز خودکار شروع شود";
            case "Play a short cue when a phase ends": return "پایان هر مرحله با یک صدای کوتاه اعلام شود";
            case "This file provider does not allow persistent access after reopening.":
                return "دسترسی پایدار به این فایل پس از بستن برنامه ممکن نیست.";
            case "Added 5 minutes to your focus timer.": return "۵ دقیقه به زمان‌سنج تمرکز اضافه شد.";
            case "Choose a music file with Add music, or allow library access.":
                return "یک فایل موسیقی انتخاب کنید یا اجازهٔ دسترسی به کتابخانه را بدهید.";
            case "Music library access was not granted.": return "اجازهٔ دسترسی به موسیقی داده نشد.";
            case "This track could not be played. It was removed from the queue.":
                return "پخش این آهنگ ممکن نشد و از صف حذف شد.";
            case "Unable to play this audio file.": return "پخش این فایل صوتی ممکن نشد.";
            case "White noise could not be started on this device.":
                return "صدای سفید در این دستگاه شروع نشد.";
            case "Choose a page from 1 to ": return "شمارهٔ صفحه را بین ۱ تا وارد کنید: ";
            case "Enter a page from 1 to ": return "شمارهٔ صفحه را از ۱ تا ";
            case "This provider may not keep audio access after Focus closes.":
                return "ممکن است دسترسی به این فایل صوتی پس از بستن برنامه حفظ نشود.";
            case "Folder access could not be saved.": return "ذخیرهٔ دسترسی پوشه ممکن نشد.";
            case "Could not scan this folder. Choose a folder that Focus can access.":
                return "اسکن پوشه انجام نشد؛ پوشه‌ای را انتخاب کنید که برنامه به آن دسترسی دارد.";
            case "Add PDF document": return "افزودن سند PDF";
            case "Unable to render this PDF page.": return "نمایش این صفحهٔ PDF ممکن نشد.";
            case "Track tags or artwork could not be read.": return "اطلاعات یا تصویر آهنگ خوانده نشد.";
            case "Track metadata reader could not be closed cleanly.":
                return "بستن خوانندهٔ اطلاعات آهنگ با خطا روبه‌رو شد.";
            case "Do Not Disturb controls require Android 6 or newer.":
                return "کنترل حالت مزاحم‌نشوید به اندروید ۶ یا جدیدتر نیاز دارد.";
            case "Time remaining in focus session": return "زمان باقی‌مانده از جلسهٔ تمرکز";
            case "Continue reading ": return "ادامهٔ مطالعهٔ ";
            case "Open PDF ": return "بازکردن PDF ";
            case "Play music": return "پخش موسیقی";
            case "Pause music": return "توقف موسیقی";
            case "Play": return "پخش";
            case "Previous track": return "آهنگ قبلی";
            case "Next track": return "آهنگ بعدی";
            case "Reset timer": return "بازنشانی زمان‌سنج";
            case "Choose an app": return "انتخاب برنامه";
            case "Choose PDF": return "انتخاب PDF";
            case "Later": return "بعداً";
            case "Show album": return "نمایش آلبوم";
            case "Play now": return "پخش";
            case "Add to queue": return "افزودن به صف";
            case "Remove from queue": return "حذف از صف";
            case "Open playback queue with ": return "بازکردن صف پخش با ";
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
                if (value.startsWith("Queue ")) return "صف پخش · " + persianDigits(value.substring(6));
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
        else if ("Queue".equals(title)) backAction = this::showMusic;
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
        miniPlayerTitle = null;
        miniPlayerArtist = null;
        miniPlayerArtwork = null;
        miniPlayerPlayButton = null;
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
        contentParams.bottomMargin = dp(144);
        shell.addView(root, contentParams);
        MaterialCardView miniPlayer = miniPlayer();
        FrameLayout.LayoutParams miniPlayerParams = new FrameLayout.LayoutParams(-1, dp(64),
                Gravity.BOTTOM);
        miniPlayerParams.leftMargin = dp(8);
        miniPlayerParams.rightMargin = dp(8);
        miniPlayerParams.bottomMargin = dp(76);
        shell.addView(miniPlayer, miniPlayerParams);
        FrameLayout.LayoutParams navigationParams = new FrameLayout.LayoutParams(-1, dp(72), Gravity.BOTTOM);
        shell.addView(bottomNavigation(), navigationParams);
        ViewCompat.setOnApplyWindowInsetsListener(shell, (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            contentParams.bottomMargin = dp(144) + systemBars.bottom;
            miniPlayerParams.bottomMargin = dp(76) + systemBars.bottom;
            navigationParams.bottomMargin = systemBars.bottom;
            root.setPadding(left + systemBars.left, top + systemBars.top,
                    right + systemBars.right, bottom + systemBars.bottom);
            shell.requestLayout();
            return insets;
        });
        setContentView(shell);
        refreshMiniPlayer();
    }

    private MaterialCardView miniPlayer() {
        MaterialCardView playerCard = card();
        playerCard.setRadius(dp(16));
        playerCard.setCardElevation(dp(4));
        LinearLayout player = row();
        player.setPadding(dp(8), dp(4), dp(4), dp(4));
        miniPlayerArtwork = new ImageView(this);
        miniPlayerArtwork.setScaleType(ImageView.ScaleType.CENTER_CROP);
        miniPlayerArtwork.setBackground(makeArtworkBackground());
        player.addView(miniPlayerArtwork, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout details = column();
        details.setGravity(Gravity.CENTER_VERTICAL);
        miniPlayerTitle = text(currentTrackTitle(), 12, Color.WHITE);
        miniPlayerTitle.setSingleLine(true);
        miniPlayerTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        details.addView(miniPlayerTitle);
        miniPlayerArtist = text(currentTrackArtist(), 10, Color.LTGRAY);
        miniPlayerArtist.setSingleLine(true);
        miniPlayerArtist.setEllipsize(android.text.TextUtils.TruncateAt.END);
        details.addView(miniPlayerArtist);
        LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(0, -1, 1);
        detailsParams.leftMargin = dp(8);
        player.addView(details, detailsParams);

        MaterialButton previous = iconButton(R.drawable.ic_skip_previous, "Previous track", this::playPrevious);
        player.addView(previous, new LinearLayout.LayoutParams(dp(48), dp(48)));
        miniPlayerPlayButton = iconButton(
                isAudioPlaying() ? R.drawable.ic_pause : R.drawable.ic_play_arrow,
                isAudioPlaying() ? "Pause music" : "Play music", this::togglePlayback);
        player.addView(miniPlayerPlayButton, new LinearLayout.LayoutParams(dp(48), dp(48)));
        MaterialButton next = iconButton(R.drawable.ic_skip_next, "Next track", this::playNext);
        player.addView(next, new LinearLayout.LayoutParams(dp(48), dp(48)));
        playerCard.addView(player);
        playerCard.setOnClickListener(v -> showMusic());
        playerCard.setContentDescription("Open music player");
        return playerCard;
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
        android.graphics.drawable.GradientDrawable rippleContent = new android.graphics.drawable.GradientDrawable();
        rippleContent.setColor(Color.TRANSPARENT);
        rippleContent.setCornerRadius(dp(20));
        item.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x3360B4E6), rippleContent, null));
        FrameLayout iconContainer = new FrameLayout(this);
        android.graphics.drawable.GradientDrawable iconBackground = new android.graphics.drawable.GradientDrawable();
        iconBackground.setColor(selected ? Color.rgb(38, 40, 51) : Color.TRANSPARENT);
        iconBackground.setCornerRadius(dp(18));
        iconContainer.setBackground(iconBackground);
        LinearLayout.LayoutParams iconContainerParams = new LinearLayout.LayoutParams(dp(60), dp(32));
        item.addView(iconContainer, iconContainerParams);
        ImageView iconView = new ImageView(this);
        iconView.setImageResource(icon);
        int foreground = selected ? Color.rgb(60, 180, 230) : Color.rgb(178, 182, 193);
        iconView.setImageTintList(android.content.res.ColorStateList.valueOf(foreground));
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER);
        iconContainer.addView(iconView, iconParams);
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
        closePdf();
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
        content.addView(brand);
        TextView title = text("برای کارهای مهم وقت بساز.", 22, Color.WHITE);
        title.setTypeface(appTypeface, Typeface.BOLD);
        LinearLayout.LayoutParams titleMargin = new LinearLayout.LayoutParams(-1, -2);
        titleMargin.topMargin = dp(6);
        titleMargin.bottomMargin = dp(14);
        content.addView(title, titleMargin);
        TextView dateLabel = text(todayDateLabel(), 13, Color.LTGRAY);
        dateLabel.setGravity(Gravity.CENTER);
        dateLabel.setPadding(dp(12), dp(10), dp(12), dp(10));
        dateLabel.setBackgroundResource(android.R.drawable.list_selector_background);
        dateLabel.setClickable(true);
        dateLabel.setFocusable(true);
        dateLabel.setContentDescription("انتخاب تاریخ و مشاهدهٔ یادداشت همان روز");
        dateLabel.setOnClickListener(v -> showDatePickerForNotes());
        LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(-1, -2);
        dateParams.bottomMargin = dp(14);
        content.addView(dateLabel, dateParams);

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
        timerText.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
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

        MaterialCardView playerCard = card();
        LinearLayout player = row();
        player.setPadding(dp(12), dp(4), dp(6), dp(4));
        playerArtwork = new ImageView(this);
        playerArtwork.setScaleType(ImageView.ScaleType.CENTER_CROP);
        playerArtwork.setBackground(makeArtworkBackground());
        player.addView(playerArtwork, new LinearLayout.LayoutParams(dp(38), dp(38)));
        LinearLayout trackDetails = column();
        trackDetails.setGravity(Gravity.CENTER_VERTICAL);
        playerTitle = text(currentTrackTitle(), 13, Color.WHITE);
        playerTitle.setMaxLines(1);
        playerTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        trackDetails.addView(playerTitle);
        playerArtist = text(currentTrackArtist(), 11, Color.LTGRAY);
        playerArtist.setMaxLines(1);
        playerArtist.setEllipsize(android.text.TextUtils.TruncateAt.END);
        trackDetails.addView(playerArtist);
        LinearLayout.LayoutParams playerTitleParams = new LinearLayout.LayoutParams(0, -2, 1);
        playerTitleParams.leftMargin = dp(9);
        player.addView(trackDetails, playerTitleParams);
        MaterialButton previous = iconButton(R.drawable.ic_skip_previous, "Previous track", this::playPrevious);
        player.addView(previous);
        playerPlayButton = iconButton(isAudioPlaying() ? R.drawable.ic_pause : R.drawable.ic_play_arrow,
                isAudioPlaying() ? "Pause music" : "Play music", this::togglePlayback);
        player.addView(playerPlayButton);
        MaterialButton next = iconButton(R.drawable.ic_skip_next, "Next track", this::playNext);
        player.addView(next);
        playerCard.addView(player);
        LinearLayout.LayoutParams playerParams = new LinearLayout.LayoutParams(-1, -2);
        playerParams.topMargin = dp(8);
        bottom.addView(playerCard, playerParams);
        LinearLayout.LayoutParams bottomParams = new LinearLayout.LayoutParams(-1, -2);
        bottomParams.topMargin = dp(4);
        content.addView(bottom, bottomParams);

        ScrollView scroll = scroll(content);
        scroll.setBackgroundColor(Color.rgb(17, 19, 24));
        setScreenContent(scroll);
        loadSavedPdfs();
        refreshHomePlayer();
    }

    private List<HomeShortcut> homeShortcuts() {
        List<HomeShortcut> shortcuts = new ArrayList<>();
        shortcuts.add(new HomeShortcut("ماشین‌حساب", R.drawable.ic_calculator, this::launchCalculator, null));
        shortcuts.add(new HomeShortcut("یادداشت‌ها", R.drawable.ic_edit, this::showNotes, null));
        shortcuts.add(new HomeShortcut("موسیقی", R.drawable.ic_music_note, this::showMusic, null));
        shortcuts.add(new HomeShortcut("کتابخانهٔ PDF", R.drawable.ic_picture_as_pdf, this::showPdfLibrary, null));
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

    private android.graphics.drawable.Drawable makeArtworkBackground() {
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
        if (readerTimerLabel != null) readerTimerLabel.setText(getString(
                R.string.reader_timer_status, phaseTitle(timerPhase), timerText.getText()));
        if (readerTimerButton != null) {
            readerTimerButton.setIconResource(timerRunning ? R.drawable.ic_pause : R.drawable.ic_play_arrow);
            readerTimerButton.setContentDescription(fa(timerRunning ? "Pause timer" : "Start or resume timer"));
        }
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
        body.addView(preferenceSwitch("Continue music", "Keep playback running when a session ends", "keep_music", true));
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
            LinearLayout taskHeader = row();
            TextView taskTitle = text("کارهای این روز", 13, Color.WHITE);
            taskTitle.setTypeface(appTypeface, Typeface.BOLD);
            taskHeader.addView(taskTitle, new LinearLayout.LayoutParams(0, -2, 1));
            MaterialButton addTask = button("+ کار");
            addTask.setMinHeight(dp(40));
            addTask.setOnClickListener(v -> showTaskEditor(date, null));
            taskHeader.addView(addTask);
            noteContent.addView(taskHeader);
            addTaskRows(noteContent, loadTasks(date), date, false);
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
            int[] jalali = gregorianToJalali(calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH));
            String[] months = {"فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
                    "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"};
            String jalaliText = calendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG,
                    new Locale("fa", "IR")) + "، " + persianDigits(jalali[2]) + " "
                    + months[jalali[1] - 1] + " " + persianDigits(jalali[0]);
            String gregorianText = new SimpleDateFormat("EEEE, d MMMM yyyy", Locale.US).format(date);
            return jalaliText + "\n" + gregorianText;
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

    private int[] gregorianToJalali(int year, int month, int day) {
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
                        item.optLong("remindAt"), item.optBoolean("done", false));
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
                array.put(item);
            }
        } catch (JSONException e) {
            android.util.Log.e("FocusTasks", "Unable to save tasks for " + date, e);
            Toast.makeText(this, "ذخیرهٔ کارها انجام نشد.", Toast.LENGTH_LONG).show();
            return;
        }
        prefs.edit().putString(tasksKey(date), array.toString()).apply();
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
                            String.valueOf(details.getText()).trim(), dueAt, remindAt, false)
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
        if (tasks.isEmpty()) return;
        TextView heading = text("کارهای پیشِ‌رو", 15, Color.WHITE);
        heading.setTypeface(appTypeface, Typeface.BOLD);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(-1, -2);
        headingParams.bottomMargin = dp(6);
        parent.addView(heading, headingParams);
        MaterialCardView taskCard = card();
        LinearLayout taskList = column();
        taskList.setPadding(dp(10), dp(6), dp(10), dp(6));
        List<PlannerTask> nextTasks = tasks.subList(0, Math.min(3, tasks.size()));
        for (PlannerTask task : nextTasks) {
            String date = task.noteDate == null ? todayKey() : task.noteDate;
            addTaskRows(taskList, Collections.singletonList(task), date, true);
        }
        taskCard.addView(taskList);
        taskCard.setOnClickListener(v -> showReminders());
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
                JSONArray current = loadReminders();
                for (int index = current.length() - 1; index >= 0; index--) {
                    JSONObject saved = current.optJSONObject(index);
                    if (saved != null && id.equals(saved.optString("id"))) current.remove(index);
                }
                prefs.edit().putString(SAVED_REMINDERS, current.toString()).apply();
                ReminderScheduler.cancel(this, id);
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

    private JSONArray loadReminders() {
        try {
            return new JSONArray(prefs.getString(SAVED_REMINDERS, "[]"));
        } catch (JSONException e) {
            android.util.Log.e("FocusReminders", "Unable to read saved reminders.", e);
            return new JSONArray();
        }
    }

    private void showReminderEditor() {
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
        when.add(Calendar.HOUR_OF_DAY, 1);
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

    private void showMusic() {
        LinearLayout list = column();
        list.setPadding(0, dp(4), 0, dp(12));
        if (tracks.isEmpty() && !tracksLoaded && !loadingSavedTracks) loadTracks();
        MaterialCardView noise = card();
        LinearLayout noiseRow = row();
        noiseRow.setPadding(dp(12), dp(9), dp(12), dp(9));
        ImageView noiseIcon = new ImageView(this);
        noiseIcon.setImageResource(R.drawable.ic_graphic_eq);
        noiseIcon.setImageTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        noiseIcon.setContentDescription("White noise");
        noiseIcon.setBackground(makeArtworkBackground());
        noiseIcon.setPadding(dp(8), dp(8), dp(8), dp(8));
        noiseRow.addView(noiseIcon, new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout noiseDetails = column();
        TextView noiseName = text("White noise", 14, Color.WHITE);
        noiseDetails.addView(noiseName);
        noiseDetails.addView(text("Built-in · study ambience", 11, Color.LTGRAY));
        LinearLayout.LayoutParams noiseNameParams = new LinearLayout.LayoutParams(0, -2, 1);
        noiseNameParams.leftMargin = dp(10);
        noiseRow.addView(noiseDetails, noiseNameParams);
        MaterialButton noisePlay = iconButton(noisePlaying && isAudioPlaying()
                ? R.drawable.ic_pause : R.drawable.ic_play_arrow, "Toggle white noise", this::toggleNoise);
        noiseRow.addView(noisePlay);
        noise.addView(noiseRow);
        noiseRow.setOnClickListener(v -> toggleNoise());
        LinearLayout.LayoutParams noiseParams = new LinearLayout.LayoutParams(-1, -2);
        list.addView(noise, noiseParams);

        LinearLayout libraryActions = row();
        MaterialButton add = button("+ Add files");
        add.setMinHeight(dp(42));
        add.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("audio/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(intent, REQUEST_AUDIO);
        });
        libraryActions.addView(add, new LinearLayout.LayoutParams(0, dp(42), 1));
        MaterialButton queueButton = button("Queue " + queue.size());
        queueButton.setIconResource(R.drawable.ic_queue_music);
        queueButton.setIconTint(android.content.res.ColorStateList.valueOf(Color.WHITE));
        queueButton.setMinHeight(dp(48));
        queueButton.setContentDescription("Open playback queue with " + queue.size() + " tracks");
        queueButton.setOnClickListener(v -> showQueue());
        LinearLayout.LayoutParams queueParams = new LinearLayout.LayoutParams(0, dp(42), 1);
        queueParams.leftMargin = dp(8);
        libraryActions.addView(queueButton, queueParams);
        list.addView(libraryActions);
        if (tracks.isEmpty()) {
            MaterialButton scan = button("Load device music library");
            LinearLayout.LayoutParams scanParams = new LinearLayout.LayoutParams(-1, dp(42));
            scanParams.topMargin = dp(6);
            list.addView(scan, scanParams);
            scan.setOnClickListener(v -> {
                requestAudioPermission();
                if (hasAudioPermission()) {
                    loadTracks();
                    showMusic();
                }
            });
        }
        musicSearch = new TextInputEditText(this);
        musicSearch.setSingleLine(true);
        musicSearch.setTextColor(Color.WHITE);
        musicSearch.setHintTextColor(Color.GRAY);
        musicSearch.setHint(fa("Search tracks, artists, albums"));
        android.graphics.drawable.Drawable searchIcon = ContextCompat.getDrawable(this, R.drawable.ic_search);
        if (searchIcon != null) {
            searchIcon = searchIcon.mutate();
            searchIcon.setTint(Color.LTGRAY);
        }
        musicSearch.setCompoundDrawablesWithIntrinsicBounds(searchIcon, null, null, null);
        musicSearch.setCompoundDrawablePadding(dp(8));
        musicSearch.setPadding(dp(12), dp(4), dp(12), dp(4));
        musicSearch.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(60, 65, 78)));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(-1, dp(46));
        searchParams.topMargin = dp(9);
        list.addView(musicSearch, searchParams);

        HorizontalScrollView albumScroller = new HorizontalScrollView(this);
        albumScroller.setHorizontalScrollBarEnabled(false);
        LinearLayout albumChips = row();
        albumChips.setPadding(0, dp(6), 0, dp(4));
        Set<String> albums = new HashSet<>();
        for (Track track : tracks) if (!track.album.isEmpty()) albums.add(track.album);
        for (String album : albums) {
            MaterialButton chip = button(album);
            chip.setMinHeight(dp(48));
            chip.setTextSize(11);
            chip.setOnClickListener(v -> musicSearch.setText(album));
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(-2, dp(36));
            chipParams.rightMargin = dp(6);
            albumChips.addView(chip, chipParams);
        }
        albumScroller.addView(albumChips);
        list.addView(albumScroller);

        TextView count = text(tracks.size() + " tracks", 11, Color.LTGRAY);
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(-1, -2);
        countParams.topMargin = dp(5);
        list.addView(count, countParams);
        LinearLayout rows = column();
        LinearLayout.LayoutParams rowsParams = new LinearLayout.LayoutParams(-1, -2);
        rowsParams.topMargin = dp(3);
        list.addView(rows, rowsParams);
        renderTrackRows(rows, "");
        musicSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                visibleTrackCount = LIBRARY_PAGE_SIZE;
                renderTrackRows(rows, s.toString());
            }
            @Override public void afterTextChanged(android.text.Editable s) { }
        });
        if (loadingTracks || loadingSavedTracks) {
            TextView loading = text("Loading your music library…", 12, Color.LTGRAY);
            LinearLayout.LayoutParams loadingParams = new LinearLayout.LayoutParams(-1, -2);
            loadingParams.topMargin = dp(8);
            list.addView(loading, loadingParams);
        } else if (tracks.isEmpty() && !hasAudioPermission()) {
            TextView empty = text("Allow music access to scan your library, or add individual audio files.", 12, Color.LTGRAY);
            LinearLayout.LayoutParams emptyParams = new LinearLayout.LayoutParams(-1, -2);
            emptyParams.topMargin = dp(8);
            list.addView(empty, emptyParams);
        } else if (tracks.isEmpty()) {
            TextView empty = text("No tracks found. Add audio files from your device.", 12, Color.LTGRAY);
            LinearLayout.LayoutParams emptyParams = new LinearLayout.LayoutParams(-1, -2);
            emptyParams.topMargin = dp(8);
            list.addView(empty, emptyParams);
        }
        showScreen("Music", scroll(list));
    }

    private void renderTrackRows(LinearLayout target, String query) {
        target.removeAllViews();
        String needle = query.trim().toLowerCase(Locale.getDefault());
        int matches = 0;
        int rendered = 0;
        for (int i = 0; i < tracks.size(); i++) {
            final int index = i;
            Track track = tracks.get(i);
            String searchable = (track.title + " " + track.artist + " " + track.album).toLowerCase(Locale.getDefault());
            if (!searchable.contains(needle)) continue;
            matches++;
            if (rendered >= visibleTrackCount) continue;
            rendered++;
            MaterialCardView trackCard = card();
            LinearLayout trackRow = row();
            trackRow.setPadding(dp(9), dp(7), dp(5), dp(7));
            ImageView artwork = new ImageView(this);
            artwork.setScaleType(ImageView.ScaleType.CENTER_CROP);
            artwork.setBackground(makeArtworkBackground());
            requestArtwork(track, artwork);
            trackRow.addView(artwork, new LinearLayout.LayoutParams(dp(46), dp(46)));
            LinearLayout details = column();
            details.setGravity(Gravity.CENTER_VERTICAL);
            TextView name = text(track.title, 13, Color.WHITE);
            name.setMaxLines(1);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            details.addView(name);
            TextView subtitle = text(track.artist + (track.album.isEmpty() ? "" : " · " + track.album), 11, Color.LTGRAY);
            subtitle.setMaxLines(1);
            subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
            details.addView(subtitle);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, -2, 1);
            nameParams.leftMargin = dp(9);
            trackRow.addView(details, nameParams);
            boolean isQueued = queue.contains(index);
            MaterialButton addQueue = iconButton(isQueued ? R.drawable.ic_check : R.drawable.ic_add,
                    isQueued ? "Remove from queue" : "Add to queue", () -> {
                        if (queue.contains(index)) queue.remove((Integer) index);
                        else queue.add(index);
                        saveQueue();
                        showMusic();
                    });
            trackRow.addView(addQueue);
            trackCard.addView(trackRow);
            trackCard.setMinimumHeight(dp(72));
            trackCard.setClickable(true);
            trackCard.setFocusable(true);
            trackCard.setContentDescription("Play " + track.title + " by " + track.artist);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.topMargin = dp(4);
            target.addView(trackCard, params);
            trackCard.setOnClickListener(v -> playTrack(index));
            trackCard.setOnLongClickListener(v -> {
                new MaterialAlertDialogBuilder(this).setTitle(track.title)
                        .setItems(new String[]{"Play now", "Add to queue", "Show album"}, (dialog, choice) -> {
                            if (choice == 0) playTrack(index);
                            else if (choice == 1) {
                                if (!queue.contains(index)) queue.add(index);
                                saveQueue();
                                showMusic();
                            } else if (!track.album.isEmpty() && musicSearch != null) musicSearch.setText(track.album);
                        }).show();
                return true;
            });
        }
        if (matches == 0) target.addView(text("No matching tracks.", 12, Color.LTGRAY));
        else if (matches > rendered) {
            MaterialButton loadMore = button("Load more");
            loadMore.setMinHeight(dp(44));
            loadMore.setOnClickListener(v -> {
                visibleTrackCount += LIBRARY_PAGE_SIZE;
                renderTrackRows(target, query);
            });
            LinearLayout.LayoutParams moreParams = new LinearLayout.LayoutParams(-1, dp(44));
            moreParams.topMargin = dp(8);
            target.addView(loadMore, moreParams);
        }
    }

    private Bitmap loadArtwork(Track track) {
        Uri artworkUri = track.artworkUri();
        String cacheKey = track.uri.toString();
        Bitmap cached = readBitmapCache("music-artwork", cacheKey);
        if (cached != null) return cached;

        Bitmap artwork = null;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            if (artworkUri != null) {
                try (java.io.InputStream bounds = getContentResolver().openInputStream(artworkUri)) {
                    if (bounds != null) BitmapFactory.decodeStream(bounds, null, options);
                } catch (IOException | RuntimeException e) {
                    android.util.Log.w("FocusArtwork", "Album art URI could not be read: " + artworkUri, e);
                }
            }
            if (options.outWidth > 0 && options.outHeight > 0) {
                options.inSampleSize = sampleSize(options, dp(96));
                options.inJustDecodeBounds = false;
                try (java.io.InputStream input = getContentResolver().openInputStream(artworkUri)) {
                    if (input != null) artwork = BitmapFactory.decodeStream(input, null, options);
                } catch (IOException | RuntimeException e) {
                    android.util.Log.w("FocusArtwork", "Album art could not be decoded: " + artworkUri, e);
                }
            }
            if (artwork == null) {
                byte[] embedded = track.embeddedArtwork;
                if (embedded == null) {
                    MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                    try {
                        retriever.setDataSource(this, track.uri);
                        embedded = retriever.getEmbeddedPicture();
                    } catch (RuntimeException e) {
                        android.util.Log.w("FocusArtwork", "Embedded album art could not be read for "
                                + track.uri, e);
                    } finally {
                        try {
                            retriever.release();
                        } catch (IOException e) {
                            android.util.Log.w("FocusArtwork", "Metadata reader could not be released.", e);
                        }
                    }
                }
                if (embedded != null) {
                    options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(embedded, 0, embedded.length, options);
                    if (options.outWidth > 0 && options.outHeight > 0) {
                        options.inSampleSize = sampleSize(options, dp(96));
                        options.inJustDecodeBounds = false;
                        artwork = BitmapFactory.decodeByteArray(embedded, 0, embedded.length, options);
                    }
                }
            }
            if (artwork != null) writeBitmapCache("music-artwork", cacheKey, artwork);
            return artwork;
        } catch (RuntimeException | OutOfMemoryError e) {
            android.util.Log.w("FocusArtwork", "Album art could not be decoded for " + track.uri, e);
            return null;
        }
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

    private void requestArtwork(Track track, ImageView target) {
        String key = track.uri.toString();
        target.setTag(key);
        Bitmap cached = artworkCache.get(key);
        if (cached != null && !cached.isRecycled()) {
            target.setImageBitmap(cached);
            target.setBackground(null);
            return;
        }
        target.setImageDrawable(null);
        target.setBackground(makeArtworkBackground());
        if (!artworkLoads.add(key)) return;
        ioExecutor.execute(() -> {
            Bitmap artwork = loadArtwork(track);
            runOnUiThread(() -> {
                artworkLoads.remove(key);
                if (isFinishing() || isDestroyed()) {
                    if (artwork != null) artwork.recycle();
                    return;
                }
                if (artwork != null) {
                    if (artworkCache.size() >= 48) {
                        String oldestKey = artworkCache.keySet().iterator().next();
                        artworkCache.remove(oldestKey);
                    }
                    artworkCache.put(key, artwork);
                    if (key.equals(target.getTag())) {
                        target.setImageBitmap(artwork);
                        target.setBackground(null);
                    }
                }
            });
        });
    }

    private int sampleSize(BitmapFactory.Options options, int targetSize) {
        int sample = 1;
        while (options.outWidth / sample > targetSize * 2 || options.outHeight / sample > targetSize * 2) sample *= 2;
        return sample;
    }

    private boolean hasAudioPermission() {
        if (Build.VERSION.SDK_INT < 23) return true;
        String permission = Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE;
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
    }

    private void showQueue() {
        LinearLayout list = column();
        list.setPadding(0, dp(6), 0, dp(10));
        TextView caption = text(queue.size() + " queued tracks · tap a track to play", 12, Color.LTGRAY);
        list.addView(caption);
        for (int position = 0; position < queue.size(); position++) {
            final int queuePosition = position;
            int trackIndex = queue.get(position);
            if (trackIndex < 0 || trackIndex >= tracks.size()) continue;
            Track track = tracks.get(trackIndex);
            MaterialCardView item = card();
            LinearLayout row = row();
            row.setPadding(dp(10), dp(8), dp(5), dp(8));
            TextView number = text(String.valueOf(position + 1), 12, Color.LTGRAY);
            row.addView(number, new LinearLayout.LayoutParams(dp(28), -2));
            LinearLayout details = column();
            details.addView(text(track.title, 13, Color.WHITE));
            details.addView(text(track.artist, 11, Color.LTGRAY));
            row.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
            MaterialButton remove = iconButton(R.drawable.ic_close, "Remove from queue", () -> {
                queue.remove(queuePosition);
                saveQueue();
                showQueue();
            });
            row.addView(remove);
            item.addView(row);
            item.setMinimumHeight(dp(72));
            item.setClickable(true);
            item.setFocusable(true);
            item.setContentDescription("Play " + track.title + " from queue");
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.topMargin = dp(5);
            list.addView(item, params);
            item.setOnClickListener(v -> playTrack(trackIndex));
            item.setOnLongClickListener(v -> {
                new MaterialAlertDialogBuilder(this).setTitle(track.title)
                        .setItems(new String[]{"Play now", "Remove from queue"}, (dialog, choice) -> {
                            if (choice == 0) playTrack(trackIndex);
                            else {
                                queue.remove((Integer) trackIndex);
                                saveQueue();
                                showQueue();
                            }
                        }).show();
                return true;
            });
        }
        if (queue.isEmpty()) {
            TextView empty = text("Your queue is empty. Add tracks with + in the music list.", 13, Color.LTGRAY);
            LinearLayout.LayoutParams emptyParams = new LinearLayout.LayoutParams(-1, -2);
            emptyParams.topMargin = dp(16);
            list.addView(empty, emptyParams);
        }
        showScreen("Queue", scroll(list));
    }

    private Track readTrackMetadata(Uri uri) {
        String title = getDisplayName(uri);
        String artist = "Unknown artist";
        String album = "Unknown album";
        byte[] artwork = null;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(this, uri);
            String metadataTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            String metadataArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            String metadataAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
            if (metadataTitle != null && !metadataTitle.trim().isEmpty()) title = metadataTitle;
            if (metadataArtist != null && !metadataArtist.trim().isEmpty()) artist = metadataArtist;
            if (metadataAlbum != null && !metadataAlbum.trim().isEmpty()) album = metadataAlbum;
            artwork = retriever.getEmbeddedPicture();
        } catch (RuntimeException e) {
            Toast.makeText(this, "Track tags or artwork could not be read.", Toast.LENGTH_SHORT).show();
        } finally {
            try {
                retriever.release();
            } catch (IOException e) {
                Toast.makeText(this, "Track metadata reader could not be closed cleanly.", Toast.LENGTH_SHORT).show();
            }
        }
        return new Track(title, artist, album, uri, -1, artwork);
    }

    private void loadSavedTracks() {
        String raw = prefs.getString(AUDIO_LIST, "[]");
        loadingSavedTracks = true;
        ioExecutor.execute(() -> {
            List<Track> savedTracks = new ArrayList<>();
            boolean invalidData = false;
            try {
                JSONArray array = new JSONArray(raw);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject item = array.getJSONObject(i);
                    Uri uri = Uri.parse(item.getString("uri"));
                    savedTracks.add(new Track(item.optString("title", "Audio track"),
                            item.optString("artist", "Unknown artist"), item.optString("album", ""),
                            uri, item.optLong("album_id", -1), null));
                }
            } catch (JSONException e) {
                invalidData = true;
            }
            boolean removeInvalidData = invalidData;
            runOnUiThread(() -> {
                loadingSavedTracks = false;
                if (isFinishing() || isDestroyed()) return;
                if (removeInvalidData) prefs.edit().remove(AUDIO_LIST).apply();
                tracks.clear();
                tracks.addAll(savedTracks);
                restoreQueue();
                if (tracks.isEmpty() && !tracksLoaded && "Music".equals(activeScreen)) loadTracks();
                if ("Music".equals(activeScreen)) showMusic();
            });
        });
    }

    private void saveQueue() {
        JSONArray array = new JSONArray(queue);
        prefs.edit().putString("music_queue", array.toString())
                .putInt("music_current_track", playingTrack).apply();
    }

    private void restoreQueue() {
        queue.clear();
        try {
            JSONArray array = new JSONArray(prefs.getString("music_queue", "[]"));
            for (int i = 0; i < array.length(); i++) {
                int index = array.getInt(i);
                if (index >= 0 && index < tracks.size() && !queue.contains(index)) queue.add(index);
            }
        } catch (JSONException ignored) {
            prefs.edit().remove("music_queue").apply();
        }
        playingTrack = prefs.getInt("music_current_track", -1);
        if (playingTrack < 0 || playingTrack >= tracks.size()) playingTrack = -1;
    }

    private void saveTrackList() {
        List<Track> snapshot = new ArrayList<>(tracks);
        persistenceExecutor.execute(() -> {
            JSONArray array = new JSONArray();
            for (Track track : snapshot) {
                try {
                    JSONObject item = new JSONObject();
                    item.put("title", track.title);
                    item.put("artist", track.artist);
                    item.put("album", track.album);
                    item.put("uri", track.uri.toString());
                    item.put("album_id", track.albumId);
                    array.put(item);
                } catch (JSONException e) {
                    android.util.Log.w("FocusMusic", "Unable to serialize a music library entry.", e);
                }
            }
            prefs.edit().putString(AUDIO_LIST, array.toString()).apply();
        });
    }

    private void requestAudioPermission() {
        String permission = Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (Build.VERSION.SDK_INT < 23 || ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            loadTracks();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{permission}, REQUEST_AUDIO_PERMISSION);
        }
    }

    private void loadTracks() {
        if (Build.VERSION.SDK_INT >= 23) {
            String permission = Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE;
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) return;
        }
        if (loadingTracks) return;
        loadingTracks = true;
        ioExecutor.execute(() -> {
            List<Track> discovered = new ArrayList<>();
            boolean accessDenied = false;
            Uri collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            String[] projection = {MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID};
            try (Cursor cursor = getContentResolver().query(collection, projection,
                    MediaStore.Audio.Media.IS_MUSIC + " != 0", null,
                    MediaStore.Audio.Media.TITLE + " COLLATE NOCASE")) {
                if (cursor != null) {
                    int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                    int titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                    int artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                    int albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
                    int albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);
                    while (cursor.moveToNext()) {
                        Uri uri = ContentUris.withAppendedId(collection, cursor.getLong(idColumn));
                        discovered.add(new Track(cursor.getString(titleColumn), cursor.getString(artistColumn),
                                cursor.getString(albumColumn), uri, cursor.getLong(albumIdColumn), null));
                    }
                }
            } catch (SecurityException e) {
                accessDenied = true;
            }
            boolean finalAccessDenied = accessDenied;
            runOnUiThread(() -> {
                loadingTracks = false;
                if (isFinishing() || isDestroyed()) return;
                tracksLoaded = true;
                boolean changed = false;
                for (Track track : discovered) {
                    if (!containsTrack(track.uri)) {
                        tracks.add(track);
                        changed = true;
                    }
                }
                if (changed) saveTrackList();
                if (finalAccessDenied) {
                    Toast.makeText(this, "Music library access was not granted.", Toast.LENGTH_SHORT).show();
                }
                if ("Music".equals(activeScreen)) showMusic();
            });
        });
    }

    private boolean containsTrack(Uri uri) {
        for (Track track : tracks) if (track.uri.equals(uri)) return true;
        return false;
    }

    private void playTrack(int index) {
        if (index < 0 || index >= tracks.size()) return;
        if (!queue.contains(index)) queue.add(index);
        stopAudio();
        playingTrack = index;
        saveQueue();
        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(this, tracks.get(index).uri);
            final int failedTrack = index;
            mediaPlayer.setOnErrorListener((player, what, extra) -> {
                stopAudio();
                queue.remove((Integer) failedTrack);
                saveQueue();
                Toast.makeText(this, "This track could not be played. It was removed from the queue.",
                        Toast.LENGTH_LONG).show();
                refreshHomePlayer();
                if (!queue.isEmpty()) playTrack(queue.get(0));
                return true;
            });
            mediaPlayer.setOnCompletionListener(mp -> {
                refreshHomePlayer();
                if (queue.size() > 1) playNext();
            });
            mediaPlayer.setOnPreparedListener(mp -> {
                mp.start();
                refreshHomePlayer();
            });
            mediaPlayer.prepareAsync();
        } catch (IOException | SecurityException e) {
            stopAudio();
            Toast.makeText(this, "Unable to play this audio file.", Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleNoise() {
        if (noisePlaying) {
            stopAudio();
            refreshHomePlayer();
            showMusic();
            return;
        }
        stopAudio();
        int sampleRate = 22050;
        short[] samples = new short[sampleRate * 2];
        java.util.Random random = new java.util.Random();
        for (int i = 0; i < samples.length; i++) samples[i] = (short) (random.nextInt(12000) - 6000);
        try {
            noisePlayer = new AudioTrack(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(),
                    new AudioFormat.Builder().setSampleRate(sampleRate).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
                    samples.length * 2, AudioTrack.MODE_STATIC, 0);
            noisePlayer.write(samples, 0, samples.length);
            noisePlayer.setLoopPoints(0, samples.length, -1);
            noisePlayer.play();
            noisePlaying = true;
            refreshHomePlayer();
            showMusic();
        } catch (IllegalArgumentException e) {
            stopAudio();
            Toast.makeText(this, "White noise could not be started on this device.", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isAudioPlaying() {
        return noisePlaying && noisePlayer != null && noisePlayer.getPlayState() == AudioTrack.PLAYSTATE_PLAYING
                || mediaPlayer != null && mediaPlayer.isPlaying();
    }

    private void togglePlayback() {
        if (noisePlaying) {
            if (noisePlayer != null) {
                if (noisePlayer.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) noisePlayer.pause();
                else noisePlayer.play();
            }
        } else if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) mediaPlayer.pause();
                else mediaPlayer.start();
            } catch (IllegalStateException e) {
                if (playingTrack >= 0) playTrack(playingTrack);
            }
        } else if (!tracks.isEmpty()) {
            playTrack(0);
        } else {
            requestAudioPermission();
        }
        refreshHomePlayer();
    }

    private void playPrevious() {
        if (tracks.isEmpty()) {
            requestAudioPermission();
            return;
        }
        int position = queue.indexOf(playingTrack);
        if (position > 0) playTrack(queue.get(position - 1));
        else if (!queue.isEmpty()) playTrack(queue.get(queue.size() - 1));
        else playTrack(0);
    }

    private void playNext() {
        if (tracks.isEmpty()) {
            requestAudioPermission();
            return;
        }
        int position = queue.indexOf(playingTrack);
        if (queue.size() > 1) playTrack(queue.get((position + 1 + queue.size()) % queue.size()));
        else if (!tracks.isEmpty()) playTrack(0);
    }

    private void stopAudio() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
            } catch (IllegalStateException ignored) { }
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (noisePlayer != null) {
            noisePlayer.release();
            noisePlayer = null;
        }
        noisePlaying = false;
        playingTrack = -1;
        saveQueue();
    }

    private void releaseAudio() {
        stopAudio();
    }

    private String currentTrackTitle() {
        if (noisePlaying) return "White noise";
        if (playingTrack >= 0 && playingTrack < tracks.size()) return tracks.get(playingTrack).title;
        return "Nothing playing";
    }

    private String currentTrackArtist() {
        if (noisePlaying) return "Study ambience";
        if (playingTrack >= 0 && playingTrack < tracks.size()) return tracks.get(playingTrack).artist;
        return "Choose a track";
    }

    private void refreshHomePlayer() {
        if (playerTitle != null) playerTitle.setText(currentTrackTitle());
        if (playerArtist != null) playerArtist.setText(currentTrackArtist());
        if (playerArtwork != null) {
            if (playingTrack >= 0 && playingTrack < tracks.size()) {
                requestArtwork(tracks.get(playingTrack), playerArtwork);
            } else {
                playerArtwork.setImageDrawable(null);
                playerArtwork.setBackground(makeArtworkBackground());
            }
        }
        if (playerPlayButton != null) {
            boolean playing = isAudioPlaying();
            playerPlayButton.setIconResource(playing ? R.drawable.ic_pause : R.drawable.ic_play_arrow);
            playerPlayButton.setContentDescription(playing ? "Pause music" : "Play music");
        }
        if (readerPlayerLabel != null) readerPlayerLabel.setText(getString(
                R.string.track_artist_status, currentTrackTitle(), currentTrackArtist()));
        if (readerPlayerButton != null) {
            boolean playing = isAudioPlaying();
            readerPlayerButton.setIconResource(playing ? R.drawable.ic_pause : R.drawable.ic_play_arrow);
            readerPlayerButton.setContentDescription(playing ? "Pause music" : "Play music");
        }
        refreshMiniPlayer();
    }

    private void refreshMiniPlayer() {
        if (miniPlayerTitle != null) miniPlayerTitle.setText(currentTrackTitle());
        if (miniPlayerArtist != null) miniPlayerArtist.setText(currentTrackArtist());
        if (miniPlayerArtwork != null) {
            if (playingTrack >= 0 && playingTrack < tracks.size()) {
                requestArtwork(tracks.get(playingTrack), miniPlayerArtwork);
            } else {
                miniPlayerArtwork.setImageDrawable(null);
                miniPlayerArtwork.setBackground(makeArtworkBackground());
            }
        }
        if (miniPlayerPlayButton != null) {
            boolean playing = isAudioPlaying();
            miniPlayerPlayButton.setIconResource(playing ? R.drawable.ic_pause : R.drawable.ic_play_arrow);
            miniPlayerPlayButton.setContentDescription(playing ? "Pause music" : "Play music");
        }
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
        int displayedPdfs = Math.min(pdfs.size(), visiblePdfCount);
        for (int pdfIndex = 0; pdfIndex < displayedPdfs; pdfIndex++) {
            PdfItem pdf = pdfs.get(pdfIndex);
            MaterialCardView item = card();
            LinearLayout details = row();
            details.setPadding(dp(10), dp(9), dp(8), dp(9));
            ImageView thumbnail = new ImageView(this);
            thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumbnail.setBackground(makeArtworkBackground());
            thumbnail.setTag(pdf.uri);
            LinearLayout.LayoutParams thumbnailParams = new LinearLayout.LayoutParams(dp(42), dp(54));
            details.addView(thumbnail, thumbnailParams);
            TextView name = text(pdf.title, 13, Color.WHITE);
            name.setMaxLines(2);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            int savedPage = prefs.getInt("pdf_page:" + pdf.uri, 0);
            boolean hasProgress = prefs.contains("pdf_page:" + pdf.uri);
            LinearLayout textDetails = column();
            textDetails.setGravity(Gravity.CENTER_VERTICAL);
            textDetails.addView(name);
            TextView progress = text(hasProgress ? "Page " + (savedPage + 1) : "Not started", 11, Color.LTGRAY);
            LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(-1, -2);
            progressParams.topMargin = dp(4);
            textDetails.addView(progress, progressParams);
            int pageCount = prefs.getInt("pdf_pages:" + pdf.uri, 0);
            if (hasProgress && pageCount > 0) {
                progress.setText(getString(R.string.pdf_page_progress, savedPage + 1, pageCount));
                ProgressBar progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
                progressBar.setMax(100);
                progressBar.setProgress(Math.min(100, (savedPage + 1) * 100 / pageCount));
                progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.rgb(34, 158, 217)));
                LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(-1, dp(3));
                barParams.topMargin = dp(5);
                textDetails.addView(progressBar, barParams);
            }
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
                                        prefs.edit().remove("pdf_page:" + pdf.uri).remove("pdf_pages:" + pdf.uri).apply();
                                        savePdfList();
                                        showPdfLibrary();
                                    }).show();
                        }).show();
                return true;
            });
        }
        if (pdfs.size() > displayedPdfs) {
            MaterialButton loadMore = button("Load more");
            loadMore.setMinHeight(dp(44));
            loadMore.setOnClickListener(v -> {
                visiblePdfCount += LIBRARY_PAGE_SIZE;
                showPdfLibrary();
            });
            LinearLayout.LayoutParams moreParams = new LinearLayout.LayoutParams(-1, dp(44));
            moreParams.topMargin = dp(8);
            list.addView(loadMore, moreParams);
        }
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
                    Set<String> savedUris = new HashSet<>();
                    for (PdfItem saved : pdfs) savedUris.add(saved.uri.toString());
                    for (PdfItem pdf : found) {
                        if (savedUris.add(pdf.uri.toString())) {
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
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQUEST_PDF || requestCode == REQUEST_PDF_RECONNECT) {
            persistReadPermission(uri);
            String reconnectUri = prefs.getString("pdf_reconnect_uri", "");
            if (requestCode == REQUEST_PDF_RECONNECT && !reconnectUri.isEmpty()) {
                PdfItem previous = null;
                for (PdfItem pdf : pdfs) {
                    if (pdf.uri.toString().equals(reconnectUri)) {
                        previous = pdf;
                        break;
                    }
                }
                if (previous != null) {
                    pdfs.remove(previous);
                    PdfItem restored = new PdfItem(previous.title, uri);
                    pdfs.add(0, restored);
                    android.content.SharedPreferences.Editor editor = prefs.edit();
                    String oldUri = previous.uri.toString();
                    int page = prefs.getInt("pdf_page:" + oldUri, 0);
                    int pageCount = prefs.getInt("pdf_pages:" + oldUri, 0);
                    if (prefs.contains("pdf_page:" + oldUri)) editor.putInt("pdf_page:" + uri, page);
                    if (prefs.contains("pdf_pages:" + oldUri)) editor.putInt("pdf_pages:" + uri, pageCount);
                    editor.remove("pdf_page:" + oldUri).remove("pdf_pages:" + oldUri)
                            .remove("pdf_reconnect_uri").apply();
                } else {
                    prefs.edit().remove("pdf_reconnect_uri").apply();
                    pdfs.add(0, new PdfItem(getDisplayName(uri), uri));
                }
                savePdfList();
                openPdf(pdfs.get(0));
                return;
            }
            for (PdfItem pdf : pdfs) if (pdf.uri.equals(uri)) {
                openPdf(pdf);
                return;
            }
            PdfItem pdf = new PdfItem(getDisplayName(uri), uri);
            pdfs.add(0, pdf);
            savePdfList();
            openPdf(pdf);
        } else if (requestCode == REQUEST_AUDIO) {
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException e) {
                Toast.makeText(this, "This provider may not keep audio access after Focus closes.", Toast.LENGTH_LONG).show();
            }
            int index = -1;
            for (int i = 0; i < tracks.size(); i++) if (tracks.get(i).uri.equals(uri)) index = i;
            if (index < 0) {
                tracks.add(readTrackMetadata(uri));
                index = tracks.size() - 1;
                saveTrackList();
            }
            playTrack(index);
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
        if (pdfs.isEmpty()) return "Browse";
        String lastUri = prefs.getString("last_pdf_uri", "");
        for (PdfItem pdf : pdfs) {
            if (pdf.uri.toString().equals(lastUri)) {
                String key = "pdf_page:" + pdf.uri;
                return prefs.contains(key) ? "Page " + (prefs.getInt(key, 0) + 1) : "Open";
            }
        }
        return prefs.contains("pdf_page:" + pdfs.get(0).uri)
                ? "Page " + (prefs.getInt("pdf_page:" + pdfs.get(0).uri, 0) + 1) : "Open";
    }

    @SuppressLint("SourceLockedOrientationActivity")
    private void openPdf(PdfItem pdf) {
        AppDiagnostics.record("PDF_OPEN_REQUEST api=" + Build.VERSION.SDK_INT);
        isHome = false;
        activeScreen = "PDF reader";
        closePdf();
        activePdf = pdf;
        prefs.edit().putString("last_pdf_uri", pdf.uri.toString()).apply();
        currentPage = Math.max(0, prefs.getInt("pdf_page:" + pdf.uri, 0));
        pageRotated = Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1
                || prefs.getBoolean("pdf_rotate:" + pdf.uri, false);
        readerControlsVisible = true;
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        readerRoot = new FrameLayout(this);
        readerRoot.setBackgroundColor(Color.rgb(17, 19, 24));
        LinearLayout reader = column();
        reader.setPadding(dp(8), dp(6), dp(8), dp(8));
        readerControls = row();
        readerControls.setGravity(Gravity.CENTER_VERTICAL);
        readerControls.setPadding(dp(3), 0, dp(3), 0);
        readerNavbar = new HorizontalScrollView(this);
        readerNavbar.setHorizontalScrollBarEnabled(false);
        readerNavbar.setFillViewport(false);
        readerNavbar.setBackgroundColor(Color.rgb(29, 32, 40));
        readerNavbar.setElevation(dp(3));
        readerNavbar.addView(readerControls);
        readerTimerLabel = text("", 12, Color.WHITE);
        readerControls.addView(readerTimerLabel);
        readerTimerButton = iconButton(timerRunning ? R.drawable.ic_pause : R.drawable.ic_play_arrow,
                timerRunning ? "Pause Pomodoro timer" : "Start Pomodoro timer", this::toggleTimer);
        readerControls.addView(readerTimerButton);
        readerPreviousButton = iconButton(R.drawable.ic_arrow_back, "Previous PDF page",
                () -> renderPage(currentPage - 1));
        readerControls.addView(readerPreviousButton);
        readerPageLabel = text("", 12, Color.LTGRAY);
        readerPageLabel.setGravity(Gravity.CENTER);
        readerPageLabel.setMaxWidth(dp(150));
        readerPageLabel.setSingleLine(true);
        readerPageLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);
        readerControls.addView(readerPageLabel);
        readerPageSeekBar = new SeekBar(this);
        readerPageSeekBar.setMax(0);
        readerPageSeekBar.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.rgb(34, 158, 217)));
        readerPageSeekBar.setThumbTintList(android.content.res.ColorStateList.valueOf(Color.rgb(85, 196, 161)));
        readerPageSeekBar.setContentDescription("Navigate PDF pages");
        readerPageSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                renderPage(seekBar.getProgress());
            }
        });
        readerControls.addView(readerPageSeekBar, new LinearLayout.LayoutParams(dp(104), dp(48)));
        TextInputEditText pageJump = new TextInputEditText(this);
        pageJump.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        pageJump.setSingleLine(true);
        pageJump.setTextSize(12);
        pageJump.setTextColor(Color.WHITE);
        pageJump.setHint(fa("Page"));
        pageJump.setHintTextColor(Color.GRAY);
        pageJump.setMinWidth(dp(64));
        pageJump.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_GO);
        pageJump.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_GO
                    || event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER) {
                try {
                    int requestedPage = Integer.parseInt(String.valueOf(view.getText())) - 1;
                    if (requestedPage >= 0 && requestedPage < pdfPageCount) {
                        renderPage(requestedPage);
                    } else {
                        Toast.makeText(this, "Enter a page from 1 to "
                                + pdfPageCount, Toast.LENGTH_SHORT).show();
                    }
                } catch (NumberFormatException ignored) {
                    Toast.makeText(this, "Enter a valid page number", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
            return false;
        });
        readerControls.addView(pageJump, new LinearLayout.LayoutParams(dp(72), dp(44)));
        readerPlayerLabel = text(currentTrackTitle(), 12, Color.WHITE);
        readerPlayerLabel.setMaxWidth(dp(130));
        readerPlayerLabel.setSingleLine(true);
        readerPlayerLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);
        readerControls.addView(readerPlayerLabel);
        readerPlayerButton = iconButton(isAudioPlaying() ? R.drawable.ic_pause : R.drawable.ic_play_arrow,
                isAudioPlaying() ? "Pause music" : "Play music", this::togglePlayback);
        readerControls.addView(readerPlayerButton);
        readerNextButton = iconButton(R.drawable.ic_arrow_forward, "Next PDF page",
                () -> renderPage(currentPage + 1));
        readerControls.addView(readerNextButton);
        MaterialButton rotate = iconButton(R.drawable.ic_rotate_right, "Rotate PDF page", () -> {
            pageRotated = !pageRotated;
            prefs.edit().putBoolean("pdf_rotate:" + activePdf.uri, pageRotated).apply();
            renderPage(currentPage);
        });
        readerControls.addView(rotate);
        MaterialButton addTime = iconButton(R.drawable.ic_add, "Add five minutes to the focus timer",
                this::addFiveMinutes);
        readerControls.addView(addTime);
        MaterialButton hide = iconButton(R.drawable.ic_visibility_off, "Hide reader controls",
                this::toggleReaderControls);
        readerControls.addView(hide);
        reader.addView(readerNavbar, new LinearLayout.LayoutParams(-1, dp(56)));
        pdfLoading = new ProgressBar(this);
        LinearLayout.LayoutParams loadingParams = new LinearLayout.LayoutParams(dp(40), dp(40));
        loadingParams.gravity = Gravity.CENTER_HORIZONTAL;
        loadingParams.topMargin = dp(16);
        reader.addView(pdfLoading, loadingParams);
        pdfImage = new PdfPageView();
        pdfImage.setVisibility(View.GONE);
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(-1, 0, 1);
        reader.addView(pdfImage, imageParams);
        readerRoot.addView(reader);
        MaterialButton show = iconButton(R.drawable.ic_visibility, "Show reader controls", this::toggleReaderControls);
        FrameLayout.LayoutParams showParams = new FrameLayout.LayoutParams(dp(48), dp(48),
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        showParams.topMargin = dp(8);
        readerRoot.addView(show, showParams);
        show.setVisibility(View.GONE);
        readerRoot.setTag(show);
        setScreenContent(readerRoot, false);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1) {
            try {
                pdfDescriptor = getContentResolver().openFileDescriptor(pdf.uri, "r");
                if (pdfDescriptor == null) throw new IOException("Unable to open document");
                renderer = new PdfRenderer(pdfDescriptor);
                pdfPageCount = renderer.getPageCount();
                if (pdfPageCount == 0) throw new IOException("PDF has no pages");
                currentPage = Math.min(currentPage, pdfPageCount - 1);
                prefs.edit().putInt("pdf_pages:" + pdf.uri, pdfPageCount).apply();
                AppDiagnostics.record("PDF_RENDERER_OPENED pages=" + pdfPageCount);
                renderPage(currentPage);
            } catch (IOException | RuntimeException | OutOfMemoryError e) {
                AppDiagnostics.record("PDF_OPEN_FAILURE type=" + e.getClass().getSimpleName());
                android.util.Log.e("FocusPdf", "Unable to open PDF with Android 7 renderer.", e);
                closePdf();
                Toast.makeText(this, "بازکردن این PDF ممکن نشد؛ گزارش خطا را از تنظیمات ارسال کنید.",
                        Toast.LENGTH_LONG).show();
                showPdfLibrary();
            }
            return;
        }
        int requestId = pdfRenderRequestId.incrementAndGet();
        pdfExecutor.execute(() -> {
            File stagedFile = null;
            ParcelFileDescriptor descriptor = null;
            PdfRenderer openedRenderer = null;
            try {
                stagedFile = copyPdfToCache(pdf.uri);
                descriptor = ParcelFileDescriptor.open(stagedFile, ParcelFileDescriptor.MODE_READ_ONLY);
                openedRenderer = new PdfRenderer(descriptor);
                int pageCount = openedRenderer.getPageCount();
                if (pageCount == 0) throw new IOException("PDF has no pages");
                boolean attached;
                synchronized (pdfLock) {
                    attached = requestId == pdfRenderRequestId.get() && !isFinishing() && !isDestroyed();
                    if (attached) {
                        pdfDescriptor = descriptor;
                        renderer = openedRenderer;
                        activePdfFile = stagedFile;
                        pdfPageCount = pageCount;
                    }
                }
                if (!attached) {
                    closeUnattachedPdf(openedRenderer, descriptor);
                    deleteStagedPdf(stagedFile);
                    return;
                }
                currentPage = Math.min(currentPage, pageCount - 1);
                prefs.edit().putInt("pdf_pages:" + pdf.uri, pageCount).apply();
                AppDiagnostics.record("PDF_RENDERER_OPENED pages=" + pageCount);
                renderPage(currentPage);
            } catch (SecurityException e) {
                AppDiagnostics.record("PDF_OPEN_FAILURE type=SecurityException");
                closeUnattachedPdf(openedRenderer, descriptor);
                deleteStagedPdf(stagedFile);
                runOnUiThread(() -> {
                    if (requestId == pdfRenderRequestId.get() && !isFinishing()) {
                        closePdf();
                        promptReconnectPdf(pdf);
                    }
                });
            } catch (IOException | RuntimeException | OutOfMemoryError e) {
                AppDiagnostics.record("PDF_OPEN_FAILURE type=" + e.getClass().getSimpleName());
                closeUnattachedPdf(openedRenderer, descriptor);
                deleteStagedPdf(stagedFile);
                android.util.Log.e("FocusPdf", "Unable to open PDF: " + pdf.uri, e);
                runOnUiThread(() -> {
                    if (requestId == pdfRenderRequestId.get() && !isFinishing()) {
                        closePdf();
                        Toast.makeText(this, "این فایل PDF باز نشد؛ فایل دیگری را امتحان کنید.",
                                Toast.LENGTH_LONG).show();
                        showPdfLibrary();
                    }
                });
            }
        });
    }

    private void closeUnattachedPdf(@Nullable PdfRenderer openedRenderer,
                                    @Nullable ParcelFileDescriptor descriptor) {
        if (openedRenderer != null) {
            try {
                openedRenderer.close();
            } catch (RuntimeException e) {
                android.util.Log.w("FocusPdf", "Unable to close PDF renderer cleanly.", e);
            }
        }
        if (descriptor != null) {
            try {
                descriptor.close();
            } catch (IOException | RuntimeException e) {
                android.util.Log.w("FocusPdf", "Unable to close PDF file descriptor.", e);
            }
        }
    }

    private void promptReconnectPdf(PdfItem pdf) {
        prefs.edit().putString("pdf_reconnect_uri", pdf.uri.toString()).apply();
        new MaterialAlertDialogBuilder(this)
                .setTitle("Reconnect this PDF")
                .setMessage("Android revoked access to \"" + pdf.title
                        + "\" after the app was closed. Choose the same file again; your reading page will be kept.")
                .setNegativeButton("Later", (dialog, which) -> showPdfLibrary())
                .setPositiveButton("Choose PDF", (dialog, which) -> {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/pdf");
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                    startActivityForResult(intent, REQUEST_PDF_RECONNECT);
                })
                .setCancelable(false)
                .show();
    }

    private void persistReadPermission(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            Toast.makeText(this, "This file provider does not allow persistent access after reopening.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void renderPage(int pageNumber) {
        if (pdfImage == null || pageNumber < 0 || pageNumber >= pdfPageCount) return;
        final PdfRenderer pageRenderer;
        pageRenderer = renderer;
        if (pageRenderer == null) return;
        currentPage = pageNumber;
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1) {
            renderLegacyPdfPage(pageRenderer, pageNumber);
            return;
        }
        int requestId = pdfRenderRequestId.incrementAndGet();
        boolean rotate = pageRotated;
        Uri pdfUri = activePdf == null ? null : activePdf.uri;
        pdfExecutor.execute(() -> {
            Bitmap display = null;
            try {
                if (requestId != pdfRenderRequestId.get() || renderer != pageRenderer) return;
                try (PdfRenderer.Page page = pageRenderer.openPage(pageNumber)) {
                    float scale = Math.min(2f, 1200f / Math.max(page.getWidth(), page.getHeight()));
                    int width = Math.max(1, Math.round(page.getWidth() * scale));
                    int height = Math.max(1, Math.round(page.getHeight() * scale));
                    Bitmap original = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    display = original;
                    original.eraseColor(Color.WHITE);
                    page.render(original, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    if (rotate) {
                        Matrix matrix = new Matrix();
                        matrix.postRotate(90);
                        display = Bitmap.createBitmap(original, 0, 0, original.getWidth(),
                                original.getHeight(), matrix, true);
                        if (display != original) original.recycle();
                    }
                }
                Bitmap renderedBitmap = display;
                runOnUiThread(() -> {
                    if (requestId != pdfRenderRequestId.get() || renderer != pageRenderer
                            || pdfImage == null || isFinishing() || isDestroyed()) {
                        if (!renderedBitmap.isRecycled()) renderedBitmap.recycle();
                        return;
                    }
                    pdfImage.setBitmap(renderedBitmap);
                    pdfImage.setVisibility(View.VISIBLE);
                    if (pdfLoading != null) pdfLoading.setVisibility(View.GONE);
                    updateReaderPageNavigation(pageNumber);
                    if (pdfUri != null) prefs.edit().putInt("pdf_page:" + pdfUri, pageNumber).apply();
                });
            } catch (RuntimeException | OutOfMemoryError e) {
                if (display != null && !display.isRecycled()) display.recycle();
                android.util.Log.e("FocusPdf", "Unable to render PDF page " + (pageNumber + 1), e);
                runOnUiThread(() -> {
                    if (requestId == pdfRenderRequestId.get() && !isFinishing()) {
                        if (pdfLoading != null) pdfLoading.setVisibility(View.GONE);
                        Toast.makeText(this, "این صفحهٔ PDF قابل نمایش نیست.", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void renderLegacyPdfPage(PdfRenderer pageRenderer, int pageNumber) {
        PdfRenderer.Page page = null;
        Bitmap original = null;
        try {
            AppDiagnostics.record("PDF_PAGE_RENDER_START page=" + (pageNumber + 1));
            page = pageRenderer.openPage(pageNumber);
            int width = Math.max(1, page.getWidth() * 2);
            int height = Math.max(1, page.getHeight() * 2);
            float maxDimension = Math.max(width, height);
            if (maxDimension > 1600) {
                width = Math.max(1, Math.round(width * 1600f / maxDimension));
                height = Math.max(1, Math.round(height * 1600f / maxDimension));
            }
            original = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            original.eraseColor(Color.WHITE);
            page.render(original, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            Bitmap display = original;
            if (pageRotated) {
                Matrix matrix = new Matrix();
                matrix.postRotate(90);
                display = Bitmap.createBitmap(original, 0, 0, original.getWidth(),
                        original.getHeight(), matrix, true);
                if (display != original) {
                    original.recycle();
                    original = null;
                }
            }
            pdfImage.setBitmap(display);
            pdfImage.setVisibility(View.VISIBLE);
            if (pdfLoading != null) pdfLoading.setVisibility(View.GONE);
            updateReaderPageNavigation(pageNumber);
            if (activePdf != null) {
                prefs.edit().putInt("pdf_page:" + activePdf.uri, pageNumber).apply();
            }
            AppDiagnostics.record("PDF_PAGE_RENDER_SUCCESS page=" + (pageNumber + 1));
        } catch (RuntimeException | OutOfMemoryError e) {
            if (original != null && !original.isRecycled()) original.recycle();
            AppDiagnostics.record("PDF_PAGE_RENDER_FAILURE page=" + (pageNumber + 1)
                    + " type=" + e.getClass().getSimpleName());
            android.util.Log.e("FocusPdf", "Unable to render PDF page " + (pageNumber + 1), e);
            if (pdfLoading != null) pdfLoading.setVisibility(View.GONE);
            Toast.makeText(this, "نمایش این صفحه ممکن نشد؛ گزارش خطا را از تنظیمات ارسال کنید.",
                    Toast.LENGTH_LONG).show();
        } finally {
            if (page != null) page.close();
        }
    }

    private void updateReaderPageNavigation(int pageNumber) {
        String title = activePdf == null ? "" : activePdf.title;
        if (readerPageLabel != null) {
            readerPageLabel.setText(getString(R.string.pdf_page_status,
                    title, pageNumber + 1, pdfPageCount));
        }
        if (readerPageSeekBar != null) {
            readerPageSeekBar.setMax(Math.max(0, pdfPageCount - 1));
            readerPageSeekBar.setProgress(pageNumber);
        }
        if (readerPreviousButton != null) readerPreviousButton.setEnabled(pageNumber > 0);
        if (readerNextButton != null) readerNextButton.setEnabled(pageNumber < pdfPageCount - 1);
    }

    private void addFiveMinutes() {
        durationMillis += 5 * 60_000L;
        remainingMillis += 5 * 60_000L;
        if (timerRunning) {
            endAtElapsed += 5 * 60_000L;
            prefs.edit().putLong("timer_end_wall", System.currentTimeMillis() + remainingMillis)
                    .putLong("timer_duration", durationMillis).putLong("timer_remaining", remainingMillis).apply();
        } else {
            prefs.edit().putLong("timer_duration", durationMillis).putLong("timer_remaining", remainingMillis).apply();
        }
        updateTimerDisplay();
        Toast.makeText(this, fa("Added 5 minutes to your focus timer."), Toast.LENGTH_SHORT).show();
    }

    private void toggleReaderControls() {
        readerControlsVisible = !readerControlsVisible;
        if (readerNavbar != null) readerNavbar.setVisibility(readerControlsVisible ? View.VISIBLE : View.GONE);
        View show = readerRoot == null ? null : (View) readerRoot.getTag();
        if (show != null) show.setVisibility(readerControlsVisible ? View.GONE : View.VISIBLE);
    }

    private void closePdf() {
        pdfRenderRequestId.incrementAndGet();
        PdfRenderer rendererToClose;
        ParcelFileDescriptor descriptorToClose;
        File fileToDelete;
        synchronized (pdfLock) {
            rendererToClose = renderer;
            descriptorToClose = pdfDescriptor;
            fileToDelete = activePdfFile;
            renderer = null;
            pdfDescriptor = null;
            activePdfFile = null;
            pdfPageCount = 0;
        }
        if (rendererToClose != null || descriptorToClose != null || fileToDelete != null) {
            Runnable closeResources = () -> {
                closeUnattachedPdf(rendererToClose, descriptorToClose);
                deleteStagedPdf(fileToDelete);
            };
            if (pdfExecutor.isShutdown()) closeResources.run();
            else pdfExecutor.execute(closeResources);
        }
        if (pdfImage != null) pdfImage.clearBitmap();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        activePdf = null;
        pdfImage = null;
        pdfLoading = null;
        readerControls = null;
        readerNavbar = null;
        readerTimerLabel = null;
        readerTimerButton = null;
        readerPlayerLabel = null;
        readerPlayerButton = null;
        readerPreviousButton = null;
        readerNextButton = null;
        readerPageSeekBar = null;
        readerPageLabel = null;
        readerRoot = null;
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

    private boolean isGuardEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        ComponentName service = new ComponentName(this, FocusGuardService.class);
        if (enabled == null) return false;
        for (String component : enabled.split(":")) {
            if (service.equals(ComponentName.unflattenFromString(component))) return true;
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_AUDIO_PERMISSION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadTracks();
            showMusic();
        } else if (requestCode == REQUEST_AUDIO_PERMISSION) {
            Toast.makeText(this, "Choose a music file with Add music, or allow library access.", Toast.LENGTH_LONG).show();
            showMusic();
        }
    }

    private static class Track {
        final String title;
        final String artist;
        final String album;
        final Uri uri;
        final long albumId;
        final byte[] embeddedArtwork;
        Track(String title, String artist, String album, Uri uri, long albumId, byte[] embeddedArtwork) {
            this.title = title == null || title.trim().isEmpty() ? "Audio track" : title;
            this.artist = artist == null || artist.trim().isEmpty() || "<unknown>".equals(artist) ? "Unknown artist" : artist;
            this.album = album == null || album.trim().isEmpty() || "<unknown>".equals(album) ? "" : album;
            this.uri = uri;
            this.albumId = albumId;
            this.embeddedArtwork = embeddedArtwork;
        }
        Uri artworkUri() {
            return albumId < 0 ? null : Uri.parse("content://media/external/audio/albumart/" + albumId);
        }
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

        PlannerTask(String id, String title, String details, long dueAt, long remindAt, boolean done) {
            this.id = id;
            this.title = title;
            this.details = details == null ? "" : details;
            this.dueAt = dueAt;
            this.remindAt = remindAt;
            this.done = done;
        }

        PlannerTask withValues(String newTitle, String newDetails, long newDueAt, long newRemindAt) {
            PlannerTask updated = new PlannerTask(id, newTitle, newDetails, newDueAt, newRemindAt, done);
            updated.noteDate = noteDate;
            return updated;
        }
    }

    private final class PdfPageView extends View {
        private final Paint pagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final ScaleGestureDetector scaleDetector;
        private final android.view.GestureDetector gestureDetector;
        private Bitmap bitmap;
        private float zoom = 1f;
        private float offsetX;
        private float offsetY;
        private float rotation;
        private float lastX;
        private float lastY;
        private float lastAngle;
        private float downX;
        private float downY;
        private boolean multiTouch;

        PdfPageView() {
            super(MainActivity.this);
            setBackgroundColor(Color.rgb(17, 19, 24));
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            scaleDetector = new ScaleGestureDetector(MainActivity.this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScale(ScaleGestureDetector detector) {
                    float oldZoom = zoom;
                    float newZoom = Math.max(1f, Math.min(6f, oldZoom * detector.getScaleFactor()));
                    if (bitmap == null || bitmap.isRecycled()) return false;
                    float fit = fitScale();
                    float anchorX = (detector.getFocusX() - getWidth() / 2f - offsetX) / (fit * oldZoom);
                    float anchorY = (detector.getFocusY() - getHeight() / 2f - offsetY) / (fit * oldZoom);
                    zoom = newZoom;
                    offsetX = detector.getFocusX() - getWidth() / 2f - anchorX * fit * zoom;
                    offsetY = detector.getFocusY() - getHeight() / 2f - anchorY * fit * zoom;
                    clampOffset();
                    invalidate();
                    return true;
                }
            });
            gestureDetector = new android.view.GestureDetector(MainActivity.this,
                    new android.view.GestureDetector.SimpleOnGestureListener() {
                        @Override
                        public boolean onDoubleTap(MotionEvent event) {
                            float oldZoom = zoom;
                            zoom = zoom > 1.05f ? 1f : 2.5f;
                            if (zoom == 1f) {
                                offsetX = 0;
                                offsetY = 0;
                            } else {
                                float fit = fitScale();
                                float anchorX = (event.getX() - getWidth() / 2f - offsetX) / (fit * oldZoom);
                                float anchorY = (event.getY() - getHeight() / 2f - offsetY) / (fit * oldZoom);
                                offsetX = event.getX() - getWidth() / 2f - anchorX * fit * zoom;
                                offsetY = event.getY() - getHeight() / 2f - anchorY * fit * zoom;
                                clampOffset();
                            }
                            invalidate();
                            return true;
                        }
                    });
        }

        void setBitmap(Bitmap newBitmap) {
            if (bitmap != null && bitmap != newBitmap && !bitmap.isRecycled()) bitmap.recycle();
            bitmap = newBitmap;
            zoom = 1f;
            offsetX = 0;
            offsetY = 0;
            rotation = 0;
            invalidate();
        }

        private float fitScale() {
            if (bitmap == null || bitmap.isRecycled()) return 1f;
            return Math.min((float) getWidth() / bitmap.getWidth(), (float) getHeight() / bitmap.getHeight());
        }

        private void clampOffset() {
            if (zoom <= 1f) {
                offsetX = 0;
                offsetY = 0;
                return;
            }
            float scaledWidth = bitmap.getWidth() * fitScale() * zoom;
            float scaledHeight = bitmap.getHeight() * fitScale() * zoom;
            float horizontalLimit = Math.max(0, (scaledWidth - getWidth()) / 2f);
            float verticalLimit = Math.max(0, (scaledHeight - getHeight()) / 2f);
            offsetX = Math.max(-horizontalLimit, Math.min(horizontalLimit, offsetX));
            offsetY = Math.max(-verticalLimit, Math.min(verticalLimit, offsetY));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (bitmap == null || bitmap.isRecycled()) return;
            float fit = fitScale();
            canvas.save();
            canvas.translate(getWidth() / 2f + offsetX, getHeight() / 2f + offsetY);
            canvas.rotate(rotation);
            canvas.scale(fit * zoom, fit * zoom);
            canvas.drawBitmap(bitmap, -bitmap.getWidth() / 2f, -bitmap.getHeight() / 2f, pagePaint);
            canvas.restore();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            scaleDetector.onTouchEvent(event);
            gestureDetector.onTouchEvent(event);
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastX = downX = event.getX();
                    lastY = downY = event.getY();
                    multiTouch = false;
                    return true;
                case MotionEvent.ACTION_POINTER_DOWN:
                    multiTouch = true;
                    lastAngle = pointerAngle(event);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (event.getPointerCount() >= 2) {
                        float angle = pointerAngle(event);
                        float delta = angle - lastAngle;
                        while (delta > 180) delta -= 360;
                        while (delta < -180) delta += 360;
                        rotation += delta;
                        lastAngle = angle;
                        multiTouch = true;
                    } else if (!scaleDetector.isInProgress()) {
                        offsetX += event.getX() - lastX;
                        offsetY += event.getY() - lastY;
                        clampOffset();
                    }
                    lastX = event.getX();
                    lastY = event.getY();
                    invalidate();
                    return true;
                case MotionEvent.ACTION_POINTER_UP:
                    multiTouch = true;
                    if (event.getPointerCount() > 2) lastAngle = pointerAngle(event, event.getActionIndex());
                    int remainingPointer = event.getActionIndex() == 0 ? 1 : 0;
                    lastX = event.getX(remainingPointer);
                    lastY = event.getY(remainingPointer);
                    return true;
                case MotionEvent.ACTION_UP:
                    float dx = event.getX() - downX;
                    float dy = event.getY() - downY;
                    if (!multiTouch && zoom <= 1.05f && Math.abs(dx) > dp(72) && Math.abs(dx) > Math.abs(dy)) {
                        renderPage(currentPage + (dx < 0 ? 1 : -1));
                    } else if (!multiTouch && Math.abs(dx) < dp(8) && Math.abs(dy) < dp(8) && !readerControlsVisible) {
                        toggleReaderControls();
                    }
                    performClick();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    return true;
            }
            return true;
        }

        private float pointerAngle(MotionEvent event) {
            return pointerAngle(event, -1);
        }

        private float pointerAngle(MotionEvent event, int skipIndex) {
            int first = -1;
            int second = -1;
            for (int i = 0; i < event.getPointerCount(); i++) {
                if (i == skipIndex) continue;
                if (first < 0) first = i;
                else {
                    second = i;
                    break;
                }
            }
            if (first < 0 || second < 0) return lastAngle;
            return (float) Math.toDegrees(Math.atan2(event.getY(second) - event.getY(first),
                    event.getX(second) - event.getX(first)));
        }

        void clearBitmap() {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
            bitmap = null;
            invalidate();
        }

        @Override
        public boolean performClick() {
            super.performClick();
            return true;
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

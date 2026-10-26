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
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.HorizontalScrollView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
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
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_AUDIO_PERMISSION = 10;
    private static final int REQUEST_PDF = 11;
    private static final int REQUEST_AUDIO = 12;
    private static final int REQUEST_PDF_FOLDER = 13;
    private static final String PREFS = "focus_data";
    private static final String PDF_LIST = "pdf_library";
    private static final String BLOCKED_APPS = "blocked_apps";
    private static final long TICK_MS = 1000;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Track> tracks = new ArrayList<>();
    private final List<Integer> queue = new ArrayList<>();
    private final List<PdfItem> pdfs = new ArrayList<>();
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
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
    private TextInputEditText musicSearch;
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
    private PdfRenderer renderer;
    private ParcelFileDescriptor pdfDescriptor;
    private PdfItem activePdf;
    private int currentPage;
    private boolean pageRotated = true;
    private boolean readerControlsVisible = true;
    private boolean isHome = true;
    private LinearLayout readerControls;
    private HorizontalScrollView readerNavbar;
    private FrameLayout readerRoot;
    private PdfPageView pdfImage;

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
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
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
        buildHome();
        if (timerRunning) handler.post(timerTick);
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
    protected void onPause() {
        super.onPause();
        if (timerRunning) {
            remainingMillis = Math.max(0, endAtElapsed - SystemClock.elapsedRealtime());
            prefs.edit().putLong("timer_end_wall", System.currentTimeMillis() + remainingMillis).apply();
        } else if (prefs != null) {
            prefs.edit().putLong("timer_remaining", remainingMillis).apply();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(timerTick);
        ioExecutor.shutdownNow();
        releaseAudio();
        closePdf();
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
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(textColor);
        return view;
    }

    private MaterialButton button(String label) {
        MaterialButton button = new MaterialButton(this);
        button.setText(label);
        button.setTextSize(14);
        button.setCornerRadius(dp(14));
        return button;
    }

    private MaterialButton iconButton(int icon, String description, Runnable action) {
        MaterialButton button = new MaterialButton(this);
        button.setText("");
        button.setIconResource(icon);
        button.setIconTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        button.setIconSize(dp(22));
        button.setIconPadding(0);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setMinWidth(dp(44));
        button.setMinimumWidth(dp(44));
        button.setMinHeight(dp(44));
        button.setMinimumHeight(dp(44));
        button.setPadding(0, 0, 0, 0);
        button.setCornerRadius(dp(24));
        button.setContentDescription(description);
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
        heading.setTypeface(null, Typeface.BOLD);
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
        setContentView(root);
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
        content.setPadding(0, dp(8), 0, dp(8));
        LinearLayout brand = row();
        TextView greeting = text("FOCUS", 13, Color.rgb(166, 161, 255));
        greeting.setLetterSpacing(0.18f);
        brand.addView(greeting, new LinearLayout.LayoutParams(0, -2, 1));
        MaterialButton settings = iconButton(R.drawable.ic_settings,
                "Pomodoro and focus guard settings", this::showPomodoroSettings);
        brand.addView(settings);
        content.addView(brand);
        TextView title = text("Make room for what matters.", 22, Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams titleMargin = new LinearLayout.LayoutParams(-1, -2);
        titleMargin.topMargin = dp(6);
        titleMargin.bottomMargin = dp(14);
        content.addView(title, titleMargin);

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
        timerText.setContentDescription("Time remaining in focus session");
        clock.addView(timerText, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams clockParams = new LinearLayout.LayoutParams(-1, dp(166));
        clockParams.topMargin = dp(2);
        timerContent.addView(clock, clockParams);
        TextView duration = text("Pomodoro settings", 13, Color.LTGRAY);
        timerContent.addView(duration);
        duration.setOnClickListener(v -> showPomodoroSettings());
        timerContent.setOnClickListener(v -> showPomodoroSettings());
        timerButton = button("");
        timerButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(108, 99, 255)));
        LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(dp(170), dp(52));
        startParams.topMargin = dp(10);
        timerContent.addView(timerButton, startParams);
        timerButton.setOnClickListener(v -> toggleTimer());
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

        LinearLayout actions = row();
        actions.setGravity(Gravity.CENTER);
        addAction(actions, R.drawable.ic_edit, "Notes", this::showNotes);
        addAction(actions, R.drawable.ic_music_note, "Music", this::showMusic);
        addAction(actions, R.drawable.ic_picture_as_pdf, "PDFs", this::showPdfLibrary);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(-1, -2);
        actionsParams.bottomMargin = dp(12);
        content.addView(actions, actionsParams);

        LinearLayout bottom = column();
        MaterialCardView recent = card();
        LinearLayout recentContent = row();
        recentContent.setPadding(dp(15), dp(13), dp(15), dp(13));
        ImageView pdfIcon = new ImageView(this);
        pdfIcon.setImageResource(R.drawable.ic_picture_as_pdf);
        pdfIcon.setImageTintList(android.content.res.ColorStateList.valueOf(Color.rgb(166, 161, 255)));
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
        recentContent.addView(text(lastPdfStatus, 12, Color.rgb(166, 161, 255)));
        recent.addView(recentContent);
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

    private void addAction(LinearLayout parent, int icon, String label, Runnable action) {
        LinearLayout item = column();
        item.setGravity(Gravity.CENTER);
        item.setContentDescription(label);
        ImageView circle = new ImageView(this);
        circle.setImageResource(icon);
        circle.setImageTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setColor(Color.rgb(47, 51, 62));
        shape.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        circle.setBackground(shape);
        circle.setContentDescription(label);
        item.addView(circle, new LinearLayout.LayoutParams(dp(50), dp(50)));
        TextView caption = text(label, 13, Color.LTGRAY);
        LinearLayout.LayoutParams captionParams = new LinearLayout.LayoutParams(-2, -2);
        captionParams.topMargin = dp(7);
        item.addView(caption, captionParams);
        item.setOnClickListener(v -> action.run());
        parent.addView(item, new LinearLayout.LayoutParams(0, -2, 1));
    }

    private android.graphics.drawable.Drawable makeArtworkBackground() {
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(108, 99, 255), Color.rgb(55, 177, 161)});
        drawable.setCornerRadius(dp(8));
        return drawable;
    }

    private void updateTimerDisplay() {
        if (timerText == null || timerButton == null || timerProgress == null) return;
        long seconds = Math.max(0, (remainingMillis + 999) / 1000);
        timerText.setText(String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60));
        timerProgress.setProgress(remainingMillis, durationMillis);
        timerButton.setText(timerRunning ? "Pause" : remainingMillis < durationMillis ? "Resume" : "Start");
        if (timerStatus != null) timerStatus.setText(phaseTitle(timerPhase).toUpperCase(Locale.getDefault()));
        if (statsSummary != null) statsSummary.setText(todayStatsText());
        if (readerTimerLabel != null) readerTimerLabel.setText(getString(
                R.string.reader_timer_status, phaseTitle(timerPhase), timerText.getText()));
        if (readerTimerButton != null) {
            readerTimerButton.setIconResource(timerRunning ? R.drawable.ic_pause : R.drawable.ic_play_arrow);
            readerTimerButton.setContentDescription(timerRunning ? "Pause Pomodoro timer" : "Resume Pomodoro timer");
        }
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
        showScreen("Pomodoro settings", scroll(body));
    }

    private TextInputEditText settingsField(String hint, int value) {
        TextInputEditText input = new TextInputEditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setSingleLine(true);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.GRAY);
        input.setHint(hint);
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
        if ("long_break".equals(phase)) return "Long break";
        if ("short_break".equals(phase)) return "Short break";
        return "Study session";
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
        prefs.edit().putString("timer_phase", timerPhase).putLong("timer_duration", durationMillis)
                .putLong("timer_remaining", remainingMillis).remove("timer_end_wall")
                .putBoolean("focus_guard_active", false).apply();
        updateTimerDisplay();
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
        return "TODAY  ·  " + sessions + "/" + goal + " pomodoros  ·  "
                + (hours > 0 ? hours + "h " : "") + minutes + "m study";
    }

    private void appendPomodoroNote() {
        String key = noteKey(todayKey());
        String old = prefs.getString(key, "");
        String entry = "• Pomodoro completed — " + new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date());
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
        TextView noteHeading = text("Your days", 15, Color.WHITE);
        notesToolbar.addView(noteHeading, new LinearLayout.LayoutParams(0, -2, 1));
        MaterialButton addToday = button("+ Today");
        addToday.setMinHeight(dp(40));
        addToday.setOnClickListener(v -> showNoteEditor(todayKey()));
        notesToolbar.addView(addToday);
        list.addView(notesToolbar);
        TextView hint = text("One note for each day · changes save as you type", 12, Color.LTGRAY);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2);
        hintParams.topMargin = dp(4);
        hintParams.bottomMargin = dp(10);
        list.addView(hint, hintParams);
        List<String> dates = new ArrayList<>();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            if (entry.getKey().startsWith("note:")) dates.add(entry.getKey().substring(5));
        }
        if (!dates.contains(todayKey())) dates.add(todayKey());
        Collections.sort(dates, Collections.reverseOrder());
        for (String date : dates) {
            MaterialCardView noteCard = card();
            LinearLayout noteContent = row();
            noteContent.setPadding(dp(14), dp(12), dp(12), dp(12));
            TextView dateBadge = text(new SimpleDateFormat("dd", Locale.getDefault()).format(parseDay(date)),
                    20, Color.rgb(166, 161, 255));
            dateBadge.setGravity(Gravity.CENTER);
            noteContent.addView(dateBadge, new LinearLayout.LayoutParams(dp(42), dp(42)));
            LinearLayout noteDetails = column();
            TextView dateTitle = text(prettyDate(date), 14, Color.WHITE);
            dateTitle.setMaxLines(1);
            dateTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
            noteDetails.addView(dateTitle);
            String note = prefs.getString(noteKey(date), "");
            String preview = note.trim().isEmpty() ? "No note yet · tap to write" : note.trim().replace('\n', ' ');
            TextView previewText = text(preview, 12, Color.LTGRAY);
            previewText.setMaxLines(1);
            previewText.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(-1, -2);
            previewParams.topMargin = dp(4);
            noteDetails.addView(previewText, previewParams);
            LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(0, -2, 1);
            detailsParams.leftMargin = dp(10);
            noteContent.addView(noteDetails, detailsParams);
            ImageView chevron = new ImageView(this);
            chevron.setImageResource(R.drawable.ic_chevron_right);
            chevron.setImageTintList(android.content.res.ColorStateList.valueOf(Color.LTGRAY));
            noteContent.addView(chevron, new LinearLayout.LayoutParams(dp(24), dp(24)));
            noteCard.addView(noteContent);
            noteCard.setOnClickListener(v -> showNoteEditor(date));
            noteCard.setOnLongClickListener(v -> {
                new MaterialAlertDialogBuilder(this).setTitle(prettyDate(date))
                        .setItems(new String[]{"Open note", "Delete note"}, (dialog, which) -> {
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
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
            cardParams.bottomMargin = dp(7);
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
        TextView dateLabel = text(prettyDate(date), 13, Color.rgb(166, 161, 255));
        editorScreen.addView(dateLabel);
        TextInputEditText editor = new TextInputEditText(this);
        editor.setTextColor(Color.WHITE);
        editor.setHintTextColor(Color.GRAY);
        editor.setHint("Write a plan, capture an idea, or list your tasks…");
        editor.setMinLines(8);
        editor.setGravity(Gravity.TOP);
        editor.setPadding(dp(14), dp(14), dp(14), dp(14));
        editor.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(108, 99, 255)));
        editor.setText(prefs.getString(noteKey(date), ""));
        LinearLayout.LayoutParams editorParams = new LinearLayout.LayoutParams(-1, 0, 1);
        editorParams.topMargin = dp(8);
        editorScreen.addView(editor, editorParams);
        TextView savedStatus = text("Saved automatically", 12, Color.LTGRAY);
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
            return DateFormat.getDateInstance(DateFormat.FULL).format(date);
        } catch (Exception ignored) {
            return value;
        }
    }

    private void showMusic() {
        LinearLayout list = column();
        list.setPadding(0, dp(4), 0, dp(12));
        if (tracks.isEmpty()) loadTracks();
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
        queueButton.setIconTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
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
        musicSearch.setHint("Search tracks, artists, albums");
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
            chip.setMinHeight(dp(34));
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
                renderTrackRows(rows, s.toString());
            }
            @Override public void afterTextChanged(android.text.Editable s) { }
        });
        if (tracks.isEmpty() && !hasAudioPermission()) {
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
        for (int i = 0; i < tracks.size(); i++) {
            final int index = i;
            Track track = tracks.get(i);
            String searchable = (track.title + " " + track.artist + " " + track.album).toLowerCase(Locale.getDefault());
            if (!searchable.contains(needle)) continue;
            matches++;
            MaterialCardView trackCard = card();
            LinearLayout trackRow = row();
            trackRow.setPadding(dp(9), dp(7), dp(5), dp(7));
            ImageView artwork = new ImageView(this);
            artwork.setScaleType(ImageView.ScaleType.CENTER_CROP);
            artwork.setBackground(makeArtworkBackground());
            artwork.setTag(track.uri);
            ioExecutor.execute(() -> {
                Bitmap cover = loadArtwork(track);
                runOnUiThread(() -> {
                    if (cover != null && track.uri.equals(artwork.getTag())) artwork.setImageBitmap(cover);
                });
            });
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
                        showMusic();
                    });
            trackRow.addView(addQueue);
            trackCard.addView(trackRow);
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
                                showMusic();
                            } else if (!track.album.isEmpty() && musicSearch != null) musicSearch.setText(track.album);
                        }).show();
                return true;
            });
        }
        if (matches == 0) target.addView(text("No matching tracks.", 12, Color.LTGRAY));
    }

    private Bitmap loadArtwork(Track track) {
        Uri artworkUri = track.artworkUri();
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            if (track.embeddedArtwork != null) {
                BitmapFactory.decodeByteArray(track.embeddedArtwork, 0, track.embeddedArtwork.length, options);
                options.inSampleSize = sampleSize(options, dp(96));
                options.inJustDecodeBounds = false;
                return BitmapFactory.decodeByteArray(track.embeddedArtwork, 0, track.embeddedArtwork.length, options);
            }
            if (artworkUri == null) return null;
            try (java.io.InputStream bounds = getContentResolver().openInputStream(artworkUri)) {
                if (bounds == null) return null;
                BitmapFactory.decodeStream(bounds, null, options);
            }
            options.inSampleSize = sampleSize(options, dp(96));
            options.inJustDecodeBounds = false;
            try (java.io.InputStream input = getContentResolver().openInputStream(artworkUri)) {
                return input == null ? null : BitmapFactory.decodeStream(input, null, options);
            }
        } catch (Exception ignored) {
            return null;
        }
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
                showQueue();
            });
            row.addView(remove);
            item.addView(row);
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
        Uri collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID};
        try (Cursor cursor = getContentResolver().query(collection, projection,
                MediaStore.Audio.Media.IS_MUSIC + " != 0", null, MediaStore.Audio.Media.TITLE + " COLLATE NOCASE")) {
            if (cursor == null) return;
            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            int titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
            int artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
            int albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
            int albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);
            while (cursor.moveToNext()) {
                Uri uri = ContentUris.withAppendedId(collection, cursor.getLong(idColumn));
                long albumId = cursor.getLong(albumIdColumn);
                if (!containsTrack(uri)) tracks.add(new Track(cursor.getString(titleColumn), cursor.getString(artistColumn),
                        cursor.getString(albumColumn), uri, albumId, null));
            }

        } catch (SecurityException ignored) {
            Toast.makeText(this, "Music library access was not granted.", Toast.LENGTH_SHORT).show();
        }
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
        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(this, tracks.get(index).uri);
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
            Bitmap artwork = playingTrack >= 0 && playingTrack < tracks.size() ? loadArtwork(tracks.get(playingTrack)) : null;
            playerArtwork.setImageBitmap(artwork);
            playerArtwork.setBackground(artwork == null ? makeArtworkBackground() : null);
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
    }

    private void showPdfLibrary() {
        loadSavedPdfs();
        LinearLayout list = column();
        list.setPadding(0, dp(4), 0, dp(12));
        LinearLayout actions = row();
        MaterialButton add = button("Add PDF");
        add.setIconResource(R.drawable.ic_add);
        add.setIconTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        add.setMinHeight(dp(42));
        add.setOnClickListener(v -> pickPdf());
        actions.addView(add, new LinearLayout.LayoutParams(0, dp(42), 1));
        MaterialButton scan = button(scanningPdfs ? "Scanning…" : "Scan folder");
        scan.setMinHeight(dp(42));
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
        for (PdfItem pdf : pdfs) {
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
                progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.rgb(108, 99, 255)));
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
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.topMargin = dp(5);
            list.addView(item, params);
            ioExecutor.execute(() -> {
                Bitmap cover = renderPdfThumbnail(pdf);
                runOnUiThread(() -> {
                    if (!isFinishing() && cover != null && pdf.uri.equals(thumbnail.getTag())) {
                        thumbnail.setImageBitmap(cover);
                    }
                });
            });
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
        try (ParcelFileDescriptor descriptor = getContentResolver().openFileDescriptor(pdf.uri, "r")) {
            if (descriptor == null) return null;
            PdfRenderer thumbnailRenderer = new PdfRenderer(descriptor);
            if (thumbnailRenderer.getPageCount() == 0) {
                thumbnailRenderer.close();
                return null;
            }
            PdfRenderer.Page page = thumbnailRenderer.openPage(0);
            float scale = Math.min(120f / page.getWidth(), 160f / page.getHeight());
            int width = Math.max(1, Math.round(page.getWidth() * scale));
            int height = Math.max(1, Math.round(page.getHeight() * scale));
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(Color.WHITE);
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            page.close();
            thumbnailRenderer.close();
            return bitmap;
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
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
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQUEST_PDF) {
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException e) {
                Toast.makeText(this, "This provider may not keep PDF access after Focus closes.", Toast.LENGTH_LONG).show();
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
        isHome = false;
        activeScreen = "PDF reader";
        closePdf();
        activePdf = pdf;
        prefs.edit().putString("last_pdf_uri", pdf.uri.toString()).apply();
        currentPage = prefs.getInt("pdf_page:" + pdf.uri, 0);
        pageRotated = true;
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
        readerNavbar.addView(readerControls);
        readerTimerLabel = text("", 12, Color.WHITE);
        readerControls.addView(readerTimerLabel);
        readerTimerButton = iconButton(timerRunning ? R.drawable.ic_pause : R.drawable.ic_play_arrow,
                timerRunning ? "Pause Pomodoro timer" : "Start Pomodoro timer", this::toggleTimer);
        readerControls.addView(readerTimerButton);
        readerPageLabel = text("", 12, Color.LTGRAY);
        readerPageLabel.setGravity(Gravity.CENTER);
        readerControls.addView(readerPageLabel);
        readerPlayerLabel = text(currentTrackTitle(), 12, Color.WHITE);
        readerPlayerLabel.setMaxWidth(dp(130));
        readerPlayerLabel.setSingleLine(true);
        readerPlayerLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);
        readerControls.addView(readerPlayerLabel);
        readerPlayerButton = iconButton(isAudioPlaying() ? R.drawable.ic_pause : R.drawable.ic_play_arrow,
                isAudioPlaying() ? "Pause music" : "Play music", this::togglePlayback);
        readerControls.addView(readerPlayerButton);
        MaterialButton prev = iconButton(R.drawable.ic_arrow_back, "Previous PDF page",
                () -> renderPage(currentPage - 1));
        readerControls.addView(prev);
        MaterialButton next = iconButton(R.drawable.ic_arrow_forward, "Next PDF page",
                () -> renderPage(currentPage + 1));
        readerControls.addView(next);
        MaterialButton rotate = iconButton(R.drawable.ic_rotate_right, "Rotate PDF page", () -> {
            pageRotated = !pageRotated;
            renderPage(currentPage);
        });
        readerControls.addView(rotate);
        MaterialButton addTime = iconButton(R.drawable.ic_add, "Add five minutes to the focus timer",
                this::addFiveMinutes);
        readerControls.addView(addTime);
        MaterialButton hide = iconButton(R.drawable.ic_visibility_off, "Hide reader controls",
                this::toggleReaderControls);
        readerControls.addView(hide);
        reader.addView(readerNavbar, new LinearLayout.LayoutParams(-1, dp(48)));
        pdfImage = new PdfPageView();
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
        setScreenContent(readerRoot);
        try {
            pdfDescriptor = getContentResolver().openFileDescriptor(pdf.uri, "r");
            if (pdfDescriptor == null) throw new IOException("Unable to open document");
            renderer = new PdfRenderer(pdfDescriptor);
            if (renderer.getPageCount() == 0) throw new IOException("PDF has no pages");
            currentPage = Math.min(currentPage, renderer.getPageCount() - 1);
            prefs.edit().putInt("pdf_pages:" + pdf.uri, renderer.getPageCount()).apply();
            renderPage(currentPage);
        } catch (IOException | SecurityException e) {
            closePdf();
            Toast.makeText(this, "This PDF is no longer available. Add it to the library again.", Toast.LENGTH_LONG).show();
            showPdfLibrary();
        }
    }

    private void renderPage(int pageNumber) {
        if (renderer == null || pdfImage == null) return;
        if (pageNumber < 0 || pageNumber >= renderer.getPageCount()) return;
        currentPage = pageNumber;
        PdfRenderer.Page page = null;
        try {
            page = renderer.openPage(currentPage);
            int width = Math.max(1, page.getWidth() * 2);
            int height = Math.max(1, page.getHeight() * 2);
            float maxDim = Math.max(width, height);
            if (maxDim > 1600) {
                width = Math.round(width * 1600f / maxDim);
                height = Math.round(height * 1600f / maxDim);
            }
            Bitmap original = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            original.eraseColor(Color.WHITE);
            page.render(original, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            Bitmap display = original;
            if (pageRotated) {
                Matrix matrix = new Matrix();
                matrix.postRotate(90);
                display = Bitmap.createBitmap(original, 0, 0, original.getWidth(), original.getHeight(), matrix, true);
                if (display != original) original.recycle();
            }
            pdfImage.setBitmap(display);
            readerPageLabel.setText(getString(R.string.pdf_page_status, activePdf.title, currentPage + 1, renderer.getPageCount()));
            prefs.edit().putInt("pdf_page:" + activePdf.uri, currentPage).apply();
        } catch (RuntimeException e) {
            Toast.makeText(this, "Unable to render this PDF page.", Toast.LENGTH_SHORT).show();
        } finally {
            if (page != null) page.close();
        }
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
        Toast.makeText(this, "Added 5 minutes to your focus timer.", Toast.LENGTH_SHORT).show();
    }

    private void toggleReaderControls() {
        readerControlsVisible = !readerControlsVisible;
        if (readerNavbar != null) readerNavbar.setVisibility(readerControlsVisible ? View.VISIBLE : View.GONE);
        View show = readerRoot == null ? null : (View) readerRoot.getTag();
        if (show != null) show.setVisibility(readerControlsVisible ? View.GONE : View.VISIBLE);
    }

    private void closePdf() {
        if (renderer != null) {
            renderer.close();
            renderer = null;
        }
        if (pdfDescriptor != null) {
            try {
                pdfDescriptor.close();
            } catch (IOException ignored) { }
            pdfDescriptor = null;
        }
        activePdf = null;
        pdfImage = null;
        readerControls = null;
        readerNavbar = null;
        readerTimerLabel = null;
        readerTimerButton = null;
        readerPlayerLabel = null;
        readerPlayerButton = null;
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
            checkBox.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.rgb(166, 161, 255)));
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
            setLayerType(View.LAYER_TYPE_HARDWARE, null);
            scaleDetector = new ScaleGestureDetector(MainActivity.this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScale(ScaleGestureDetector detector) {
                    float oldZoom = zoom;
                    zoom = Math.max(1f, Math.min(6f, zoom * detector.getScaleFactor()));
                    float factor = zoom / oldZoom;
                    offsetX = detector.getFocusX() - (detector.getFocusX() - offsetX) * factor;
                    offsetY = detector.getFocusY() - (detector.getFocusY() - offsetY) * factor;
                    invalidate();
                    return true;
                }
            });
            gestureDetector = new android.view.GestureDetector(MainActivity.this,
                    new android.view.GestureDetector.SimpleOnGestureListener() {
                        @Override
                        public boolean onDoubleTap(MotionEvent event) {
                            zoom = zoom > 1.05f ? 1f : 2.5f;
                            if (zoom == 1f) {
                                offsetX = 0;
                                offsetY = 0;
                            } else {
                                offsetX = getWidth() / 2f - event.getX();
                                offsetY = getHeight() / 2f - event.getY();
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

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (bitmap == null || bitmap.isRecycled()) return;
            float fit = Math.min((float) getWidth() / bitmap.getWidth(), (float) getHeight() / bitmap.getHeight());
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
            progressPaint.setColor(Color.rgb(108, 99, 255));
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

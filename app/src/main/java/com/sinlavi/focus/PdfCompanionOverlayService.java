package com.sinlavi.focus;

import android.app.Service;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatImageView;

import com.google.android.material.button.MaterialButton;

import java.util.Locale;

public final class PdfCompanionOverlayService extends Service {
    private static final int NOTIFICATION_ID = 6021;
    private static final String NOTIFICATION_CHANNEL = "focus_pdf_controls";
    static final String ACTION_CONTROL = "com.sinlavi.focus.PDF_OVERLAY_CONTROL";
    static final String EXTRA_CONTROL = "control";
    static final String CONTROL_TIMER = "timer";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;
    private WindowManager.LayoutParams windowParams;
    private LinearLayout overlay;
    private LinearLayout panel;
    private TextView phaseLabel;
    private TextView timerStatus;
    private ProgressBar timerProgress;
    private MaterialButton timerToggleButton;
    private ImageView eyeButton;
    private boolean panelVisible;

    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            updateStatus();
            if (overlay != null) handler.postDelayed(this, 1000);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            android.util.Log.e("FocusOverlay", "Overlay permission is missing; companion service stopped.");
            stopSelf();
            return;
        }
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager == null) {
            stopSelf();
            return;
        }
        createOverlay();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!startInForeground()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (overlay == null) stopSelf();
        else {
            updateStatus();
            handler.removeCallbacks(refresh);
            handler.post(refresh);
        }
        return START_NOT_STICKY;
    }

    private boolean startInForeground() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager != null) {
            manager.createNotificationChannel(new NotificationChannel(NOTIFICATION_CHANNEL,
                    "کنترل مطالعه روی PDF", NotificationManager.IMPORTANCE_LOW));
        }
        Intent open = new Intent(this, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content = PendingIntent.getActivity(this, NOTIFICATION_ID, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder notification = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, NOTIFICATION_CHANNEL)
                : new Notification.Builder(this);
        notification.setSmallIcon(R.drawable.ic_focus)
                .setContentTitle("پنل مطالعهٔ Focus فعال است")
                .setContentText("برای کنترل زمان‌سنج هنگام مطالعه روی PDF")
                .setContentIntent(content)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setPriority(Notification.PRIORITY_LOW);
        try {
            startForeground(NOTIFICATION_ID, notification.build());
            return true;
        } catch (SecurityException | IllegalStateException e) {
            android.util.Log.e("FocusOverlay", "Could not keep PDF controls running in foreground.", e);
            return false;
        }
    }

    private void createOverlay() {
        int windowType = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        windowParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                windowType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        windowParams.gravity = Gravity.TOP | Gravity.END;
        windowParams.x = dp(12);
        windowParams.y = dp(96);

        overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setGravity(Gravity.END);
        overlay.setClipChildren(false);
        overlay.setClipToPadding(false);

        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(16), dp(18), dp(16));
        GradientDrawable panelBackground = rounded(Color.rgb(29, 32, 40), dp(20));
        panelBackground.setStroke(dp(1), Color.rgb(65, 73, 91));
        panel.setBackground(panelBackground);
        panel.setElevation(dp(8));
        panel.setVisibility(View.GONE);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = statusText(13, Color.rgb(119, 205, 239));
        title.setText("دستیار مطالعهٔ فوکِس");
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        ImageView close = new AccessibleImageView(this);
        close.setImageResource(R.drawable.ic_close);
        close.setImageTintList(android.content.res.ColorStateList.valueOf(Color.LTGRAY));
        close.setContentDescription("بستن پنل");
        close.setPadding(dp(8), dp(8), dp(8), dp(8));
        header.addView(close, new LinearLayout.LayoutParams(dp(40), dp(40)));
        close.setOnClickListener(v -> {
            panelVisible = false;
            panel.setVisibility(View.GONE);
            eyeButton.setContentDescription("نمایش کنترل‌های مطالعه");
            updateWindow();
        });
        panel.addView(header);

        phaseLabel = statusText(12, Color.LTGRAY);
        LinearLayout.LayoutParams phaseParams = new LinearLayout.LayoutParams(-1, -2);
        phaseParams.topMargin = dp(8);
        panel.addView(phaseLabel, phaseParams);
        timerStatus = statusText(38, Color.WHITE);
        timerStatus.setGravity(Gravity.CENTER_VERTICAL);
        timerStatus.setContentDescription("زمان باقی‌ماندهٔ پومودورو");
        LinearLayout.LayoutParams timerParams = new LinearLayout.LayoutParams(-1, dp(58));
        timerParams.topMargin = dp(2);
        panel.addView(timerStatus, timerParams);

        timerProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        timerProgress.setMax(1000);
        timerProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.rgb(60, 180, 230)));
        timerProgress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(54, 59, 72)));
        panel.addView(timerProgress, new LinearLayout.LayoutParams(-1, dp(4)));

        timerToggleButton = new MaterialButton(this);
        timerToggleButton.setTextColor(Color.WHITE);
        timerToggleButton.setTextSize(14);
        timerToggleButton.setCornerRadius(dp(14));
        timerToggleButton.setMinHeight(dp(52));
        timerToggleButton.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(Color.rgb(34, 158, 217)));
        timerToggleButton.setIconTint(
                android.content.res.ColorStateList.valueOf(Color.WHITE));
        timerToggleButton.setIconSize(dp(20));
        LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(-1, dp(52));
        toggleParams.topMargin = dp(14);
        panel.addView(timerToggleButton, toggleParams);
        timerToggleButton.setOnClickListener(v -> sendTimerControl());

        overlay.addView(panel, new LinearLayout.LayoutParams(dp(328), -2));

        eyeButton = new AccessibleImageView(this);
        eyeButton.setImageResource(R.drawable.ic_visibility);
        eyeButton.setContentDescription("نمایش کنترل‌های مطالعه");
        eyeButton.setPadding(dp(12), dp(12), dp(12), dp(12));
        eyeButton.setBackground(rounded(Color.rgb(34, 158, 217), dp(28)));
        eyeButton.setElevation(dp(8));
        LinearLayout.LayoutParams eyeParams = new LinearLayout.LayoutParams(dp(52), dp(52));
        eyeParams.topMargin = dp(8);
        overlay.addView(eyeButton, eyeParams);
        eyeButton.setOnClickListener(v -> {
            panelVisible = !panelVisible;
            panel.setVisibility(panelVisible ? View.VISIBLE : View.GONE);
            eyeButton.setContentDescription(panelVisible ? "پنهان‌کردن کنترل‌های مطالعه"
                    : "نمایش کنترل‌های مطالعه");
            updateStatus();
            updateWindow();
        });
        eyeButton.setOnTouchListener(new View.OnTouchListener() {
            private float downX;
            private float downY;
            private int originX;
            private int originY;
            private boolean dragging;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX();
                        downY = event.getRawY();
                        originX = windowParams.x;
                        originY = windowParams.y;
                        dragging = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - downX;
                        float dy = event.getRawY() - downY;
                        if (!dragging && Math.hypot(dx, dy) > dp(8)) dragging = true;
                        if (dragging) {
                            windowParams.x = Math.max(0, originX - (int) dx);
                            windowParams.y = Math.max(0, originY + (int) dy);
                            updateWindow();
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!dragging) view.performClick();
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        return true;
                    default:
                        return false;
                }
            }
        });

        try {
            windowManager.addView(overlay, windowParams);
            updateStatus();
            handler.post(refresh);
        } catch (SecurityException | WindowManager.BadTokenException e) {
            android.util.Log.e("FocusOverlay", "Unable to show PDF companion controls.", e);
            overlay = null;
            stopSelf();
        }
    }

    private void sendTimerControl() {
        Intent action = new Intent(ACTION_CONTROL).setPackage(getPackageName());
        action.putExtra(EXTRA_CONTROL, CONTROL_TIMER);
        sendBroadcast(action);
    }

    private TextView statusText(int size, int color) {
        TextView text = new TextView(this);
        text.setTextSize(size);
        text.setTextColor(color);
        text.setTypeface(android.graphics.Typeface.create("sans-serif-medium",
                android.graphics.Typeface.NORMAL));
        return text;
    }

    private void updateStatus() {
        if (timerStatus == null || phaseLabel == null || timerProgress == null
                || timerToggleButton == null) return;
        android.content.SharedPreferences prefs = getSharedPreferences("focus_data", MODE_PRIVATE);
        String phase = prefs.getString("timer_phase", "focus");
        long duration = Math.max(1, prefs.getLong("timer_duration", 25 * 60_000L));
        long endAt = prefs.getLong("timer_end_wall", 0L);
        boolean running = endAt > 0;
        long remaining = endAt > 0 ? Math.max(0, endAt - System.currentTimeMillis())
                : Math.max(0, prefs.getLong("timer_remaining", 0L));
        long seconds = (remaining + 999) / 1000;
        phaseLabel.setText("focus".equals(phase) ? "زمان تمرکز" : "زمان استراحت");
        timerStatus.setText(String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60));
        timerProgress.setProgress((int) Math.min(1000, Math.max(0,
                (duration - Math.min(duration, remaining)) * 1000 / duration)));
        timerToggleButton.setText(running ? "توقف پومودورو" : "شروع پومودورو");
        timerToggleButton.setIconResource(running ? R.drawable.ic_pause : R.drawable.ic_play_arrow);
        timerToggleButton.setContentDescription(running
                ? "توقف موقت پومودورو" : "شروع یا ادامهٔ پومودورو");
    }

    private void updateWindow() {
        if (windowManager == null || overlay == null) return;
        try {
            windowParams.width = panelVisible ? dp(344) : WindowManager.LayoutParams.WRAP_CONTENT;
            windowManager.updateViewLayout(overlay, windowParams);
        } catch (IllegalArgumentException | WindowManager.BadTokenException e) {
            android.util.Log.w("FocusOverlay", "Overlay is no longer attached.", e);
        }
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(refresh);
        if (windowManager != null && overlay != null) {
            try {
                windowManager.removeView(overlay);
            } catch (IllegalArgumentException e) {
                android.util.Log.w("FocusOverlay", "Overlay was already detached.", e);
            }
        }
        overlay = null;
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private static final class AccessibleImageView extends AppCompatImageView {
        AccessibleImageView(android.content.Context context) {
            super(context);
        }

        @Override
        public boolean performClick() {
            super.performClick();
            return true;
        }
    }
}

package com.sinlavi.focus;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class AppDiagnostics {
    private static final String TAG = "FocusDiagnostics";
    private static final long MAX_LOG_BYTES = 512 * 1024;
    private static final Object LOCK = new Object();
    private static File logFile;
    private static boolean crashHandlerInstalled;

    private AppDiagnostics() { }

    static void initialize(Context context) {
        synchronized (LOCK) {
            File directory = new File(context.getFilesDir(), "diagnostics");
            if (!directory.isDirectory() && !directory.mkdirs()) {
                Log.e(TAG, "Unable to create diagnostics directory.");
                return;
            }
            logFile = new File(directory, "focus-diagnostics.txt");
        }
        synchronized (LOCK) {
            if (!crashHandlerInstalled) {
                Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
                Thread.setDefaultUncaughtExceptionHandler((crashedThread, error) -> {
                    write("UNCAUGHT CRASH on " + crashedThread.getName(), error);
                    if (previous != null) previous.uncaughtException(crashedThread, error);
                });
                crashHandlerInstalled = true;
            }
        }
        write("Application started; Android API " + android.os.Build.VERSION.SDK_INT, null);
    }

    static void record(String event) {
        write(event, null);
    }

    static File getLogFile() {
        synchronized (LOCK) {
            return logFile;
        }
    }

    private static void write(String event, Throwable error) {
        synchronized (LOCK) {
            if (logFile == null) return;
            try {
                if (logFile.length() > MAX_LOG_BYTES && !logFile.delete()) {
                    Log.w(TAG, "Unable to rotate diagnostics log.");
                }
                String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
                        .format(new Date());
                StringBuilder entry = new StringBuilder(timestamp).append("  ").append(event).append('\n');
                if (error != null) {
                    StringWriter details = new StringWriter();
                    error.printStackTrace(new PrintWriter(details));
                    entry.append(details);
                }
                try (FileOutputStream output = new FileOutputStream(logFile, true)) {
                    output.write(entry.toString().getBytes(StandardCharsets.UTF_8));
                    output.flush();
                }
            } catch (IOException e) {
                Log.e(TAG, "Unable to write diagnostics log.", e);
            }
        }
    }
}

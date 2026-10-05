package com.antigravity.btbridge;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

public class AppLogger {
    private static final String TAG = "Lapdroid";
    private static final int MAX_LOG_ENTRIES = 600;

    public interface LogListener {
        void onLogAdded(String formattedLine);
        void onLogsCleared();
    }

    private static final LinkedList<String> sLogs = new LinkedList<>();
    private static final List<LogListener> sListeners = new ArrayList<>();
    private static final Handler sMainHandler = new Handler(Looper.getMainLooper());
    private static final SimpleDateFormat sDateFormat = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

    public static synchronized void i(String tag, String msg) {
        log("INFO", tag, msg);
        Log.i(tag, msg);
    }

    public static synchronized void d(String tag, String msg) {
        log("DEBUG", tag, msg);
        Log.d(tag, msg);
    }

    public static synchronized void w(String tag, String msg) {
        log("WARN", tag, msg);
        Log.w(tag, msg);
    }

    public static synchronized void e(String tag, String msg) {
        log("ERROR", tag, msg);
        Log.e(tag, msg);
    }

    public static synchronized void e(String tag, String msg, Throwable tr) {
        String fullMsg = msg + "\n" + Log.getStackTraceString(tr);
        log("ERROR", tag, fullMsg);
        Log.e(tag, msg, tr);
    }

    private static synchronized void log(String level, String tag, String msg) {
        String timestamp = sDateFormat.format(new Date());
        String entry = String.format(Locale.US, "[%s] [%s] [%s] %s", timestamp, level, tag, msg);

        if (sLogs.size() >= MAX_LOG_ENTRIES) {
            sLogs.removeFirst();
        }
        sLogs.addLast(entry);

        sMainHandler.post(() -> {
            synchronized (AppLogger.class) {
                for (LogListener l : sListeners) {
                    try {
                        l.onLogAdded(entry);
                    } catch (Exception ignored) {}
                }
            }
        });
    }

    public static synchronized void addListener(LogListener listener) {
        if (listener != null && !sListeners.contains(listener)) {
            sListeners.add(listener);
        }
    }

    public static synchronized void removeListener(LogListener listener) {
        sListeners.remove(listener);
    }

    public static synchronized String getAllLogs() {
        StringBuilder sb = new StringBuilder();
        for (String line : sLogs) {
            sb.append(line).append("\n");
        }
        return sb.toString();
    }

    public static synchronized void clearLogs() {
        sLogs.clear();
        sMainHandler.post(() -> {
            synchronized (AppLogger.class) {
                for (LogListener l : sListeners) {
                    try {
                        l.onLogsCleared();
                    } catch (Exception ignored) {}
                }
            }
        });
    }
}

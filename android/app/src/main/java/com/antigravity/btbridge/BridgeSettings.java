package com.antigravity.btbridge;

import android.content.Context;
import android.content.SharedPreferences;

public class BridgeSettings {
    private static final String PREF_NAME = "lapdroid_settings";

    // Enter Key Options
    public static final String KEY_ENTER_MODE = "pref_enter_mode";
    public static final int ENTER_MODE_SMART = 0;   // Newline in multiline, Action/Click in single-line
    public static final int ENTER_MODE_NEWLINE = 1; // Always Newline (\n)
    public static final int ENTER_MODE_ACTION = 2;  // Always Action / Submit

    // Tab Key Options
    public static final String KEY_TAB_MODE = "pref_tab_mode";
    public static final int TAB_MODE_FOCUS = 0;   // Navigate Focus Next/Prev
    public static final int TAB_MODE_SPACES = 1;  // Insert 4 Spaces
    public static final int TAB_MODE_TAB = 2;     // Insert \t

    // Windows Key Option
    public static final String KEY_WIN_ACTION = "pref_win_action";
    public static final int WIN_ACTION_HOME = 0;    // Android Home Screen
    public static final int WIN_ACTION_NOTIF = 1;   // Open Notifications
    public static final int WIN_ACTION_RECENTS = 2; // Open Recents

    // Toggles
    public static final String KEY_ENABLE_CTRL_SHORTCUTS = "pref_ctrl_shortcuts";
    public static final String KEY_ENABLE_ALT_TAB = "pref_alt_tab";
    public static final String KEY_IGNORE_PLACEHOLDERS = "pref_ignore_placeholders";

    private final SharedPreferences mPrefs;
    private static BridgeSettings sInstance;

    public static synchronized BridgeSettings getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new BridgeSettings(context.getApplicationContext());
        }
        return sInstance;
    }

    private BridgeSettings(Context context) {
        mPrefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public int getEnterMode() {
        return mPrefs.getInt(KEY_ENTER_MODE, ENTER_MODE_SMART);
    }

    public void setEnterMode(int mode) {
        mPrefs.edit().putInt(KEY_ENTER_MODE, mode).apply();
    }

    public int getTabMode() {
        return mPrefs.getInt(KEY_TAB_MODE, TAB_MODE_FOCUS);
    }

    public void setTabMode(int mode) {
        mPrefs.edit().putInt(KEY_TAB_MODE, mode).apply();
    }

    public int getWinAction() {
        return mPrefs.getInt(KEY_WIN_ACTION, WIN_ACTION_HOME);
    }

    public void setWinAction(int action) {
        mPrefs.edit().putInt(KEY_WIN_ACTION, action).apply();
    }

    public boolean isCtrlShortcutsEnabled() {
        return mPrefs.getBoolean(KEY_ENABLE_CTRL_SHORTCUTS, true);
    }

    public void setCtrlShortcutsEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(KEY_ENABLE_CTRL_SHORTCUTS, enabled).apply();
    }

    public boolean isAltTabEnabled() {
        return mPrefs.getBoolean(KEY_ENABLE_ALT_TAB, true);
    }

    public void setAltTabEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(KEY_ENABLE_ALT_TAB, enabled).apply();
    }

    public boolean isIgnorePlaceholdersEnabled() {
        return mPrefs.getBoolean(KEY_IGNORE_PLACEHOLDERS, true);
    }

    public void setIgnorePlaceholdersEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(KEY_IGNORE_PLACEHOLDERS, enabled).apply();
    }
}

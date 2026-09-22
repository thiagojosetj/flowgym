package io.github.thiagojosetj.gym.data.prefs;

import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * Preferences that belong to THIS device and are never synced (docs/DATABASE.md: theme and
 * interface size differ per device). Preferences that follow the account (unit, default rest)
 * will live in the user_setting table instead.
 */
public final class UiPreferences {

    public enum ThemeMode {
        SYSTEM(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
        LIGHT(AppCompatDelegate.MODE_NIGHT_NO),
        DARK(AppCompatDelegate.MODE_NIGHT_YES);

        @AppCompatDelegate.NightMode
        private final int nightMode;

        ThemeMode(@AppCompatDelegate.NightMode int nightMode) {
            this.nightMode = nightMode;
        }

        @AppCompatDelegate.NightMode
        public int nightMode() {
            return nightMode;
        }
    }

    public static final String FILE_NAME = "ui_prefs";
    private static final String KEY_THEME = "theme_mode";

    private final SharedPreferences preferences;

    public UiPreferences(SharedPreferences preferences) {
        this.preferences = preferences;
    }

    public ThemeMode themeMode() {
        String stored = preferences.getString(KEY_THEME, ThemeMode.SYSTEM.name());
        try {
            return ThemeMode.valueOf(stored);
        } catch (IllegalArgumentException e) {
            return ThemeMode.SYSTEM;
        }
    }

    /** Persists and applies immediately (the activity is recreated with the new theme). */
    public void setThemeMode(ThemeMode mode) {
        preferences.edit().putString(KEY_THEME, mode.name()).apply();
        AppCompatDelegate.setDefaultNightMode(mode.nightMode());
    }
}

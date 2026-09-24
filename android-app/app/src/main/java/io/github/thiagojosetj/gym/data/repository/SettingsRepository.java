package io.github.thiagojosetj.gym.data.repository;

import android.util.Log;

import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import java.time.Clock;
import java.util.List;
import java.util.function.Consumer;

import io.github.thiagojosetj.gym.core.AppExecutors;
import io.github.thiagojosetj.gym.data.local.dao.UserSettingDao;
import io.github.thiagojosetj.gym.data.local.entity.UserSettingEntity;
import io.github.thiagojosetj.gym.domain.settings.AppSettings;

/**
 * Account-level preferences stored as key/value rows (docs/DATABASE.md). Unknown or corrupt values
 * fall back to the defaults instead of crashing: a bad row must never lock the user out of the app.
 */
public final class SettingsRepository {

    private static final String TAG = "SettingsRepository";

    private final UserSettingDao dao;
    private final UserRepository users;
    private final AppExecutors executors;
    private final Clock clock;

    public SettingsRepository(UserSettingDao dao, UserRepository users, AppExecutors executors, Clock clock) {
        this.dao = dao;
        this.users = users;
        this.executors = executors;
        this.clock = clock;
    }

    public LiveData<AppSettings> observeSettings() {
        return Transformations.map(dao.observeCurrentUser(), SettingsRepository::toSettings);
    }

    /** Reads the current values; used when a background operation needs them (e.g. template defaults). */
    public void loadSettings(Consumer<AppSettings> onResult, Consumer<Throwable> onError) {
        executors.runOnDisk(() -> toSettings(dao.findCurrentUser()), onResult, onError);
    }

    public void setDefaultRestSeconds(int seconds) {
        put(UserSettingEntity.KEY_REST_DEFAULT_SECONDS, Integer.toString(seconds));
    }

    public void setRestSoundEnabled(boolean enabled) {
        put(UserSettingEntity.KEY_REST_SOUND, Boolean.toString(enabled));
    }

    public void setRestVibrationEnabled(boolean enabled) {
        put(UserSettingEntity.KEY_REST_VIBRATION, Boolean.toString(enabled));
    }

    private void put(String key, String value) {
        executors.diskIO().execute(() -> {
            try {
                dao.upsert(new UserSettingEntity(users.requireCurrentUserId(), key, value, clock.millis()));
            } catch (RuntimeException e) {
                // Only the key is logged: values are never private, but keep the habit (§49).
                Log.e(TAG, "Could not save setting " + key, e);
            }
        });
    }

    @WorkerThread
    static AppSettings toSettings(List<UserSettingEntity> rows) {
        AppSettings settings = AppSettings.standard();
        for (UserSettingEntity row : rows) {
            try {
                switch (row.key) {
                    case UserSettingEntity.KEY_REST_DEFAULT_SECONDS ->
                            settings = settings.withDefaultRestSeconds(Integer.parseInt(row.value));
                    case UserSettingEntity.KEY_REST_SOUND ->
                            settings = settings.withRestSoundEnabled(Boolean.parseBoolean(row.value));
                    case UserSettingEntity.KEY_REST_VIBRATION ->
                            settings = settings.withRestVibrationEnabled(Boolean.parseBoolean(row.value));
                    default -> {
                        // A key written by a newer version: ignore it, do not lose it.
                    }
                }
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Ignoring invalid value for setting " + row.key);
            }
        }
        return settings;
    }
}

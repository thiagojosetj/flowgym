package io.github.thiagojosetj.gym.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;

/**
 * Preferences that belong to the ACCOUNT and must follow the user to another device (default rest,
 * rest sound and vibration, unit). Device-only preferences (theme, interface size) stay in
 * SharedPreferences — see {@code data/prefs/UiPreferences} and docs/DATABASE.md.
 *
 * <p>Key/value on purpose: each setting syncs independently with last-write-wins per key
 * (docs/SYNC.md §6), which is harmless for preferences and avoids a wide table that changes shape
 * every time a setting is added.
 */
@Entity(tableName = "user_setting", primaryKeys = {"owner_user_id", "setting_key"})
public class UserSettingEntity {

    public static final String KEY_REST_DEFAULT_SECONDS = "rest_default_seconds";
    public static final String KEY_REST_SOUND = "rest_sound_enabled";
    public static final String KEY_REST_VIBRATION = "rest_vibration_enabled";

    @NonNull
    @ColumnInfo(name = "owner_user_id")
    public String ownerUserId = "";

    @NonNull
    @ColumnInfo(name = "setting_key")
    public String key = "";

    @NonNull
    public String value = "";

    @ColumnInfo(name = "updated_at")
    public long updatedAt;

    @NonNull
    @ColumnInfo(name = "sync_status")
    public SyncStatus syncStatus = SyncStatus.PENDING;

    public UserSettingEntity() {
    }

    @androidx.room.Ignore
    public UserSettingEntity(@NonNull String ownerUserId, @NonNull String key, @NonNull String value,
                             long updatedAt) {
        this.ownerUserId = ownerUserId;
        this.key = key;
        this.value = value;
        this.updatedAt = updatedAt;
    }
}

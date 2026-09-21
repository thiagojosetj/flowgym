package io.github.thiagojosetj.gym.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * Small key/value facts about this database (catalog version, current user). Kept inside the
 * database, not in SharedPreferences, so they can never disagree with the data after a
 * backup/restore (docs/DATABASE.md).
 */
@Entity(tableName = "app_metadata")
public class AppMetadataEntity {

    public static final String KEY_CATALOG_VERSION = "catalog_version";
    public static final String KEY_CURRENT_USER_ID = "current_user_id";

    /**
     * SQL expression for the current user's id. DAOs embed it in their queries so that observed
     * lists automatically follow a future account switch, with no user id held in Java state.
     */
    public static final String CURRENT_USER_ID_SQL =
            "(SELECT meta_value FROM app_metadata WHERE meta_key = 'current_user_id')";

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "meta_key")
    public String key = "";

    @NonNull
    @ColumnInfo(name = "meta_value")
    public String value = "";

    public AppMetadataEntity() {
    }

    @Ignore
    public AppMetadataEntity(@NonNull String key, @NonNull String value) {
        this.key = key;
        this.value = value;
    }
}

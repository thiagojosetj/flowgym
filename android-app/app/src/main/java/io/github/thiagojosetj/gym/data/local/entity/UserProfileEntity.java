package io.github.thiagojosetj.gym.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * A person using the app on this device. Before accounts exist there is one local identity
 * ({@code is_local = 1}); the schema never assumes a single user (docs/SYNC.md §7).
 */
@Entity(tableName = "user_profile")
public class UserProfileEntity {

    @PrimaryKey
    @NonNull
    public String id = "";

    @Nullable
    @ColumnInfo(name = "display_name")
    public String displayName;

    @ColumnInfo(name = "is_local")
    public boolean isLocal;

    @Nullable
    @ColumnInfo(name = "remote_user_id")
    public String remoteUserId;

    @ColumnInfo(name = "created_at")
    public long createdAt;

    @ColumnInfo(name = "updated_at")
    public long updatedAt;
}

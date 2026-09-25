package io.github.thiagojosetj.gym.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * One pause of a session, persisted as an interval (PRODUCT_SPEC section 7). A row with
 * {@code ended_at IS NULL} means the session is paused right now.
 */
@Entity(
        tableName = "session_pause",
        foreignKeys = @ForeignKey(entity = WorkoutSessionEntity.class, parentColumns = "id",
                childColumns = "session_id", onDelete = ForeignKey.CASCADE),
        indices = @Index("session_id"))
public class SessionPauseEntity {

    @PrimaryKey
    @NonNull
    public String id = "";

    @NonNull
    @ColumnInfo(name = "session_id")
    public String sessionId = "";

    @ColumnInfo(name = "started_at")
    public long startedAt;

    @Nullable
    @ColumnInfo(name = "ended_at")
    public Long endedAt;
}

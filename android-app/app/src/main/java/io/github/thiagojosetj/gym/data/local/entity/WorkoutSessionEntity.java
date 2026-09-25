package io.github.thiagojosetj.gym.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import io.github.thiagojosetj.gym.domain.session.SessionStatus;

/**
 * One performed (or in progress) workout: aggregate root of the history (docs/DATABASE.md).
 *
 * <p>Everything the screen and the summary need is snapshotted here and in its children, so editing
 * or deleting a template never rewrites the past. {@code template_id} therefore has no foreign key.
 *
 * <p>The rest countdown lives on this row (not in memory) so it is still correct after the process
 * is killed: {@code rest_ends_at} is an instant, and while the session is paused the remaining time
 * is frozen in {@code rest_remaining_ms_when_paused}.
 */
@Entity(
        tableName = "workout_session",
        indices = {@Index("owner_user_id"), @Index({"owner_user_id", "local_date"})})
public class WorkoutSessionEntity {

    @PrimaryKey
    @NonNull
    public String id = "";

    @NonNull
    @ColumnInfo(name = "owner_user_id")
    public String ownerUserId = "";

    /** Template this came from, for comparisons. No FK: the template may be deleted. */
    @Nullable
    @ColumnInfo(name = "template_id")
    public String templateId;

    /** Snapshot of the template name at the start of the session. */
    @NonNull
    public String name = "";

    /** Note written during this session (the template's permanent note is elsewhere). */
    @Nullable
    public String notes;

    @NonNull
    public SessionStatus status = SessionStatus.ACTIVE;

    @ColumnInfo(name = "started_at")
    public long startedAt;

    @Nullable
    @ColumnInfo(name = "ended_at")
    public Long endedAt;

    /** Zone the session was performed in, so the local date never shifts when the user travels. */
    @NonNull
    @ColumnInfo(name = "time_zone")
    public String timeZone = "UTC";

    /** ISO date in that zone ("2026-09-25"): what the calendar and streaks group by. */
    @NonNull
    @ColumnInfo(name = "local_date")
    public String localDate = "";

    /** Cache of the paused time, written when the session is finished. */
    @ColumnInfo(name = "total_paused_ms")
    public long totalPausedMs;

    /** Subjective rating 1-5, optional (HIS-01). */
    @Nullable
    public Integer rating;

    /** Set whose rest is running. No FK: transient state, and the set may be removed. */
    @Nullable
    @ColumnInfo(name = "rest_set_log_id")
    public String restSetLogId;

    @Nullable
    @ColumnInfo(name = "rest_ends_at")
    public Long restEndsAt;

    /** While the session is paused the rest stops here and resumes from this value. */
    @Nullable
    @ColumnInfo(name = "rest_remaining_ms_when_paused")
    public Long restRemainingMsWhenPaused;

    @ColumnInfo(name = "created_at")
    public long createdAt;

    @ColumnInfo(name = "updated_at")
    public long updatedAt;

    @Nullable
    @ColumnInfo(name = "deleted_at")
    public Long deletedAt;

    @NonNull
    @ColumnInfo(name = "sync_status")
    public SyncStatus syncStatus = SyncStatus.PENDING;

    @Nullable
    @ColumnInfo(name = "server_version")
    public Long serverVersion;
}

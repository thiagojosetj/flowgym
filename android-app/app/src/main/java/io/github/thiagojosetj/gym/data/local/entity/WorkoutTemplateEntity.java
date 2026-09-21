package io.github.thiagojosetj.gym.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import io.github.thiagojosetj.gym.domain.template.TemplateOrigin;

/**
 * Workout template: aggregate root (docs/SYNC.md §3). Deletion is logical ({@code deleted_at}) so
 * the deletion can be synced and past sessions are never touched.
 */
@Entity(tableName = "workout_template", indices = @Index("owner_user_id"))
public class WorkoutTemplateEntity {

    @PrimaryKey
    @NonNull
    public String id = "";

    @NonNull
    @ColumnInfo(name = "owner_user_id")
    public String ownerUserId = "";

    @NonNull
    public String name = "";

    @Nullable
    public String description;

    /** Permanent note of the template (not the note of one session). */
    @Nullable
    public String notes;

    @ColumnInfo(name = "sort_order")
    public int sortOrder;

    @Nullable
    @ColumnInfo(name = "archived_at")
    public Long archivedAt;

    @NonNull
    public TemplateOrigin origin = TemplateOrigin.CREATED;

    /** Source template of a copy. No foreign key: it may belong to another user or be deleted. */
    @Nullable
    @ColumnInfo(name = "origin_template_id")
    public String originTemplateId;

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

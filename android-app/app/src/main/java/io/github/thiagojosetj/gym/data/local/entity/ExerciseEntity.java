package io.github.thiagojosetj.gym.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import io.github.thiagojosetj.gym.domain.model.Laterality;
import io.github.thiagojosetj.gym.domain.model.LoadBasis;
import io.github.thiagojosetj.gym.domain.model.TrackingType;

/**
 * Library exercise. {@code owner_user_id == null} means system catalog; a value means a custom
 * exercise of that user (future LIB-06). Aggregate root for sync when custom.
 * Enum columns are stored by name via Room's built-in enum converter.
 */
@Entity(
        tableName = "exercise",
        indices = {
                @Index(value = "code", unique = true),
                @Index("owner_user_id")
        })
public class ExerciseEntity {

    @PrimaryKey
    @NonNull
    public String id = "";

    @Nullable
    @ColumnInfo(name = "owner_user_id")
    public String ownerUserId;

    /** Stable slug for catalog rows (e.g. barbell_bench_press); null for custom exercises. */
    @Nullable
    public String code;

    @NonNull
    public String name = "";

    @Nullable
    public String aliases;

    /** Normalized name + aliases (TextNormalizer), used by the library search. */
    @NonNull
    @ColumnInfo(name = "search_text")
    public String searchText = "";

    @Nullable
    public String description;

    /** One step per line. */
    @Nullable
    public String instructions;

    @Nullable
    public String tips;

    @Nullable
    @ColumnInfo(name = "common_mistakes")
    public String commonMistakes;

    @Nullable
    public String notes;

    @NonNull
    @ColumnInfo(name = "tracking_type")
    public TrackingType trackingType = TrackingType.WEIGHT_REPS;

    @NonNull
    @ColumnInfo(name = "load_basis")
    public LoadBasis loadBasis = LoadBasis.TOTAL;

    @ColumnInfo(name = "implement_count")
    public int implementCount = 1;

    @NonNull
    public Laterality laterality = Laterality.BILATERAL;

    @ColumnInfo(name = "is_active")
    public boolean isActive = true;

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

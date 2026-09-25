package io.github.thiagojosetj.gym.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import io.github.thiagojosetj.gym.domain.session.SetStatus;

/**
 * One set of a session: what was planned and what was actually done, side by side
 * (PRODUCT_SPEC section 6.5).
 *
 * <p>A performed field that is NULL means "not confirmed yet" — suggestions are never written here,
 * they are derived from the previous session. Only {@code COMPLETED} sets count for volume and
 * records, and a warm-up technique never counts.
 *
 * <p>{@code parent_set_id} is reserved for the segments of a drop-set or rest-pause (40 kg x 10 then
 * 30 kg x 8): each segment is a child row of the same set.
 */
@Entity(
        tableName = "set_log",
        foreignKeys = {
                @ForeignKey(entity = SessionExerciseEntity.class, parentColumns = "id",
                        childColumns = "session_exercise_id", onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = SetLogEntity.class, parentColumns = "id",
                        childColumns = "parent_set_id", onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = TrainingTechniqueEntity.class, parentColumns = "id",
                        childColumns = "technique_id")
        },
        indices = {@Index("session_exercise_id"), @Index("parent_set_id"), @Index("technique_id")})
public class SetLogEntity {

    @PrimaryKey
    @NonNull
    public String id = "";

    @NonNull
    @ColumnInfo(name = "session_exercise_id")
    public String sessionExerciseId = "";

    /** Segment of another set (drop-set, rest-pause). Null for a normal set. */
    @Nullable
    @ColumnInfo(name = "parent_set_id")
    public String parentSetId;

    public int position;

    /** Technique of this set; null = normal working set. */
    @Nullable
    @ColumnInfo(name = "technique_id")
    public String techniqueId;

    // ---- planned (snapshot of the template at the start of the session)

    @Nullable
    @ColumnInfo(name = "planned_reps_min")
    public Integer plannedRepsMin;

    @Nullable
    @ColumnInfo(name = "planned_reps_max")
    public Integer plannedRepsMax;

    @Nullable
    @ColumnInfo(name = "planned_weight_g")
    public Long plannedWeightGrams;

    @Nullable
    @ColumnInfo(name = "planned_duration_s")
    public Integer plannedDurationSeconds;

    @ColumnInfo(name = "planned_rest_seconds")
    public int plannedRestSeconds;

    // ---- performed (null until the user confirms)

    @Nullable
    @ColumnInfo(name = "weight_g")
    public Long weightGrams;

    @Nullable
    public Integer reps;

    /** Per-side logging (E 10 / D 9); null when the reps are logged together. */
    @Nullable
    @ColumnInfo(name = "reps_left")
    public Integer repsLeft;

    @Nullable
    @ColumnInfo(name = "reps_right")
    public Integer repsRight;

    @Nullable
    @ColumnInfo(name = "duration_s")
    public Integer durationSeconds;

    @NonNull
    public SetStatus status = SetStatus.PENDING;

    @Nullable
    @ColumnInfo(name = "completed_at")
    public Long completedAt;

    @Nullable
    public String notes;
}

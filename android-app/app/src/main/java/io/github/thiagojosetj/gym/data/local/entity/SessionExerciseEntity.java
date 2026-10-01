package io.github.thiagojosetj.gym.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import io.github.thiagojosetj.gym.domain.model.Laterality;
import io.github.thiagojosetj.gym.domain.model.LoadBasis;
import io.github.thiagojosetj.gym.domain.model.SideMode;
import io.github.thiagojosetj.gym.domain.model.TrackingType;

/**
 * One exercise inside a session, with the snapshots needed to redisplay and recalculate it years
 * later (docs/DATABASE.md section 4): the name it had, how its load is interpreted, whether it is
 * unilateral, and the rest that was planned.
 *
 * <p>Muscles are deliberately NOT snapshotted: statistics by muscle group read the current catalog,
 * so an anatomical correction also fixes the past.
 */
@Entity(
        tableName = "session_exercise",
        foreignKeys = {
                @ForeignKey(entity = WorkoutSessionEntity.class, parentColumns = "id",
                        childColumns = "session_id", onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = ExerciseEntity.class, parentColumns = "id",
                        childColumns = "exercise_id"),
                @ForeignKey(entity = SessionExerciseGroupEntity.class, parentColumns = "id",
                        childColumns = "group_id", onDelete = ForeignKey.SET_NULL)
        },
        indices = {@Index("session_id"), @Index("exercise_id"), @Index("group_id")})
public class SessionExerciseEntity {

    @PrimaryKey
    @NonNull
    public String id = "";

    @NonNull
    @ColumnInfo(name = "session_id")
    public String sessionId = "";

    /** The snapshot of the group this exercise was in, or null when it stood alone. */
    @Nullable
    @ColumnInfo(name = "group_id")
    public String groupId;

    /** The library exercise: a stable identity, kept so statistics can group across sessions. */
    @NonNull
    @ColumnInfo(name = "exercise_id")
    public String exerciseId = "";

    /** Where it came from in the template. No FK: the template may change or be deleted. */
    @Nullable
    @ColumnInfo(name = "template_exercise_id")
    public String templateExerciseId;

    public int position;

    /** Snapshot: the exercise name as it was when the session started. */
    @NonNull
    @ColumnInfo(name = "exercise_name")
    public String exerciseName = "";

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

    @NonNull
    @ColumnInfo(name = "side_mode")
    public SideMode sideMode = SideMode.COMBINED;

    @ColumnInfo(name = "rest_seconds")
    public int restSeconds;

    /** Snapshot of the template's permanent note for this exercise. */
    @Nullable
    @ColumnInfo(name = "permanent_notes")
    public String permanentNotes;

    /** Note written during this session. */
    @Nullable
    public String notes;

    /**
     * Frozen pointer to the same exercise in the previous session, so "anterior" keeps showing what
     * it showed on the day. No FK: that session may be deleted.
     */
    @Nullable
    @ColumnInfo(name = "previous_session_exercise_id")
    public String previousSessionExerciseId;

    @Nullable
    @ColumnInfo(name = "started_at")
    public Long startedAt;
}

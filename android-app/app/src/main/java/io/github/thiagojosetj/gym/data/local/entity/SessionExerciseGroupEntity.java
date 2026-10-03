package io.github.thiagojosetj.gym.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * The snapshot of a template's exercise group, taken when the session started
 * (docs/DATABASE.md section 4). Editing or deleting the group afterwards must not change what this
 * session says happened.
 *
 * <p>{@code technique_code} is stored, not joined. The badge a past session shows has to be the one
 * it showed that day, and the adversarial review of 28/09/2026 found exactly this exposure on
 * {@code training_technique.counts_as_working_set}, where it survives only as a documented
 * invariant because snapshotting it now would cost another migration. Here the table is new, so
 * the column is free.
 */
@Entity(
        tableName = "session_exercise_group",
        foreignKeys = {
                @ForeignKey(entity = WorkoutSessionEntity.class, parentColumns = "id",
                        childColumns = "session_id", onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = TrainingTechniqueEntity.class, parentColumns = "id",
                        childColumns = "technique_id")
        },
        indices = {@Index("session_id"), @Index("technique_id")})
public class SessionExerciseGroupEntity {

    @PrimaryKey
    @NonNull
    public String id = "";

    @NonNull
    @ColumnInfo(name = "session_id")
    public String sessionId = "";

    @NonNull
    public String label = "";

    @Nullable
    @ColumnInfo(name = "technique_id")
    public String techniqueId;

    /** Snapshot of the badge ("SS", "BI"), so a catalog edit cannot rewrite a past session. */
    @Nullable
    @ColumnInfo(name = "technique_code")
    public String techniqueCode;

    @ColumnInfo(name = "rest_after_round_s")
    public int restAfterRoundSeconds;

    public int position;
}

package io.github.thiagojosetj.gym.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Planned set ("SetPlan") of a template exercise. Loads in grams (ADR-0008). */
@Entity(
        tableName = "template_set",
        foreignKeys = {
                @ForeignKey(entity = TemplateExerciseEntity.class, parentColumns = "id",
                        childColumns = "template_exercise_id", onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = TrainingTechniqueEntity.class, parentColumns = "id",
                        childColumns = "technique_id")
        },
        indices = {@Index("template_exercise_id"), @Index("technique_id")})
public class TemplateSetEntity {

    @PrimaryKey
    @NonNull
    public String id = "";

    @NonNull
    @ColumnInfo(name = "template_exercise_id")
    public String templateExerciseId = "";

    public int position;

    @Nullable
    @ColumnInfo(name = "target_reps_min")
    public Integer targetRepsMin;

    @Nullable
    @ColumnInfo(name = "target_reps_max")
    public Integer targetRepsMax;

    @Nullable
    @ColumnInfo(name = "target_weight_g")
    public Long targetWeightGrams;

    @Nullable
    @ColumnInfo(name = "target_duration_s")
    public Integer targetDurationSeconds;

    /** Overrides the exercise's rest for this set; null = inherit. */
    @Nullable
    @ColumnInfo(name = "rest_seconds")
    public Integer restSeconds;

    /** Set technique (warm-up, drop-set…); null = normal working set. */
    @Nullable
    @ColumnInfo(name = "technique_id")
    public String techniqueId;
}

package io.github.thiagojosetj.gym.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import io.github.thiagojosetj.gym.domain.model.SideMode;

/**
 * Exercise inside a template (child of the template aggregate). Children carry no sync columns:
 * they are saved and synced together with their root, keeping their UUIDs (docs/DATABASE.md §2).
 */
@Entity(
        tableName = "template_exercise",
        foreignKeys = {
                @ForeignKey(entity = WorkoutTemplateEntity.class, parentColumns = "id",
                        childColumns = "template_id", onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = ExerciseEntity.class, parentColumns = "id",
                        childColumns = "exercise_id")
        },
        indices = {@Index("template_id"), @Index("exercise_id")})
public class TemplateExerciseEntity {

    @PrimaryKey
    @NonNull
    public String id = "";

    @NonNull
    @ColumnInfo(name = "template_id")
    public String templateId = "";

    @NonNull
    @ColumnInfo(name = "exercise_id")
    public String exerciseId = "";

    public int position;

    /** Rest after each set; 0 = no automatic rest timer. */
    @ColumnInfo(name = "rest_seconds")
    public int restSeconds;

    /** Permanent note for this exercise in this template ("Banco no terceiro encaixe"). */
    @Nullable
    public String notes;

    @NonNull
    @ColumnInfo(name = "side_mode")
    public SideMode sideMode = SideMode.COMBINED;
}

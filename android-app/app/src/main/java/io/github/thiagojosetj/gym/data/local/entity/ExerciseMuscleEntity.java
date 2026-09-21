package io.github.thiagojosetj.gym.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;

import io.github.thiagojosetj.gym.domain.model.MuscleRole;

/** Muscles targeted by an exercise, with their role. The first PRIMARY (by sort_order) is highlighted. */
@Entity(
        tableName = "exercise_muscle",
        primaryKeys = {"exercise_id", "muscle_id"},
        foreignKeys = {
                @ForeignKey(entity = ExerciseEntity.class, parentColumns = "id",
                        childColumns = "exercise_id", onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = MuscleEntity.class, parentColumns = "id",
                        childColumns = "muscle_id")
        },
        indices = @Index("muscle_id"))
public class ExerciseMuscleEntity {

    @NonNull
    @ColumnInfo(name = "exercise_id")
    public String exerciseId = "";

    @NonNull
    @ColumnInfo(name = "muscle_id")
    public String muscleId = "";

    @NonNull
    public MuscleRole role = MuscleRole.PRIMARY;

    @ColumnInfo(name = "sort_order")
    public int sortOrder;

    public ExerciseMuscleEntity() {
    }

    @Ignore
    public ExerciseMuscleEntity(@NonNull String exerciseId, @NonNull String muscleId,
                                @NonNull MuscleRole role, int sortOrder) {
        this.exerciseId = exerciseId;
        this.muscleId = muscleId;
        this.role = role;
        this.sortOrder = sortOrder;
    }
}

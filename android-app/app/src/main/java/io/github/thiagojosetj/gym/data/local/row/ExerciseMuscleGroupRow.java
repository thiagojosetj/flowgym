package io.github.thiagojosetj.gym.data.local.row;

import androidx.annotation.NonNull;

import io.github.thiagojosetj.gym.domain.model.MuscleRole;

/** One (exercise, muscle group, role) row for the statistics of a period (PRODUCT_SPEC PRG-04). */
public class ExerciseMuscleGroupRow {

    @NonNull
    public String exerciseId = "";

    @NonNull
    public String muscleGroupId = "";

    @NonNull
    public String name = "";

    public int sortOrder;

    @NonNull
    public MuscleRole role = MuscleRole.PRIMARY;
}

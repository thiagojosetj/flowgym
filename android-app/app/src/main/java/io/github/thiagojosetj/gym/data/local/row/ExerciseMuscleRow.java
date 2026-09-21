package io.github.thiagojosetj.gym.data.local.row;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.thiagojosetj.gym.domain.model.MuscleRole;

/** A muscle linked to an exercise, with its group, for the detail screen. */
public class ExerciseMuscleRow {

    @NonNull
    public String muscleId = "";

    @NonNull
    public String muscleName = "";

    /** Parent group name; null when the link points directly to a group. */
    @Nullable
    public String groupName;

    @NonNull
    public MuscleRole role = MuscleRole.PRIMARY;

    public int sortOrder;
}

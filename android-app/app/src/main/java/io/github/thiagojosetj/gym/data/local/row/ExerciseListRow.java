package io.github.thiagojosetj.gym.data.local.row;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** One line of the library list, assembled by SQL (no N+1 queries). */
public class ExerciseListRow {

    @NonNull
    public String id = "";

    @NonNull
    public String name = "";

    /** Name of the highlighted primary muscle (usually a subgroup). */
    @Nullable
    public String primaryMuscleName;

    /** Top-level group of that muscle (itself when the link points to a group). */
    @Nullable
    public String primaryGroupName;

    /** Name of the primary equipment. */
    @Nullable
    public String primaryEquipmentName;
}

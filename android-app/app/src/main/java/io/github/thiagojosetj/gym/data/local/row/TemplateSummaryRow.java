package io.github.thiagojosetj.gym.data.local.row;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** One card of "Meus treinos". */
public class TemplateSummaryRow {

    @NonNull
    public String id = "";

    @NonNull
    public String name = "";

    @Nullable
    public String description;

    public int exerciseCount;

    public int setCount;

    /** Distinct primary muscle groups, comma-separated, in exercise order. */
    @Nullable
    public String muscleGroups;

    public long updatedAt;
}

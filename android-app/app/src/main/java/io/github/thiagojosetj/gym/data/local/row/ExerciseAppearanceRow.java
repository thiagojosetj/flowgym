package io.github.thiagojosetj.gym.data.local.row;

import androidx.annotation.NonNull;

/** Which finished session one appearance of an exercise belongs to (PRODUCT_SPEC PRG-03). */
public class ExerciseAppearanceRow {

    @NonNull
    public String sessionExerciseId = "";

    @NonNull
    public String sessionId = "";

    /** The day as it was lived; never derived from {@link #startedAt}. */
    @NonNull
    public String localDate = "";

    public long startedAt;
}

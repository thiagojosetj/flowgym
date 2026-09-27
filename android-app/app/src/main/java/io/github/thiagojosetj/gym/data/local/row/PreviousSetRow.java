package io.github.thiagojosetj.gym.data.local.row;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.thiagojosetj.gym.domain.session.SetStatus;

/**
 * One set of the previous session, used to show "anterior" and to pre-fill the current set
 * (PRODUCT_SPEC section 6.5). Read separately from the current sets because the pairing is by
 * ordinal among working sets, which SQL on API 28 cannot express (no window functions).
 *
 * <p>Sets that were not performed are loaded as well: they still occupy an ordinal, so ignoring them
 * would shift every comparison by one.
 */
public class PreviousSetRow {

    /** The PREVIOUS session_exercise this set belongs to. */
    @NonNull
    public String sessionExerciseId = "";

    public int position;

    /** 0 for a warm-up; null when the set has no technique, which means a normal working set. */
    @Nullable
    public Integer countsAsWorkingSet;

    @NonNull
    public SetStatus status = SetStatus.PENDING;

    @Nullable
    public Long weightGrams;

    @Nullable
    public Integer reps;

    @Nullable
    public Integer repsLeft;

    @Nullable
    public Integer repsRight;

    @Nullable
    public Integer durationSeconds;
}

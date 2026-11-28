package io.github.thiagojosetj.gym.domain.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.thiagojosetj.gym.domain.model.RepRange;
import io.github.thiagojosetj.gym.domain.model.Weight;

/**
 * One set of a session: what was planned, what was performed, and what the same set did last time
 * (PRODUCT_SPEC section 6.5). The three never overwrite each other.
 *
 * @param workingNumber   1-based number among the working sets, or null for a warm-up; this is what
 *                        the screen shows and what pairs sets with the previous session (section 11)
 * @param techniqueCode   badge of the technique ("AQ", "D"), null for a normal working set
 * @param countsAsWorkingSet false for a warm-up: out of volume and records (section 6.2)
 * @param values          what the user typed/confirmed; every field may be null
 * @param previous        the same set in the previous session, source of the suggestion; may be null
 * @param segments        the later drops of a drop-set or rest-pause, in order. They belong to THIS
 *                        set rather than standing beside it: their load and repetitions are summed
 *                        into it, and the whole thing still counts as one set (ADR-0037)
 */
public record LoggedSet(
        String id,
        int position,
        Integer workingNumber,
        String techniqueId,
        String techniqueCode,
        boolean countsAsWorkingSet,
        RepRange plannedReps,
        Weight plannedWeight,
        Integer plannedDurationSeconds,
        int plannedRestSeconds,
        SetValues values,
        SetStatus status,
        Long completedAt,
        String notes,
        SetValues previous,
        List<LoggedSet> segments) {

    public LoggedSet {
        if (values == null) {
            values = SetValues.EMPTY;
        }
        segments = segments == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(segments));
    }

    /** True when this set was taken past failure in drops (PRODUCT_SPEC section 9.1). */
    public boolean hasSegments() {
        return !segments.isEmpty();
    }

    public boolean isCompleted() {
        return status == SetStatus.COMPLETED;
    }

    public boolean isWarmUp() {
        return !countsAsWorkingSet;
    }

    /**
     * What to pre-fill when the user opens this set: the previous session's result, or else the plan.
     * It is only a suggestion - it becomes a result when the user confirms the set (ACT-03).
     *
     * <p>A planned range (8-10) does not pre-fill the repetitions: picking 8 for the user would be
     * inventing a result. A fixed plan (12) does, because that is an unambiguous intention.
     */
    public SetValues suggestion() {
        if (!values.isEmpty()) {
            return values; // the user already typed something: never overwrite it
        }
        if (previous != null && !previous.isEmpty()) {
            return previous;
        }
        Integer reps = plannedReps != null && plannedReps.isFixed() ? plannedReps.min() : null;
        return new SetValues(plannedWeight, reps, null, null, plannedDurationSeconds);
    }
}

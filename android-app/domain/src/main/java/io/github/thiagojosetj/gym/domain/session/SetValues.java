package io.github.thiagojosetj.gym.domain.session;

import io.github.thiagojosetj.gym.domain.model.SideMode;
import io.github.thiagojosetj.gym.domain.model.TrackingType;
import io.github.thiagojosetj.gym.domain.model.Weight;

/**
 * What the user typed into one set. Every field is optional because a set is filled in gradually,
 * and a null field means "not informed" — never zero (PRODUCT_SPEC section 6.5).
 *
 * @param weight     load, per implement for dumbbells, negative for assistance (section 6.4)
 * @param reps       repetitions when both sides are logged together
 * @param repsLeft   repetitions of the left side when logging per side
 * @param repsRight  repetitions of the right side when logging per side
 * @param durationSeconds time under tension for exercises measured in time
 */
public record SetValues(Weight weight, Integer reps, Integer repsLeft, Integer repsRight,
                        Integer durationSeconds) {

    public static final SetValues EMPTY = new SetValues(null, null, null, null, null);

    /** Nothing was informed: the set is still exactly as it was planned. */
    public boolean isEmpty() {
        return weight == null && reps == null && repsLeft == null && repsRight == null
                && durationSeconds == null;
    }

    public boolean hasAnyReps() {
        return reps != null || repsLeft != null || repsRight != null;
    }

    /**
     * Total repetitions of the set: the sum of both sides when they are logged separately.
     * Unilateral exercises count repetitions per side, so 10 per side is 20 in total
     * (PRODUCT_SPEC sections 6.4 and 9).
     */
    public Integer totalReps() {
        if (repsLeft != null || repsRight != null) {
            return (repsLeft == null ? 0 : repsLeft) + (repsRight == null ? 0 : repsRight);
        }
        return reps;
    }

    /**
     * Whether this set has everything its exercise needs to be considered performed. What is
     * required depends on how the exercise is measured, not on the fields that happen to be filled.
     *
     * @param sideMode whether the user is logging each side separately
     */
    public boolean isComplete(TrackingType tracking, SideMode sideMode) {
        if (tracking.usesDuration() && durationSeconds == null) {
            return false;
        }
        if (tracking.usesReps()) {
            if (sideMode == SideMode.PER_SIDE) {
                // Both sides are needed: "10 and nothing" is a half-filled set, not a result.
                if (repsLeft == null || repsRight == null) {
                    return false;
                }
            } else if (reps == null) {
                return false;
            }
        }
        // The load may legitimately be absent: body weight with no extra load, or an exercise
        // whose load is not measurable. Only reps and duration make a set "performed".
        return true;
    }

    /** True when some field was typed but the set still does not describe a performed set. */
    public boolean isPartial(TrackingType tracking, SideMode sideMode) {
        return !isEmpty() && !isComplete(tracking, sideMode);
    }
}

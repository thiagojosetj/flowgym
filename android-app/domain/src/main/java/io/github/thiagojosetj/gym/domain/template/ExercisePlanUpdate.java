package io.github.thiagojosetj.gym.domain.template;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.thiagojosetj.gym.domain.model.RepRange;
import io.github.thiagojosetj.gym.domain.model.SideMode;
import io.github.thiagojosetj.gym.domain.model.TrackingType;
import io.github.thiagojosetj.gym.domain.model.Weight;

/**
 * "Simple mode" edit of one template exercise: the same plan for every set
 * (e.g. 4 × 8–10 × 40 kg, 120 s rest). Per-set editing arrives with Phase 2's advanced editor.
 *
 * @param reps            null = no rep target
 * @param weight          null = no planned load
 * @param durationSeconds null unless the exercise is timed
 * @param notes           permanent note for this exercise in this template, may be null
 */
public record ExercisePlanUpdate(
        int setCount,
        RepRange reps,
        Weight weight,
        Integer durationSeconds,
        int restSeconds,
        String notes,
        SideMode sideMode) {

    /** Why an update cannot be applied. The UI maps each code to a localized message. */
    public enum Error {
        SET_COUNT_OUT_OF_RANGE,
        REST_OUT_OF_RANGE,
        WEIGHT_NOT_APPLICABLE,
        NEGATIVE_WEIGHT_NOT_ALLOWED,
        DURATION_NOT_APPLICABLE,
        DURATION_OUT_OF_RANGE,
        REPS_NOT_APPLICABLE,
        NOTES_TOO_LONG,
        PER_SIDE_REQUIRES_UNILATERAL
    }

    /** Validates this update against the exercise it targets. Empty list = valid. */
    public List<Error> validateFor(ExerciseRef exercise) {
        List<Error> errors = new ArrayList<>();
        TrackingType tracking = exercise.trackingType();
        if (setCount < 1 || setCount > TemplateRules.MAX_SETS_PER_EXERCISE) {
            errors.add(Error.SET_COUNT_OUT_OF_RANGE);
        }
        if (restSeconds < 0 || restSeconds > TemplateRules.MAX_REST_SECONDS) {
            errors.add(Error.REST_OUT_OF_RANGE);
        }
        if (weight != null) {
            if (!tracking.usesWeight()) {
                errors.add(Error.WEIGHT_NOT_APPLICABLE);
            } else if (weight.isNegative() && !tracking.allowsNegativeWeight()) {
                errors.add(Error.NEGATIVE_WEIGHT_NOT_ALLOWED);
            }
        }
        if (reps != null && !tracking.usesReps()) {
            errors.add(Error.REPS_NOT_APPLICABLE);
        }
        if (durationSeconds != null) {
            if (!tracking.usesDuration()) {
                errors.add(Error.DURATION_NOT_APPLICABLE);
            } else if (durationSeconds < 1 || durationSeconds > TemplateRules.MAX_DURATION_SECONDS) {
                errors.add(Error.DURATION_OUT_OF_RANGE);
            }
        }
        if (notes != null && notes.length() > TemplateRules.MAX_NOTES_LENGTH) {
            errors.add(Error.NOTES_TOO_LONG);
        }
        if (sideMode == SideMode.PER_SIDE && !exercise.isUnilateral()) {
            errors.add(Error.PER_SIDE_REQUIRES_UNILATERAL);
        }
        return Collections.unmodifiableList(errors);
    }
}

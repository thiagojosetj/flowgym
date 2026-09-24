package io.github.thiagojosetj.gym.domain.template;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.thiagojosetj.gym.domain.model.RepRange;
import io.github.thiagojosetj.gym.domain.model.SideMode;
import io.github.thiagojosetj.gym.domain.model.TrackingType;
import io.github.thiagojosetj.gym.domain.model.Weight;
import io.github.thiagojosetj.gym.domain.technique.TechniqueCatalog;
import io.github.thiagojosetj.gym.domain.technique.TechniqueScope;
import io.github.thiagojosetj.gym.domain.technique.TrainingTechnique;

/**
 * The new plan for one template exercise: one {@link SetSpec} per set (so sets may differ, e.g. a
 * warm-up then three working sets, or a pyramid), plus the values that belong to the exercise.
 *
 * @param notes permanent note for this exercise in this template, may be null
 */
public record ExercisePlanUpdate(List<SetSpec> sets, int restSeconds, String notes, SideMode sideMode) {

    /** Why an update cannot be applied. The UI maps each code to a localized message. */
    public enum Error {
        NO_SETS,
        TOO_MANY_SETS,
        REST_OUT_OF_RANGE,
        WEIGHT_NOT_APPLICABLE,
        NEGATIVE_WEIGHT_NOT_ALLOWED,
        DURATION_NOT_APPLICABLE,
        DURATION_OUT_OF_RANGE,
        REPS_NOT_APPLICABLE,
        NOTES_TOO_LONG,
        PER_SIDE_REQUIRES_UNILATERAL,
        UNKNOWN_TECHNIQUE,
        TECHNIQUE_NOT_FOR_SETS
    }

    public ExercisePlanUpdate {
        sets = sets == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(sets));
    }

    /** Every set with the same plan — the common case ("4 × 8–10 × 40 kg"). */
    public static ExercisePlanUpdate uniform(int setCount, RepRange reps, Weight weight,
                                             Integer durationSeconds, int restSeconds, String notes,
                                             SideMode sideMode) {
        List<SetSpec> sets = new ArrayList<>(Math.max(0, setCount));
        for (int i = 0; i < setCount; i++) {
            sets.add(SetSpec.of(reps, weight, durationSeconds));
        }
        return new ExercisePlanUpdate(sets, restSeconds, notes, sideMode);
    }

    public int setCount() {
        return sets.size();
    }

    /**
     * Validates this update against the exercise it targets and the available techniques.
     * Empty list = valid.
     */
    public List<Error> validateFor(ExerciseRef exercise, TechniqueCatalog techniques) {
        List<Error> errors = new ArrayList<>();
        TrackingType tracking = exercise.trackingType();
        if (sets.isEmpty()) {
            errors.add(Error.NO_SETS);
        } else if (sets.size() > TemplateRules.MAX_SETS_PER_EXERCISE) {
            errors.add(Error.TOO_MANY_SETS);
        }
        if (restSeconds < 0 || restSeconds > TemplateRules.MAX_REST_SECONDS) {
            errors.add(Error.REST_OUT_OF_RANGE);
        }
        for (SetSpec set : sets) {
            checkSet(set, tracking, techniques, errors);
        }
        if (notes != null && notes.length() > TemplateRules.MAX_NOTES_LENGTH) {
            errors.add(Error.NOTES_TOO_LONG);
        }
        if (sideMode == SideMode.PER_SIDE && !exercise.isUnilateral()) {
            errors.add(Error.PER_SIDE_REQUIRES_UNILATERAL);
        }
        return Collections.unmodifiableList(distinct(errors));
    }

    private static void checkSet(SetSpec set, TrackingType tracking, TechniqueCatalog techniques,
                                 List<Error> errors) {
        if (set.weight() != null) {
            if (!tracking.usesWeight()) {
                errors.add(Error.WEIGHT_NOT_APPLICABLE);
            } else if (set.weight().isNegative() && !tracking.allowsNegativeWeight()) {
                errors.add(Error.NEGATIVE_WEIGHT_NOT_ALLOWED);
            }
        }
        if (set.reps() != null && !tracking.usesReps()) {
            errors.add(Error.REPS_NOT_APPLICABLE);
        }
        if (set.durationSeconds() != null) {
            if (!tracking.usesDuration()) {
                errors.add(Error.DURATION_NOT_APPLICABLE);
            } else if (set.durationSeconds() < 1 || set.durationSeconds() > TemplateRules.MAX_DURATION_SECONDS) {
                errors.add(Error.DURATION_OUT_OF_RANGE);
            }
        }
        if (set.techniqueId() != null) {
            TrainingTechnique technique = techniques.byId(set.techniqueId());
            if (technique == null) {
                errors.add(Error.UNKNOWN_TECHNIQUE);
            } else if (technique.scope() != TechniqueScope.SET) {
                // Pyramids belong to the exercise and supersets to a group of exercises.
                errors.add(Error.TECHNIQUE_NOT_FOR_SETS);
            }
        }
    }

    /** One message per problem, even when several sets share it. */
    private static List<Error> distinct(List<Error> errors) {
        List<Error> unique = new ArrayList<>(errors.size());
        for (Error error : errors) {
            if (!unique.contains(error)) {
                unique.add(error);
            }
        }
        return unique;
    }
}

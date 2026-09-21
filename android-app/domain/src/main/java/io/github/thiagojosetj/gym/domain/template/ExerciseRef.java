package io.github.thiagojosetj.gym.domain.template;

import java.util.Objects;

import io.github.thiagojosetj.gym.domain.model.Laterality;
import io.github.thiagojosetj.gym.domain.model.LoadBasis;
import io.github.thiagojosetj.gym.domain.model.TrackingType;

/**
 * The subset of a library exercise that a template needs: identity, how it is logged, and a label
 * for display. Rules that depend on the exercise type (e.g. "weight not applicable") read it here.
 *
 * @param primaryMuscleName display name of the highlighted primary muscle, may be null
 */
public record ExerciseRef(
        String id,
        String name,
        TrackingType trackingType,
        LoadBasis loadBasis,
        int implementCount,
        Laterality laterality,
        String primaryMuscleName) {

    public ExerciseRef {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(trackingType, "trackingType");
        Objects.requireNonNull(loadBasis, "loadBasis");
        Objects.requireNonNull(laterality, "laterality");
        if (implementCount < 1) {
            throw new IllegalArgumentException("implementCount must be >= 1");
        }
    }

    public boolean isUnilateral() {
        return laterality == Laterality.UNILATERAL;
    }

    /** True when the typed load is per dumbbell/kettlebell ("kg por halter"). */
    public boolean isLoadPerImplement() {
        return loadBasis == LoadBasis.PER_IMPLEMENT;
    }
}

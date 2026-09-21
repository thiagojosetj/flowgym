package io.github.thiagojosetj.gym.domain.template;

import java.util.Objects;

import io.github.thiagojosetj.gym.domain.model.RepRange;
import io.github.thiagojosetj.gym.domain.model.Weight;

/**
 * One planned set of a template exercise. Immutable; edits create a new instance with the same id.
 *
 * @param reps                 planned repetitions, null when there is no rep target
 * @param weight               planned load, null when not planned
 * @param durationSeconds      planned time for timed exercises, null otherwise
 * @param restSecondsOverride  rest after this set; null means "use the exercise's rest"
 */
public record SetPlan(
        String id,
        RepRange reps,
        Weight weight,
        Integer durationSeconds,
        Integer restSecondsOverride) {

    public SetPlan {
        Objects.requireNonNull(id, "id");
    }
}

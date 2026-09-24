package io.github.thiagojosetj.gym.domain.template;

import io.github.thiagojosetj.gym.domain.model.RepRange;
import io.github.thiagojosetj.gym.domain.model.Weight;

/**
 * What the editor wants one set to become. It has no id: {@link TemplateDraft} keeps the ids of the
 * existing sets (by position) so references stay stable across saves.
 *
 * @param techniqueId set technique id, null = normal working set
 */
public record SetSpec(RepRange reps, Weight weight, Integer durationSeconds, String techniqueId) {

    /** A set with no technique, i.e. a normal working set. */
    public static SetSpec of(RepRange reps, Weight weight, Integer durationSeconds) {
        return new SetSpec(reps, weight, durationSeconds, null);
    }

    public SetSpec withTechnique(String newTechniqueId) {
        return new SetSpec(reps, weight, durationSeconds, newTechniqueId);
    }
}

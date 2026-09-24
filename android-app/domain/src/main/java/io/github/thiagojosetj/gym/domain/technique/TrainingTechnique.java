package io.github.thiagojosetj.gym.domain.technique;

import java.util.Objects;

/**
 * A training method the user can attach to a set (or, later, to an exercise or a group).
 *
 * @param code               short badge shown in the UI ("AQ", "D", "RP"); unique
 * @param countsAsWorkingSet false for warm-up: those sets stay out of volume and records
 * @param instructions       how to perform and record it; shown by the ⓘ button
 */
public record TrainingTechnique(
        String id,
        String code,
        String name,
        TechniqueScope scope,
        boolean countsAsWorkingSet,
        String description,
        String instructions) {

    public TrainingTechnique {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(scope, "scope");
    }
}

package io.github.thiagojosetj.gym.ui.templates.editor;

import io.github.thiagojosetj.gym.domain.model.RepRange;
import io.github.thiagojosetj.gym.domain.model.Weight;

/**
 * One planned set as the editor shows it.
 *
 * @param techniqueId   null = normal working set
 * @param techniqueCode badge to display ("AQ", "D"…); null when there is no technique, or when the
 *                      technique id no longer exists in the catalog
 */
public record TemplateSetItem(
        String setId,
        RepRange reps,
        Weight weight,
        Integer durationSeconds,
        String techniqueId,
        String techniqueCode) {
}

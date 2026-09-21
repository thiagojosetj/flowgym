package io.github.thiagojosetj.gym.domain.library;

/**
 * One row of the exercise library.
 *
 * @param primaryMuscleName    highlighted primary muscle (usually a subgroup), may be null
 * @param primaryGroupName     its top-level group, may be null
 * @param primaryEquipmentName may be null (e.g. body weight exercises without equipment)
 */
public record ExerciseSummary(
        String id,
        String name,
        String primaryMuscleName,
        String primaryGroupName,
        String primaryEquipmentName) {
}

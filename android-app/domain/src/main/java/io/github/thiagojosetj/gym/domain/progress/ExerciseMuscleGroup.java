package io.github.thiagojosetj.gym.domain.progress;

import io.github.thiagojosetj.gym.domain.model.MuscleRole;

/**
 * One muscle GROUP an exercise trains, and in which role (PRODUCT_SPEC PRG-04).
 *
 * <p>Group, not subgroup. "Did I train back enough this week" is a question about the back, and an
 * exercise that hits the lats and the rhombuds is still one exercise doing one set of back work -
 * counting it twice because it names two subgroups of the same group would inflate every number
 * the screen shows.
 *
 * @param sortOrder the catalogue's own order for the group, so the screen does not invent one
 */
public record ExerciseMuscleGroup(String exerciseId, String muscleGroupId, String name,
                                  int sortOrder, MuscleRole role) {
}

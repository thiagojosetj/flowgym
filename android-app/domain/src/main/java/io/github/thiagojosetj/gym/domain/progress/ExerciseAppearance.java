package io.github.thiagojosetj.gym.domain.progress;

import io.github.thiagojosetj.gym.domain.session.SessionExercise;

/**
 * One time an exercise was performed: the session it happened in, and that session's own snapshot
 * of the exercise (PRODUCT_SPEC PRG-03).
 *
 * @param localDate the day as it was LIVED, which is what the screen and any chart plot against;
 *                  never derived from {@code startedAt} and the current zone (section 11)
 * @param startedAt only to order two appearances that share a day
 * @param exercise  that session's snapshot, so a load basis or laterality that has changed since
 *                  does not rewrite what the numbers meant that day
 */
public record ExerciseAppearance(String sessionId, String localDate, long startedAt,
                                 SessionExercise exercise) {
}

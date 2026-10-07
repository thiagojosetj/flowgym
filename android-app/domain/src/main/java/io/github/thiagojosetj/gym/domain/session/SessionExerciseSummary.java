package io.github.thiagojosetj.gym.domain.session;

/**
 * One exercise of a finished session, rolled up (PRODUCT_SPEC HIS-01 and HIS-03).
 *
 * <p>The name is the snapshot taken when the session started, so a workout performed before the
 * exercise was renamed still reads the way it did that day (docs/DATABASE.md section 4).
 *
 * <p>The totals come from {@link SessionVolume} rather than being counted again here. "N sets not
 * included in volume" has to mean exactly the same thing on this screen as it meant in the dialog
 * shown when the workout was finished; two implementations would eventually disagree, and the one
 * the user saw first is the one they would trust.
 */
public record SessionExerciseSummary(
        String sessionExerciseId,
        String exerciseId,
        int position,
        String name,
        int performedSets,
        int skippedSets,
        SessionVolume.Totals totals) {

    public static SessionExerciseSummary of(SessionExercise exercise) {
        int performed = 0;
        int skipped = 0;
        for (LoggedSet set : exercise.sets()) {
            if (set.isCompleted()) {
                performed++;
            } else if (set.status() == SetStatus.SKIPPED) {
                skipped++;
            }
        }
        return new SessionExerciseSummary(exercise.id(), exercise.exerciseId(), exercise.position(),
                exercise.name(), performed, skipped, SessionVolume.ofExercise(exercise));
    }

    /** True when this exercise contributed load volume at all (PRODUCT_SPEC section 9). */
    public boolean hasVolume() {
        return totals.loadGrams() > 0L;
    }
}

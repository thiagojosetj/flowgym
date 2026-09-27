package io.github.thiagojosetj.gym.domain.session;

/**
 * What the user sees right after finishing (ACT-09). The full summary screen belongs to phase 4
 * (HIS-01); this is the honest subset that can be computed from the session alone.
 *
 * @param totalMs          end - start
 * @param effectiveMs      total minus the pauses
 * @param performedSets    sets confirmed as done
 * @param skippedSets      sets the user did not perform (kept in history as planned-but-not-done)
 * @param totalReps        repetitions of every performed set, both sides counted (section 9)
 * @param durationSeconds  time under tension of performed timed sets
 * @param volumeGrams      load volume, section 9 - body weight and timed sets are NOT in here
 * @param setsOutsideVolume performed sets that legitimately have no load volume, so the screen can
 *                          say so instead of letting the total look complete
 */
public record SessionSummary(
        String sessionId,
        String name,
        long totalMs,
        long effectiveMs,
        int exercises,
        int performedSets,
        int skippedSets,
        int totalReps,
        int durationSeconds,
        long volumeGrams,
        int setsOutsideVolume) {

    public static SessionSummary of(ActiveSession session, long now) {
        SessionVolume.Totals totals = session.totals();
        int skipped = 0;
        for (SessionExercise exercise : session.exercises()) {
            for (LoggedSet set : exercise.sets()) {
                if (set.status() == SetStatus.SKIPPED) {
                    skipped++;
                }
            }
        }
        SessionHeader header = session.header();
        return new SessionSummary(
                header.id(),
                header.name(),
                header.clock().totalMs(now),
                header.clock().effectiveMs(now),
                session.exercises().size(),
                session.completedSets(),
                skipped,
                totals.totalReps(),
                totals.durationSeconds(),
                totals.loadGrams(),
                totals.excludedSets());
    }

    public boolean hasVolume() {
        return volumeGrams > 0;
    }
}

package io.github.thiagojosetj.gym.domain.session;

/**
 * This session next to the previous session of the same workout (PRODUCT_SPEC section 11).
 *
 * <p>"Previous" here means the last completed session started from the same template. That is a
 * different question from the "previous" shown next to each set while training, which is the last
 * completed session containing that exercise, from any workout, frozen when this session started
 * (ADR-0033). Both are useful and they are not interchangeable, so they stay separate.
 *
 * <p>Four metrics, and no verdict. A heavier session is not automatically a better one - more
 * volume with fewer reps may be exactly what was planned - so the app presents the numbers and
 * leaves the reading to the person (PRODUCT_SPEC section 11).
 */
public record SessionComparison(
        String previousSessionId,
        long previousStartedAt,
        MetricChange volumeGrams,
        MetricChange performedSets,
        MetricChange totalReps,
        MetricChange effectiveMs) {

    /**
     * @param previous          the earlier session's summary, or null when this workout had never
     *                          been performed before - there is nothing to compare against, and the
     *                          screen says so rather than comparing against zero
     * @param previousStartedAt when that earlier session started, so the screen can name the day it
     *                          is comparing with
     * @return null when there is no previous session
     */
    public static SessionComparison between(SessionSummary current, SessionSummary previous,
                                            long previousStartedAt) {
        if (current == null || previous == null) {
            return null;
        }
        return new SessionComparison(
                previous.sessionId(),
                previousStartedAt,
                new MetricChange(current.volumeGrams(), previous.volumeGrams()),
                new MetricChange(current.performedSets(), previous.performedSets()),
                new MetricChange(current.totalReps(), previous.totalReps()),
                new MetricChange(current.effectiveMs(), previous.effectiveMs()));
    }
}

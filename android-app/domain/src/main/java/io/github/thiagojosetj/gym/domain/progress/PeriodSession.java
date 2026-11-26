package io.github.thiagojosetj.gym.domain.progress;

import io.github.thiagojosetj.gym.domain.session.ActiveSession;
import io.github.thiagojosetj.gym.domain.session.SessionVolume;

/**
 * One finished session of a period, as a point a chart can draw (PRODUCT_SPEC PRG-01).
 *
 * <p>The totals come from {@link SessionVolume} - the same rules, applied to one session instead
 * of to the collection - so a point on the chart and the period's total above it can never be
 * two different opinions of the same sets.
 *
 * <p>There is no "load" here, and there should not be. A session is many exercises, and the
 * heaviest weight in a workout of squats and curls is a number about the squats, not about the
 * workout. Volume, sets and repetitions are the three that mean something for a whole session.
 *
 * @param localDate the day as it was LIVED, which is why this is built where the row is read:
 *                  the domain has the session's clock but not its time zone, and a chart labelled
 *                  from the device's zone would move the past when the phone travels
 */
public record PeriodSession(String sessionId, String localDate, long startedAt,
                            int performedSets, int totalReps, long volumeGrams) {

    public static PeriodSession of(String localDate, ActiveSession session) {
        SessionVolume.Totals totals = SessionVolume.of(session.exercises());
        return new PeriodSession(session.header().id(), localDate,
                session.header().clock().startedAt(), session.completedSets(),
                totals.totalReps(), totals.loadGrams());
    }
}

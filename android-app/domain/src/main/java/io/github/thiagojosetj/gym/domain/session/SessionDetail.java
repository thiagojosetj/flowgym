package io.github.thiagojosetj.gym.domain.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Everything the screen of one finished session shows (PRODUCT_SPEC HIS-01, HIS-03 and HIS-04),
 * built only from what that session itself stored.
 *
 * <p>Every exercise, set and planned value comes from the snapshot the session took when it
 * started, never from the template as it stands today: editing a workout must not rewrite what
 * happened last Tuesday (PRODUCT_SPEC section 2.3, docs/DATABASE.md section 4).
 *
 * <p>This is loaded once rather than observed, because a finished session cannot change - there is
 * no later value to wait for. That also removes a whole class of bug: nothing here can be rendered
 * half-built while a second query is still on its way.
 *
 * @param rating     the 1-5 rating the person gave, or null when they gave none. Null is not zero
 * @param comparison the previous session of the same workout, or null when this was the first time
 *                   that workout was performed
 */
public record SessionDetail(
        ActiveSession session,
        SessionSummary summary,
        String localDate,
        String timeZone,
        Integer rating,
        List<SessionExerciseSummary> exercises,
        SessionComparison comparison) {

    public SessionDetail {
        exercises = exercises == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(exercises));
    }

    /**
     * @param previous          summary of the previous session of the same workout, or null
     * @param previousExercises that session's exercises, for the exercise-by-exercise comparison
     *                          (HIS-04). Null asks for the totals only, which is all a caller that
     *                          did not load the earlier session in full can honestly provide
     * @param previousStartedAt when that session started; ignored when {@code previous} is null
     * @param previousTimeZone  the zone THAT session was performed in, which is not necessarily
     *                          this one's: the same person can perform the same workout either side
     *                          of a flight, and the day each one belongs to is decided where it
     *                          happened
     */
    public static SessionDetail of(ActiveSession session, String localDate, String timeZone,
                                   Integer rating, SessionSummary previous,
                                   List<SessionExercise> previousExercises,
                                   long previousStartedAt, String previousTimeZone) {
        SessionClock clock = session.header().clock();
        // A finished session's clock ignores "now" entirely; passing the end instant keeps the call
        // honest if this is ever handed a session that is somehow still running.
        long now = clock.endedAt() == null ? clock.startedAt() : clock.endedAt();
        SessionSummary summary = SessionSummary.of(session, now);
        List<SessionExerciseSummary> rollups = new ArrayList<>(session.exercises().size());
        for (SessionExercise exercise : session.exercises()) {
            rollups.add(SessionExerciseSummary.of(exercise));
        }
        return new SessionDetail(session, summary, localDate, timeZone, rating, rollups,
                SessionComparison.between(summary, session.exercises(), previous,
                        previousExercises, previousStartedAt, previousTimeZone));
    }

    public boolean hasComparison() {
        return comparison != null;
    }
}

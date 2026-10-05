package io.github.thiagojosetj.gym.domain.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 *
 * <p>Below the totals, the same question per exercise (HIS-04). Three lists rather than one,
 * because three different things can happen to an exercise between two sessions and only one of
 * them is a change in numbers:
 *
 * <ul>
 *   <li>{@code exercises} - in both sessions, so there is something to compare;</li>
 *   <li>{@code addedExercises} - done this time and not last time. Comparing it against zero
 *       would announce a gain that is really a new exercise;</li>
 *   <li>{@code droppedExercises} - done last time and not this time. Named, never dropped
 *       silently: work the person is no longer doing is exactly what they would want told.</li>
 * </ul>
 *
 * @param addedExercises   names, in this session's order
 * @param droppedExercises names as the EARLIER session recorded them - that is what it was called
 *                         that day, and this session has no name for an exercise it did not have
 */
public record SessionComparison(
        String previousSessionId,
        long previousStartedAt,
        String previousTimeZone,
        MetricChange volumeGrams,
        MetricChange performedSets,
        MetricChange totalReps,
        MetricChange effectiveMs,
        List<ExerciseComparison> exercises,
        List<String> addedExercises,
        List<String> droppedExercises) {

    public SessionComparison {
        exercises = immutable(exercises);
        addedExercises = immutable(addedExercises);
        droppedExercises = immutable(droppedExercises);
    }

    private static <T> List<T> immutable(List<T> values) {
        return values == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(values));
    }

    /**
     * @param previous          the earlier session's summary, or null when this workout had never
     *                          been performed before - there is nothing to compare against, and the
     *                          screen says so rather than comparing against zero
     * @param previousStartedAt when that earlier session started, so the screen can name the day it
     *                          is comparing with
     * @param previousTimeZone  the zone THAT session was performed in. It travels with the instant
     *                          because it is the only thing that can turn it back into the right
     *                          day: naming it in the zone of the session being viewed is how the
     *                          summary ends up claiming a day on which the workout did not happen,
     *                          and disagreeing with the history list about the very same session
     * @return null when there is no previous session
     */
    public static SessionComparison between(SessionSummary current, SessionSummary previous,
                                            long previousStartedAt, String previousTimeZone) {
        return between(current, null, previous, null, previousStartedAt, previousTimeZone);
    }

    /**
     * The same, plus the exercise-by-exercise part (HIS-04).
     *
     * @param currentExercises  this session's exercises, in its own order; null for totals only
     * @param previousExercises the earlier session's exercises; null for totals only
     */
    public static SessionComparison between(SessionSummary current,
                                            List<SessionExercise> currentExercises,
                                            SessionSummary previous,
                                            List<SessionExercise> previousExercises,
                                            long previousStartedAt, String previousTimeZone) {
        if (current == null || previous == null) {
            return null;
        }
        List<ExerciseComparison> paired = new ArrayList<>();
        List<String> added = new ArrayList<>();
        List<String> dropped = new ArrayList<>();
        matchExercises(currentExercises, previousExercises, paired, added, dropped);
        return new SessionComparison(
                previous.sessionId(),
                previousStartedAt,
                previousTimeZone,
                new MetricChange(current.volumeGrams(), previous.volumeGrams()),
                new MetricChange(current.performedSets(), previous.performedSets()),
                new MetricChange(current.totalReps(), previous.totalReps()),
                new MetricChange(current.effectiveMs(), previous.effectiveMs()),
                paired, added, dropped);
    }

    /**
     * Lines the two sessions' exercises up by exercise, then by nth occurrence of it.
     *
     * <p>Not by position: reordering a workout does not make an exercise a different one, and
     * comparing today's first exercise with last time's first exercise would quietly compare a
     * bench press with a row the day someone moves a block up the list.
     */
    private static void matchExercises(List<SessionExercise> currentExercises,
                                       List<SessionExercise> previousExercises,
                                       List<ExerciseComparison> paired, List<String> added,
                                       List<String> dropped) {
        if (currentExercises == null || previousExercises == null) {
            return;
        }
        Map<String, List<SessionExercise>> remaining = new LinkedHashMap<>();
        for (SessionExercise exercise : previousExercises) {
            remaining.computeIfAbsent(exercise.exerciseId(), key -> new ArrayList<>()).add(exercise);
        }
        for (SessionExercise exercise : currentExercises) {
            List<SessionExercise> candidates = remaining.get(exercise.exerciseId());
            if (candidates == null || candidates.isEmpty()) {
                added.add(exercise.name());
                continue;
            }
            paired.add(ExerciseComparison.between(exercise, candidates.remove(0)));
        }
        for (List<SessionExercise> leftovers : remaining.values()) {
            for (SessionExercise exercise : leftovers) {
                dropped.add(exercise.name());
            }
        }
    }
}

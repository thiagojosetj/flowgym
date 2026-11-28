package io.github.thiagojosetj.gym.domain.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.github.thiagojosetj.gym.domain.model.LoadBasis;
import io.github.thiagojosetj.gym.domain.model.Laterality;
import io.github.thiagojosetj.gym.domain.model.RepRange;
import io.github.thiagojosetj.gym.domain.model.SideMode;
import io.github.thiagojosetj.gym.domain.model.TrackingType;
import io.github.thiagojosetj.gym.domain.model.Weight;
import io.github.thiagojosetj.gym.domain.model.WeightUnit;

/**
 * Which exercise of this session goes next to which exercise of the one before it (HIS-04).
 *
 * <p>Getting this wrong is not a cosmetic bug: a bench press compared with a row prints a drop or
 * a gain that nobody performed, and the screen gives no hint that the two rows are not the same
 * movement.
 */
public class SessionComparisonExercisesTest {

    @Test
    public void exercisesPairByWhatTheyAreAndNotByWhereTheySitInTheList() {
        // The same two exercises, reordered between the two sessions. Pairing by position would
        // compare the bench press with the row.
        SessionComparison comparison = between(
                Arrays.asList(row(working("a", kg(60), 10)), bench(working("b", kg(40), 10))),
                Arrays.asList(bench(working("x", kg(35), 10)), row(working("y", kg(55), 10))));

        assertEquals(2, comparison.exercises().size());
        assertEquals("Remada curvada", comparison.exercises().get(0).name());
        assertEquals("Remada curvada", comparison.exercises().get(0).previous().name());
        assertEquals("Supino reto com barra", comparison.exercises().get(1).name());
        assertEquals("Supino reto com barra", comparison.exercises().get(1).previous().name());
    }

    @Test
    public void anExerciseDoneOnlyThisTimeIsNamedRatherThanComparedWithZero() {
        SessionComparison comparison = between(
                Arrays.asList(bench(working("a", kg(40), 10)), row(working("b", kg(60), 10))),
                Collections.singletonList(bench(working("x", kg(40), 10))));

        assertEquals(1, comparison.exercises().size());
        assertEquals(Collections.singletonList("Remada curvada"), comparison.addedExercises());
        assertTrue(comparison.droppedExercises().isEmpty());
    }

    @Test
    public void anExerciseDroppedSinceLastTimeIsSaidOutLoud() {
        // Nothing is discarded in silence: work the person stopped doing is exactly what they
        // would want to be told about.
        SessionComparison comparison = between(
                Collections.singletonList(bench(working("a", kg(40), 10))),
                Arrays.asList(bench(working("x", kg(40), 10)), row(working("y", kg(60), 10))));

        assertEquals(1, comparison.exercises().size());
        assertEquals(Collections.singletonList("Remada curvada"), comparison.droppedExercises());
        assertTrue(comparison.addedExercises().isEmpty());
    }

    @Test
    public void theSameExerciseTwiceInAWorkoutPairsFirstWithFirstAndSecondWithSecond() {
        SessionExercise firstBlock = bench(working("a", kg(40), 10));
        SessionExercise secondBlock = bench(working("b", kg(30), 15));
        SessionExercise firstBefore = bench(working("x", kg(35), 10));
        SessionExercise secondBefore = bench(working("y", kg(25), 15));

        SessionComparison comparison = between(Arrays.asList(firstBlock, secondBlock),
                Arrays.asList(firstBefore, secondBefore));

        assertEquals(2, comparison.exercises().size());
        assertEquals("a", comparison.exercises().get(0).sets().get(0).current().id());
        assertEquals("x", comparison.exercises().get(0).sets().get(0).previous().id());
        assertEquals("b", comparison.exercises().get(1).sets().get(0).current().id());
        assertEquals("y", comparison.exercises().get(1).sets().get(0).previous().id());
    }

    @Test
    public void aSecondBlockOfTheSameExerciseWithNoCounterpartIsNewRatherThanPaired() {
        SessionComparison comparison = between(
                Arrays.asList(bench(working("a", kg(40), 10)), bench(working("b", kg(30), 15))),
                Collections.singletonList(bench(working("x", kg(35), 10))));

        assertEquals(1, comparison.exercises().size());
        assertEquals(Collections.singletonList("Supino reto com barra"),
                comparison.addedExercises());
    }

    @Test
    public void theExerciseNameIsTheOneEachSessionRecordedThatDay() {
        // Renaming an exercise in the library must not rewrite what the earlier session called it.
        SessionExercise now = named("Supino reto (barra)", working("a", kg(40), 10));
        SessionExercise then = named("Supino reto", working("x", kg(40), 10));

        SessionComparison comparison = between(Collections.singletonList(now),
                Collections.singletonList(then));

        assertEquals("Supino reto (barra)", comparison.exercises().get(0).name());
        assertEquals("Supino reto", comparison.exercises().get(0).previous().name());
    }

    @Test
    public void askingForTheTotalsOnlyLeavesTheExerciseListsEmptyRatherThanWrong() {
        SessionComparison comparison = SessionComparison.between(
                summary("now", 400_000L), null, summary("prev", 350_000L), null, 1L, "UTC");

        assertTrue(comparison.exercises().isEmpty());
        assertTrue(comparison.addedExercises().isEmpty());
        assertTrue(comparison.droppedExercises().isEmpty());
        assertEquals(MetricChange.Direction.UP, comparison.volumeGrams().direction());
    }

    @Test
    public void theListsHandedOutCannotBeModified() {
        SessionComparison comparison = between(
                Collections.singletonList(bench(working("a", kg(40), 10))),
                Collections.singletonList(row(working("x", kg(60), 10))));

        for (List<?> list : Arrays.asList(comparison.exercises(), comparison.addedExercises(),
                comparison.droppedExercises())) {
            try {
                list.clear();
                throw new AssertionError("a lista devia ser imutavel");
            } catch (UnsupportedOperationException expected) {
                // what we want
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private static SessionComparison between(List<SessionExercise> now,
                                             List<SessionExercise> before) {
        return SessionComparison.between(summary("now", 0L), now, summary("prev", 0L), before,
                1_700_000_000_000L, "America/Sao_Paulo");
    }

    private static SessionSummary summary(String id, long volumeGrams) {
        return new SessionSummary(id, "Push A", 3_600_000L, 3_000_000L, 1, 3, 0, 30, 0,
                volumeGrams, 0);
    }

    private static Weight kg(double value) {
        return Weight.of(value, WeightUnit.KILOGRAM);
    }

    private static LoggedSet working(String id, Weight weight, int reps) {
        return new LoggedSet(id, 0, 1, null, null, true, RepRange.exactly(10), null, null, 90,
                new SetValues(weight, reps, null, null, null), SetStatus.COMPLETED, 1L, null, null,
                Collections.emptyList());
    }

    private static SessionExercise bench(LoggedSet... sets) {
        return exercise("ex-bench", "Supino reto com barra", sets);
    }

    private static SessionExercise named(String name, LoggedSet... sets) {
        return exercise("ex-bench", name, sets);
    }

    private static SessionExercise row(LoggedSet... sets) {
        return exercise("ex-row", "Remada curvada", sets);
    }

    private static SessionExercise exercise(String exerciseId, String name, LoggedSet... sets) {
        return new SessionExercise("se-" + exerciseId + "-" + sets[0].id(), exerciseId, 0, name,
                TrackingType.WEIGHT_REPS, LoadBasis.TOTAL, 1, Laterality.BILATERAL,
                SideMode.COMBINED, 90, null, null, null, new ArrayList<>(Arrays.asList(sets)));
    }
}

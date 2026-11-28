package io.github.thiagojosetj.gym.domain.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
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
 * One exercise of a session next to the same exercise of the one before it (PRODUCT_SPEC HIS-04).
 *
 * <p>What is being protected here is the pairing. Numbers that belong to different sets, placed
 * side by side, read as progress or as a loss that never happened - and nothing on the screen
 * would say otherwise.
 */
public class ExerciseComparisonTest {

    @Test
    public void setsPairByTheirOrdinalAmongWorkingSetsAndNotByPosition() {
        // Today starts with a warm-up. Pairing by raw position would put today's first WORKING set
        // against last time's second one, and every row would be off by one.
        SessionExercise today = bench(warmUp("w", kg(20), 15),
                working("a", kg(40), 10), working("b", kg(40), 10));
        SessionExercise lastTime = bench(working("x", kg(35), 10), working("y", kg(35), 8));

        List<ExerciseComparison.SetPair> pairs = ExerciseComparison.between(today, lastTime).sets();

        assertEquals(2, pairs.size());
        assertEquals("a", pairs.get(0).current().id());
        assertEquals("x", pairs.get(0).previous().id());
        assertEquals("b", pairs.get(1).current().id());
        assertEquals("y", pairs.get(1).previous().id());
    }

    @Test
    public void aWarmUpIsNeverComparedBecauseItIsNotAResult() {
        SessionExercise today = bench(warmUp("w", kg(20), 15), working("a", kg(40), 10));
        SessionExercise lastTime = bench(warmUp("w0", kg(20), 15), working("x", kg(35), 10));

        List<ExerciseComparison.SetPair> pairs = ExerciseComparison.between(today, lastTime).sets();

        assertEquals(1, pairs.size());
        assertEquals("a", pairs.get(0).current().id());
    }

    @Test
    public void aSetLastTimeHadAndTodayDidNotIsListedRatherThanDropped() {
        SessionExercise today = bench(working("a", kg(40), 10));
        SessionExercise lastTime = bench(working("x", kg(35), 10), working("y", kg(35), 8));

        List<ExerciseComparison.SetPair> pairs = ExerciseComparison.between(today, lastTime).sets();

        assertEquals("a quarta serie da semana passada nao pode sumir da tela", 2, pairs.size());
        assertTrue(pairs.get(1).isUnpaired());
        assertNull(pairs.get(1).current());
        assertEquals("y", pairs.get(1).previous().id());
        assertNull(pairs.get(1).volumeGrams());
    }

    @Test
    public void aSetTodayThatHadNoCounterpartIsShownWithNothingBesideIt() {
        SessionExercise today = bench(working("a", kg(40), 10), working("b", kg(40), 8));
        SessionExercise lastTime = bench(working("x", kg(35), 10));

        List<ExerciseComparison.SetPair> pairs = ExerciseComparison.between(today, lastTime).sets();

        assertEquals(2, pairs.size());
        assertTrue(pairs.get(1).isUnpaired());
        assertNull(pairs.get(1).previous());
        assertNull("a quarta serie inedita nao e um ganho: nao havia quarta serie",
                pairs.get(1).volumeGrams());
    }

    @Test
    public void theVolumeOfASetIncludesTheDropsItWasTakenThrough() {
        // 40 kg x 10 = 400 kg, plus a drop of 30 kg x 8 = 240 kg. Comparing only the first part
        // would report a fall against a straight set that never happened.
        LoggedSet withDrop = withSegments(working("a", kg(40), 10),
                working("a-drop", kg(30), 8));
        SessionExercise today = bench(withDrop);
        SessionExercise lastTime = bench(working("x", kg(40), 10));

        ExerciseComparison comparison = ExerciseComparison.between(today, lastTime);

        assertEquals(640_000L, comparison.sets().get(0).volumeGrams().current());
        assertEquals(400_000L, comparison.sets().get(0).volumeGrams().previous());
        assertEquals(MetricChange.Direction.UP, comparison.volumeGrams().direction());
    }

    @Test
    public void anExerciseWithNoLoadOnEitherSideHasNoVolumeToCompare() {
        // Pull-ups by body weight. "=" would read as "the same amount of load", and there is no
        // load at all - the repetitions are the thing that moved.
        SessionExercise today = pullUps(working("a", null, 12));
        SessionExercise lastTime = pullUps(working("x", null, 10));

        ExerciseComparison comparison = ExerciseComparison.between(today, lastTime);

        assertFalse(comparison.hasVolume());
        assertNull(comparison.sets().get(0).volumeGrams());
        assertEquals(MetricChange.Direction.UP, comparison.totalReps().direction());
    }

    @Test
    public void eachSideKeepsItsOwnSnapshotBecauseTheExerciseItselfMayHaveChanged() {
        // Logged with dumbbells then, with a barbell now. 20 kg per dumbbell is 40 kg moved; 40 kg
        // on a bar is 40 kg. Reading last time's number with today's snapshot would halve it.
        SessionExercise today = bench(working("a", kg(40), 10));
        SessionExercise lastTime = dumbbells(working("x", kg(20), 10));

        ExerciseComparison comparison = ExerciseComparison.between(today, lastTime);

        assertSame(today, comparison.current());
        assertSame(lastTime, comparison.previous());
        assertEquals(400_000L, comparison.volumeGrams().current());
        assertEquals(400_000L, comparison.volumeGrams().previous());
        assertEquals(MetricChange.Direction.SAME, comparison.volumeGrams().direction());
        // And the same per set, which is a separate calculation and can get it wrong on its own:
        // read with today's barbell snapshot, last time's 20 kg would count once instead of twice
        // and the row would announce a doubling nobody lifted.
        MetricChange ofSet = comparison.sets().get(0).volumeGrams();
        assertEquals(400_000L, ofSet.current());
        assertEquals(400_000L, ofSet.previous());
        assertEquals(MetricChange.Direction.SAME, ofSet.direction());
    }

    @Test
    public void aSetThatWasNotPerformedCountsAsNoWorkRatherThanAsNoData() {
        // Skipping a set is a result: the work was not done. That is a fall, and saying so is not
        // the same as inventing a measurement.
        SessionExercise today = bench(skipped("a"));
        SessionExercise lastTime = bench(working("x", kg(40), 10));

        ExerciseComparison comparison = ExerciseComparison.between(today, lastTime);

        assertEquals(0L, comparison.sets().get(0).volumeGrams().current());
        assertEquals(400_000L, comparison.sets().get(0).volumeGrams().previous());
        assertEquals(MetricChange.Direction.DOWN,
                comparison.sets().get(0).volumeGrams().direction());
    }

    @Test
    public void theListOfPairsHandedOutCannotBeModified() {
        ExerciseComparison comparison = ExerciseComparison.between(
                bench(working("a", kg(40), 10)), bench(working("x", kg(40), 10)));

        try {
            comparison.sets().clear();
            throw new AssertionError("a lista devia ser imutavel");
        } catch (UnsupportedOperationException expected) {
            // what we want
        }
    }

    // ------------------------------------------------------------------ helpers

    private static Weight kg(double value) {
        return Weight.of(value, WeightUnit.KILOGRAM);
    }

    private static LoggedSet working(String id, Weight weight, int reps) {
        return new LoggedSet(id, 0, 1, null, null, true, RepRange.exactly(10), null, null, 90,
                new SetValues(weight, reps, null, null, null), SetStatus.COMPLETED, 1L, null, null,
                Collections.emptyList());
    }

    private static LoggedSet skipped(String id) {
        return new LoggedSet(id, 0, 1, null, null, true, RepRange.exactly(10), null, null, 90,
                SetValues.EMPTY, SetStatus.SKIPPED, null, null, null, Collections.emptyList());
    }

    private static LoggedSet warmUp(String id, Weight weight, int reps) {
        return new LoggedSet(id, 0, null, "tech-warmup", "AQ", false, RepRange.exactly(reps), null,
                null, 60, new SetValues(weight, reps, null, null, null), SetStatus.COMPLETED, 1L,
                null, null, Collections.emptyList());
    }

    private static LoggedSet withSegments(LoggedSet parent, LoggedSet... segments) {
        return new LoggedSet(parent.id(), parent.position(), parent.workingNumber(),
                parent.techniqueId(), parent.techniqueCode(), parent.countsAsWorkingSet(),
                parent.plannedReps(), parent.plannedWeight(), parent.plannedDurationSeconds(),
                parent.plannedRestSeconds(), parent.values(), parent.status(),
                parent.completedAt(), parent.notes(), parent.previous(), Arrays.asList(segments));
    }

    private static SessionExercise bench(LoggedSet... sets) {
        return exercise("ex-bench", "Supino reto com barra", TrackingType.WEIGHT_REPS,
                LoadBasis.TOTAL, 1, sets);
    }

    private static SessionExercise dumbbells(LoggedSet... sets) {
        return exercise("ex-bench", "Supino reto com halteres", TrackingType.WEIGHT_REPS,
                LoadBasis.PER_IMPLEMENT, 2, sets);
    }

    private static SessionExercise pullUps(LoggedSet... sets) {
        return exercise("ex-pullup", "Barra fixa", TrackingType.BODYWEIGHT_REPS,
                LoadBasis.TOTAL, 1, sets);
    }

    private static SessionExercise exercise(String exerciseId, String name, TrackingType tracking,
                                            LoadBasis basis, int implements_, LoggedSet... sets) {
        return new SessionExercise("se-" + exerciseId, exerciseId, 0, name, tracking, basis,
                implements_, Laterality.BILATERAL, SideMode.COMBINED, 90, null, null, null,
                new ArrayList<>(Arrays.asList(sets)));
    }
}

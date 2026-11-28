package io.github.thiagojosetj.gym.domain.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.github.thiagojosetj.gym.domain.model.Laterality;
import io.github.thiagojosetj.gym.domain.model.LoadBasis;
import io.github.thiagojosetj.gym.domain.model.RepRange;
import io.github.thiagojosetj.gym.domain.model.SideMode;
import io.github.thiagojosetj.gym.domain.model.TrackingType;
import io.github.thiagojosetj.gym.domain.model.Weight;
import io.github.thiagojosetj.gym.domain.model.WeightUnit;

public class SessionVolumeTest {

    private static final long KG = 1000L;

    @Test
    public void barbellVolumeIsLoadTimesReps() {
        SessionExercise bench = exercise(TrackingType.WEIGHT_REPS, LoadBasis.TOTAL, 1,
                Laterality.BILATERAL, SideMode.COMBINED, Arrays.asList(
                        completed("a", kg(40), 10),
                        completed("b", kg(40), 8)));

        SessionVolume.Totals totals = SessionVolume.ofExercise(bench);

        assertEquals(40 * 10 * KG + 40 * 8 * KG, totals.loadGrams());
        assertEquals(2, totals.countedSets());
        assertEquals(18, totals.totalReps());
        assertFalse(totals.hasExclusions());
    }

    @Test
    public void dumbbellsCountBothWhileTheScreenStillShowsThePerDumbbellLoad() {
        // 2 x 12 kg x 10 reps = 240 kg of volume, but the user typed 12.
        SessionExercise curl = exercise(TrackingType.WEIGHT_REPS, LoadBasis.PER_IMPLEMENT, 2,
                Laterality.BILATERAL, SideMode.COMBINED, Collections.singletonList(
                        completed("a", kg(12), 10)));

        assertEquals(240 * KG, SessionVolume.ofExercise(curl).loadGrams());
        assertEquals(24 * KG, SessionVolume.effectiveLoadGrams(curl, kg(12)));
    }

    @Test
    public void unilateralRepsAreCountedPerSide() {
        SessionExercise rowCombined = exercise(TrackingType.WEIGHT_REPS, LoadBasis.PER_IMPLEMENT, 1,
                Laterality.UNILATERAL, SideMode.COMBINED, Collections.singletonList(
                        completed("a", kg(20), 10)));
        // "10 reps por lado" logged together: 20 repetitions were performed.
        assertEquals(20 * 20 * KG, SessionVolume.ofExercise(rowCombined).loadGrams());
        assertEquals(20, SessionVolume.ofExercise(rowCombined).totalReps());

        SessionExercise rowPerSide = exercise(TrackingType.WEIGHT_REPS, LoadBasis.PER_IMPLEMENT, 1,
                Laterality.UNILATERAL, SideMode.PER_SIDE, Collections.singletonList(
                        set("a", new SetValues(kg(20), null, 10, 9, null), SetStatus.COMPLETED, true)));
        assertEquals(19, SessionVolume.ofExercise(rowPerSide).totalReps());
        assertEquals(20 * 19 * KG, SessionVolume.ofExercise(rowPerSide).loadGrams());
    }

    @Test
    public void warmUpSetsStayOutOfVolumeButStillCountAsReps() {
        SessionExercise bench = exercise(TrackingType.WEIGHT_REPS, LoadBasis.TOTAL, 1,
                Laterality.BILATERAL, SideMode.COMBINED, Arrays.asList(
                        set("warm", new SetValues(kg(20), 12, null, null, null), SetStatus.COMPLETED, false),
                        completed("a", kg(40), 10)));

        SessionVolume.Totals totals = SessionVolume.ofExercise(bench);

        assertEquals(40 * 10 * KG, totals.loadGrams());
        assertEquals(1, totals.countedSets());
        assertEquals(1, totals.excludedSets());
        assertEquals(22, totals.totalReps());
        assertTrue(totals.hasExclusions());
    }

    @Test
    public void bodyWeightIsExcludedFromLoadVolumeOnPurpose() {
        // The fraction of the body being moved differs per exercise: no invented numbers.
        SessionExercise pullUp = exercise(TrackingType.BODYWEIGHT_REPS, LoadBasis.TOTAL, 1,
                Laterality.BILATERAL, SideMode.COMBINED, Collections.singletonList(
                        completed("a", kg(10), 8)));

        SessionVolume.Totals totals = SessionVolume.ofExercise(pullUp);

        assertEquals(0L, totals.loadGrams());
        assertEquals(1, totals.excludedSets());
        assertEquals(8, totals.totalReps());
    }

    @Test
    public void timedSetsContributeTimeNotVolume() {
        SessionExercise plank = exercise(TrackingType.DURATION, LoadBasis.TOTAL, 1,
                Laterality.BILATERAL, SideMode.COMBINED, Collections.singletonList(
                        set("a", new SetValues(null, null, null, null, 45), SetStatus.COMPLETED, true)));

        SessionVolume.Totals totals = SessionVolume.ofExercise(plank);

        assertEquals(0L, totals.loadGrams());
        assertEquals(45, totals.durationSeconds());
        assertEquals(1, totals.excludedSets());
    }

    @Test
    public void unknownLoadAndUnconfirmedSetsDoNotEnterTheVolume() {
        SessionExercise bench = exercise(TrackingType.WEIGHT_REPS, LoadBasis.TOTAL, 1,
                Laterality.BILATERAL, SideMode.COMBINED, Arrays.asList(
                        set("noLoad", new SetValues(null, 10, null, null, null), SetStatus.COMPLETED, true),
                        set("pending", new SetValues(kg(40), 10, null, null, null), SetStatus.PENDING, true),
                        set("skipped", new SetValues(kg(40), 10, null, null, null), SetStatus.SKIPPED, true)));

        SessionVolume.Totals totals = SessionVolume.ofExercise(bench);

        assertEquals(0L, totals.loadGrams());
        assertEquals(1, totals.excludedSets()); // only the performed one is even considered
        assertEquals(10, totals.totalReps());
    }

    @Test
    public void zeroRepsIsNotVolume() {
        SessionExercise bench = exercise(TrackingType.WEIGHT_REPS, LoadBasis.TOTAL, 1,
                Laterality.BILATERAL, SideMode.COMBINED, Collections.singletonList(
                        completed("a", kg(40), 0)));

        assertEquals(0L, SessionVolume.ofExercise(bench).loadGrams());
    }

    @Test
    public void aSessionSumsItsExercises() {
        SessionExercise bench = exercise(TrackingType.WEIGHT_REPS, LoadBasis.TOTAL, 1,
                Laterality.BILATERAL, SideMode.COMBINED, Collections.singletonList(completed("a", kg(40), 10)));
        SessionExercise curl = exercise(TrackingType.WEIGHT_REPS, LoadBasis.PER_IMPLEMENT, 2,
                Laterality.BILATERAL, SideMode.COMBINED, Collections.singletonList(completed("b", kg(10), 12)));

        SessionVolume.Totals totals = SessionVolume.of(Arrays.asList(bench, curl));

        assertEquals(40 * 10 * KG + 20 * 12 * KG, totals.loadGrams());
        assertEquals(2, totals.countedSets());
        assertEquals(22, totals.totalReps());
    }

    // ---------------------------------------------- segments (section 9.1, ADR-0037)

    @Test
    public void aDropSetSumsEveryDropIntoTheVolume() {
        // 40x10 + 30x8 + 20x6. The repetitions at 30 kg and 20 kg were performed; counting only
        // the top load would throw away real work.
        SessionExercise bench = barbell(dropSet("s1", kg(40), 10,
                drop("s1-a", kg(30), 8), drop("s1-b", kg(20), 6)));

        SessionVolume.Totals totals = SessionVolume.ofExercise(bench);

        assertEquals(760_000L, totals.loadGrams());
    }

    @Test
    public void aDropSetIsOneSetAndNotThree() {
        // The rule that makes "series feitas" and the weekly sets-per-muscle figures mean anything.
        SessionExercise bench = barbell(dropSet("s1", kg(40), 10,
                drop("s1-a", kg(30), 8), drop("s1-b", kg(20), 6)));

        SessionVolume.Totals totals = SessionVolume.ofExercise(bench);

        assertEquals(1, totals.countedSets());
        assertEquals(0, totals.excludedSets());
    }

    @Test
    public void aDropSetSumsTheRepetitionsOfEveryDrop() {
        SessionExercise bench = barbell(dropSet("s1", kg(40), 10,
                drop("s1-a", kg(30), 8), drop("s1-b", kg(20), 6)));

        assertEquals(24, SessionVolume.ofExercise(bench).totalReps());
    }

    @Test
    public void aDropThatWasPlannedAndNotPerformedAddsNothing() {
        LoggedSet pendingDrop = new LoggedSet("s1-a", 0, null, null, null, true, null, null, null,
                0, new SetValues(kg(30), 8, null, null, null), SetStatus.PENDING, null, null, null,
                Collections.emptyList());
        SessionExercise bench = barbell(dropSet("s1", kg(40), 10, pendingDrop));

        SessionVolume.Totals totals = SessionVolume.ofExercise(bench);

        assertEquals(400_000L, totals.loadGrams());
        assertEquals(10, totals.totalReps());
    }

    @Test
    public void theDropsOfAWarmUpAreWarmUpToo() {
        // A set is excluded as a whole or not at all. Letting the drops of a warm-up add load would
        // put half of one set inside the volume and half outside it.
        LoggedSet warmUp = set("s1", new SetValues(kg(20), 12, null, null, null),
                SetStatus.COMPLETED, false);
        LoggedSet withDrops = new LoggedSet(warmUp.id(), 0, null, warmUp.techniqueId(),
                warmUp.techniqueCode(), false, null, null, null, 0, warmUp.values(),
                SetStatus.COMPLETED, 1L, null, null,
                Collections.singletonList(drop("s1-a", kg(15), 10)));

        SessionVolume.Totals totals = SessionVolume.ofExercise(barbell(withDrops));

        assertEquals(0L, totals.loadGrams());
        assertEquals(1, totals.excludedSets());
        assertEquals(22, totals.totalReps()); // the repetitions still happened
    }

    @Test
    public void aDropSetWithNoKnownLoadIsExcludedExactlyOnce() {
        // Not once per drop: the exclusions line counts SETS, and this is one set.
        SessionExercise bench = barbell(dropSet("s1", null, 10,
                drop("s1-a", null, 8), drop("s1-b", null, 6)));

        SessionVolume.Totals totals = SessionVolume.ofExercise(bench);

        assertEquals(0L, totals.loadGrams());
        assertEquals(1, totals.excludedSets());
        assertEquals(24, totals.totalReps());
    }

    @Test
    public void everyDropOfADumbbellSetCountsBothImplements() {
        // Per-implement load applies drop by drop: 2x12x10 + 2x10x8 = 240 + 160 kg.
        SessionExercise curl = exercise(TrackingType.WEIGHT_REPS, LoadBasis.PER_IMPLEMENT, 2,
                Laterality.BILATERAL, SideMode.COMBINED,
                Collections.singletonList(dropSet("s1", kg(12), 10, drop("s1-a", kg(10), 8))));

        assertEquals(400_000L, SessionVolume.ofExercise(curl).loadGrams());
    }

    // ------------------------------------------------------------------ helpers

    private static Weight kg(double value) {
        return Weight.of(value, WeightUnit.KILOGRAM);
    }

    private static LoggedSet completed(String id, Weight weight, int reps) {
        return set(id, new SetValues(weight, reps, null, null, null), SetStatus.COMPLETED, true);
    }

    private static LoggedSet set(String id, SetValues values, SetStatus status, boolean working) {
        return new LoggedSet(id, 0, working ? 1 : null, working ? null : "technique-warmup",
                working ? null : "AQ", working, RepRange.exactly(10), null, null, 90,
                values, status, status == SetStatus.COMPLETED ? 1L : null, null, null,
                Collections.emptyList());
    }

    /** A performed set with its later drops nested inside it (PRODUCT_SPEC section 9.1). */
    private static LoggedSet dropSet(String id, Weight weight, int reps, LoggedSet... drops) {
        return new LoggedSet(id, 0, 1, null, null, true, RepRange.exactly(reps), null, null, 90,
                new SetValues(weight, reps, null, null, null), SetStatus.COMPLETED, 1L, null, null,
                Arrays.asList(drops));
    }

    /** One drop. It has no plan and no "previous": it is a drop off the set above it. */
    private static LoggedSet drop(String id, Weight weight, int reps) {
        return new LoggedSet(id, 0, null, null, null, true, null, null, null, 0,
                new SetValues(weight, reps, null, null, null), SetStatus.COMPLETED, 1L, null, null,
                Collections.emptyList());
    }

    private static SessionExercise barbell(LoggedSet... sets) {
        return exercise(TrackingType.WEIGHT_REPS, LoadBasis.TOTAL, 1, Laterality.BILATERAL,
                SideMode.COMBINED, Arrays.asList(sets));
    }

    private static SessionExercise exercise(TrackingType tracking, LoadBasis basis, int implements_,
                                            Laterality laterality, SideMode sideMode, List<LoggedSet> sets) {
        return new SessionExercise("se-1", "ex-1", 0, "Exercicio", tracking, basis, implements_,
                laterality, sideMode, 90, null, null, null, sets);
    }
}

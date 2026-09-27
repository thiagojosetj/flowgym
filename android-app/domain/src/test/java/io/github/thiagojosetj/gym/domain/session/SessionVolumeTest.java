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
                values, status, status == SetStatus.COMPLETED ? 1L : null, null, null);
    }

    private static SessionExercise exercise(TrackingType tracking, LoadBasis basis, int implements_,
                                            Laterality laterality, SideMode sideMode, List<LoggedSet> sets) {
        return new SessionExercise("se-1", "ex-1", 0, "Exercicio", tracking, basis, implements_,
                laterality, sideMode, 90, null, null, null, sets);
    }
}

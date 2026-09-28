package io.github.thiagojosetj.gym.domain.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
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

public class SessionDetailTest {

    @Test
    public void theFirstTimeAWorkoutIsPerformedThereIsNothingToCompareWith() {
        SessionDetail detail = SessionDetail.of(session(bench(completed("s1", kg(40), 10))),
                "2026-09-28", "America/Sao_Paulo", null, null, 0L);

        assertFalse(detail.hasComparison());
        assertNull(detail.comparison());
    }

    @Test
    public void theSummaryUsesTheSameVolumeRuleTheFinishDialogUsed() {
        // 40 kg x 10 = 400 kg, in grams. Not recomputed here: SessionVolume is the only implementation.
        SessionDetail detail = SessionDetail.of(session(bench(completed("s1", kg(40), 10))),
                "2026-09-28", "America/Sao_Paulo", null, null, 0L);

        assertEquals(400_000L, detail.summary().volumeGrams());
        assertEquals(1, detail.summary().performedSets());
        assertEquals(10, detail.summary().totalReps());
    }

    @Test
    public void abodyWeightSetIsCountedAsOutsideTheVolumeInsteadOfBeingHidden() {
        SessionExercise pullUps = new SessionExercise("se-2", "ex-2", 1, "Barra fixa",
                TrackingType.BODYWEIGHT_REPS, LoadBasis.TOTAL, 1, Laterality.BILATERAL,
                SideMode.COMBINED, 90, null, null, null,
                Collections.singletonList(completed("s2", null, 8)));

        SessionDetail detail = SessionDetail.of(session(bench(completed("s1", kg(40), 10)), pullUps),
                "2026-09-28", "America/Sao_Paulo", null, null, 0L);

        assertEquals(400_000L, detail.summary().volumeGrams());
        assertEquals(1, detail.summary().setsOutsideVolume());
        assertEquals(18, detail.summary().totalReps()); // the reps still count
    }

    @Test
    public void everyExerciseGetsItsOwnRollupInThePlannedOrder() {
        SessionExercise rows = new SessionExercise("se-2", "ex-2", 1, "Remada curvada",
                TrackingType.WEIGHT_REPS, LoadBasis.TOTAL, 1, Laterality.BILATERAL,
                SideMode.COMBINED, 90, null, null, null,
                Arrays.asList(completed("s2", kg(30), 12), skipped("s3")));

        SessionDetail detail = SessionDetail.of(session(bench(completed("s1", kg(40), 10)), rows),
                "2026-09-28", "America/Sao_Paulo", null, null, 0L);

        assertEquals(2, detail.exercises().size());
        SessionExerciseSummary first = detail.exercises().get(0);
        SessionExerciseSummary second = detail.exercises().get(1);
        assertEquals("Supino reto com barra", first.name());
        assertEquals(400_000L, first.totals().loadGrams());
        assertEquals("Remada curvada", second.name());
        assertEquals(360_000L, second.totals().loadGrams());
        assertEquals(1, second.performedSets());
        assertEquals(1, second.skippedSets());
    }

    @Test
    public void theRollupKeepsTheNameTheExerciseHadOnTheDay() {
        // The snapshot is the point: renaming the exercise later must not rewrite this session.
        SessionExercise asItWasNamedThen = new SessionExercise("se-1", "ex-1", 0, "Supino reto",
                TrackingType.WEIGHT_REPS, LoadBasis.TOTAL, 1, Laterality.BILATERAL,
                SideMode.COMBINED, 90, null, null, null,
                Collections.singletonList(completed("s1", kg(40), 10)));

        SessionDetail detail = SessionDetail.of(session(asItWasNamedThen),
                "2026-09-28", "America/Sao_Paulo", null, null, 0L);

        assertEquals("Supino reto", detail.exercises().get(0).name());
    }

    @Test
    public void comparingWithTheSameWorkoutLastWeekReportsEachMetric() {
        SessionSummary lastWeek = new SessionSummary("prev", "Push A", 3_600_000L, 3_000_000L,
                1, 3, 0, 30, 0, 300_000L, 0);

        SessionDetail detail = SessionDetail.of(session(bench(completed("s1", kg(40), 10))),
                "2026-09-28", "America/Sao_Paulo", null, lastWeek, 1_700_000_000_000L);

        assertTrue(detail.hasComparison());
        SessionComparison comparison = detail.comparison();
        assertEquals("prev", comparison.previousSessionId());
        assertEquals(1_700_000_000_000L, comparison.previousStartedAt());
        assertEquals(MetricChange.Direction.UP, comparison.volumeGrams().direction());
        assertEquals(100_000L, comparison.volumeGrams().delta());
        assertEquals(MetricChange.Direction.DOWN, comparison.performedSets().direction());
        assertEquals(MetricChange.Direction.DOWN, comparison.totalReps().direction());
    }

    @Test
    public void aWorkoutThatRecordedNoVolumeLastTimeShowsTheArrowButNoPercentage() {
        SessionSummary bodyweightOnly = new SessionSummary("prev", "Push A", 3_600_000L, 3_000_000L,
                1, 3, 0, 30, 0, 0L, 3);

        SessionDetail detail = SessionDetail.of(session(bench(completed("s1", kg(40), 10))),
                "2026-09-28", "America/Sao_Paulo", null, bodyweightOnly, 1L);

        MetricChange volume = detail.comparison().volumeGrams();
        assertEquals(MetricChange.Direction.UP, volume.direction());
        assertFalse(volume.hasPercent());
        assertThrows(IllegalStateException.class, volume::percent);
    }

    @Test
    public void theDurationComesFromTheStoredInstantsAndNotFromTheClockOfToday() {
        SessionHeader header = new SessionHeader("sess-1", "tpl-1", "Push A", null,
                SessionStatus.COMPLETED,
                new SessionClock(1_000_000L, 1_000_000L + 3_600_000L, 600_000L, null),
                null, null, null);
        ActiveSession finished = new ActiveSession(header,
                Collections.singletonList(bench(completed("s1", kg(40), 10))));

        SessionDetail detail = SessionDetail.of(finished, "2026-09-28", "America/Sao_Paulo",
                null, null, 0L);

        assertEquals(3_600_000L, detail.summary().totalMs());
        assertEquals(3_000_000L, detail.summary().effectiveMs()); // total minus the 10 min paused
    }

    @Test
    public void aRatingThatWasNeverGivenStaysNullInsteadOfBecomingZero() {
        SessionDetail withoutRating = SessionDetail.of(session(bench(completed("s1", kg(40), 10))),
                "2026-09-28", "America/Sao_Paulo", null, null, 0L);
        SessionDetail withRating = SessionDetail.of(session(bench(completed("s1", kg(40), 10))),
                "2026-09-28", "America/Sao_Paulo", 4, null, 0L);

        assertNull(withoutRating.rating());
        assertEquals(Integer.valueOf(4), withRating.rating());
    }

    @Test
    public void theExerciseListHandedOutCannotBeModifiedByTheScreen() {
        SessionDetail detail = SessionDetail.of(session(bench(completed("s1", kg(40), 10))),
                "2026-09-28", "America/Sao_Paulo", null, null, 0L);

        assertThrows(UnsupportedOperationException.class, () -> detail.exercises().clear());
    }

    @Test
    public void theZoneTheWorkoutHappenedInIsCarriedAlongWithTheDate() {
        // Section 11 and the calendar group by the day as it was lived, not by the phone's zone now.
        SessionDetail detail = SessionDetail.of(session(bench(completed("s1", kg(40), 10))),
                "2026-09-28", "America/Sao_Paulo", null, null, 0L);

        assertEquals("2026-09-28", detail.localDate());
        assertEquals("America/Sao_Paulo", detail.timeZone());
    }

    // ------------------------------------------------------------------ helpers

    private static Weight kg(double value) {
        return Weight.of(value, WeightUnit.KILOGRAM);
    }

    private static LoggedSet completed(String id, Weight weight, int reps) {
        return set(id, new SetValues(weight, reps, null, null, null), SetStatus.COMPLETED);
    }

    private static LoggedSet skipped(String id) {
        return set(id, SetValues.EMPTY, SetStatus.SKIPPED);
    }

    private static LoggedSet set(String id, SetValues values, SetStatus status) {
        return new LoggedSet(id, 0, 1, null, null, true, RepRange.exactly(10), null, null, 90,
                values, status, status == SetStatus.COMPLETED ? 1L : null, null, null);
    }

    private static SessionExercise bench(LoggedSet... sets) {
        return new SessionExercise("se-1", "ex-1", 0, "Supino reto com barra",
                TrackingType.WEIGHT_REPS, LoadBasis.TOTAL, 1, Laterality.BILATERAL,
                SideMode.COMBINED, 90, null, null, null, Arrays.asList(sets));
    }

    private static ActiveSession session(SessionExercise... exercises) {
        SessionHeader header = new SessionHeader("sess-1", "tpl-1", "Push A", null,
                SessionStatus.COMPLETED, new SessionClock(1_000_000L, 2_000_000L, 0L, null),
                null, null, null);
        List<SessionExercise> list = Arrays.asList(exercises);
        return new ActiveSession(header, list);
    }
}

package io.github.thiagojosetj.gym.domain.progress;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
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
import io.github.thiagojosetj.gym.domain.session.LoggedSet;
import io.github.thiagojosetj.gym.domain.session.SessionExercise;
import io.github.thiagojosetj.gym.domain.session.SetStatus;
import io.github.thiagojosetj.gym.domain.session.SetValues;

/**
 * One exercise across its history, and the records of PRODUCT_SPEC section 10.
 *
 * <p>Records are the part of this app most likely to be believed without checking, so every rule
 * section 10 states gets a test that would fail if the rule were dropped: a tie is not a record,
 * the first time is a baseline and not an achievement, body weight counts for repetitions and for
 * added load but not for volume, and a load is reported in the units the person typed.
 */
public class ExerciseProgressTest {

    @Test
    public void anExerciseNeverPerformedHasNothingToShow() {
        ExerciseProgress progress = ExerciseProgress.of("ex-bench", "Supino",
                Collections.emptyList());

        assertTrue(progress.isEmpty());
        assertTrue(progress.records().isEmpty());
        assertNull(progress.records().maxLoad());
    }

    @Test
    public void eachSessionBecomesOnePointWithItsOwnNumbers() {
        ExerciseProgress progress = ExerciseProgress.of("ex-bench", "Supino", Arrays.asList(
                appearance("s1", "2026-09-21", bench(working("a", kg(40), 10),
                        working("b", kg(40), 8))),
                appearance("s2", "2026-09-28", bench(working("c", kg(45), 10)))));

        assertEquals(2, progress.sessions().size());
        ExerciseProgress.Point first = progress.sessions().get(0);
        assertEquals("2026-09-21", first.localDate());
        assertEquals(2, first.performedSets());
        assertEquals(18, first.totalReps());
        assertEquals(40_000L, first.maxLoadGrams());
        assertEquals(720_000L, first.volumeGrams());
        assertEquals(450_000L, progress.sessions().get(1).volumeGrams());
    }

    @Test
    public void theFirstTimeIsABaselineAndNotAnAchievement() {
        // Section 10: "A primeira ocorrência de um exercício cria uma linha de base (sem
        // celebração)." Turning up for the first time is not beating anything.
        ExerciseProgress only = ExerciseProgress.of("ex-bench", "Supino",
                Collections.singletonList(
                        appearance("s1", "2026-09-21", bench(working("a", kg(40), 10)))));

        assertTrue(only.records().maxLoad().isBaseline());
        assertTrue(only.records().maxSetVolume().isBaseline());
        assertTrue(only.records().maxSessionVolume().isBaseline());
        assertTrue(only.records().repsAtLoad().get(0).isBaseline());
    }

    @Test
    public void beatingTheBaselineLaterIsAnAchievement() {
        ExerciseProgress progress = ExerciseProgress.of("ex-bench", "Supino", Arrays.asList(
                appearance("s1", "2026-09-21", bench(working("a", kg(40), 10))),
                appearance("s2", "2026-09-28", bench(working("b", kg(45), 10)))));

        assertEquals(45_000L, progress.records().maxLoad().value());
        assertEquals("s2", progress.records().maxLoad().sessionId());
        assertFalse(progress.records().maxLoad().isBaseline());
    }

    @Test
    public void aTieIsNotARecordAndTheOlderSessionKeepsIt() {
        // Equalling your best is not beating it. The record stays with the day it was first done,
        // which is also the honest answer to "when did I do this?".
        ExerciseProgress progress = ExerciseProgress.of("ex-bench", "Supino", Arrays.asList(
                appearance("s1", "2026-09-21", bench(working("a", kg(40), 10))),
                appearance("s2", "2026-09-28", bench(working("b", kg(40), 10)))));

        assertEquals("s1", progress.records().maxLoad().sessionId());
        assertEquals("s1", progress.records().maxSetVolume().sessionId());
        assertEquals("s1", progress.records().repsAtLoad().get(0).sessionId());
        assertTrue("segue sendo a linha de base", progress.records().maxLoad().isBaseline());
    }

    @Test
    public void theLoadRecordIsTheWeightThatWasTypedAndNotTheOneVolumeMultiplies() {
        // Section 10 says the load record is per implement, and the app says "12 kg por halter"
        // everywhere else. Reporting 24 kg would be the app claiming something the person never
        // entered - even though section 9 is right to multiply it for VOLUME.
        ExerciseProgress progress = ExerciseProgress.of("ex-db", "Supino com halteres",
                Collections.singletonList(
                        appearance("s1", "2026-09-21", dumbbells(working("a", kg(20), 10)))));

        assertEquals(20_000L, progress.records().maxLoad().value());
        assertEquals("mas o volume dobra, como manda a secao 9",
                400_000L, progress.records().maxSetVolume().value());
        // 20_000 says nothing on its own: it is "20 kg por halter" or "20 kg" depending on this
        // flag, and the two are different claims. Without it the screen has to guess.
        assertTrue("o recorde de carga sabe que e por implemento",
                progress.records().maxLoad().loadPerImplement());
        assertTrue(progress.sessions().get(0).loadPerImplement());
        assertTrue(progress.records().repsAtLoad().get(0).loadPerImplement());
        assertFalse("volume e soma de carga efetiva: 'por implemento' nao se aplica",
                progress.records().maxSetVolume().loadPerImplement());
        assertFalse(progress.records().maxSessionVolume().loadPerImplement());
    }

    @Test
    public void theSameNumberOnTwoBasesIsTwoLoadsAndNotOne() {
        // The same movement with a pair of dumbbells one month and a barbell the next. Section 10
        // compares loads "em gramas", and 12 kg per dumbbell is the same number as a 12 kg bar
        // while being twice the work - so they are two records. Merged on grams alone, the screen
        // would print one session's repetitions under the other session's weight.
        ExerciseProgress progress = ExerciseProgress.of("ex-press", "Desenvolvimento", Arrays.asList(
                appearance("s1", "2026-09-21", exercise("ex-press", "Desenvolvimento",
                        TrackingType.WEIGHT_REPS, LoadBasis.PER_IMPLEMENT, 2,
                        working("a", kg(12), 10))),
                appearance("s2", "2026-09-28", exercise("ex-press", "Desenvolvimento",
                        TrackingType.WEIGHT_REPS, LoadBasis.TOTAL, 1,
                        working("b", kg(12), 15)))));

        List<ExercisePersonalRecords.RepsAtLoad> reps = progress.records().repsAtLoad();
        assertEquals("duas cargas, nao uma", 2, reps.size());
        assertTrue("a por implemento vem primeiro: e o trabalho maior",
                reps.get(0).loadPerImplement());
        assertEquals(10, reps.get(0).reps());
        assertFalse(reps.get(1).loadPerImplement());
        assertEquals(15, reps.get(1).reps());
        // And the load record stays with the first to do it: equalling 12 kg is not beating it.
        assertEquals(12_000L, progress.records().maxLoad().value());
        assertEquals("s1", progress.records().maxLoad().sessionId());
    }

    @Test
    public void twoBasesOnTheSameNumberAreOrderedBySomethingOtherThanWhoCameFirst() {
        // The same pair of loads as above, in the other order. Proved in both orders on purpose:
        // the map keeps insertion order and the sort is stable, so a test with the per-implement
        // session first would pass with no tiebreak at all - and the list would then be ordered by
        // which session happened to come first, which is not an order anybody can read.
        ExerciseProgress progress = ExerciseProgress.of("ex-press", "Desenvolvimento", Arrays.asList(
                appearance("s1", "2026-09-21", exercise("ex-press", "Desenvolvimento",
                        TrackingType.WEIGHT_REPS, LoadBasis.TOTAL, 1,
                        working("a", kg(12), 15))),
                appearance("s2", "2026-09-28", exercise("ex-press", "Desenvolvimento",
                        TrackingType.WEIGHT_REPS, LoadBasis.PER_IMPLEMENT, 2,
                        working("b", kg(12), 10)))));

        List<ExercisePersonalRecords.RepsAtLoad> reps = progress.records().repsAtLoad();
        assertEquals(2, reps.size());
        assertTrue("ainda a por implemento primeiro", reps.get(0).loadPerImplement());
        assertEquals(10, reps.get(0).reps());
        assertFalse(reps.get(1).loadPerImplement());
    }

    @Test
    public void mostRepsIsKeptPerExactLoad() {
        // "Maior no de reps para a mesma carga exata (comparada em gramas)." 10 reps at 40 kg and
        // 6 at 50 kg are two different records, not one superseding the other.
        ExerciseProgress progress = ExerciseProgress.of("ex-bench", "Supino", Arrays.asList(
                appearance("s1", "2026-09-21", bench(working("a", kg(40), 10),
                        working("b", kg(50), 5))),
                appearance("s2", "2026-09-28", bench(working("c", kg(40), 12),
                        working("d", kg(50), 4)))));

        List<ExercisePersonalRecords.RepsAtLoad> reps = progress.records().repsAtLoad();
        assertEquals("ordenado da carga maior para a menor", 2, reps.size());
        assertEquals(50_000L, reps.get(0).loadGrams());
        assertEquals(5, reps.get(0).reps());
        assertEquals("s1", reps.get(0).sessionId());
        assertEquals(40_000L, reps.get(1).loadGrams());
        assertEquals(12, reps.get(1).reps());
        assertEquals("s2", reps.get(1).sessionId());
    }

    @Test
    public void aWarmUpIsNotAResultAndCannotSetARecord() {
        ExerciseProgress progress = ExerciseProgress.of("ex-bench", "Supino",
                Collections.singletonList(appearance("s1", "2026-09-21",
                        bench(warmUp("w", kg(100), 1), working("a", kg(40), 10)))));

        assertEquals(40_000L, progress.records().maxLoad().value());
        assertEquals(1, progress.sessions().get(0).performedSets());
    }

    @Test
    public void aSetThatWasNotPerformedCannotSetARecordEither() {
        ExerciseProgress progress = ExerciseProgress.of("ex-bench", "Supino",
                Collections.singletonList(appearance("s1", "2026-09-21",
                        bench(skipped("s", kg(100)), working("a", kg(40), 10)))));

        assertEquals(40_000L, progress.records().maxLoad().value());
    }

    @Test
    public void bodyWeightCountsForRepsAndAddedLoadButNotForVolume() {
        // Section 10's one deliberate departure from section 9: pull-ups have no load volume, but
        // "+5 kg for 8 reps" is unquestionably a record.
        ExerciseProgress progress = ExerciseProgress.of("ex-pullup", "Barra fixa", Arrays.asList(
                appearance("s1", "2026-09-21", pullUps(working("a", kg(0), 10))),
                appearance("s2", "2026-09-28", pullUps(working("b", kg(5), 8)))));

        assertEquals(5_000L, progress.records().maxLoad().value());
        assertEquals(2, progress.records().repsAtLoad().size());
        assertNull("peso corporal fica fora do volume", progress.records().maxSetVolume());
        assertNull(progress.records().maxSessionVolume());
        assertEquals(0L, progress.sessions().get(0).volumeGrams());
        assertEquals("mas as repeticoes estao ali", 10, progress.sessions().get(0).totalReps());
    }

    @Test
    public void reducingTheAssistanceIsProgressAndNotAZero() {
        // Assistance is a negative load. A maximum that started at zero would report "0 kg" for
        // somebody still on the band, and call it a record.
        ExerciseProgress progress = ExerciseProgress.of("ex-pullup", "Barra fixa", Arrays.asList(
                appearance("s1", "2026-09-21", pullUps(working("a", kg(-20), 8))),
                appearance("s2", "2026-09-28", pullUps(working("b", kg(-10), 8)))));

        assertEquals(-10_000L, progress.records().maxLoad().value());
        assertEquals("s2", progress.records().maxLoad().sessionId());
        assertEquals(-20_000L, progress.sessions().get(0).maxLoadGrams());
    }

    @Test
    public void aDropSetIsOneSetAndItsDropsCountTowardsItsVolume() {
        // ADR-0037 and section 9.1: one set taken past failure. Its drops are summed into it, so
        // the set-volume record includes them - and it is still one set.
        LoggedSet withDrops = withSegments(working("a", kg(40), 10), working("a1", kg(30), 8));

        ExerciseProgress progress = ExerciseProgress.of("ex-bench", "Supino",
                Collections.singletonList(appearance("s1", "2026-09-21", bench(withDrops))));

        assertEquals(640_000L, progress.records().maxSetVolume().value());
        assertEquals(1, progress.sessions().get(0).performedSets());
    }

    @Test
    public void theSessionVolumeRecordIsTheWholeExerciseInThatSession() {
        ExerciseProgress progress = ExerciseProgress.of("ex-bench", "Supino", Arrays.asList(
                appearance("s1", "2026-09-21", bench(working("a", kg(40), 10),
                        working("b", kg(40), 10))),
                appearance("s2", "2026-09-28", bench(working("c", kg(60), 10)))));

        assertEquals("800 kg em duas series ganha de 600 em uma",
                800_000L, progress.records().maxSessionVolume().value());
        assertEquals("s1", progress.records().maxSessionVolume().sessionId());
        assertEquals("mas a serie mais pesada e a outra",
                600_000L, progress.records().maxSetVolume().value());
        assertEquals("s2", progress.records().maxSetVolume().sessionId());
    }

    @Test
    public void anExerciseWithNoLoadAtAllHasNoLoadRecords() {
        ExerciseProgress progress = ExerciseProgress.of("ex-abs", "Abdominal",
                Collections.singletonList(appearance("s1", "2026-09-21",
                        repsOnly(working("a", null, 20)))));

        assertNull(progress.records().maxLoad());
        assertTrue(progress.records().repsAtLoad().isEmpty());
        assertFalse(progress.sessions().get(0).hasLoad());
        assertEquals(20, progress.sessions().get(0).totalReps());
    }

    @Test
    public void onePerformanceIsAPointAndNotYetATrend() {
        ExerciseProgress progress = ExerciseProgress.of("ex-bench", "Supino",
                Collections.singletonList(
                        appearance("s1", "2026-09-21", bench(working("a", kg(40), 10)))));

        assertTrue(progress.isSingleSession());
    }

    @Test
    public void theListsHandedOutCannotBeModified() {
        ExerciseProgress progress = ExerciseProgress.of("ex-bench", "Supino",
                Collections.singletonList(
                        appearance("s1", "2026-09-21", bench(working("a", kg(40), 10)))));

        assertThrows(UnsupportedOperationException.class, () -> progress.sessions().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> progress.records().repsAtLoad().clear());
    }

    // ------------------------------------------------------------------ helpers

    private static ExerciseAppearance appearance(String sessionId, String day,
                                                 SessionExercise exercise) {
        return new ExerciseAppearance(sessionId, day, 1_000_000L, exercise);
    }

    private static Weight kg(double value) {
        return Weight.of(value, WeightUnit.KILOGRAM);
    }

    private static LoggedSet working(String id, Weight weight, int reps) {
        return new LoggedSet(id, 0, 1, null, null, true, RepRange.exactly(10), null, null, 90,
                new SetValues(weight, reps, null, null, null), SetStatus.COMPLETED, 1L, null, null,
                Collections.emptyList());
    }

    private static LoggedSet skipped(String id, Weight weight) {
        return new LoggedSet(id, 0, 1, null, null, true, RepRange.exactly(10), null, null, 90,
                new SetValues(weight, 10, null, null, null), SetStatus.SKIPPED, null, null, null,
                Collections.emptyList());
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
        return exercise("ex-bench", "Supino", TrackingType.WEIGHT_REPS, LoadBasis.TOTAL, 1, sets);
    }

    private static SessionExercise dumbbells(LoggedSet... sets) {
        return exercise("ex-db", "Supino com halteres", TrackingType.WEIGHT_REPS,
                LoadBasis.PER_IMPLEMENT, 2, sets);
    }

    private static SessionExercise pullUps(LoggedSet... sets) {
        return exercise("ex-pullup", "Barra fixa", TrackingType.BODYWEIGHT_REPS, LoadBasis.TOTAL,
                1, sets);
    }

    private static SessionExercise repsOnly(LoggedSet... sets) {
        return exercise("ex-abs", "Abdominal", TrackingType.REPS_ONLY, LoadBasis.TOTAL, 1, sets);
    }

    private static SessionExercise exercise(String exerciseId, String name, TrackingType tracking,
                                            LoadBasis basis, int implements_, LoggedSet... sets) {
        return new SessionExercise("se-" + exerciseId, exerciseId, 0, name, tracking, basis,
                implements_, Laterality.BILATERAL, SideMode.COMBINED, 90, null, null, null,
                new ArrayList<>(Arrays.asList(sets)));
    }
}

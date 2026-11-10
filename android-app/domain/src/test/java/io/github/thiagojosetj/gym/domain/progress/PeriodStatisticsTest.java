package io.github.thiagojosetj.gym.domain.progress;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.github.thiagojosetj.gym.domain.model.Laterality;
import io.github.thiagojosetj.gym.domain.model.LoadBasis;
import io.github.thiagojosetj.gym.domain.model.MuscleRole;
import io.github.thiagojosetj.gym.domain.model.RepRange;
import io.github.thiagojosetj.gym.domain.model.SideMode;
import io.github.thiagojosetj.gym.domain.model.TrackingType;
import io.github.thiagojosetj.gym.domain.model.Weight;
import io.github.thiagojosetj.gym.domain.model.WeightUnit;
import io.github.thiagojosetj.gym.domain.session.ActiveSession;
import io.github.thiagojosetj.gym.domain.session.LoggedSet;
import io.github.thiagojosetj.gym.domain.session.SessionClock;
import io.github.thiagojosetj.gym.domain.session.SessionExercise;
import io.github.thiagojosetj.gym.domain.session.SessionHeader;
import io.github.thiagojosetj.gym.domain.session.SessionStatus;
import io.github.thiagojosetj.gym.domain.session.SetStatus;
import io.github.thiagojosetj.gym.domain.session.SetValues;

/**
 * A week or a month added up (PRODUCT_SPEC PRG-04).
 *
 * <p>The numbers on this screen answer "am I training this muscle enough", which is a question
 * people change their training over. Every way of getting it wrong here inflates: counting a
 * warm-up, counting a drop-set as three, counting one set twice because the exercise names two
 * subgroups of the same group. None of those would look wrong on screen.
 */
public class PeriodStatisticsTest {

    @Test
    public void aPeriodWithNothingInItIsEmptyRatherThanZeroed() {
        PeriodStatistics statistics = PeriodStatistics.of(Collections.emptyList(), chestAndTriceps());

        assertTrue(statistics.isEmpty());
        assertEquals(0, statistics.sessions());
        assertTrue(statistics.muscles().isEmpty());
    }

    @Test
    public void thePeriodAddsUpWhatEachSessionAlreadySaid() {
        // 40 x 10 and 45 x 10 is 400 kg + 450 kg. Not recomputed here: SessionVolume is the only
        // implementation of the section 9 rules, and the figure has to match what each finish
        // screen showed.
        PeriodStatistics statistics = PeriodStatistics.of(
                Arrays.asList(session(bench(working("a", kg(40), 10))),
                        session(bench(working("b", kg(45), 10)))),
                chestAndTriceps());

        assertEquals(2, statistics.sessions());
        assertEquals(850_000L, statistics.volumeGrams());
        assertEquals(20, statistics.totalReps());
        assertEquals(2, statistics.performedSets());
    }

    @Test
    public void aSetCountsForEveryMuscleTheExerciseTrains() {
        PeriodStatistics statistics = PeriodStatistics.of(
                Collections.singletonList(session(bench(working("a", kg(40), 10)))),
                chestAndTriceps());

        assertEquals(2, statistics.muscles().size());
        assertEquals("Peito", statistics.muscles().get(0).name());
        assertEquals(1, statistics.muscles().get(0).primarySets());
        assertEquals(0, statistics.muscles().get(0).secondarySets());
        assertEquals("Tríceps", statistics.muscles().get(1).name());
        assertEquals(0, statistics.muscles().get(1).primarySets());
        assertEquals(1, statistics.muscles().get(1).secondarySets());
    }

    @Test
    public void aMainSetAndAnAssistingSetAreNeverAddedIntoOneNumber() {
        // There is no honest weight for an assisting muscle: one would claim the triceps worked as
        // hard as the chest, a half would be a number this app made up. Both are reported.
        PeriodStatistics statistics = PeriodStatistics.of(
                Collections.singletonList(session(bench(working("a", kg(40), 10)),
                        tricepsPushdown(working("b", kg(30), 12)))),
                chestAndTriceps());

        MuscleWorkload triceps = byName(statistics, "Tríceps");
        assertEquals("a extensão é principal para o tríceps", 1, triceps.primarySets());
        assertEquals("o supino é secundário para ele", 1, triceps.secondarySets());
    }

    @Test
    public void aWarmUpIsNotTrainingForTheMuscleAndIsCountedSeparately() {
        // Section 6.2: a warm-up is out of volume and out of records. It is out of
        // sets-per-muscle too, or every week would read heavier than it was.
        PeriodStatistics statistics = PeriodStatistics.of(
                Collections.singletonList(session(bench(
                        warmUp("w", kg(20), 15), working("a", kg(40), 10)))),
                chestAndTriceps());

        assertEquals("séries feitas inclui o aquecimento, como em toda outra tela",
                2, statistics.performedSets());
        assertEquals(1, statistics.warmUpSets());
        assertEquals("mas o músculo não recebeu duas séries", 1,
                byName(statistics, "Peito").primarySets());
    }

    @Test
    public void aDropSetIsOneSetForTheMuscleAndNotThree() {
        // ADR-0037: a drop-set is one set taken past failure. Counting its drops would inflate the
        // weekly sets-per-muscle figure, which is exactly the number people train by.
        LoggedSet withDrops = withSegments(working("a", kg(40), 10),
                working("a1", kg(30), 8), working("a2", kg(20), 6));

        PeriodStatistics statistics = PeriodStatistics.of(
                Collections.singletonList(session(bench(withDrops))), chestAndTriceps());

        assertEquals(1, byName(statistics, "Peito").primarySets());
        // 40x10 + 30x8 + 20x6 = 400 + 240 + 120 kg.
        assertEquals("mas o volume soma as quedas", 760_000L, statistics.volumeGrams());
    }

    @Test
    public void anExerciseNamingTwoSubgroupsOfOneGroupStillDidOneSetOfIt() {
        // The catalogue splits the back into lats, traps and rhomboids. A row that names two of
        // them is still one set of back work; counting it per subgroup would double every row.
        List<ExerciseMuscleGroup> back = Arrays.asList(
                group("ex-row", "back", "Costas", 2, MuscleRole.PRIMARY),
                group("ex-row", "back", "Costas", 2, MuscleRole.PRIMARY));

        PeriodStatistics statistics = PeriodStatistics.of(
                Collections.singletonList(session(row(working("a", kg(60), 10)))), back);

        assertEquals(1, statistics.muscles().size());
        assertEquals(1, statistics.muscles().get(0).primarySets());
    }

    @Test
    public void whenAGroupIsBothMainAndAssistingForOneExerciseTheMainRoleWins() {
        // Middle chest as the target, upper chest as assisting. Kept as two, the same set would
        // land in both columns of the same row and the work would appear twice.
        // Asserted in both orders on purpose: SQL promises no order, so a rule that happens to
        // work because the main role arrived last is not a rule.
        for (List<ExerciseMuscleGroup> chestTwice : Arrays.asList(
                Arrays.asList(group("ex-bench", "chest", "Peito", 1, MuscleRole.SECONDARY),
                        group("ex-bench", "chest", "Peito", 1, MuscleRole.PRIMARY)),
                Arrays.asList(group("ex-bench", "chest", "Peito", 1, MuscleRole.PRIMARY),
                        group("ex-bench", "chest", "Peito", 1, MuscleRole.SECONDARY)))) {
            PeriodStatistics statistics = PeriodStatistics.of(
                    Collections.singletonList(session(bench(working("a", kg(40), 10)))),
                    chestTwice);

            MuscleWorkload chest = byName(statistics, "Peito");
            assertEquals(1, chest.primarySets());
            assertEquals(0, chest.secondarySets());
        }
    }

    @Test
    public void aSetThatWasNotPerformedIsNotWorkForAnyMuscle() {
        PeriodStatistics statistics = PeriodStatistics.of(
                Collections.singletonList(session(bench(skipped("a"), working("b", kg(40), 10)))),
                chestAndTriceps());

        assertEquals(1, byName(statistics, "Peito").primarySets());
        assertEquals(1, statistics.performedSets());
    }

    @Test
    public void anExerciseTheCatalogueSaysNothingAboutIsCountedInTheTotalsAndInNoMuscleRow() {
        // A custom exercise with no muscles mapped yet. Dropping its set from the totals too would
        // quietly shrink the week; inventing a muscle for it would be worse.
        PeriodStatistics statistics = PeriodStatistics.of(
                Collections.singletonList(session(unmapped(working("a", kg(50), 8)))),
                chestAndTriceps());

        assertEquals(1, statistics.performedSets());
        assertEquals(400_000L, statistics.volumeGrams());
        assertTrue(statistics.muscles().isEmpty());
    }

    @Test
    public void aBodyweightSetIsWorkForTheMuscleEvenWithNoLoadVolume() {
        // Pull-ups train the back whether or not a kilogram can be attributed to them. Section 9
        // keeps them out of the LOAD total and says how many were left out; it does not pretend
        // they did not happen.
        PeriodStatistics statistics = PeriodStatistics.of(
                Collections.singletonList(session(pullUps(working("a", null, 12)))),
                Collections.singletonList(group("ex-pullup", "back", "Costas", 2,
                        MuscleRole.PRIMARY)));

        assertFalse(statistics.hasVolume());
        assertEquals(1, statistics.setsOutsideVolume());
        assertEquals(1, byName(statistics, "Costas").primarySets());
        assertEquals(12, statistics.totalReps());
    }

    @Test
    public void theMusclesComeOrderedByHowMuchTheyWereTrained() {
        PeriodStatistics statistics = PeriodStatistics.of(
                Collections.singletonList(session(
                        bench(working("a", kg(40), 10), working("b", kg(40), 10)),
                        row(working("c", kg(60), 10)))),
                Arrays.asList(group("ex-bench", "chest", "Peito", 1, MuscleRole.PRIMARY),
                        group("ex-bench", "triceps", "Tríceps", 5, MuscleRole.SECONDARY),
                        group("ex-row", "back", "Costas", 2, MuscleRole.PRIMARY)));

        assertEquals(Arrays.asList("Peito", "Costas", "Tríceps"),
                Arrays.asList(statistics.muscles().get(0).name(),
                        statistics.muscles().get(1).name(),
                        statistics.muscles().get(2).name()));
    }

    @Test
    public void theMuscleListHandedOutCannotBeModified() {
        PeriodStatistics statistics = PeriodStatistics.of(
                Collections.singletonList(session(bench(working("a", kg(40), 10)))),
                chestAndTriceps());

        assertThrows(UnsupportedOperationException.class, () -> statistics.muscles().clear());
    }

    // ------------------------------------------------------------------ helpers

    private static MuscleWorkload byName(PeriodStatistics statistics, String name) {
        for (MuscleWorkload workload : statistics.muscles()) {
            if (workload.name().equals(name)) {
                return workload;
            }
        }
        throw new AssertionError("Sem o grupo " + name + " em " + statistics.muscles());
    }

    private static List<ExerciseMuscleGroup> chestAndTriceps() {
        return Arrays.asList(
                group("ex-bench", "chest", "Peito", 1, MuscleRole.PRIMARY),
                group("ex-bench", "triceps", "Tríceps", 5, MuscleRole.SECONDARY),
                group("ex-triceps", "triceps", "Tríceps", 5, MuscleRole.PRIMARY));
    }

    private static ExerciseMuscleGroup group(String exerciseId, String groupId, String name,
                                             int sortOrder, MuscleRole role) {
        return new ExerciseMuscleGroup(exerciseId, groupId, name, sortOrder, role);
    }

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
        return exercise("ex-bench", "Supino reto", TrackingType.WEIGHT_REPS, sets);
    }

    private static SessionExercise row(LoggedSet... sets) {
        return exercise("ex-row", "Remada curvada", TrackingType.WEIGHT_REPS, sets);
    }

    private static SessionExercise tricepsPushdown(LoggedSet... sets) {
        return exercise("ex-triceps", "Tríceps na polia", TrackingType.WEIGHT_REPS, sets);
    }

    private static SessionExercise pullUps(LoggedSet... sets) {
        return exercise("ex-pullup", "Barra fixa", TrackingType.BODYWEIGHT_REPS, sets);
    }

    private static SessionExercise unmapped(LoggedSet... sets) {
        return exercise("ex-custom", "Exercício meu", TrackingType.WEIGHT_REPS, sets);
    }

    private static SessionExercise exercise(String exerciseId, String name, TrackingType tracking,
                                            LoggedSet... sets) {
        return new SessionExercise("se-" + exerciseId + "-" + sets[0].id(), exerciseId, 0, name,
                tracking, LoadBasis.TOTAL, 1, Laterality.BILATERAL, SideMode.COMBINED, 90, null,
                null, null, new ArrayList<>(Arrays.asList(sets)));
    }

    private static ActiveSession session(SessionExercise... exercises) {
        SessionHeader header = new SessionHeader("sess-" + exercises[0].id(), "tpl-1", "Push A",
                null, SessionStatus.COMPLETED, new SessionClock(1_000_000L, 4_600_000L, 0L, null),
                null, null, null);
        return new ActiveSession(header, Arrays.asList(exercises), null);
    }
}

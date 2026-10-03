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
import io.github.thiagojosetj.gym.domain.model.SideMode;
import io.github.thiagojosetj.gym.domain.model.TrackingType;

public class GroupRoundsTest {

    @Test
    public void theRoundIsNotOverWhileTheOtherExerciseStillOwesItsSet() {
        // A1 done, A2 not. In a superset the rest belongs to the round, so it must not start yet.
        List<SessionExercise> group = Arrays.asList(
                exercise("a1", done(), pending()),
                exercise("a2", pending(), pending()));

        assertFalse(GroupRounds.isRoundComplete(group, 0));
    }

    @Test
    public void theRoundIsOverOnceEveryExerciseHasSettledThatSet() {
        List<SessionExercise> group = Arrays.asList(
                exercise("a1", done(), pending()),
                exercise("a2", done(), pending()));

        assertTrue(GroupRounds.isRoundComplete(group, 0));
        assertFalse(GroupRounds.isRoundComplete(group, 1));
    }

    @Test
    public void finishingTheGroupOutOfOrderStillEndsTheRound() {
        // ACT-01: the planned order is not binding. Reading "after the LAST exercise" positionally
        // would mean someone who does A2 before A1 never gets a rest.
        List<SessionExercise> group = Arrays.asList(
                exercise("a1", pending()),
                exercise("a2", done()));
        assertFalse(GroupRounds.isRoundComplete(group, 0));

        List<SessionExercise> bothDone = Arrays.asList(
                exercise("a1", done()),
                exercise("a2", done()));
        assertTrue(GroupRounds.isRoundComplete(bothDone, 0));
    }

    @Test
    public void aSkippedSetEndsTheRoundBecauseNothingIsStillOwed() {
        List<SessionExercise> group = Arrays.asList(
                exercise("a1", done()),
                exercise("a2", skipped()));

        assertTrue(GroupRounds.isRoundComplete(group, 0));
    }

    @Test
    public void anUnevenGroupRestsAfterEveryRound() {
        // 3 sets against 2. On round 3 the short exercise owes nothing, so it must not hold the
        // round open for ever.
        List<SessionExercise> group = Arrays.asList(
                exercise("a1", done(), done(), done()),
                exercise("a2", done(), done()));

        assertTrue(GroupRounds.isRoundComplete(group, 0));
        assertTrue(GroupRounds.isRoundComplete(group, 1));
        assertTrue(GroupRounds.isRoundComplete(group, 2));
    }

    @Test
    public void aRoundNobodyHasASetForIsNotAFinishedRound() {
        // Past the end of every exercise there is no round, so there is nothing to rest after.
        List<SessionExercise> group = Arrays.asList(exercise("a1", done()), exercise("a2", done()));

        assertFalse(GroupRounds.isRoundComplete(group, 5));
        assertFalse(GroupRounds.isRoundComplete(Collections.emptyList(), 0));
        assertFalse(GroupRounds.isRoundComplete(null, 0));
        assertFalse(GroupRounds.isRoundComplete(group, -1));
    }

    @Test
    public void theRoundOfASetIsItsOrdinalAmongItsExercisesSets() {
        SessionExercise exercise = exercise("a1", done(), pending(), pending());

        assertEquals(0, GroupRounds.roundOf(exercise, exercise.sets().get(0).id()));
        assertEquals(2, GroupRounds.roundOf(exercise, exercise.sets().get(2).id()));
        assertEquals(-1, GroupRounds.roundOf(exercise, "not-in-this-exercise"));
        assertEquals(-1, GroupRounds.roundOf(exercise, null));
        assertEquals(-1, GroupRounds.roundOf(null, "whatever"));
    }

    @Test
    public void aDropDoesNotCountAsARoundOfItsOwn() {
        // Segments are nested in their set (ADR-0037), so a drop-set in a superset is one round,
        // not several, and the drops never shift the round index of the sets after it.
        LoggedSet withDrops = new LoggedSet("s1", 0, 1, null, null, true, null, null, null, 0,
                SetValues.EMPTY, SetStatus.COMPLETED, 1L, null, null,
                Collections.singletonList(drop()));
        SessionExercise a1 = exercise("a1", withDrops, pending());
        SessionExercise a2 = exercise("a2", done(), pending());

        assertEquals(1, GroupRounds.roundOf(a1, a1.sets().get(1).id()));
        assertTrue(GroupRounds.isRoundComplete(Arrays.asList(a1, a2), 0));
    }

    // ------------------------------------------------------------------ helpers


    @Test
    public void aWarmUpOnOneExerciseDoesNotPairWithTheOthersWorkingSet() {
        // A1 warms up twice before its working sets; A2 goes straight in. A "round" of a superset
        // is a round of WORKING sets - pairing A1's warm-up with A2's first real set would end the
        // round, and the rest would start after a warm-up.
        SessionExercise a1 = exercise("a1", warmUp(), warmUp(), done(), pending());
        SessionExercise a2 = exercise("a2", done(), pending());
        List<SessionExercise> group = Arrays.asList(a1, a2);

        LoggedSet a1FirstWorking = a1.sets().get(2);
        LoggedSet a2FirstWorking = a2.sets().get(0);

        assertEquals("a primeira serie de verdade de cada um e a mesma rodada",
                GroupRounds.roundOf(a2, a2FirstWorking.id()),
                GroupRounds.roundOf(a1, a1FirstWorking.id()));
    }

    @Test
    public void aWarmUpDoesNotCloseTheRound() {
        SessionExercise a1 = exercise("a1", warmUp(), pending());
        SessionExercise a2 = exercise("a2", done());
        List<SessionExercise> group = Arrays.asList(a1, a2);

        // A1 has done only its warm-up; its working set is still owed, so the round is not over.
        assertFalse("um aquecimento nao fecha a rodada",
                GroupRounds.isRoundComplete(group, 0));
    }

    private static LoggedSet warmUp() {
        nextId++;
        return new LoggedSet("warm-" + nextId, 0, null, "technique-warmup", "AQ", false, null,
                null, null, 0, SetValues.EMPTY, SetStatus.COMPLETED, 1L, null, null,
                Collections.emptyList());
    }

    private static LoggedSet done() {
        return set(SetStatus.COMPLETED);
    }

    private static LoggedSet pending() {
        return set(SetStatus.PENDING);
    }

    private static LoggedSet skipped() {
        return set(SetStatus.SKIPPED);
    }

    private static int nextId;

    private static LoggedSet set(SetStatus status) {
        nextId++;
        return new LoggedSet("set-" + nextId, 0, 1, null, null, true, null, null, null, 0,
                SetValues.EMPTY, status, status == SetStatus.COMPLETED ? 1L : null, null, null,
                Collections.emptyList());
    }

    private static LoggedSet drop() {
        nextId++;
        return new LoggedSet("drop-" + nextId, 0, null, null, null, true, null, null, null, 0,
                SetValues.EMPTY, SetStatus.COMPLETED, 1L, null, null, Collections.emptyList());
    }

    private static SessionExercise exercise(String id, LoggedSet... sets) {
        return new SessionExercise(id, "ex-" + id, 0, "Exercicio " + id, TrackingType.WEIGHT_REPS,
                LoadBasis.TOTAL, 1, Laterality.BILATERAL, SideMode.COMBINED, 90, null, null, null,
                Arrays.asList(sets));
    }
}

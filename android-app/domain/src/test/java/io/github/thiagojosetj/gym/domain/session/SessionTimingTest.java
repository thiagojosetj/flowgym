package io.github.thiagojosetj.gym.domain.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SessionTimingTest {

    private static final long START = 1_000_000L;
    private static final long MINUTE = 60_000L;

    @Test
    public void withoutPausesEffectiveTimeIsTheWholeElapsedTime() {
        SessionTiming timing = SessionTiming.running(START);

        assertEquals(10 * MINUTE, timing.totalMs(START + 10 * MINUTE));
        assertEquals(0L, timing.pausedMs(START + 10 * MINUTE));
        assertEquals(10 * MINUTE, timing.effectiveMs(START + 10 * MINUTE));
        assertFalse(timing.isPaused());
        assertFalse(timing.isFinished());
    }

    @Test
    public void aClosedPauseIsSubtractedFromTheEffectiveTime() {
        SessionTiming timing = new SessionTiming(START, null, Collections.singletonList(
                new PauseInterval(START + 2 * MINUTE, START + 5 * MINUTE)));

        long now = START + 10 * MINUTE;
        assertEquals(10 * MINUTE, timing.totalMs(now));
        assertEquals(3 * MINUTE, timing.pausedMs(now));
        assertEquals(7 * MINUTE, timing.effectiveMs(now));
    }

    @Test
    public void anOpenPauseFreezesTheEffectiveTime() {
        SessionTiming timing = new SessionTiming(START, null,
                Collections.singletonList(PauseInterval.open(START + 4 * MINUTE)));

        // Two more minutes of wall clock, but the session is paused: effective time does not move.
        assertEquals(4 * MINUTE, timing.effectiveMs(START + 4 * MINUTE));
        assertEquals(4 * MINUTE, timing.effectiveMs(START + 6 * MINUTE));
        assertEquals(6 * MINUTE, timing.totalMs(START + 6 * MINUTE));
        assertTrue(timing.isPaused());
    }

    @Test
    public void afterFinishingTheNumbersStopMovingWithNow() {
        SessionTiming timing = new SessionTiming(START, START + 30 * MINUTE, Collections.singletonList(
                new PauseInterval(START + MINUTE, START + 3 * MINUTE)));

        long muchLater = START + 10 * 60 * MINUTE;
        assertEquals(30 * MINUTE, timing.totalMs(muchLater));
        assertEquals(2 * MINUTE, timing.pausedMs(muchLater));
        assertEquals(28 * MINUTE, timing.effectiveMs(muchLater));
        assertTrue(timing.isFinished());
    }

    @Test
    public void aPauseStillOpenWhenTheSessionEndedIsMeasuredUpToTheEnd() {
        // The user paused and finished from the notification without resuming.
        SessionTiming timing = new SessionTiming(START, START + 20 * MINUTE,
                Collections.singletonList(PauseInterval.open(START + 15 * MINUTE)));

        long muchLater = START + 100 * MINUTE;
        assertEquals(5 * MINUTE, timing.pausedMs(muchLater));
        assertEquals(15 * MINUTE, timing.effectiveMs(muchLater));
    }

    @Test
    public void aClockThatMovesBackwardsShowsZeroInsteadOfANegativeTime() {
        SessionTiming timing = SessionTiming.running(START);

        // The user corrected the device time to an hour earlier, mid-workout.
        assertEquals(0L, timing.totalMs(START - 60 * MINUTE));
        assertEquals(0L, timing.effectiveMs(START - 60 * MINUTE));
    }

    @Test
    public void pausedTimeNeverExceedsTheTotalTime() {
        // Defensive: overlapping rows (a bug elsewhere, or a clock change) must not make the
        // effective time negative.
        SessionTiming timing = new SessionTiming(START, null, Arrays.asList(
                new PauseInterval(START, START + 10 * MINUTE),
                new PauseInterval(START, START + 10 * MINUTE)));

        long now = START + 10 * MINUTE;
        assertEquals(10 * MINUTE, timing.pausedMs(now));
        assertEquals(0L, timing.effectiveMs(now));
    }

    @Test
    public void twoOpenPausesAreRejected() {
        List<PauseInterval> pauses = Arrays.asList(PauseInterval.open(START), PauseInterval.open(START + MINUTE));
        assertThrows(IllegalArgumentException.class, () -> new SessionTiming(START, null, pauses));
    }

    @Test
    public void theListOfPausesIsCopiedAndUnmodifiable() {
        List<PauseInterval> mutable = new ArrayList<>();
        mutable.add(new PauseInterval(START, START + MINUTE));
        SessionTiming timing = new SessionTiming(START, null, mutable);

        mutable.add(PauseInterval.open(START + 2 * MINUTE));

        assertEquals(1, timing.pauses().size());
        assertThrows(UnsupportedOperationException.class,
                () -> timing.pauses().add(PauseInterval.open(START)));
    }

    @Test
    public void anIntervalThatEndsBeforeItStartsIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new PauseInterval(START, START - 1));
    }
}

package io.github.thiagojosetj.gym.domain.session;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class WorkingSetPairingTest {

    private static final List<Boolean> THREE_WORKING = Arrays.asList(true, true, true);

    @Test
    public void workingSetsAreNumberedAndWarmUpsAreNot() {
        List<Integer> numbers = WorkingSetPairing.numberWorkingSets(
                Arrays.asList(false, true, true, false, true));

        assertEquals(Arrays.asList(null, 1, 2, null, 3), numbers);
    }

    @Test
    public void sameStructureMeansSamePositions() {
        int[] pairs = WorkingSetPairing.pair(THREE_WORKING, THREE_WORKING);

        assertArrayEquals(new int[]{0, 1, 2}, pairs);
    }

    @Test
    public void aWarmUpAddedTodayDoesNotShiftTheComparison() {
        // Today: warm-up + 3 working. Last time: 3 working. The first working set of today must be
        // compared with the first working set of last time, not with its warm-up.
        int[] pairs = WorkingSetPairing.pair(Arrays.asList(false, true, true, true), THREE_WORKING);

        assertArrayEquals(new int[]{WorkingSetPairing.NONE, 0, 1, 2}, pairs);
    }

    @Test
    public void aWarmUpInThePreviousSessionIsSkippedToo() {
        int[] pairs = WorkingSetPairing.pair(THREE_WORKING, Arrays.asList(false, true, true, true));

        assertArrayEquals(new int[]{1, 2, 3}, pairs);
    }

    @Test
    public void anExtraSetTodayHasNothingToCompareWith() {
        int[] pairs = WorkingSetPairing.pair(Arrays.asList(true, true, true, true), THREE_WORKING);

        assertArrayEquals(new int[]{0, 1, 2, WorkingSetPairing.NONE}, pairs);
    }

    @Test
    public void withNoPreviousSessionNothingIsPaired() {
        int[] pairs = WorkingSetPairing.pair(THREE_WORKING, Collections.emptyList());

        assertArrayEquals(new int[]{WorkingSetPairing.NONE, WorkingSetPairing.NONE, WorkingSetPairing.NONE},
                pairs);
        assertArrayEquals(new int[0], WorkingSetPairing.pair(null, THREE_WORKING));
        assertArrayEquals(new int[]{0}, WorkingSetPairing.pair(Collections.singletonList(true),
                Arrays.asList(true, true)));
    }

    @Test
    public void aNullFlagCountsAsAWarmUpRatherThanCrashing() {
        // A missing technique row should degrade, not throw, while reading a session.
        assertEquals(Collections.singletonList(null),
                WorkingSetPairing.numberWorkingSets(Collections.singletonList(null)));
        assertArrayEquals(new int[]{WorkingSetPairing.NONE},
                WorkingSetPairing.pair(Collections.singletonList(null), THREE_WORKING));
    }
}

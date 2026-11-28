package io.github.thiagojosetj.gym.domain.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MetricChangeTest {

    @Test
    public void moreThanLastTimePointsUp() {
        MetricChange change = new MetricChange(1_200L, 1_000L);

        assertEquals(MetricChange.Direction.UP, change.direction());
        assertEquals(200L, change.delta());
    }

    @Test
    public void lessThanLastTimePointsDown() {
        MetricChange change = new MetricChange(900L, 1_000L);

        assertEquals(MetricChange.Direction.DOWN, change.direction());
        assertEquals(-100L, change.delta());
    }

    @Test
    public void theExactSameNumberIsATieAndNotAnImprovement() {
        MetricChange change = new MetricChange(1_000L, 1_000L);

        assertEquals(MetricChange.Direction.SAME, change.direction());
        assertEquals(0L, change.delta());
        assertEquals(0.0, change.percent(), 0.0001);
    }

    @Test
    public void thePercentageIsSignedAndRelativeToThePreviousValue() {
        assertEquals(25.0, new MetricChange(1_250L, 1_000L).percent(), 0.0001);
        assertEquals(-20.0, new MetricChange(800L, 1_000L).percent(), 0.0001);
    }

    @Test
    public void thereIsNoPercentageWhenThereWasNothingToGrowFrom() {
        // PRODUCT_SPEC section 11: the percentage only appears when the previous value is > 0.
        // The first time an exercise is loaded at all, "+infinity%" would be a made-up number.
        MetricChange fromNothing = new MetricChange(5_000L, 0L);

        assertFalse(fromNothing.hasPercent());
        assertEquals(MetricChange.Direction.UP, fromNothing.direction());
        assertEquals(5_000L, fromNothing.delta());
    }

    @Test
    public void askingForAPercentageThatDoesNotExistFailsLoudlyInsteadOfReturningZero() {
        MetricChange fromNothing = new MetricChange(5_000L, 0L);

        // A screen that forgets to call hasPercent() must break in a test, not print "0%" to a user.
        assertThrows(IllegalStateException.class, fromNothing::percent);
    }

    @Test
    public void aNegativePreviousValueAlsoHasNoPercentage() {
        // Assistance loads are stored as negative grams (PRODUCT_SPEC section 6.4), so a metric can
        // legitimately arrive negative; a ratio against it would not mean what the arrow means.
        assertFalse(new MetricChange(10L, -50L).hasPercent());
    }

    @Test
    public void bothSidesAtZeroIsATieWithNoPercentage() {
        MetricChange nothingEitherTime = new MetricChange(0L, 0L);

        assertEquals(MetricChange.Direction.SAME, nothingEitherTime.direction());
        assertFalse(nothingEitherTime.hasPercent());
    }

    @Test
    public void aValueIsComparedAsStoredWithNoUnitConversion() {
        // Grams in, grams out: the type never guesses what it is holding.
        MetricChange grams = new MetricChange(24_000L, 20_000L);

        assertTrue(grams.hasPercent());
        assertEquals(20.0, grams.percent(), 0.0001);
    }
}

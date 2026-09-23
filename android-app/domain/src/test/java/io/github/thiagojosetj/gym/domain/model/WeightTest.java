package io.github.thiagojosetj.gym.domain.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class WeightTest {

    @Test
    public void storesKilogramsAsExactGrams() {
        assertEquals(42_500L, Weight.of(42.5, WeightUnit.KILOGRAM).grams());
        assertEquals(1_250L, Weight.of(1.25, WeightUnit.KILOGRAM).grams());
    }

    @Test
    public void poundsRoundTripWithoutVisibleError() {
        Weight w = Weight.of(45, WeightUnit.POUND);
        assertEquals(20_412L, w.grams());
        assertEquals(45.0, w.in(WeightUnit.POUND), 0.01);
    }

    @Test
    public void equalityIsExactOnGrams() {
        assertEquals(Weight.ofGrams(40_000), Weight.of(40, WeightUnit.KILOGRAM));
        assertEquals(Weight.ofGrams(40_000).hashCode(), Weight.of(40, WeightUnit.KILOGRAM).hashCode());
    }

    @Test
    public void negativeMeansAssistanceAndIsAllowedHere() {
        assertEquals(-25_000L, Weight.of(-25, WeightUnit.KILOGRAM).grams());
    }

    @Test
    public void rejectsAbsurdValues() {
        assertThrows(IllegalArgumentException.class, () -> Weight.ofGrams(Weight.MAX_ABS_GRAMS + 1));
        assertThrows(IllegalArgumentException.class, () -> Weight.ofGrams(-Weight.MAX_ABS_GRAMS - 1));
        // Regression (review 2026-09-22): Math.abs(Long.MIN_VALUE) overflowed past the check.
        assertThrows(IllegalArgumentException.class, () -> Weight.ofGrams(Long.MIN_VALUE));
    }

    @Test
    public void rejectsNonFiniteInputInsteadOfSilentlyUsingZero() {
        // Regression (review 2026-09-22): Math.round(NaN) == 0 made NaN a valid 0 g.
        assertThrows(IllegalArgumentException.class, () -> Weight.of(Double.NaN, WeightUnit.KILOGRAM));
        assertThrows(IllegalArgumentException.class,
                () -> Weight.of(Double.NEGATIVE_INFINITY, WeightUnit.KILOGRAM));
        assertThrows(IllegalArgumentException.class,
                () -> Weight.of(Double.POSITIVE_INFINITY, WeightUnit.POUND));
    }
}

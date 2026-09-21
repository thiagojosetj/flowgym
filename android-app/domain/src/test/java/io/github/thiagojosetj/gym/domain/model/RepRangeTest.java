package io.github.thiagojosetj.gym.domain.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RepRangeTest {

    @Test
    public void formatsFixedAndRange() {
        assertEquals("12", RepRange.exactly(12).format());
        assertEquals("8–10", RepRange.between(8, 10).format());
    }

    @Test
    public void fixedWhenMinEqualsMax() {
        assertTrue(RepRange.between(10, 10).isFixed());
        assertFalse(RepRange.between(8, 10).isFixed());
    }

    @Test
    public void rejectsInvalidRanges() {
        assertThrows(IllegalArgumentException.class, () -> RepRange.between(0, 5));
        assertThrows(IllegalArgumentException.class, () -> RepRange.between(10, 8));
        assertThrows(IllegalArgumentException.class, () -> RepRange.exactly(RepRange.MAX_REPS + 1));
    }
}

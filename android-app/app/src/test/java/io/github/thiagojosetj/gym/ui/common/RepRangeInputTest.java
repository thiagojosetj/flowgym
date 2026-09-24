package io.github.thiagojosetj.gym.ui.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import io.github.thiagojosetj.gym.domain.model.RepRange;

/** Plain JVM test: the reps field accepts "8" or "8-10" (and reads back what the app displays). */
public class RepRangeInputTest {

    @Test
    public void parsesFixedValue() {
        assertEquals(RepRange.exactly(12), RepRangeInput.parse("12"));
        assertEquals(RepRange.exactly(8), RepRangeInput.parse("  8 "));
    }

    @Test
    public void parsesRangesWithHyphenOrEnDash() {
        assertEquals(RepRange.between(8, 10), RepRangeInput.parse("8-10"));
        assertEquals(RepRange.between(8, 10), RepRangeInput.parse("8–10")); // what format() produces
        assertEquals(RepRange.between(6, 8), RepRangeInput.parse(" 6 - 8 "));
    }

    @Test
    public void blankIsNoTarget() {
        assertNull(RepRangeInput.parse(""));
        assertNull(RepRangeInput.parse("   "));
        assertNull(RepRangeInput.parse(null));
    }

    @Test
    public void rejectsNonsense() {
        assertThrows(IllegalArgumentException.class, () -> RepRangeInput.parse("8-"));
        assertThrows(IllegalArgumentException.class, () -> RepRangeInput.parse("-8"));
        assertThrows(IllegalArgumentException.class, () -> RepRangeInput.parse("10-8")); // max < min
        assertThrows(IllegalArgumentException.class, () -> RepRangeInput.parse("0"));
        assertThrows(IllegalArgumentException.class, () -> RepRangeInput.parse("oito"));
    }

    @Test
    public void formatRoundTripsThroughParse() {
        RepRange range = RepRange.between(8, 10);
        assertEquals(range, RepRangeInput.parse(RepRangeInput.format(range)));
        assertEquals("", RepRangeInput.format(null));
    }
}

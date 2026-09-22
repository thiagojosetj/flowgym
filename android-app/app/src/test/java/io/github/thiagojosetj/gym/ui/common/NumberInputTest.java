package io.github.thiagojosetj.gym.ui.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import java.util.Locale;

import org.junit.Test;

/** Plain JVM test (no Robolectric): NumberInput is pure Java. */
public class NumberInputTest {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    @Test
    public void acceptsCommaAndDotAsDecimalSeparator() {
        assertEquals(42.5, NumberInput.parseDecimal("42,5"), 0.0);
        assertEquals(42.5, NumberInput.parseDecimal("42.5"), 0.0);
        assertEquals(40.0, NumberInput.parseDecimal(" 40 "), 0.0);
        assertEquals(1.25, NumberInput.parseDecimal("1,25"), 0.0);
    }

    @Test
    public void blankIsNull() {
        assertNull(NumberInput.parseDecimal(""));
        assertNull(NumberInput.parseDecimal("   "));
        assertNull(NumberInput.parseDecimal(null));
        assertNull(NumberInput.parseWholeNumber(""));
    }

    @Test
    public void rejectsMalformedNumbers() {
        assertThrows(NumberFormatException.class, () -> NumberInput.parseDecimal("4,2,5"));
        assertThrows(NumberFormatException.class, () -> NumberInput.parseDecimal("-5"));
        assertThrows(NumberFormatException.class, () -> NumberInput.parseDecimal("abc"));
        assertThrows(NumberFormatException.class, () -> NumberInput.parseDecimal("1,2345"));
        assertThrows(NumberFormatException.class, () -> NumberInput.parseWholeNumber("12.5"));
        assertThrows(NumberFormatException.class, () -> NumberInput.parseWholeNumber("-1"));
    }

    @Test
    public void formatsWithLocaleSeparatorAndNoTrailingZeros() {
        assertEquals("42,5", NumberInput.formatDecimal(42.5, PT_BR));
        assertEquals("40", NumberInput.formatDecimal(40.0, PT_BR));
        assertEquals("42.5", NumberInput.formatDecimal(42.5, Locale.US));
        assertEquals("20,41", NumberInput.formatDecimal(20.4117, PT_BR));
    }
}

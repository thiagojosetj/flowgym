package io.github.thiagojosetj.gym.domain.progress;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** The window filters of PRODUCT_SPEC PRG-02. */
public class ProgressWindowTest {

    @Test
    public void aWindowKeepsTheLastOnesAndKeepsThemInOrder() {
        List<String> days = Arrays.asList("d1", "d2", "d3", "d4", "d5", "d6", "d7");

        List<String> last5 = ProgressWindow.LAST_5.applyTo(days);

        assertEquals(5, last5.size());
        // Oldest first on the way out too: a window slices the history, it does not reverse it.
        assertEquals("d3", last5.get(0));
        assertEquals("d7", last5.get(4));
    }

    @Test
    public void aWindowWiderThanTheHistoryChangesNothing() {
        List<String> days = Arrays.asList("d1", "d2", "d3");

        // Same list, not a copy: there is nothing to leave out, so there is nothing to build.
        assertSame(days, ProgressWindow.LAST_5.applyTo(days));
        assertSame(days, ProgressWindow.LAST_30.applyTo(days));
        assertSame(days, ProgressWindow.ALL.applyTo(days));
        assertSame(days, ProgressWindow.ALL.applyTo(days));
    }

    @Test
    public void aWindowThatHidesNothingIsNotAChoice() {
        // Four sessions: "the last 5" and "the last 30" both show all four, which is what "all"
        // shows. Offering them as filters would claim a choice that does not exist.
        assertFalse(ProgressWindow.LAST_5.narrows(4));
        assertFalse(ProgressWindow.LAST_30.narrows(4));
        assertFalse("um limite igual ao total nao esconde nada",
                ProgressWindow.LAST_5.narrows(5));
        assertTrue(ProgressWindow.LAST_5.narrows(6));
        assertFalse("'tudo' nunca e um filtro", ProgressWindow.ALL.narrows(1000));
    }

    @Test
    public void anEmptyOrAbsentHistorySurvivesAWindow() {
        assertTrue(ProgressWindow.LAST_5.applyTo(new ArrayList<String>()).isEmpty());
        assertTrue(ProgressWindow.LAST_5.applyTo(Collections.<String>emptyList()).isEmpty());
        assertEquals(null, ProgressWindow.LAST_5.applyTo(null));
        assertFalse(ProgressWindow.LAST_5.narrows(0));
    }
}

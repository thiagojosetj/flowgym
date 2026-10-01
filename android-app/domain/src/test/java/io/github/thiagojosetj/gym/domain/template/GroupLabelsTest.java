package io.github.thiagojosetj.gym.domain.template;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class GroupLabelsTest {

    @Test
    public void theFirstGroupsAreLettersInOrder() {
        assertEquals("A", GroupLabels.forIndex(0));
        assertEquals("B", GroupLabels.forIndex(1));
        assertEquals("Y", GroupLabels.forIndex(24));
        assertEquals("Z", GroupLabels.forIndex(25));
    }

    @Test
    public void afterZTheLabelsKeepGoingLikeSpreadsheetColumns() {
        assertEquals("AA", GroupLabels.forIndex(26));
        assertEquals("AB", GroupLabels.forIndex(27));
        assertEquals("AZ", GroupLabels.forIndex(51));
        assertEquals("BA", GroupLabels.forIndex(52));
        assertEquals("ZZ", GroupLabels.forIndex(701));
        assertEquals("AAA", GroupLabels.forIndex(702));
    }

    @Test
    public void noTwoGroupsOfOneTemplateShareALabel() {
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < 2_000; index++) {
            seen.add(GroupLabels.forIndex(index));
        }
        assertEquals(2_000, seen.size());
    }

    @Test
    public void aLabelIsOnlyLettersSoItCannotBeReadAsAnExerciseNumber() {
        // "A1" must mean group A, exercise 1: a label ending in a digit would make "A11" ambiguous.
        for (int index = 0; index < 1_000; index++) {
            String label = GroupLabels.forIndex(index);
            assertTrue(label, label.matches("[A-Z]+"));
        }
    }

    @Test
    public void aGroupCannotSitBeforeTheFirst() {
        assertThrows(IllegalArgumentException.class, () -> GroupLabels.forIndex(-1));
    }
}

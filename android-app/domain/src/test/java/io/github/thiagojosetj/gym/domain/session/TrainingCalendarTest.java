package io.github.thiagojosetj.gym.domain.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.Collections;

public class TrainingCalendarTest {

    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);

    @Test
    public void theFirstOfTheMonthSitsUnderItsOwnWeekday() {
        // 1 October 2026 is a Thursday. With weeks starting on Sunday that is the fifth column,
        // so four blanks come before it.
        TrainingCalendar calendar = TrainingCalendar.of(OCTOBER, DayOfWeek.SUNDAY,
                Collections.emptyList());

        assertEquals(4, leadingBlanks(calendar));
        assertEquals(LocalDate.of(2026, 10, 1), calendar.days().get(4).date());
    }

    @Test
    public void startingTheWeekOnMondayMovesEverySquare() {
        TrainingCalendar sunday = TrainingCalendar.of(OCTOBER, DayOfWeek.SUNDAY,
                Collections.emptyList());
        TrainingCalendar monday = TrainingCalendar.of(OCTOBER, DayOfWeek.MONDAY,
                Collections.emptyList());

        assertEquals(4, leadingBlanks(sunday));
        assertEquals(3, leadingBlanks(monday));
    }

    @Test
    public void everyRowHasSevenSquares() {
        for (int month = 1; month <= 12; month++) {
            TrainingCalendar calendar = TrainingCalendar.of(YearMonth.of(2026, month),
                    DayOfWeek.SUNDAY, Collections.emptyList());
            assertEquals("mes " + month, 0, calendar.days().size() % 7);
            assertEquals("mes " + month, calendar.days().size() / 7, calendar.weeks());
        }
    }

    @Test
    public void februaryOfALeapYearHasTwentyNineDays() {
        TrainingCalendar calendar = TrainingCalendar.of(YearMonth.of(2028, 2), DayOfWeek.SUNDAY,
                Collections.emptyList());

        assertEquals(29, realDays(calendar));
    }

    @Test
    public void aDayWithASessionIsMarkedAndTheOthersAreNot() {
        TrainingCalendar calendar = TrainingCalendar.of(OCTOBER, DayOfWeek.SUNDAY,
                Arrays.asList(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 5)));

        assertTrue(dayOf(calendar, 3).trained());
        assertTrue(dayOf(calendar, 5).trained());
        assertFalse(dayOf(calendar, 4).trained());
        assertEquals(2, calendar.trainedDays());
    }

    @Test
    public void twoSessionsOnTheSameDayMarkItOnce() {
        // The grid says "trained", not "how many times": counting would make a square mean two
        // different things depending on how the day went.
        TrainingCalendar calendar = TrainingCalendar.of(OCTOBER, DayOfWeek.SUNDAY,
                Arrays.asList(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 3)));

        assertEquals(1, calendar.trainedDays());
    }

    @Test
    public void datesFromOtherMonthsAreIgnoredRatherThanRefused() {
        TrainingCalendar calendar = TrainingCalendar.of(OCTOBER, DayOfWeek.SUNDAY,
                Arrays.asList(LocalDate.of(2026, 9, 30), LocalDate.of(2026, 10, 1),
                        LocalDate.of(2026, 11, 1)));

        assertEquals(1, calendar.trainedDays());
        assertTrue(dayOf(calendar, 1).trained());
    }

    @Test
    public void theBlanksAreBlankAndNotDayZero() {
        TrainingCalendar calendar = TrainingCalendar.of(OCTOBER, DayOfWeek.SUNDAY,
                Collections.emptyList());

        assertTrue(calendar.days().get(0).isBlank());
        assertFalse(calendar.days().get(0).trained());
    }

    @Test
    public void theGridHandedOutCannotBeChangedFromOutside() {
        TrainingCalendar calendar = TrainingCalendar.of(OCTOBER, DayOfWeek.SUNDAY,
                Collections.emptyList());

        assertThrows(UnsupportedOperationException.class, () -> calendar.days().clear());
    }

    // ------------------------------------------------------------------ helpers

    private static int leadingBlanks(TrainingCalendar calendar) {
        int blanks = 0;
        for (TrainingCalendar.Day day : calendar.days()) {
            if (!day.isBlank()) {
                break;
            }
            blanks++;
        }
        return blanks;
    }

    private static int realDays(TrainingCalendar calendar) {
        int count = 0;
        for (TrainingCalendar.Day day : calendar.days()) {
            if (!day.isBlank()) {
                count++;
            }
        }
        return count;
    }

    private static TrainingCalendar.Day dayOf(TrainingCalendar calendar, int dayOfMonth) {
        for (TrainingCalendar.Day day : calendar.days()) {
            if (!day.isBlank() && day.date().getDayOfMonth() == dayOfMonth) {
                return day;
            }
        }
        throw new AssertionError("Sem dia " + dayOfMonth);
    }
}

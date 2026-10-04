package io.github.thiagojosetj.gym.domain.session;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * One month of the training calendar (PRODUCT_SPEC HIS-02).
 *
 * <p>A day is "trained" when a session's {@code local_date} says so. That column is written when the
 * session starts, in the zone the person was in, and it is never recomputed: a workout done at 23:30
 * in São Paulo belongs to that day even when the phone is later in Tokyo. Deriving the day from
 * {@code started_at} and the current zone would move workouts between squares every time someone
 * crosses a time zone, which is the same mistake the comparison subtitle made (ADR-0004 keeps the
 * API-level note; the rule itself is section 11).
 *
 * <p>Pure: it is handed the dates that have sessions and lays out the grid. It does not know what a
 * session is, and it never asks a clock.
 */
public record TrainingCalendar(YearMonth month, DayOfWeek firstDayOfWeek, List<Day> days) {

    /**
     * One square.
     *
     * @param date    null for the blanks that pad the first week, so the 1st lands under its weekday
     * @param trained true when at least one session is recorded on this date
     */
    public record Day(LocalDate date, boolean trained) {

        public boolean isBlank() {
            return date == null;
        }
    }

    public TrainingCalendar {
        days = Collections.unmodifiableList(new ArrayList<>(days));
    }

    /**
     * Lays out {@code month}, marking every date in {@code trainedDates}.
     *
     * <p>Dates outside the month are ignored rather than rejected: the caller hands over everything
     * it has, and a calendar that threw because the person also trained last month would be a
     * strange thing to build.
     */
    public static TrainingCalendar of(YearMonth month, DayOfWeek firstDayOfWeek,
                                      Collection<LocalDate> trainedDates) {
        Set<LocalDate> trained = trainedDates == null
                ? Collections.emptySet()
                : new HashSet<>(trainedDates);

        List<Day> days = new ArrayList<>(42);
        LocalDate first = month.atDay(1);
        // How many squares before the 1st, so it sits under the right weekday column.
        int lead = Math.floorMod(first.getDayOfWeek().getValue() - firstDayOfWeek.getValue(), 7);
        for (int i = 0; i < lead; i++) {
            days.add(new Day(null, false));
        }
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            LocalDate date = month.atDay(day);
            days.add(new Day(date, trained.contains(date)));
        }
        // Pad the last week so every row has seven squares and the grid does not go ragged.
        while (days.size() % 7 != 0) {
            days.add(new Day(null, false));
        }
        return new TrainingCalendar(month, firstDayOfWeek, days);
    }

    /** Days of this month that have at least one session. */
    public int trainedDays() {
        int count = 0;
        for (Day day : days) {
            if (day.trained()) {
                count++;
            }
        }
        return count;
    }

    /** Seven per row, by construction, so a grid can rely on it. */
    public int weeks() {
        return days.size() / 7;
    }
}

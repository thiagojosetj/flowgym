package io.github.thiagojosetj.gym.domain.progress;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/**
 * A closed range of days the statistics screen shows at once (PRODUCT_SPEC PRG-04).
 *
 * <p>Both ends are inclusive, and both are days as they were LIVED - the {@code local_date} a
 * session wrote in the zone the person was in. A week that was computed from instants would move
 * a late Sunday workout into the next week the moment the phone crossed a time zone.
 *
 * <p>Where a week starts comes from the caller's locale, not from here: Sunday in Brazil, Monday
 * in most of Europe, and neither is more correct than the other.
 */
public record TrainingPeriod(Kind kind, LocalDate from, LocalDate to) {

    public enum Kind { WEEK, MONTH }

    public static TrainingPeriod weekOf(LocalDate day, DayOfWeek firstDayOfWeek) {
        LocalDate start = day.with(TemporalAdjusters.previousOrSame(firstDayOfWeek));
        return new TrainingPeriod(Kind.WEEK, start, start.plusDays(6));
    }

    public static TrainingPeriod monthOf(LocalDate day) {
        LocalDate start = day.withDayOfMonth(1);
        return new TrainingPeriod(Kind.MONTH, start, start.plusMonths(1).minusDays(1));
    }

    /** The same kind of period, {@code steps} of it away. Negative goes back. */
    public TrainingPeriod shifted(int steps) {
        LocalDate anchor = kind == Kind.WEEK
                ? from.plusWeeks(steps)
                : from.plusMonths(steps);
        // Rebuilt from the anchor rather than by shifting both ends: months are not all the same
        // length, and 31 January plus one month is not 28 February plus one month back.
        return kind == Kind.WEEK
                ? new TrainingPeriod(kind, anchor, anchor.plusDays(6))
                : monthOf(anchor);
    }

    /** The same day, read as the other kind of period. */
    public TrainingPeriod as(Kind other, DayOfWeek firstDayOfWeek, LocalDate today) {
        if (other == kind) {
            return this;
        }
        // Anchored on today when today is inside, so switching Week/Month on the current period
        // keeps showing the current one instead of jumping to the month of its first day.
        LocalDate anchor = contains(today) ? today : from;
        return other == Kind.WEEK ? weekOf(anchor, firstDayOfWeek) : monthOf(anchor);
    }

    public boolean contains(LocalDate day) {
        return day != null && !day.isBefore(from) && !day.isAfter(to);
    }

    /** True when some day of this period is on or before {@code day}. */
    public boolean startsOnOrBefore(LocalDate day) {
        return day != null && !from.isAfter(day);
    }

    /** True when some day of this period is on or after {@code day}. */
    public boolean endsOnOrAfter(LocalDate day) {
        return day != null && !to.isBefore(day);
    }
}

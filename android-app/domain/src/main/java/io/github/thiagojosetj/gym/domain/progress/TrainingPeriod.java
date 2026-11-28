package io.github.thiagojosetj.gym.domain.progress;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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

    /**
     * The kinds of period the screen offers.
     *
     * <p>{@code CUSTOM} is the "período personalizado" of PRG-02: any two days, picked by hand.
     * It is the only kind that cannot be derived from a single day, which is why {@link #as} does
     * not produce one - {@link #custom} does, from the two dates it needs.
     */
    public enum Kind { WEEK, MONTH, CUSTOM }

    public static TrainingPeriod weekOf(LocalDate day, DayOfWeek firstDayOfWeek) {
        LocalDate start = day.with(TemporalAdjusters.previousOrSame(firstDayOfWeek));
        return new TrainingPeriod(Kind.WEEK, start, start.plusDays(6));
    }

    public static TrainingPeriod monthOf(LocalDate day) {
        LocalDate start = day.withDayOfMonth(1);
        return new TrainingPeriod(Kind.MONTH, start, start.plusMonths(1).minusDays(1));
    }

    /**
     * Any two days, both inclusive (PRODUCT_SPEC PRG-02).
     *
     * <p>Given back to front they are swapped rather than refused: somebody who picks the 20th
     * and then the 10th meant the days between them, and an error message would be the app being
     * pedantic about an order it can work out.
     */
    public static TrainingPeriod custom(LocalDate from, LocalDate to) {
        return from.isAfter(to)
                ? new TrainingPeriod(Kind.CUSTOM, to, from)
                : new TrainingPeriod(Kind.CUSTOM, from, to);
    }

    /** How many days this period covers, both ends counted. */
    public long lengthInDays() {
        return ChronoUnit.DAYS.between(from, to) + 1L;
    }

    /** The same kind of period, {@code steps} of it away. Negative goes back. */
    public TrainingPeriod shifted(int steps) {
        if (kind == Kind.CUSTOM) {
            // A hand-picked range steps by its OWN length: the eleven days before these eleven.
            // Stepping by a week or a month would be the app answering a question about a period
            // the person never asked for.
            long days = lengthInDays() * steps;
            return new TrainingPeriod(kind, from.plusDays(days), to.plusDays(days));
        }
        LocalDate anchor = kind == Kind.WEEK
                ? from.plusWeeks(steps)
                : from.plusMonths(steps);
        // Rebuilt from the anchor rather than by shifting both ends: months are not all the same
        // length, and 31 January plus one month is not 28 February plus one month back.
        return kind == Kind.WEEK
                ? new TrainingPeriod(kind, anchor, anchor.plusDays(6))
                : monthOf(anchor);
    }

    /**
     * The same day, read as the other kind of period.
     *
     * <p>Never produces a {@code CUSTOM} one: a hand-picked range is two dates and there is
     * nothing here to derive them from. Asking for it leaves the period alone, and the screen
     * asks for the dates instead.
     */
    public TrainingPeriod as(Kind other, DayOfWeek firstDayOfWeek, LocalDate today) {
        if (other == kind || other == Kind.CUSTOM) {
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

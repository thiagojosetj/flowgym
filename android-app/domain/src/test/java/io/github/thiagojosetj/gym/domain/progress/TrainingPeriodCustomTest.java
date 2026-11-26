package io.github.thiagojosetj.gym.domain.progress;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;

/** The "período personalizado" of PRODUCT_SPEC PRG-02. */
public class TrainingPeriodCustomTest {

    @Test
    public void twoDatesBackToFrontAreTheSameRange() {
        // Picking the 20th and then the 10th means the days between them. Refusing it would be
        // the app being pedantic about an order it can work out.
        TrainingPeriod forwards = TrainingPeriod.custom(day(10), day(20));
        TrainingPeriod backwards = TrainingPeriod.custom(day(20), day(10));

        assertEquals(forwards, backwards);
        assertEquals(day(10), backwards.from());
        assertEquals(day(20), backwards.to());
        assertEquals("os dois extremos contam", 11L, forwards.lengthInDays());
    }

    @Test
    public void oneDayIsARangeOfOneDay() {
        TrainingPeriod single = TrainingPeriod.custom(day(10), day(10));

        assertEquals(1L, single.lengthInDays());
        assertEquals(day(10), single.from());
        assertEquals(day(10), single.to());
    }

    @Test
    public void aCustomRangeStepsByItsOwnLength() {
        // Eleven days back is the eleven days before these. Stepping by a week or a month would
        // answer a question about a period the person never asked for.
        TrainingPeriod eleven = TrainingPeriod.custom(day(10), day(20));

        TrainingPeriod before = eleven.shifted(-1);
        assertEquals(LocalDate.of(2026, 9, 29), before.from());
        assertEquals(LocalDate.of(2026, 10, 9), before.to());
        assertEquals(11L, before.lengthInDays());

        TrainingPeriod after = eleven.shifted(1);
        assertEquals(day(21), after.from());
        assertEquals(LocalDate.of(2026, 10, 31), after.to());
        assertEquals("ida e volta devolve o mesmo intervalo", eleven, after.shifted(-1));
    }

    @Test
    public void aCustomRangeCannotBeDerivedFromADay() {
        TrainingPeriod week = TrainingPeriod.weekOf(day(10), DayOfWeek.SUNDAY);

        // There is nothing here to invent two dates from, so the period is left alone and the
        // screen asks for them.
        assertSame(week, week.as(TrainingPeriod.Kind.CUSTOM, DayOfWeek.SUNDAY, day(10)));
    }

    @Test
    public void aCustomRangeCanBeReadBackAsAWeekOrAMonth() {
        TrainingPeriod range = TrainingPeriod.custom(day(10), day(20));

        TrainingPeriod week = range.as(TrainingPeriod.Kind.WEEK, DayOfWeek.SUNDAY, day(15));
        // Anchored on today because today is inside the range, as for the other kinds.
        assertEquals(LocalDate.of(2026, 10, 11), week.from());
        assertEquals(LocalDate.of(2026, 10, 17), week.to());

        TrainingPeriod month = range.as(TrainingPeriod.Kind.MONTH, DayOfWeek.SUNDAY, day(15));
        assertEquals(LocalDate.of(2026, 10, 1), month.from());
        assertEquals(LocalDate.of(2026, 10, 31), month.to());
    }

    private static LocalDate day(int dayOfMonth) {
        return LocalDate.of(2026, 10, dayOfMonth);
    }
}

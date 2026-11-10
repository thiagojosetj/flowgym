package io.github.thiagojosetj.gym.domain.progress;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;

/** The week or month the statistics screen is standing on (PRODUCT_SPEC PRG-04). */
public class TrainingPeriodTest {

    @Test
    public void aWeekStartsWhereTheLocaleSaysItDoes() {
        LocalDate wednesday = LocalDate.of(2026, 9, 30);

        TrainingPeriod brazilian = TrainingPeriod.weekOf(wednesday, DayOfWeek.SUNDAY);
        TrainingPeriod european = TrainingPeriod.weekOf(wednesday, DayOfWeek.MONDAY);

        assertEquals(LocalDate.of(2026, 9, 27), brazilian.from());
        assertEquals(LocalDate.of(2026, 10, 3), brazilian.to());
        assertEquals(LocalDate.of(2026, 9, 28), european.from());
        assertEquals(LocalDate.of(2026, 10, 4), european.to());
    }

    @Test
    public void aDayThatIsAlreadyTheFirstOfItsWeekDoesNotJumpBackAWholeWeek() {
        TrainingPeriod week = TrainingPeriod.weekOf(LocalDate.of(2026, 9, 27), DayOfWeek.SUNDAY);

        assertEquals(LocalDate.of(2026, 9, 27), week.from());
    }

    @Test
    public void aMonthRunsFromTheFirstToTheLastDayItActuallyHas() {
        assertEquals(LocalDate.of(2026, 2, 28),
                TrainingPeriod.monthOf(LocalDate.of(2026, 2, 14)).to());
        assertEquals(LocalDate.of(2024, 2, 29),
                TrainingPeriod.monthOf(LocalDate.of(2024, 2, 14)).to());
    }

    @Test
    public void steppingThroughMonthsDoesNotDriftOnTheShortOnes() {
        // 31 January plus a month is not 28 February plus a month back. Shifting both ends by a
        // month would leave March starting on the 28th, and every month after it wrong.
        TrainingPeriod january = TrainingPeriod.monthOf(LocalDate.of(2026, 1, 31));

        TrainingPeriod february = january.shifted(1);
        TrainingPeriod march = february.shifted(1);

        assertEquals(LocalDate.of(2026, 2, 1), february.from());
        assertEquals(LocalDate.of(2026, 2, 28), february.to());
        assertEquals(LocalDate.of(2026, 3, 1), march.from());
        assertEquals(LocalDate.of(2026, 3, 31), march.to());
    }

    @Test
    public void steppingBackAndForwardAgainLandsExactlyWhereItStarted() {
        TrainingPeriod week = TrainingPeriod.weekOf(LocalDate.of(2026, 9, 30), DayOfWeek.SUNDAY);
        TrainingPeriod month = TrainingPeriod.monthOf(LocalDate.of(2026, 3, 15));

        assertEquals(week, week.shifted(-3).shifted(3));
        assertEquals(month, month.shifted(-5).shifted(5));
    }

    @Test
    public void switchingKindStaysOnTodayWhenTodayIsOnScreen() {
        // The week of 27 September runs into October, and today is already in October. Anchoring
        // on the week's first day would answer "September" and send the person to a month they
        // were not looking at; today is inside what is on screen, so today wins.
        LocalDate today = LocalDate.of(2026, 10, 1);
        TrainingPeriod thisWeek = TrainingPeriod.weekOf(today, DayOfWeek.SUNDAY);
        assertEquals(LocalDate.of(2026, 9, 27), thisWeek.from());

        TrainingPeriod thisMonth = thisWeek.as(TrainingPeriod.Kind.MONTH, DayOfWeek.SUNDAY, today);

        assertEquals(LocalDate.of(2026, 10, 1), thisMonth.from());
        assertEquals(LocalDate.of(2026, 10, 31), thisMonth.to());
    }

    @Test
    public void switchingKindOnAPastPeriodStaysInThePast() {
        LocalDate today = LocalDate.of(2026, 9, 30);
        TrainingPeriod marchWeek = TrainingPeriod.weekOf(LocalDate.of(2026, 3, 10),
                DayOfWeek.SUNDAY);

        TrainingPeriod asMonth = marchWeek.as(TrainingPeriod.Kind.MONTH, DayOfWeek.SUNDAY, today);

        assertEquals(LocalDate.of(2026, 3, 1), asMonth.from());
    }

    @Test
    public void askingForTheKindItAlreadyIsChangesNothing() {
        TrainingPeriod week = TrainingPeriod.weekOf(LocalDate.of(2026, 9, 30), DayOfWeek.SUNDAY);

        assertEquals(week, week.as(TrainingPeriod.Kind.WEEK, DayOfWeek.MONDAY,
                LocalDate.of(2026, 9, 30)));
    }

    @Test
    public void bothEndsOfAPeriodAreInsideIt() {
        TrainingPeriod week = TrainingPeriod.weekOf(LocalDate.of(2026, 9, 30), DayOfWeek.SUNDAY);

        assertTrue(week.contains(LocalDate.of(2026, 9, 27)));
        assertTrue(week.contains(LocalDate.of(2026, 10, 3)));
        assertFalse(week.contains(LocalDate.of(2026, 9, 26)));
        assertFalse(week.contains(LocalDate.of(2026, 10, 4)));
        assertFalse("sem dia nao ha resposta", week.contains(null));
    }

    @Test
    public void theEdgesSayWhetherThereIsAnywhereLeftToGo() {
        TrainingPeriod week = TrainingPeriod.weekOf(LocalDate.of(2026, 9, 30), DayOfWeek.SUNDAY);

        // The oldest session is inside this week: there is nothing older to step back to.
        assertFalse(week.startsOnOrBefore(LocalDate.of(2026, 9, 26)));
        assertTrue(week.startsOnOrBefore(LocalDate.of(2026, 9, 27)));
        assertTrue(week.endsOnOrAfter(LocalDate.of(2026, 10, 3)));
        assertFalse(week.endsOnOrAfter(LocalDate.of(2026, 10, 4)));
    }
}

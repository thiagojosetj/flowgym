package io.github.thiagojosetj.gym.ui.common;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * The day where the person is.
 *
 * <p>The app's clock is built with whatever zone its wiring chose, and a screen that asks it for
 * a date gets that zone's date. Every assertion here is a case where that answer differs from the
 * person's, which is the only kind that matters.
 */
public class TodayTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final ZoneId TOKYO = ZoneId.of("Asia/Tokyo");

    @Test
    public void theEveningOfTheLastDayOfAMonthIsStillThatMonthInSaoPaulo() {
        // 23:30 on 30 September there is 02:30 on 1 October in UTC.
        Clock utc = Clock.fixed(Instant.parse("2026-10-01T02:30:00Z"), ZoneOffset.UTC);

        assertEquals(LocalDate.of(2026, 9, 30), Today.of(utc, SAO_PAULO));
        assertEquals(YearMonth.of(2026, 9), Today.monthOf(utc, SAO_PAULO));
    }

    @Test
    public void theMorningOfTheFirstDayOfAMonthIsAlreadyThatMonthInTokyo() {
        // The mirror case: 08:00 on 1 October there is still 30 September in UTC.
        Clock utc = Clock.fixed(Instant.parse("2026-09-30T23:00:00Z"), ZoneOffset.UTC);

        assertEquals(LocalDate.of(2026, 10, 1), Today.of(utc, TOKYO));
        assertEquals(YearMonth.of(2026, 10), Today.monthOf(utc, TOKYO));
    }

    @Test
    public void theClocksOwnZoneIsIgnoredEvenWhenItHasOne() {
        // A clock wired to one zone and a person in another: the person wins. Nothing should
        // depend on how whoever built the clock happened to set it.
        Clock inTokyo = Clock.fixed(Instant.parse("2026-10-01T02:30:00Z"), TOKYO);

        assertEquals(LocalDate.of(2026, 9, 30), Today.of(inTokyo, SAO_PAULO));
    }

    @Test
    public void midDayIsTheSameDayWhicheverZoneAsks() {
        Clock utc = Clock.fixed(Instant.parse("2026-10-01T15:00:00Z"), ZoneOffset.UTC);

        assertEquals(LocalDate.of(2026, 10, 1), Today.of(utc, SAO_PAULO));
        assertEquals(LocalDate.of(2026, 10, 2), Today.of(utc, TOKYO));
    }
}

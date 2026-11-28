package io.github.thiagojosetj.gym.ui.common;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.LocalDate;
import java.util.TimeZone;

/** Material's date picker speaks UTC milliseconds, whatever zone the phone is in. */
public class PickedDaysTest {

    @Test
    public void aPickedDayIsTheSameDayWestOfGreenwich() {
        TimeZone original = TimeZone.getDefault();
        try {
            // Sao Paulo is UTC-3. Reading the picker's value with the device zone would turn
            // midnight UTC on the 20th into 21:00 on the 19th - a day earlier, silently.
            TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
            long millis = PickedDays.millisOf(LocalDate.of(2026, 10, 20));

            assertEquals(LocalDate.of(2026, 10, 20), PickedDays.of(millis));
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    public void andTheSameDayEastOfIt() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
            long millis = PickedDays.millisOf(LocalDate.of(2026, 10, 20));

            assertEquals(LocalDate.of(2026, 10, 20), PickedDays.of(millis));
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    public void theValueIsMidnightUtcAndNotMidnightSomewhereElse() {
        // Pinned so a "simplification" to atStartOfDay(systemDefault()) fails here rather than on
        // somebody's phone: 1 January 2026, 00:00 UTC.
        assertEquals(1_767_225_600_000L, PickedDays.millisOf(LocalDate.of(2026, 1, 1)));
        assertEquals(LocalDate.of(2026, 1, 1), PickedDays.of(1_767_225_600_000L));
    }
}

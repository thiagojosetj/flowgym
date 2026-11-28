package io.github.thiagojosetj.gym.ui.common;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Converts between a calendar day and what Material's date picker speaks.
 *
 * <p><b>The picker's milliseconds are UTC, always.</b> A selection of 20 October comes back as
 * midnight UTC on 20 October, whatever zone the phone is in. Reading it with the device's zone -
 * the obvious thing to write - turns it into the 19th for anybody west of Greenwich, which is
 * most of Brazil. This app has already shipped one bug of exactly that shape (the calendar opened
 * on next month from 21:00 in Sao Paulo), so the conversion lives here, in one place, with a test.
 *
 * <p>This is the opposite rule from {@link Today}: "today" is the day where the PERSON is, while a
 * picked day is a label the picker hands over in a fixed zone. Both are about not letting a zone
 * quietly change a date; they just come at it from different ends.
 */
public final class PickedDays {

    private PickedDays() {
    }

    /** The day Material's picker meant by these milliseconds. */
    public static LocalDate of(long utcMillis) {
        return Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate();
    }

    /** The milliseconds Material's picker expects for this day. */
    public static long millisOf(LocalDate day) {
        return day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    }
}

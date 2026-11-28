package io.github.thiagojosetj.gym.ui.common;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

/**
 * What day it is where the person is.
 *
 * <p>Not {@code LocalDate.now(clock)}. The app's clock is a source of INSTANTS and its own zone is
 * a wiring detail of whoever built it; taking a date off it means taking the date of whatever zone
 * that happened to be. With a UTC clock, everybody west of Greenwich gets tomorrow from 21:00
 * onwards - the history calendar would open on next month on the evening of the 30th, with the
 * workout just finished an arrow away in a month they have not reached.
 *
 * <p>So the zone is named at the point of use, where the rule is visible and a test can state it.
 * It is the same rule as everywhere else in this app: the day is the day it was lived
 * (PRODUCT_SPEC section 11).
 */
public final class Today {

    private Today() {
    }

    public static LocalDate of(Clock clock, ZoneId zone) {
        return LocalDate.now(clock.withZone(zone));
    }

    public static YearMonth monthOf(Clock clock, ZoneId zone) {
        return YearMonth.from(of(clock, zone));
    }
}

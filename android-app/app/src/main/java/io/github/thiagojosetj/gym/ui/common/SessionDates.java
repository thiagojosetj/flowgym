package io.github.thiagojosetj.gym.ui.common;

import android.content.res.Resources;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

import io.github.thiagojosetj.gym.R;

/**
 * Formats the day and time of a finished session, in the zone the workout was performed in.
 *
 * <p>Why the zone travels with the session: a workout done at 19:00 in São Paulo should still read
 * 19:00 after the phone lands in Lisbon. The session stores its own zone precisely so the past does
 * not move when the present does.
 *
 * <p><b>API-level trap.</b> {@code LocalDate.ofInstant(Instant, ZoneId)} exists in Java 9 but only
 * reached Android in API 34 - it compiles against desugared JDK types and then throws
 * {@code NoSuchMethodError} on a phone at API 28-33. This project already shipped that bug once.
 * {@code instant.atZone(zone)} has been there since API 26 and does the same job, so it is the one
 * used here and the one to use anywhere else.
 */
public final class SessionDates {

    private SessionDates() {
    }

    /**
     * The zone the session stored, or the device's zone when it cannot be resolved.
     *
     * <p>A zone id can genuinely fail to resolve: the row was written by a build with newer tzdata,
     * or synced from another device. Falling back keeps the screen readable instead of crashing the
     * list, and the date is the only thing that can be slightly off.
     */
    public static ZoneId zoneOf(String timeZoneId) {
        if (timeZoneId == null || timeZoneId.isEmpty()) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(timeZoneId);
        } catch (DateTimeException e) {
            return ZoneId.systemDefault();
        }
    }

    /** "28 de set. de 2026" - the day as it was lived. */
    public static String day(long epochMillis, String timeZoneId) {
        return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(Locale.getDefault())
                .format(at(epochMillis, timeZoneId));
    }

    /** "19:42", in that same zone. */
    public static String timeOfDay(long epochMillis, String timeZoneId) {
        return DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
                .withLocale(Locale.getDefault())
                .format(at(epochMillis, timeZoneId));
    }

    /**
     * "Hoje", "Ontem", or the date, for the history list where the recent days are the ones being
     * scanned.
     *
     * <p>Both days are resolved in the <b>session's</b> zone, not the device's. That keeps the label
     * consistent with the date and time shown beside it: a workout logged late on Sunday in Sao
     * Paulo reads "Ontem · 22:40" on Monday, instead of the label and the clock disagreeing because
     * one of them silently used the phone's current zone.
     *
     * <p>{@code android.text.format.DateUtils} is deliberately not used here. Its relative helpers
     * work in the device zone and cannot be told otherwise, which is exactly the mix-up above, and
     * they read the system clock directly, which makes the result untestable.
     *
     * @param nowMillis the current instant, passed in rather than read here so the result is
     *                  deterministic in a test
     */
    public static String relativeDay(Resources res, long epochMillis, String timeZoneId,
                                     long nowMillis) {
        ZoneId zone = zoneOf(timeZoneId);
        LocalDate day = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate();
        LocalDate today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate();
        long daysAgo = ChronoUnit.DAYS.between(day, today);
        if (daysAgo == 0L) {
            return res.getString(R.string.history_day_today);
        }
        if (daysAgo == 1L) {
            return res.getString(R.string.history_day_yesterday);
        }
        return day(epochMillis, timeZoneId);
    }

    private static ZonedDateTime at(long epochMillis, String timeZoneId) {
        return Instant.ofEpochMilli(epochMillis).atZone(zoneOf(timeZoneId));
    }
}

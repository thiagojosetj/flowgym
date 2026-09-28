package io.github.thiagojosetj.gym.ui.common;

import android.content.Context;
import android.text.format.DateUtils;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

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
     * "hoje", "ontem" or the date, for the history list, where the recent days are the ones being
     * scanned. Uses the platform helper so the wording follows the system language.
     */
    public static String relativeDay(Context context, long epochMillis, String timeZoneId) {
        long now = System.currentTimeMillis();
        long dayMillis = DateUtils.DAY_IN_MILLIS;
        if (Math.abs(now - epochMillis) < 2L * dayMillis) {
            return DateUtils.getRelativeTimeSpanString(context, epochMillis, false).toString();
        }
        return day(epochMillis, timeZoneId);
    }

    private static ZonedDateTime at(long epochMillis, String timeZoneId) {
        return Instant.ofEpochMilli(epochMillis).atZone(zoneOf(timeZoneId));
    }
}

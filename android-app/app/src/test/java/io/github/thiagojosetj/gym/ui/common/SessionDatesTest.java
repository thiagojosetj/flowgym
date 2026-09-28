package io.github.thiagojosetj.gym.ui.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.time.Instant;
import java.time.ZoneId;

@RunWith(AndroidJUnit4.class)
@Config(qualifiers = "pt-rBR")
public class SessionDatesTest {

    /** 2026-09-28 22:30 UTC. In São Paulo that is 19:30 the same day; in Tokyo, 07:30 the next. */
    private static final long EVENING_IN_BRAZIL =
            Instant.parse("2026-09-28T22:30:00Z").toEpochMilli();

    @Test
    public void theTimeIsShownInTheZoneTheWorkoutWasPerformedIn() {
        // The whole point of storing time_zone: a workout done at 19:30 in São Paulo still reads
        // 19:30 after the phone lands somewhere else.
        String inBrazil = SessionDates.timeOfDay(EVENING_IN_BRAZIL, "America/Sao_Paulo");
        String inTokyo = SessionDates.timeOfDay(EVENING_IN_BRAZIL, "Asia/Tokyo");

        assertNotEquals(inBrazil, inTokyo);
        assertEquals("19:30", inBrazil);
    }

    @Test
    public void theDayFollowsTheZoneToo() {
        // Same instant, two calendar days. Grouping history by the device's zone would move a
        // session to the wrong day for anyone who travels.
        String brazilDay = SessionDates.day(EVENING_IN_BRAZIL, "America/Sao_Paulo");
        String tokyoDay = SessionDates.day(EVENING_IN_BRAZIL, "Asia/Tokyo");

        assertNotEquals(brazilDay, tokyoDay);
    }

    @Test
    public void aZoneTheDeviceCannotResolveFallsBackInsteadOfCrashingTheList() {
        // A row can carry a zone this build's tzdata does not know (written by a newer build, or
        // synced from another device). A history list must still draw.
        assertEquals(ZoneId.systemDefault(), SessionDates.zoneOf("Mars/Olympus_Mons"));
        assertEquals(ZoneId.systemDefault(), SessionDates.zoneOf(""));
        assertEquals(ZoneId.systemDefault(), SessionDates.zoneOf(null));
    }

    @Test
    public void aKnownZoneIsUsedAsStored() {
        assertEquals(ZoneId.of("America/Sao_Paulo"), SessionDates.zoneOf("America/Sao_Paulo"));
    }

    @Test
    public void bothTheDayAndTheTimeProduceText() {
        // Note for whoever tidies this later: the implementation must keep using
        // Instant.atZone(...). LocalDate.ofInstant compiles fine and throws NoSuchMethodError on
        // Android below API 34 - this project already shipped that bug once. A JVM test cannot
        // catch it; lint's NewApi check is what enforces it, and the gate runs lint.
        assertNotEquals("", SessionDates.day(EVENING_IN_BRAZIL, "America/Sao_Paulo"));
        assertNotEquals("", SessionDates.timeOfDay(EVENING_IN_BRAZIL, "America/Sao_Paulo"));
    }
}

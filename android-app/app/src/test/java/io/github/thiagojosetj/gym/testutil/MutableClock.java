package io.github.thiagojosetj.gym.testutil;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * A clock the test moves by hand. Sessions are about time passing, so the tests have to be able to
 * say "twelve minutes later" without sleeping.
 */
public final class MutableClock extends Clock {

    private Instant now;
    private final ZoneId zone;

    public MutableClock(Instant start) {
        this(start, ZoneOffset.UTC);
    }

    private MutableClock(Instant start, ZoneId zone) {
        this.now = start;
        this.zone = zone;
    }

    public static MutableClock at(String isoInstant) {
        return new MutableClock(Instant.parse(isoInstant));
    }

    public void advance(Duration amount) {
        now = now.plus(amount);
    }

    public void advanceSeconds(long seconds) {
        advance(Duration.ofSeconds(seconds));
    }

    public void advanceMinutes(long minutes) {
        advance(Duration.ofMinutes(minutes));
    }

    /** For a test that needs two sessions to land on different days. */
    public void advanceDays(long days) {
        advance(Duration.ofDays(days));
    }

    /** For the "user moved the device clock backwards" case. */
    public void set(Instant instant) {
        now = instant;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId otherZone) {
        return new MutableClock(now, otherZone);
    }

    @Override
    public Instant instant() {
        return now;
    }
}

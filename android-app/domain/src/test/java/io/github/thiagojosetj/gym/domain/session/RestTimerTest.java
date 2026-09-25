package io.github.thiagojosetj.gym.domain.session;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class RestTimerTest {

    private static final long NOW = 5_000_000L;

    @Test
    public void restIsStoredAsTheInstantItEnds() {
        assertEquals(NOW + 90_000L, RestTimer.endsAt(NOW, 90));
    }

    @Test
    public void remainingTimeComesFromTheClockAndNeverGoesNegative() {
        long endsAt = RestTimer.endsAt(NOW, 90);

        assertEquals(90_000L, RestTimer.remainingMs(endsAt, NOW));
        assertEquals(30_000L, RestTimer.remainingMs(endsAt, NOW + 60_000L));
        assertEquals(0L, RestTimer.remainingMs(endsAt, NOW + 120_000L));
        assertEquals(0L, RestTimer.remainingMs(null, NOW));
    }

    @Test
    public void secondsAreRoundedUpSoTheUserNeverSeesZeroWhileWaiting() {
        long endsAt = NOW + 200L;
        assertEquals(1, RestTimer.remainingSecondsCeil(endsAt, NOW));
        assertEquals(0, RestTimer.remainingSecondsCeil(NOW, NOW));
        assertEquals(90, RestTimer.remainingSecondsCeil(NOW + 90_000L, NOW));
    }

    @Test
    public void adjustingMovesTheEndInstant() {
        long endsAt = RestTimer.endsAt(NOW, 90);

        assertEquals(endsAt + 15_000L, RestTimer.adjusted(endsAt, 15, NOW));
        assertEquals(endsAt + 30_000L, RestTimer.adjusted(endsAt, 30, NOW));
        assertEquals(endsAt - 15_000L, RestTimer.adjusted(endsAt, -15, NOW));
    }

    @Test
    public void subtractingMoreThanWhatIsLeftEndsTheRestNow() {
        long endsAt = RestTimer.endsAt(NOW, 10);

        long adjusted = RestTimer.adjusted(endsAt, -15, NOW);

        assertEquals(NOW, adjusted);
        assertEquals(0L, RestTimer.remainingMs(adjusted, NOW));
    }

    @Test
    public void restIsCappedSoATypoCannotScheduleADayOfWaiting() {
        assertEquals(NOW + RestTimer.MAX_REST_SECONDS * 1000L, RestTimer.endsAt(NOW, 99_999));
        assertEquals(NOW + RestTimer.MAX_REST_SECONDS * 1000L,
                RestTimer.adjusted(NOW + RestTimer.MAX_REST_SECONDS * 1000L, 30, NOW));
        assertEquals(NOW, RestTimer.endsAt(NOW, -5));
    }
}

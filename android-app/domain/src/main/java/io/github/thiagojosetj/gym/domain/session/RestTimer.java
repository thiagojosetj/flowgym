package io.github.thiagojosetj.gym.domain.session;

/**
 * The rest countdown, kept as the timestamp it ends at (PRODUCT_SPEC section 7).
 *
 * <p>Storing the end instant instead of "seconds left" means the countdown is correct after the
 * screen is off, after a rotation and after the process is killed: whoever renders it subtracts
 * {@code now}. Adjusting by +15 s, +30 s or -15 s moves that instant.
 *
 * <p>While the session is paused the rest also stops, so the remaining time is frozen and stored
 * as {@code remainingMsWhenPaused}; resuming turns it back into an end instant.
 */
public final class RestTimer {

    /** Adjustments offered by the UI (ACT-06). */
    public static final int[] ADJUSTMENTS_SECONDS = {-15, 15, 30};

    /** Longest rest the app will schedule; a longer value is a typo. */
    public static final int MAX_REST_SECONDS = 3600;

    private RestTimer() {
    }

    /** The instant a rest of {@code restSeconds} started now would end. */
    public static long endsAt(long now, int restSeconds) {
        int clamped = Math.max(0, Math.min(restSeconds, MAX_REST_SECONDS));
        return now + clamped * 1000L;
    }

    /** Milliseconds left, never negative. Zero means the rest is over (or there is none). */
    public static long remainingMs(Long endsAt, long now) {
        if (endsAt == null) {
            return 0L;
        }
        return Math.max(0L, endsAt - now);
    }

    /**
     * Moves the end instant by {@code deltaSeconds}. Subtracting never goes below {@code now}: the
     * rest ends immediately instead of ending "in the past", which would show a negative countdown.
     */
    public static long adjusted(long endsAt, int deltaSeconds, long now) {
        long moved = endsAt + deltaSeconds * 1000L;
        long latest = now + MAX_REST_SECONDS * 1000L;
        return Math.max(now, Math.min(moved, latest));
    }

    /** Seconds still to show, rounded up: 0.2 s left is still "1 s" for the user. */
    public static int remainingSecondsCeil(Long endsAt, long now) {
        long ms = remainingMs(endsAt, now);
        return (int) ((ms + 999L) / 1000L);
    }
}

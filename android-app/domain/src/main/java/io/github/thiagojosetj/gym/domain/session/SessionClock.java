package io.github.thiagojosetj.gym.domain.session;

/**
 * The live view of a session's time, in the shape the database can hand over cheaply: the start, the
 * end (if any), how much closed pauses already added up to, and the start of the pause that is still
 * open (PRODUCT_SPEC section 7).
 *
 * <p>This is the single implementation of the time arithmetic. {@link SessionTiming} builds one of
 * these from the pause rows themselves, so the screen and the summary can never disagree.
 *
 * <p>Every method is a pure function of {@code now}: the UI asks again every second instead of
 * incrementing a counter, which is what makes the numbers correct after the screen was off.
 *
 * @param startedAt          when the session started (wall clock, ms)
 * @param endedAt            when it finished, or null while it runs
 * @param closedPausedMs     sum of the pauses that already ended
 * @param openPauseStartedAt start of the pause in progress, or null when not paused
 */
public record SessionClock(long startedAt, Long endedAt, long closedPausedMs, Long openPauseStartedAt) {

    public SessionClock {
        if (endedAt != null && endedAt < startedAt) {
            throw new IllegalArgumentException("Session ends before it starts");
        }
        if (closedPausedMs < 0) {
            throw new IllegalArgumentException("Paused time cannot be negative: " + closedPausedMs);
        }
    }

    public static SessionClock running(long startedAt) {
        return new SessionClock(startedAt, null, 0L, null);
    }

    public boolean isPaused() {
        return openPauseStartedAt != null;
    }

    public boolean isFinished() {
        return endedAt != null;
    }

    /**
     * Wall-clock duration. Clamped at zero, so a device clock moved backwards shows 0 instead of a
     * negative time; the stored instants are never rewritten to hide it.
     */
    public long totalMs(long now) {
        return Math.max(0L, reference(now) - startedAt);
    }

    /** Everything that does not count as training: the closed pauses plus the one still open. */
    public long pausedMs(long now) {
        long open = 0L;
        if (openPauseStartedAt != null) {
            open = Math.max(0L, reference(now) - openPauseStartedAt);
        }
        return Math.min(closedPausedMs + open, totalMs(now));
    }

    /** Time actually training: what the big timer shows. */
    public long effectiveMs(long now) {
        return Math.max(0L, totalMs(now) - pausedMs(now));
    }

    /** A finished session stops moving with the clock; a running one is measured against now. */
    private long reference(long now) {
        return endedAt != null ? endedAt : now;
    }
}

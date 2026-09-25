package io.github.thiagojosetj.gym.domain.session;

/**
 * One pause of a session, persisted as an interval (PRODUCT_SPEC section 7). An open pause has no
 * end yet, so its duration keeps growing until the user resumes.
 *
 * @param startedAt wall-clock milliseconds when the pause began
 * @param endedAt   wall-clock milliseconds when it ended, or null while the session is paused
 */
public record PauseInterval(long startedAt, Long endedAt) {

    public PauseInterval {
        if (endedAt != null && endedAt < startedAt) {
            throw new IllegalArgumentException("Pause ends before it starts: " + startedAt + " > " + endedAt);
        }
    }

    public static PauseInterval open(long startedAt) {
        return new PauseInterval(startedAt, null);
    }

    public boolean isOpen() {
        return endedAt == null;
    }

    /**
     * How long this pause has lasted. An open pause is measured against {@code now}, so the number
     * comes from timestamps and never from an incremented counter.
     *
     * <p>Clamped at zero: the device clock can move backwards (the user changes the time, or a
     * network sync corrects it) and a negative pause would inflate the effective duration.
     */
    public long durationMs(long now) {
        long end = endedAt != null ? endedAt : now;
        return Math.max(0L, end - startedAt);
    }

    public PauseInterval closedAt(long end) {
        return new PauseInterval(startedAt, Math.max(startedAt, end));
    }
}

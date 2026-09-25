package io.github.thiagojosetj.gym.domain.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The clock of a session, computed from persisted timestamps (PRODUCT_SPEC section 7).
 *
 * <p>There is deliberately no counter to increment: the UI asks this class again every second, and
 * the answer is a pure function of {@code now}. That is what makes the numbers survive rotation,
 * process death and the app being in the background for an hour.
 *
 * <ul>
 *   <li>total     = end - start
 *   <li>paused    = sum of the pause intervals
 *   <li>effective = total - paused
 * </ul>
 *
 * @param startedAt when the session started (wall clock, ms)
 * @param endedAt   when it finished, or null while it is running
 * @param pauses    pause intervals in chronological order; at most the last one may be open
 */
public record SessionTiming(long startedAt, Long endedAt, List<PauseInterval> pauses) {

    public SessionTiming {
        if (endedAt != null && endedAt < startedAt) {
            throw new IllegalArgumentException("Session ends before it starts");
        }
        pauses = pauses == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(pauses));
        int open = 0;
        for (PauseInterval pause : pauses) {
            if (pause.isOpen()) {
                open++;
            }
        }
        if (open > 1) {
            throw new IllegalArgumentException("A session cannot be paused twice at the same time");
        }
    }

    public static SessionTiming running(long startedAt) {
        return new SessionTiming(startedAt, null, Collections.emptyList());
    }

    /** True while a pause is open, i.e. the general timer and the rest timer are standing still. */
    public boolean isPaused() {
        return openPause() != null;
    }

    public PauseInterval openPause() {
        for (PauseInterval pause : pauses) {
            if (pause.isOpen()) {
                return pause;
            }
        }
        return null;
    }

    public boolean isFinished() {
        return endedAt != null;
    }

    /**
     * Wall-clock duration of the session. Clamped at zero so a backwards clock change shows 0
     * instead of a negative time; the stored timestamps are never rewritten to hide it.
     */
    public long totalMs(long now) {
        long end = endedAt != null ? endedAt : now;
        return Math.max(0L, end - startedAt);
    }

    public long pausedMs(long now) {
        long reference = endedAt != null ? endedAt : now;
        long sum = 0L;
        for (PauseInterval pause : pauses) {
            sum += pause.durationMs(reference);
        }
        return Math.min(sum, totalMs(now));
    }

    /** Time actually training: total minus every pause. This is what the big timer shows. */
    public long effectiveMs(long now) {
        return Math.max(0L, totalMs(now) - pausedMs(now));
    }
}

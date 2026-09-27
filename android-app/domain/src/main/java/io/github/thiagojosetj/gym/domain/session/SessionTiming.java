package io.github.thiagojosetj.gym.domain.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A session and its pause rows, as stored (PRODUCT_SPEC section 7). This is the exact view, used
 * when the session is finished (to write the cached paused total) and by the tests.
 *
 * <p>The arithmetic itself lives in {@link SessionClock}: this record only reduces the rows to it,
 * so the live screen (which reads a cheap projection) and the summary cannot disagree.
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
     * Reduces the pause rows to the projection the screen uses. The open pause keeps its start, so
     * its duration is still computed from {@code now} by {@link SessionClock}.
     */
    public SessionClock clock() {
        long closed = 0L;
        Long open = null;
        for (PauseInterval pause : pauses) {
            if (pause.isOpen()) {
                open = pause.startedAt();
            } else {
                closed += pause.durationMs(pause.startedAt());
            }
        }
        return new SessionClock(startedAt, endedAt, closed, open);
    }

    public long totalMs(long now) {
        return clock().totalMs(now);
    }

    public long pausedMs(long now) {
        return clock().pausedMs(now);
    }

    /** Time actually training: total minus every pause. This is what the big timer shows. */
    public long effectiveMs(long now) {
        return clock().effectiveMs(now);
    }
}

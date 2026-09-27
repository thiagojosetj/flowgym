package io.github.thiagojosetj.gym.domain.session;

/**
 * The session itself, without its exercises: identity, status, time and rest state. The screen
 * observes this separately from the sets, so typing in a set never re-renders the timer and the
 * timer never re-renders the list.
 *
 * @param restSetLogId               set whose rest is running, null when nobody is resting
 * @param restEndsAt                 instant the rest ends (PRODUCT_SPEC section 7)
 * @param restRemainingMsWhenPaused  what was left when the session was paused; while this is set the
 *                                   rest is frozen, because a pause stops the rest too
 */
public record SessionHeader(
        String id,
        String templateId,
        String name,
        String notes,
        SessionStatus status,
        SessionClock clock,
        String restSetLogId,
        Long restEndsAt,
        Long restRemainingMsWhenPaused) {

    public boolean isActive() {
        return status == SessionStatus.ACTIVE;
    }

    public boolean isPaused() {
        return clock.isPaused();
    }

    /** Milliseconds of rest left: frozen while paused, otherwise counted down from the end instant. */
    public long restRemainingMs(long now) {
        if (restSetLogId == null) {
            return 0L;
        }
        if (restRemainingMsWhenPaused != null) {
            return Math.max(0L, restRemainingMsWhenPaused);
        }
        return RestTimer.remainingMs(restEndsAt, now);
    }

    /** True while there is a rest to show. It stays true at 0 until the user or the app clears it. */
    public boolean isResting(long now) {
        return restSetLogId != null && restRemainingMs(now) > 0L;
    }
}

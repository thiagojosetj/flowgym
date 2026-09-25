package io.github.thiagojosetj.gym.domain.session;

/** State of one logged set. Persisted by {@link #name()}: never rename a constant. */
public enum SetStatus {
    /** Planned but not performed yet. Values typed into it are suggestions, not results. */
    PENDING,
    /** Performed and confirmed by the user. Only these count for volume and records. */
    COMPLETED,
    /** Deliberately skipped. Kept so the session still shows what was planned. */
    SKIPPED
}

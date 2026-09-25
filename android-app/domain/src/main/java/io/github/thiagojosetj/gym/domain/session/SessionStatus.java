package io.github.thiagojosetj.gym.domain.session;

/** Lifecycle of a workout session. Persisted by {@link #name()}: never rename a constant. */
public enum SessionStatus {
    /** Being performed right now. At most one per user (docs/DATABASE.md). */
    ACTIVE,
    /** Finished by the user; belongs to the history and is immutable by default. */
    COMPLETED,
    /** Thrown away on purpose. Kept as a row so the discard can be synced. */
    DISCARDED
}

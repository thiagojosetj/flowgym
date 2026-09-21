package io.github.thiagojosetj.gym.domain.model;

/**
 * How a unilateral exercise is logged in a template/session. Persisted by {@link #name()}.
 * Irrelevant (always {@link #COMBINED}) for bilateral exercises.
 */
public enum SideMode {
    /** One value that applies to each side ("10 reps por lado"). */
    COMBINED,
    /** Separate values for left and right (E 10 / D 9). */
    PER_SIDE
}

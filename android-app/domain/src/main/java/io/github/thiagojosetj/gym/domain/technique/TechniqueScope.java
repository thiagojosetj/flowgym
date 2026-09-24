package io.github.thiagojosetj.gym.domain.technique;

/**
 * What a training technique applies to (PRODUCT_SPEC §6.2). Persisted by {@link #name()}.
 *
 * <p>The techniques themselves are rows in a table, not enum constants, so new methods can ship
 * without a new app version (ADR-0009). Only the three scopes are code-level concepts, because the
 * app treats each one differently.
 */
public enum TechniqueScope {
    /** Applies to one set: warm-up, drop-set, rest-pause, myo-reps, cluster, to failure. */
    SET,
    /** Applies to the whole sequence of sets of an exercise: pyramids. */
    EXERCISE,
    /** Links several exercises: superset, bi-set, tri-set, giant set. */
    GROUP
}

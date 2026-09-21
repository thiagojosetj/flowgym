package io.github.thiagojosetj.gym.domain.model;

/** Whether an exercise works both sides at once. Persisted by {@link #name()}. */
public enum Laterality {
    BILATERAL,
    /** One side at a time: per-side logging becomes available (PRODUCT_SPEC §6.4). */
    UNILATERAL
}

package io.github.thiagojosetj.gym.domain.model;

/** Role of a muscle in an exercise. Persisted by {@link #name()}. */
public enum MuscleRole {
    /** Main target; the first primary muscle is highlighted in the UI. */
    PRIMARY,
    /** Assisting muscle; shown in a smaller section. */
    SECONDARY
}

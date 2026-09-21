package io.github.thiagojosetj.gym.domain.model;

/**
 * How the typed load relates to the implements. Persisted by {@link #name()}.
 *
 * <p>For dumbbells the user always types the weight of EACH dumbbell ("12 kg por halter"); the UI
 * never shows 24 kg as if it were the set's load (PRODUCT_SPEC §6.4). Volume calculations multiply
 * by the implement count explicitly (PRODUCT_SPEC §9).
 */
public enum LoadBasis {
    /** The typed value is the whole load (barbell incl. bar, machine stack, cable). */
    TOTAL,
    /** The typed value is per implement (each dumbbell/kettlebell). */
    PER_IMPLEMENT
}

package io.github.thiagojosetj.gym.domain.library;

/** Which muscle links a library filter considers (PRODUCT_SPEC LIB-03). */
public enum MuscleRoleScope {
    /** Only exercises where the muscle is a primary target (default). */
    PRIMARY,
    /** Only exercises where the muscle is secondary. */
    SECONDARY,
    /** Either role. */
    ANY
}

package io.github.thiagojosetj.gym.domain.model;

/**
 * What a set of this exercise records. Persisted by {@link #name()}: never rename a constant.
 *
 * <p>This is an enum (not a table) because the UI and the calculations behave differently for
 * each value (ADR-0009).
 */
public enum TrackingType {
    /** External load + repetitions (barbell, dumbbell, machine, cable). */
    WEIGHT_REPS(true, true, false),
    /** Body weight + optional added load (positive) or assistance (negative) + repetitions. */
    BODYWEIGHT_REPS(true, true, false),
    /** Repetitions only, load not measurable (e.g. elastic band). */
    REPS_ONLY(false, true, false),
    /** Time only (e.g. plank). */
    DURATION(false, false, true),
    /** Load held for time (e.g. farmer's carry). */
    WEIGHT_DURATION(true, false, true);

    private final boolean usesWeight;
    private final boolean usesReps;
    private final boolean usesDuration;

    TrackingType(boolean usesWeight, boolean usesReps, boolean usesDuration) {
        this.usesWeight = usesWeight;
        this.usesReps = usesReps;
        this.usesDuration = usesDuration;
    }

    public boolean usesWeight() {
        return usesWeight;
    }

    public boolean usesReps() {
        return usesReps;
    }

    public boolean usesDuration() {
        return usesDuration;
    }

    /** Only body-weight exercises accept a negative load (assistance). */
    public boolean allowsNegativeWeight() {
        return this == BODYWEIGHT_REPS;
    }
}

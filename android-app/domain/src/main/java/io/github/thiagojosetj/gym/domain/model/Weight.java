package io.github.thiagojosetj.gym.domain.model;

/**
 * A load stored as whole grams (ADR-0008).
 *
 * <p>Integers make equality exact, which matters for records such as "most reps with 40 kg", and
 * avoid floating point drift when values travel through JSON. The value may be negative only for
 * body-weight exercises, where it means assistance (see PRODUCT_SPEC §6.4); that rule is enforced
 * by the code that knows the exercise type, not here.
 */
public final class Weight implements Comparable<Weight> {

    /** Upper sanity bound: 1 tonne. Anything above is certainly a typo. */
    public static final long MAX_ABS_GRAMS = 1_000_000L;

    private final long grams;

    private Weight(long grams) {
        if (Math.abs(grams) > MAX_ABS_GRAMS) {
            throw new IllegalArgumentException("Weight out of range: " + grams + " g");
        }
        this.grams = grams;
    }

    public static Weight ofGrams(long grams) {
        return new Weight(grams);
    }

    public static Weight of(double value, WeightUnit unit) {
        return new Weight(unit.toGrams(value));
    }

    public long grams() {
        return grams;
    }

    public double in(WeightUnit unit) {
        return unit.fromGrams(grams);
    }

    public boolean isNegative() {
        return grams < 0;
    }

    @Override
    public int compareTo(Weight other) {
        return Long.compare(grams, other.grams);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Weight && ((Weight) o).grams == grams;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(grams);
    }

    @Override
    public String toString() {
        return grams + " g";
    }
}

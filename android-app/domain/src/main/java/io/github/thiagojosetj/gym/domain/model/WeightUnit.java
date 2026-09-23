package io.github.thiagojosetj.gym.domain.model;

/**
 * Display unit for loads. Storage is always grams (ADR-0008); a unit only converts for display
 * and input.
 */
public enum WeightUnit {
    KILOGRAM("kg", 1000.0),
    POUND("lb", 453.59237);

    private final String symbol;
    private final double gramsPerUnit;

    WeightUnit(String symbol, double gramsPerUnit) {
        this.symbol = symbol;
        this.gramsPerUnit = gramsPerUnit;
    }

    public String symbol() {
        return symbol;
    }

    /**
     * Converts a value typed by the user in this unit to grams, rounded to the nearest gram.
     *
     * @throws IllegalArgumentException for NaN or infinite values (Math.round would silently turn
     *                                  NaN into 0 g)
     */
    public long toGrams(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("Not a finite weight: " + value);
        }
        return Math.round(value * gramsPerUnit);
    }

    /** Converts stored grams to this unit (not rounded; formatting decides the precision). */
    public double fromGrams(long grams) {
        return grams / gramsPerUnit;
    }
}

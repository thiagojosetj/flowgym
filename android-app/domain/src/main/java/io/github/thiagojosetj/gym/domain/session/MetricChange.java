package io.github.thiagojosetj.gym.domain.session;

/**
 * One metric of a session next to the same metric of an earlier session (PRODUCT_SPEC section 11).
 *
 * <p>The percentage is deliberately not always available. A previous value of zero has no
 * percentage to give: dividing by it yields infinity, and rounding that to "100%" or "—%" would be
 * inventing a number. So when {@code previous} is zero the change is still shown - an arrow and the
 * absolute difference are both honest - and only the percentage is absent. {@link #hasPercent()}
 * says which case this is, and {@link #percent()} refuses to answer in the other one instead of
 * returning a placeholder a caller might print.
 *
 * <p>Both values are plain longs in whatever unit the metric already uses - grams, milliseconds or
 * a count - so this type never has to know what it is comparing.
 */
public record MetricChange(long current, long previous) {

    /** Which way the metric moved. {@code SAME} is a genuine tie, never "no data". */
    public enum Direction { UP, DOWN, SAME }

    public long delta() {
        return current - previous;
    }

    public Direction direction() {
        if (current > previous) {
            return Direction.UP;
        }
        return current < previous ? Direction.DOWN : Direction.SAME;
    }

    /** A percentage only means something when there was something to grow from. */
    public boolean hasPercent() {
        return previous > 0L;
    }

    /**
     * Variation in percent, signed: +12.5 means the metric grew by an eighth.
     *
     * @throws IllegalStateException when {@link #hasPercent()} is false. The caller has to ask
     *                               first; that is the point, because the alternative is a screen
     *                               quietly showing a percentage nobody can justify.
     */
    public double percent() {
        if (!hasPercent()) {
            throw new IllegalStateException("No percentage from a previous value of " + previous);
        }
        return (double) delta() * 100.0 / (double) previous;
    }
}

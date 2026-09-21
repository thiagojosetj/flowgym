package io.github.thiagojosetj.gym.domain.model;

/**
 * Planned repetitions: a fixed value ({@code 12}) or a range ({@code 8–10}).
 *
 * @param min lowest acceptable repetitions (≥ 1)
 * @param max highest acceptable repetitions (≥ min)
 */
public record RepRange(int min, int max) {

    public static final int MAX_REPS = 999;

    public RepRange {
        if (min < 1 || max > MAX_REPS || min > max) {
            throw new IllegalArgumentException("Invalid rep range: " + min + "-" + max);
        }
    }

    public static RepRange exactly(int reps) {
        return new RepRange(reps, reps);
    }

    public static RepRange between(int min, int max) {
        return new RepRange(min, max);
    }

    public boolean isFixed() {
        return min == max;
    }

    /** "12" or "8–10" (en dash). Language-neutral, safe to show in any locale. */
    public String format() {
        return isFixed() ? Integer.toString(min) : min + "–" + max;
    }
}

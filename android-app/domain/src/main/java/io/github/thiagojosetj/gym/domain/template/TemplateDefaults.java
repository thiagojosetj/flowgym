package io.github.thiagojosetj.gym.domain.template;

import java.util.Objects;

import io.github.thiagojosetj.gym.domain.model.RepRange;

/**
 * Values applied when an exercise is added to a template (PRODUCT_SPEC TPL-02). They are only
 * starting points: every value can be edited afterwards. Kept as data (not constants spread in the
 * UI) so a future settings screen can change them.
 *
 * @param reps            used when the exercise counts repetitions
 * @param durationSeconds used when the exercise is timed (e.g. plank)
 */
public record TemplateDefaults(int setCount, RepRange reps, int durationSeconds, int restSeconds) {

    public static final int DEFAULT_SET_COUNT = 3;
    public static final int DEFAULT_REPS = 12;
    public static final int DEFAULT_DURATION_SECONDS = 30;
    public static final int DEFAULT_REST_SECONDS = 90;

    public TemplateDefaults {
        Objects.requireNonNull(reps, "reps");
        if (setCount < 1 || setCount > TemplateRules.MAX_SETS_PER_EXERCISE) {
            throw new IllegalArgumentException("setCount out of range: " + setCount);
        }
        if (durationSeconds < 1 || durationSeconds > TemplateRules.MAX_DURATION_SECONDS) {
            throw new IllegalArgumentException("durationSeconds out of range: " + durationSeconds);
        }
        if (restSeconds < 0 || restSeconds > TemplateRules.MAX_REST_SECONDS) {
            throw new IllegalArgumentException("restSeconds out of range: " + restSeconds);
        }
    }

    /** 3 sets × 12 reps (or 3 × 30 s for timed exercises), 90 s rest. */
    public static TemplateDefaults standard() {
        return new TemplateDefaults(
                DEFAULT_SET_COUNT,
                RepRange.exactly(DEFAULT_REPS),
                DEFAULT_DURATION_SECONDS,
                DEFAULT_REST_SECONDS);
    }
}

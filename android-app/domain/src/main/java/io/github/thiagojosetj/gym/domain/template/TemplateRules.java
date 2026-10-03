package io.github.thiagojosetj.gym.domain.template;

/** Limits for workout templates. Generous, but they stop typos and runaway input. */
public final class TemplateRules {

    public static final int MAX_NAME_LENGTH = 60;
    public static final int MAX_DESCRIPTION_LENGTH = 500;
    public static final int MAX_NOTES_LENGTH = 1000;
    public static final int MAX_EXERCISES = 50;
    /** A group is exercises done in turn (PRODUCT_SPEC section 6.3): one on its own is no group. */
    public static final int MIN_GROUP_SIZE = 2;
    public static final int MAX_SETS_PER_EXERCISE = 20;
    public static final int MAX_REST_SECONDS = 3600;
    public static final int MAX_DURATION_SECONDS = 3600;

    private TemplateRules() {
    }
}

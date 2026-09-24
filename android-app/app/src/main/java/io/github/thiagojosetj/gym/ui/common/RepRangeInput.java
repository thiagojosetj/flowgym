package io.github.thiagojosetj.gym.ui.common;

import io.github.thiagojosetj.gym.domain.model.RepRange;

/**
 * Reads the repetitions field of a set: "8" for a fixed target, "8-10" (or "8–10", as the app
 * displays it) for a range. One field instead of two keeps a set row usable on a phone.
 */
public final class RepRangeInput {

    private RepRangeInput() {
    }

    /**
     * @return null for blank input
     * @throws IllegalArgumentException for anything that is not "n" or "n-m" with 1 ≤ n ≤ m
     */
    public static RepRange parse(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        String normalized = text.trim().replace('–', '-').replace('—', '-');
        int dash = normalized.indexOf('-');
        if (dash < 0) {
            return RepRange.exactly(parsePart(normalized));
        }
        int min = parsePart(normalized.substring(0, dash));
        int max = parsePart(normalized.substring(dash + 1));
        return RepRange.between(min, max); // validates 1 ≤ min ≤ max ≤ 999
    }

    /** What {@link #parse(String)} accepts back: "12" or "8–10". */
    public static String format(RepRange range) {
        return range == null ? "" : range.format();
    }

    private static int parsePart(String part) {
        Integer value = NumberInput.parseWholeNumber(part);
        if (value == null) {
            throw new IllegalArgumentException("Empty repetitions");
        }
        return value;
    }
}

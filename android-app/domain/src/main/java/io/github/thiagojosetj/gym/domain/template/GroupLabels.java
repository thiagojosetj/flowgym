package io.github.thiagojosetj.gym.domain.template;

/**
 * The letter a group wears: the first group of a template is "A", the second "B", and an exercise
 * inside it reads A1, A2 (PRODUCT_SPEC section 6.3).
 *
 * <p>This is the only place that turns "the Nth group of a template" into a label. The data layer
 * calls it every time the groups of a template change, so a label is always a function of where the
 * group sits and never a value that has to be kept in step by hand.
 */
public final class GroupLabels {

    private static final int LETTERS = 26;

    private GroupLabels() {
    }

    /**
     * @param index 0-based place of the group among the groups of its template
     * @return "A" for 0 up to "Z" for 25, then "AA", "AB" and on, the way spreadsheet columns run.
     *         A template holds at most {@link TemplateRules#MAX_EXERCISES} exercises, so groups of
     *         two or more cannot get past "Y"; the label still goes on instead of failing, because
     *         a screen that only renders it must never be the one that crashes.
     */
    public static String forIndex(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("A group cannot sit before the first: " + index);
        }
        StringBuilder label = new StringBuilder();
        int rest = index;
        do {
            label.append((char) ('A' + rest % LETTERS));
            rest = rest / LETTERS - 1;
        } while (rest >= 0);
        return label.reverse().toString();
    }
}

package io.github.thiagojosetj.gym.domain.template;

/**
 * A group of exercises inside a template: superset, bi-set, tri-set or giant set
 * (PRODUCT_SPEC section 6.3). The screen shows the label before the number, so label "A" over two
 * exercises reads A1 and A2.
 *
 * @param techniqueId        group-scope technique (SS, BI, TRI, GS), or null for a plain grouping
 * @param techniqueCode      the badge to draw, or null
 * @param restAfterRoundSeconds rest that belongs to the ROUND, not to each exercise: in an A1/A2
 *                           superset it starts once the round is over, which is why it lives on
 *                           the group
 */
public record ExerciseGroup(
        String id,
        String label,
        String techniqueId,
        String techniqueCode,
        int restAfterRoundSeconds,
        int position) {
}

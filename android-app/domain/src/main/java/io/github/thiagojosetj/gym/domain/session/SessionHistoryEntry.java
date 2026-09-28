package io.github.thiagojosetj.gym.domain.session;

/**
 * One line of the history list (PRODUCT_SPEC HIS-03): enough to recognise a session at a glance,
 * and nothing that would make drawing the list read every set of every session ever performed.
 *
 * <p>Load volume is deliberately absent. Computing it honestly means applying the section 9 rules
 * set by set - per-implement loads multiplied, per-side repetitions added, body weight and timed
 * sets left out - so a volume column here would mean reading the whole history to paint one screen.
 * The volume belongs to the session's own screen, where it is computed from that session's sets by
 * the same code that computed it the moment the workout was finished.
 *
 * @param localDate     ISO date in the zone the workout was performed in, so a session does not
 *                      move to another day because the phone later crossed a time zone
 * @param timeZone      that zone, so the time of day can be shown as it was lived
 * @param totalMs       end - start
 * @param effectiveMs   total minus the pauses
 * @param performedSets sets confirmed as done, warm-ups included (same rule as the finish summary)
 */
public record SessionHistoryEntry(
        String sessionId,
        String name,
        String localDate,
        String timeZone,
        long startedAt,
        long totalMs,
        long effectiveMs,
        int exercises,
        int performedSets) {
}

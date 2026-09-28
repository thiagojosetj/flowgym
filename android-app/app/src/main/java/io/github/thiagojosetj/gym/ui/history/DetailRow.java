package io.github.thiagojosetj.gym.ui.history;

/** One line of the finished-session detail (PRODUCT_SPEC HIS-01, HIS-03, HIS-04). */
public interface DetailRow {

    /** Stable identity for DiffUtil. */
    String id();

    /**
     * A line of text and, when a screen reader must not read it the way it is drawn, what to say.
     *
     * @param spoken what to announce instead of {@code text} ("1 minuto e 23 segundos" for "1:23",
     *               "aumentou" for an arrow), or null when the text reads fine as it is
     */
    record Line(String text, String spoken) {

        static Line of(String text) {
            return new Line(text, null);
        }
    }

    /**
     * This session next to the previous session of the same workout (PRODUCT_SPEC section 11): one
     * line per metric, and no verdict on which session was better.
     *
     * @param subtitle names the day being compared with
     */
    record Comparison(String subtitle, Line volume, Line sets, Line reps, Line time) {
    }

    /**
     * The top of the screen: what the session was, its totals and how it compares.
     *
     * @param dateTime         day and time of day, in the zone the workout was performed in
     * @param outsideVolume    "N sets not included in the volume", or null when nothing was left
     *                         out; PRODUCT_SPEC section 9 requires the line whenever something was
     * @param timeUnderTension null when no timed set was performed
     * @param rating           null when the person gave none: no rating is not a rating of zero
     * @param comparison       null when this was the first time the workout was performed
     * @param noComparison     says so in that case, and is null otherwise
     * @param exercisesTitle   heading of the exercises below, null when there are none
     */
    record Summary(
            String id,
            String name,
            String dateTime,
            Line duration,
            String sets,
            String volume,
            String outsideVolume,
            Line timeUnderTension,
            String rating,
            Comparison comparison,
            String noComparison,
            String exercisesTitle) implements DetailRow {
    }

    /**
     * @param sets   performed out of the sets it had ("3 de 4 series"), so skipped ones show
     * @param volume this exercise's load volume, or the reason there is none
     */
    record ExerciseHeader(String id, String name, String sets, String volume) implements DetailRow {
    }

    /**
     * @param number    working-set number, or the warm-up badge
     * @param performed what was done ("10 reps - 40 kg"), or that the set was not done
     * @param planned   what the session's snapshot planned, or null when it planned nothing
     * @param previous  what the paired set did last time, or null when there is nothing to show
     */
    record SetRow(String id, String number, String performed, String planned, String previous)
            implements DetailRow {
    }
}

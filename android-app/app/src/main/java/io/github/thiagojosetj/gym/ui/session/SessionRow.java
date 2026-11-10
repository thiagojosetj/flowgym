package io.github.thiagojosetj.gym.ui.session;

/**
 * One line of the active-workout list: an exercise header, one of its sets, one of that set's drops
 * (segments), or the "add set" footer. One flat list instead of nested RecyclerViews (ARCHITECTURE
 * section 8), which is also why a drop is a row of its own rather than a list inside its set.
 *
 * <p>No time value is ever part of these records. The elapsed time and the rest live in fixed views
 * outside the list, so a tick can never cause the list to rebind.
 */
public interface SessionRow {

    /** Stable identity for DiffUtil. */
    String id();

    /**
     * @param name          "A1 Supino reto" inside a group (PRODUCT_SPEC 6.3), the plain name
     *                      otherwise
     * @param spokenName    what a screen reader says instead, because "A1" is read as a code
     * @param completedSets how many of this exercise's sets are done, for "2/4"
     * @param permanentNotes note that comes from the template
     */
    record ExerciseHeader(
            String id,
            String name,
            String spokenName,
            int completedSets,
            int totalSets,
            boolean collapsed,
            String permanentNotes,
            String restLabel) implements SessionRow {
    }

    /**
     * @param number       working-set number, or null for a warm-up (labelled by its badge)
     * @param badge        technique code shown on the row ("AQ", "D"), null for a normal set
     * @param plannedText  what the template asked for
     * @param previousText what the same set did last time, or null when there is nothing to show
     * @param weightText   text to put in the field: what the user typed, or the confirmed value
     * @param weightHint   suggestion shown as a hint - a hint is never mistaken for a result
     * @param perSide      the exercise is logged one side at a time, so the single reps field is
     *                     replaced by two (PRODUCT_SPEC 6.4). Repetitions are always counted per
     *                     side; logging them apart records 10 and 9 instead of averaging them away
     */
    record SetRow(
            String id,
            String sessionExerciseId,
            Integer number,
            String badge,
            String plannedText,
            String previousText,
            boolean showWeight,
            boolean showReps,
            String weightText,
            String repsText,
            String weightHint,
            String repsHint,
            String weightLabel,
            String repsLabel,
            boolean perSide,
            String repsLeftText,
            String repsRightText,
            String repsLeftHint,
            String repsRightHint,
            boolean done,
            boolean removable) implements SessionRow {
    }

    /**
     * A later step of a drop-set or rest-pause, drawn under the set it belongs to (PRODUCT_SPEC 9.1,
     * ADR-0037). The set is the FIRST step, so the first segment is the first DROP, never the second
     * step under a different name.
     *
     * <p>A row of its own instead of more fields on {@link SetRow}: a segment has no number, no
     * plan, no "previous" and no technique, so borrowing SetRow would leave half of its fields
     * meaning nothing and make every reader ask which half.
     *
     * @param parentSetId the SET this drop belongs to; the id "add a drop" was asked for
     * @param index       1-based drop number for the label, not counting the set itself
     * @param setNumber   number of the parent set, or null when that set is a warm-up. It is spoken,
     *                    never drawn: a screen reader has to say which set a drop belongs to
     * @param weightText  what the user typed, or the confirmed value - the same rule as on
     *                    {@link SetRow}, worked out by the same code
     * @param weightHint  suggestion shown as a hint. A segment has no plan and no "previous", so it
     *                    only ever echoes what is already stored: nothing is invented for a drop
     * @param perSide     same meaning as on {@link SetRow}. The drop of a per-side exercise is
     *                    logged per side too: one combined reps field would be discarded when the
     *                    drop is confirmed, because that exercise stores its reps per side
     */
    record Segment(
            String id,
            String parentSetId,
            int index,
            Integer setNumber,
            boolean showWeight,
            String weightText,
            String repsText,
            String weightHint,
            String repsHint,
            String weightLabel,
            String repsLabel,
            boolean perSide,
            String repsLeftText,
            String repsRightText,
            String repsLeftHint,
            String repsRightHint,
            boolean done) implements SessionRow {
    }

    record AddSet(String id, String sessionExerciseId, String exerciseName) implements SessionRow {
    }
}

package io.github.thiagojosetj.gym.ui.session;

/**
 * One line of the active-workout list: an exercise header, one of its sets, or the "add set" footer.
 * One flat list instead of nested RecyclerViews (ARCHITECTURE section 8).
 *
 * <p>No time value is ever part of these records. The elapsed time and the rest live in fixed views
 * outside the list, so a tick can never cause the list to rebind.
 */
public interface SessionRow {

    /** Stable identity for DiffUtil. */
    String id();

    /**
     * @param completedSets how many of this exercise's sets are done, for "2/4"
     * @param permanentNotes note that comes from the template
     */
    record ExerciseHeader(
            String id,
            String name,
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
            boolean done,
            boolean removable) implements SessionRow {
    }

    record AddSet(String id, String sessionExerciseId, String exerciseName) implements SessionRow {
    }
}

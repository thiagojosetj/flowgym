package io.github.thiagojosetj.gym.domain.progress;

import java.util.List;

/**
 * How much of an exercise's history is on screen (PRODUCT_SPEC PRG-02).
 *
 * <p>The windows are a product rule, not a widget's business, so they live here and the screen
 * only offers them. The sizes are the ones PRG-02 names: the last 5, 10 or 30 sessions, or all of
 * it. A custom date range is also listed there and is not built yet (docs/ROADMAP.md).
 *
 * <p><b>A window never touches the records.</b> The records of section 10 are read from the whole
 * history by definition: "your best in the last five sessions" is not a record, and a screen that
 * recomputed them per window would be publishing a different claim under the same word.
 */
public enum ProgressWindow {

    LAST_5(5),
    LAST_10(10),
    LAST_30(30),
    /** Everything there is. Size zero, because there is no number that means "no limit". */
    ALL(0);

    private final int size;

    ProgressWindow(int size) {
        this.size = size;
    }

    public int size() {
        return size;
    }

    /**
     * The last {@code size} of these, in the order they arrived.
     *
     * <p>Oldest first, like the list it slices: a window is a slice of the history and not a
     * reordering of it. Whoever hands over oldest-first points gets oldest-first points back.
     */
    public <T> List<T> applyTo(List<T> points) {
        if (points == null || size <= 0 || points.size() <= size) {
            return points;
        }
        return points.subList(points.size() - size, points.size());
    }

    /**
     * Whether this window would actually leave anything out of a history of this size.
     *
     * <p>Used to disable the ones that would not. A "last 30" button on a history of four sessions
     * shows exactly what "all" shows, so offering it as a filter claims a choice that does not
     * exist - the same reason the calendar's arrows stop where the training does.
     */
    public boolean narrows(int total) {
        return size > 0 && total > size;
    }
}

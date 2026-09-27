package io.github.thiagojosetj.gym.domain.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * How a set of today's session is matched with the same set of the previous one (PRODUCT_SPEC
 * section 11): by its ordinal <em>among the working sets</em>, not by its raw position.
 *
 * <p>The difference is not academic. If today the user adds a warm-up as the first set, matching by
 * raw position would compare today's first working set with last time's second one, and every
 * "anterior" on the screen would be off by one. Warm-ups are not compared at all: they are not
 * results (their technique has {@code counts_as_working_set = 0}).
 *
 * <p>This is derived from the technique of each set, so it is fixed by a release. Storing a
 * {@code working_position} column instead would need a migration and could drift from the techniques.
 */
public final class WorkingSetPairing {

    /** No set on the other side. */
    public static final int NONE = -1;

    private WorkingSetPairing() {
    }

    /**
     * Numbers the sets as the screen shows them: 1, 2, 3... for working sets and {@code null} for a
     * warm-up, which is labelled by its badge instead.
     *
     * @param working for each set, in order, whether it counts as a working set
     */
    public static List<Integer> numberWorkingSets(List<Boolean> working) {
        List<Integer> numbers = new ArrayList<>(working == null ? 0 : working.size());
        int next = 1;
        if (working != null) {
            for (Boolean isWorking : working) {
                if (Boolean.TRUE.equals(isWorking)) {
                    numbers.add(next++);
                } else {
                    numbers.add(null);
                }
            }
        }
        return Collections.unmodifiableList(numbers);
    }

    /**
     * For each of today's sets, the index of the previous session's set it should be compared with,
     * or {@link #NONE}.
     *
     * <p>A warm-up never pairs, and a working set with no counterpart (the user did four sets today
     * and three last time) also returns {@link #NONE} - showing nothing is better than showing a
     * number that belongs to a different set.
     */
    public static int[] pair(List<Boolean> currentWorking, List<Boolean> previousWorking) {
        int size = currentWorking == null ? 0 : currentWorking.size();
        int[] result = new int[size];
        List<Integer> previousByOrdinal = new ArrayList<>();
        if (previousWorking != null) {
            for (int i = 0; i < previousWorking.size(); i++) {
                if (Boolean.TRUE.equals(previousWorking.get(i))) {
                    previousByOrdinal.add(i);
                }
            }
        }
        int ordinal = 0;
        for (int i = 0; i < size; i++) {
            if (!Boolean.TRUE.equals(currentWorking.get(i))) {
                result[i] = NONE;
                continue;
            }
            result[i] = ordinal < previousByOrdinal.size() ? previousByOrdinal.get(ordinal) : NONE;
            ordinal++;
        }
        return result;
    }
}

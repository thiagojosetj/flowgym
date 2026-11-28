package io.github.thiagojosetj.gym.domain.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.thiagojosetj.gym.domain.model.SideMode;
import io.github.thiagojosetj.gym.domain.model.TrackingType;

/**
 * What finishing a session would do to the sets that were never confirmed (PRODUCT_SPEC section 8).
 *
 * <p>The rule is that nothing is thrown away silently. A set that was filled in but not confirmed
 * is completed; a set with half of its data, and one with none, are marked as skipped — their typed
 * values stay in the database, they simply do not count as a result. The screen shows this list
 * before anything happens, so the user can go back and fix a set instead.
 */
public record FinishReview(int alreadyCompleted, List<PendingSet> readyToComplete,
                           List<PendingSet> partiallyFilled, List<PendingSet> empty,
                           List<String> segmentIdsToComplete, List<String> segmentIdsToSkip) {

    /**
     * One set that finishing would have to decide about.
     *
     * @param exerciseName name to show in the list ("Supino reto com barra")
     * @param setNumber    1-based number of the set inside its exercise
     */
    public record PendingSet(String setId, String exerciseName, int setNumber) {
    }

    /**
     * One set as it is right now, with what its exercise requires to consider it performed.
     *
     * @param isSegment true for a drop of a drop-set. It is resolved like any other row, but it is
     *                  never counted as a set of its own: a drop-set is ONE set taken past failure
     *                  (ADR-0037), and counting the drops would make "1 serie" read as "3".
     */
    public record SetUnderReview(String setId, String exerciseName, int setNumber, SetStatus status,
                                 SetValues values, TrackingType trackingType, SideMode sideMode,
                                 boolean isSegment) {
    }

    public FinishReview {
        readyToComplete = copy(readyToComplete);
        partiallyFilled = copy(partiallyFilled);
        empty = copy(empty);
        segmentIdsToComplete = copyIds(segmentIdsToComplete);
        segmentIdsToSkip = copyIds(segmentIdsToSkip);
    }

    private static List<String> copyIds(List<String> ids) {
        return ids == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(ids));
    }

    private static List<PendingSet> copy(List<PendingSet> sets) {
        return sets == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(sets));
    }

    public static FinishReview of(List<SetUnderReview> sets) {
        int completed = 0;
        List<PendingSet> ready = new ArrayList<>();
        List<PendingSet> partial = new ArrayList<>();
        List<PendingSet> empty = new ArrayList<>();
        List<String> segmentsToComplete = new ArrayList<>();
        List<String> segmentsToSkip = new ArrayList<>();
        if (sets != null) {
            for (SetUnderReview set : sets) {
                if (set.status() == SetStatus.COMPLETED) {
                    if (!set.isSegment()) {
                        completed++;
                    }
                    continue;
                }
                if (set.status() == SetStatus.SKIPPED) {
                    continue; // already decided by the user
                }
                boolean filled = !set.values().isEmpty();
                boolean usable = filled && set.values().isComplete(set.trackingType(), set.sideMode());
                if (set.isSegment()) {
                    // A drop follows the same rule as a set - recorded when it has what it needs,
                    // skipped otherwise - but it is resolved, never left PENDING inside a session
                    // that is over. Leaving it was the bug: the reps happened, counted for
                    // nothing, and the user was never told (found in review, 03/10/2026).
                    (usable ? segmentsToComplete : segmentsToSkip).add(set.setId());
                    continue;
                }
                PendingSet pending = new PendingSet(set.setId(), set.exerciseName(), set.setNumber());
                if (!filled) {
                    empty.add(pending);
                } else if (usable) {
                    ready.add(pending);
                } else {
                    partial.add(pending);
                }
            }
        }
        return new FinishReview(completed, ready, partial, empty, segmentsToComplete,
                segmentsToSkip);
    }

    /** True when finishing would change something the user has not explicitly decided. */
    public boolean needsConfirmation() {
        return !readyToComplete.isEmpty() || !partiallyFilled.isEmpty() || !empty.isEmpty()
                || affectedSegments() > 0;
    }

    /**
     * Drops that finishing has to decide about. Counted apart from the sets, so the dialog can say
     * how many without turning one drop-set into three sets.
     */
    public int affectedSegments() {
        return segmentIdsToComplete.size() + segmentIdsToSkip.size();
    }

    /** Finishing now would record a session with no performed set at all. */
    public boolean wouldRecordNothing() {
        return alreadyCompleted == 0 && readyToComplete.isEmpty();
    }

    public int pendingCount() {
        return readyToComplete.size() + partiallyFilled.size() + empty.size();
    }

    /** Rows that finishing marks as performed, because they have everything they need. */
    public List<String> setIdsToComplete() {
        List<String> result = new ArrayList<>(readyToComplete.size()
                + segmentIdsToComplete.size());
        result.addAll(ids(readyToComplete));
        result.addAll(segmentIdsToComplete);
        return Collections.unmodifiableList(result);
    }

    /** Sets that finishing marks as skipped: empty, or filled in only halfway. */
    public List<String> setIdsToSkip() {
        List<String> result = new ArrayList<>(partiallyFilled.size() + empty.size()
                + segmentIdsToSkip.size());
        result.addAll(ids(partiallyFilled));
        result.addAll(ids(empty));
        result.addAll(segmentIdsToSkip);
        return Collections.unmodifiableList(result);
    }

    private static List<String> ids(List<PendingSet> sets) {
        List<String> result = new ArrayList<>(sets.size());
        for (PendingSet set : sets) {
            result.add(set.setId());
        }
        return Collections.unmodifiableList(result);
    }
}

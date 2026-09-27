package io.github.thiagojosetj.gym.domain.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.thiagojosetj.gym.domain.model.Laterality;
import io.github.thiagojosetj.gym.domain.model.LoadBasis;
import io.github.thiagojosetj.gym.domain.model.SideMode;
import io.github.thiagojosetj.gym.domain.model.TrackingType;

/**
 * One exercise being performed in a session, with the values snapshotted when the session started
 * (docs/DATABASE.md section 4): the name it had then, how its load is read, and the planned rest.
 *
 * @param permanentNotes note that belongs to the template ("Banco no terceiro encaixe")
 * @param notes          note written during this session
 */
public record SessionExercise(
        String id,
        String exerciseId,
        int position,
        String name,
        TrackingType trackingType,
        LoadBasis loadBasis,
        int implementCount,
        Laterality laterality,
        SideMode sideMode,
        int restSeconds,
        String permanentNotes,
        String notes,
        String primaryEquipmentCode,
        List<LoggedSet> sets) {

    public SessionExercise {
        sets = sets == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(sets));
    }

    public boolean isUnilateral() {
        return laterality == Laterality.UNILATERAL;
    }

    public boolean isLoadPerImplement() {
        return loadBasis == LoadBasis.PER_IMPLEMENT;
    }

    public int completedSets() {
        int done = 0;
        for (LoggedSet set : sets) {
            if (set.isCompleted()) {
                done++;
            }
        }
        return done;
    }

    /** Working sets only: a warm-up is not part of "3 de 4 séries". */
    public int workingSets() {
        int total = 0;
        for (LoggedSet set : sets) {
            if (!set.isWarmUp()) {
                total++;
            }
        }
        return total;
    }

    /** True when every set was decided (performed or deliberately skipped). */
    public boolean isDone() {
        for (LoggedSet set : sets) {
            if (set.status() == SetStatus.PENDING) {
                return false;
            }
        }
        return !sets.isEmpty();
    }

    /** The next set the user is expected to perform, or null when there is none left. */
    public LoggedSet nextPendingSet() {
        for (LoggedSet set : sets) {
            if (set.status() == SetStatus.PENDING) {
                return set;
            }
        }
        return null;
    }

    /**
     * Total repetitions of a set of THIS exercise. A unilateral exercise counts repetitions per
     * side, so "10" logged together is 20 in total, and per-side logging adds both sides
     * (PRODUCT_SPEC sections 6.4 and 9).
     */
    public Integer totalRepsOf(LoggedSet set) {
        SetValues values = set.values();
        if (values.repsLeft() != null || values.repsRight() != null) {
            return values.totalReps();
        }
        if (values.reps() == null) {
            return null;
        }
        return isUnilateral() && sideMode == SideMode.COMBINED ? values.reps() * 2 : values.reps();
    }
}

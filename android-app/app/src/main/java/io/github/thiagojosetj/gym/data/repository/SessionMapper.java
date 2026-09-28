package io.github.thiagojosetj.gym.data.repository;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.thiagojosetj.gym.data.local.row.ActiveSetRow;
import io.github.thiagojosetj.gym.data.local.row.PreviousSetRow;
import io.github.thiagojosetj.gym.data.local.row.SessionHeaderRow;
import io.github.thiagojosetj.gym.domain.model.RepRange;
import io.github.thiagojosetj.gym.domain.model.Weight;
import io.github.thiagojosetj.gym.domain.session.ActiveSession;
import io.github.thiagojosetj.gym.domain.session.LoggedSet;
import io.github.thiagojosetj.gym.domain.session.SessionClock;
import io.github.thiagojosetj.gym.domain.session.SessionExercise;
import io.github.thiagojosetj.gym.domain.session.SessionHeader;
import io.github.thiagojosetj.gym.domain.session.SetStatus;
import io.github.thiagojosetj.gym.domain.session.SetValues;
import io.github.thiagojosetj.gym.domain.session.WorkingSetPairing;

/**
 * Turns session rows into the read-only domain snapshot the screen renders.
 *
 * <p>The only real work here is pairing each set with the same set of the previous session. SQLite on
 * API 28 has no window functions, so the ordinal among working sets is computed in Java by
 * {@link io.github.thiagojosetj.gym.domain.session.WorkingSetPairing} instead of in the query.
 */
final class SessionMapper {

    private SessionMapper() {
    }

    static SessionHeader toHeader(SessionHeaderRow row) {
        SessionClock clock = toClock(row.startedAt, row.endedAt, row.closedPausedMs,
                row.openPauseStartedAt);
        return new SessionHeader(row.id, row.templateId, row.name, row.notes, row.status, clock,
                row.restSetLogId, row.restEndsAt, row.restRemainingMsWhenPaused);
    }

    /**
     * Builds the clock every session reading goes through, including the history list.
     *
     * <p>A row where the session "ends before it starts" is possible: the device clock can move
     * backwards mid-workout. Reading it must show a zero-length session, never throw - otherwise the
     * session becomes unfinishable and, since only one may be active, the user cannot train again
     * either (found in review, 28/09/2026). The stored instants are never rewritten to hide it.
     *
     * <p>This is one method rather than two because a second copy of the clamp would eventually be
     * the one that is missing it.
     */
    static SessionClock toClock(long startedAt, @Nullable Long endedAt, long closedPausedMs,
                                @Nullable Long openPauseStartedAt) {
        Long end = endedAt == null ? null : Math.max(startedAt, endedAt);
        return new SessionClock(startedAt, end, Math.max(0L, closedPausedMs), openPauseStartedAt);
    }

    static ActiveSession toSession(SessionHeaderRow header, List<ActiveSetRow> rows,
                                  List<PreviousSetRow> previousRows) {
        return new ActiveSession(toHeader(header), toExercises(rows, previousRows));
    }

    static List<SessionExercise> toExercises(List<ActiveSetRow> rows, List<PreviousSetRow> previousRows) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, List<PreviousSetRow>> previousByExercise = groupPrevious(previousRows);
        List<SessionExercise> exercises = new ArrayList<>();
        int index = 0;
        while (index < rows.size()) {
            String exerciseRowId = rows.get(index).sessionExerciseId;
            int end = index;
            while (end < rows.size() && rows.get(end).sessionExerciseId.equals(exerciseRowId)) {
                end++;
            }
            exercises.add(toExercise(rows.subList(index, end), previousByExercise));
            index = end;
        }
        return exercises;
    }

    private static SessionExercise toExercise(List<ActiveSetRow> rows,
                                              Map<String, List<PreviousSetRow>> previousByExercise) {
        ActiveSetRow first = rows.get(0);
        List<PreviousSetRow> previous = first.previousSessionExerciseId == null
                ? Collections.emptyList()
                : previousByExercise.getOrDefault(first.previousSessionExerciseId, Collections.emptyList());

        // Segments are nested into their set, never listed beside it. Splitting them off BEFORE
        // anything else matters: the working-set numbering and the pairing with the previous
        // session both count SETS (ADR-0033), so letting a drop through here would renumber every
        // set after it and shift the whole "anterior" column by one.
        List<ActiveSetRow> parents = new ArrayList<>(rows.size());
        Map<String, List<ActiveSetRow>> segmentsByParent = new HashMap<>();
        for (ActiveSetRow row : rows) {
            if (row.setId == null || row.parentSetId == null) {
                parents.add(row);
            } else {
                segmentsByParent.computeIfAbsent(row.parentSetId, key -> new ArrayList<>()).add(row);
            }
        }
        rows = parents;

        List<Boolean> currentWorking = new ArrayList<>(rows.size());
        for (ActiveSetRow row : rows) {
            currentWorking.add(isWorkingSet(row.techniqueId, row.techniqueCountsAsWorkingSet));
        }
        List<Boolean> previousWorking = new ArrayList<>(previous.size());
        for (PreviousSetRow row : previous) {
            previousWorking.add(isWorkingSet(null, row.countsAsWorkingSet));
        }
        List<Integer> workingNumbers = WorkingSetPairing.numberWorkingSets(currentWorking);
        int[] pairs = WorkingSetPairing.pair(currentWorking, previousWorking);

        List<LoggedSet> sets = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            ActiveSetRow row = rows.get(i);
            if (row.setId == null) {
                continue; // LEFT JOIN: this exercise has no sets left
            }
            SetValues previousValues = null;
            int pairedIndex = pairs[i];
            if (pairedIndex >= 0) {
                PreviousSetRow paired = previous.get(pairedIndex);
                if (paired.status == SetStatus.COMPLETED) {
                    previousValues = new SetValues(weight(paired.weightGrams), paired.reps,
                            paired.repsLeft, paired.repsRight, paired.durationSeconds);
                }
            }
            sets.add(new LoggedSet(
                    row.setId,
                    row.setPosition == null ? i : row.setPosition,
                    workingNumbers.get(i),
                    row.techniqueId,
                    row.techniqueCode,
                    Boolean.TRUE.equals(currentWorking.get(i)),
                    repRange(row.plannedRepsMin, row.plannedRepsMax),
                    weight(row.plannedWeightGrams),
                    row.plannedDurationSeconds,
                    row.plannedRestSeconds == null ? 0 : row.plannedRestSeconds,
                    new SetValues(weight(row.weightGrams), row.reps, row.repsLeft, row.repsRight,
                            row.durationSeconds),
                    row.status == null ? SetStatus.PENDING : row.status,
                    row.completedAt,
                    row.setNotes,
                    previousValues,
                    toSegments(segmentsByParent.get(row.setId))));
        }
        return new SessionExercise(first.sessionExerciseId, first.exerciseId, first.exercisePosition,
                first.exerciseName, first.trackingType, first.loadBasis, first.implementCount,
                first.laterality, first.sideMode, first.exerciseRestSeconds, first.permanentNotes,
                first.exerciseNotes, first.primaryEquipmentCode, sets);
    }

    private static Map<String, List<PreviousSetRow>> groupPrevious(List<PreviousSetRow> rows) {
        Map<String, List<PreviousSetRow>> grouped = new HashMap<>();
        if (rows != null) {
            for (PreviousSetRow row : rows) {
                List<PreviousSetRow> list = grouped.get(row.sessionExerciseId);
                if (list == null) {
                    list = new ArrayList<>();
                    grouped.put(row.sessionExerciseId, list);
                }
                list.add(row);
            }
        }
        return grouped;
    }

    /** No technique means a normal working set; a technique decides through its catalog flag. */
    /**
     * The later drops of a drop-set or rest-pause, as sets nested inside their own set
     * (PRODUCT_SPEC section 9.1). A segment carries no planned values and no "previous": it is a
     * drop off the set above it, not something the template asked for on its own.
     */
    private static List<LoggedSet> toSegments(@Nullable List<ActiveSetRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<LoggedSet> segments = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            ActiveSetRow row = rows.get(i);
            segments.add(new LoggedSet(
                    row.setId,
                    row.setPosition == null ? i : row.setPosition,
                    null,
                    row.techniqueId,
                    row.techniqueCode,
                    isWorkingSet(row.techniqueId, row.techniqueCountsAsWorkingSet),
                    null,
                    null,
                    null,
                    0,
                    new SetValues(weight(row.weightGrams), row.reps, row.repsLeft, row.repsRight,
                            row.durationSeconds),
                    row.status == null ? SetStatus.PENDING : row.status,
                    row.completedAt,
                    row.setNotes,
                    null,
                    Collections.emptyList()));
        }
        return segments;
    }

    private static boolean isWorkingSet(@Nullable String techniqueId, @Nullable Integer countsFlag) {
        if (countsFlag != null) {
            return countsFlag != 0;
        }
        return techniqueId == null;
    }

    @Nullable
    private static Weight weight(@Nullable Long grams) {
        return grams == null ? null : Weight.ofGrams(grams);
    }

    @Nullable
    private static RepRange repRange(@Nullable Integer min, @Nullable Integer max) {
        if (min == null && max == null) {
            return null;
        }
        int low = min == null ? max : min;
        int high = max == null ? low : max;
        if (low < 1 || high < low) {
            return null; // a broken row must not stop the screen from opening
        }
        return RepRange.between(low, high);
    }
}

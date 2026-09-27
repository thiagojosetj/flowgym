package io.github.thiagojosetj.gym.data.repository;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.thiagojosetj.gym.data.local.entity.SessionPauseEntity;
import io.github.thiagojosetj.gym.data.local.entity.WorkoutSessionEntity;
import io.github.thiagojosetj.gym.data.local.row.ActiveSetRow;
import io.github.thiagojosetj.gym.data.local.row.PreviousSetRow;
import io.github.thiagojosetj.gym.domain.model.RepRange;
import io.github.thiagojosetj.gym.domain.model.Weight;
import io.github.thiagojosetj.gym.domain.session.ActiveSession;
import io.github.thiagojosetj.gym.domain.session.LoggedSet;
import io.github.thiagojosetj.gym.domain.session.PauseInterval;
import io.github.thiagojosetj.gym.domain.session.SessionExercise;
import io.github.thiagojosetj.gym.domain.session.SessionHeader;
import io.github.thiagojosetj.gym.domain.session.SessionTiming;
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

    static SessionHeader toHeader(WorkoutSessionEntity session, List<SessionPauseEntity> pauses) {
        List<PauseInterval> intervals = new ArrayList<>(pauses == null ? 0 : pauses.size());
        if (pauses != null) {
            for (SessionPauseEntity pause : pauses) {
                intervals.add(new PauseInterval(pause.startedAt, pause.endedAt));
            }
        }
        SessionTiming timing = new SessionTiming(session.startedAt, session.endedAt, intervals);
        return new SessionHeader(session.id, session.templateId, session.name, session.notes,
                session.status, timing.clock(), session.restSetLogId, session.restEndsAt,
                session.restRemainingMsWhenPaused);
    }

    static ActiveSession toSession(WorkoutSessionEntity session, List<SessionPauseEntity> pauses,
                                   List<ActiveSetRow> rows, List<PreviousSetRow> previousRows) {
        return new ActiveSession(toHeader(session, pauses), toExercises(rows, previousRows));
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
                    previousValues));
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

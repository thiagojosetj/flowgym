package io.github.thiagojosetj.gym.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import io.github.thiagojosetj.gym.core.AppExecutors;
import io.github.thiagojosetj.gym.data.local.AppDatabase;
import io.github.thiagojosetj.gym.data.local.dao.ExerciseDao;
import io.github.thiagojosetj.gym.data.local.dao.SessionDao;
import io.github.thiagojosetj.gym.data.local.entity.ExerciseEntity;
import io.github.thiagojosetj.gym.data.local.row.ExerciseAppearanceRow;
import io.github.thiagojosetj.gym.data.local.row.ExerciseMuscleGroupRow;
import io.github.thiagojosetj.gym.data.local.row.SessionHeaderRow;
import io.github.thiagojosetj.gym.data.local.row.TrainedDayBoundsRow;
import io.github.thiagojosetj.gym.domain.progress.ExerciseAppearance;
import io.github.thiagojosetj.gym.domain.progress.ExerciseMuscleGroup;
import io.github.thiagojosetj.gym.domain.progress.ExerciseProgress;
import io.github.thiagojosetj.gym.domain.progress.PeriodSession;
import io.github.thiagojosetj.gym.domain.progress.PeriodStatistics;
import io.github.thiagojosetj.gym.domain.progress.ProgressSnapshot;
import io.github.thiagojosetj.gym.domain.progress.TrainingPeriod;
import io.github.thiagojosetj.gym.domain.session.ActiveSession;
import io.github.thiagojosetj.gym.domain.session.SessionExercise;

/**
 * What a week or a month of training came to (PRODUCT_SPEC PRG-04).
 *
 * <p><b>Why the whole sessions are read.</b> The honest total of a period is the section 9 rules
 * applied set by set - dumbbell loads multiplied, per-side repetitions added, body weight and
 * timed sets left out and counted separately. SQL cannot express that, and a second
 * implementation of it in SQL would eventually disagree with the figure each finish screen
 * already showed. So the sets are loaded and {@code SessionVolume} adds them up, once.
 *
 * <p>That is one query per session in the period. Deliberate: a period is a handful of sessions, a
 * week usually three to six, and the alternative is a clever aggregate that is wrong.
 *
 * <p><b>One observed query.</b> Only "which sessions are in this period" is observed; everything
 * else is computed from it on the disk thread. {@code switchMap} is what makes that safe: when the
 * period changes or the table changes again, the previous computation's LiveData is dropped, so a
 * slow result can never land on top of a newer one.
 */
public final class ProgressRepository {

    private final SessionDao dao;
    private final ExerciseDao exercises;
    private final AppExecutors executors;

    public ProgressRepository(AppDatabase database, AppExecutors executors) {
        this.dao = database.sessionDao();
        this.exercises = database.exerciseDao();
        this.executors = executors;
    }

    /**
     * The statistics of one closed period, recomputed whenever a session enters or leaves it.
     *
     * @param period the days to add up, as they were lived on
     */
    public LiveData<ProgressSnapshot> observePeriod(TrainingPeriod period) {
        String first = period.from().toString();
        String last = period.to().toString();
        // Observed on the ids alone, and that is enough: Room invalidates per TABLE, so any write
        // to workout_session - inside this period or outside it - re-runs the query and so
        // refreshes the bounds read below with it.
        return Transformations.switchMap(dao.observeSessionIdsBetween(first, last), ids -> {
            MutableLiveData<ProgressSnapshot> snapshot = new MutableLiveData<>();
            executors.runOnDisk(() -> compute(ids, first, last), snapshot::setValue,
                    error -> snapshot.setValue(ProgressSnapshot.EMPTY));
            return snapshot;
        });
    }

    private ProgressSnapshot compute(List<String> sessionIds, String from, String to) {
        TrainedDayBoundsRow bounds = dao.findTrainedDayBounds();
        List<PeriodSession> points = new ArrayList<>();
        PeriodStatistics statistics = statisticsOf(sessionIds, from, to, points);
        return new ProgressSnapshot(statistics, points,
                toDate(bounds == null ? null : bounds.firstDay),
                toDate(bounds == null ? null : bounds.lastDay));
    }

    /** A day that cannot be parsed is dropped rather than guessed at, as everywhere else. */
    private static LocalDate toDate(String stored) {
        if (stored == null || stored.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(stored);
        } catch (DateTimeParseException malformed) {
            return null;
        }
    }

    /**
     * One exercise across its whole history (PRODUCT_SPEC PRG-03).
     *
     * <p>Loaded once, not observed, for the reason ARCHITECTURE section 5.2 gives: finished
     * sessions cannot change, so there is no second emission to wait for and no half-built state
     * to draw. A workout finished while this screen is open is not in it, and the screen is read
     * again the next time it is opened.
     */
    public void loadExercise(String exerciseId, Consumer<ExerciseProgress> onResult,
                             Consumer<Throwable> onError) {
        executors.runOnDisk(() -> progressOf(exerciseId), onResult, onError);
    }

    private ExerciseProgress progressOf(String exerciseId) {
        List<ExerciseAppearanceRow> rows = dao.findAppearancesOfExercise(exerciseId);
        // The library's name, not the newest snapshot's: the title names the exercise as it is
        // now, while every NUMBER below it still comes from the snapshot of its own session.
        ExerciseEntity entity = exercises.findVisibleById(exerciseId);
        String name = entity == null ? "" : entity.name;
        if (rows.isEmpty()) {
            return ExerciseProgress.of(exerciseId, name, Collections.emptyList());
        }
        Map<String, ExerciseAppearanceRow> bySessionExercise = new HashMap<>();
        for (ExerciseAppearanceRow row : rows) {
            bySessionExercise.put(row.sessionExerciseId, row);
        }
        List<ExerciseAppearance> appearances = new ArrayList<>(rows.size());
        // Mapped by the same code the active screen uses, so a drop-set is nested into its set
        // here exactly as it is there - and counted once, as ADR-0037 requires.
        for (SessionExercise exercise : SessionMapper.toExercises(dao.findSetsOfExercise(exerciseId),
                null)) {
            ExerciseAppearanceRow row = bySessionExercise.get(exercise.id());
            if (row == null) {
                // Cannot happen: both queries filter the same sessions. Skipped rather than
                // guessed at, because an appearance with no day cannot be placed on a chart.
                continue;
            }
            appearances.add(new ExerciseAppearance(row.sessionId, row.localDate, row.startedAt,
                    exercise));
        }
        return ExerciseProgress.of(exerciseId, nameOf(name, appearances), appearances);
    }

    /**
     * The library's name, or the last snapshot's when the library no longer has one.
     *
     * <p>An exercise can leave the library while its history stays - the history is immutable, and
     * nothing is ever removed from it. A screen with a blank title would be the app losing the
     * name of work the person actually did, so the most recent snapshot answers instead. Only the
     * title: every number on the screen still comes from the snapshot of its own session.
     */
    private static String nameOf(String libraryName, List<ExerciseAppearance> appearances) {
        if (!libraryName.isEmpty() || appearances.isEmpty()) {
            return libraryName;
        }
        return appearances.get(appearances.size() - 1).exercise().name();
    }

    /**
     * @param points filled with one entry per session, oldest first - the chart of PRG-01. Built
     *               here rather than in the domain because the day a session was LIVED on is on
     *               the row, and a chart labelled from the device's zone would move the past
     *               every time the phone travels
     */
    private PeriodStatistics statisticsOf(List<String> sessionIds, String from, String to,
                                          List<PeriodSession> points) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            return PeriodStatistics.EMPTY;
        }
        List<ActiveSession> sessions = new ArrayList<>(sessionIds.size());
        for (String id : sessionIds) {
            SessionHeaderRow header = dao.findHeader(id);
            if (header == null) {
                // Removed between the id list and this read. Skipping it is right: it is no
                // longer a session, and the next emission will arrive without it anyway.
                continue;
            }
            ActiveSession session = SessionMapper.toSession(header, dao.findRows(id), null);
            sessions.add(session);
            points.add(PeriodSession.of(header.localDate, session));
        }
        return PeriodStatistics.of(sessions, toGroups(dao.findMuscleGroupsBetween(from, to)));
    }

    private static List<ExerciseMuscleGroup> toGroups(List<ExerciseMuscleGroupRow> rows) {
        List<ExerciseMuscleGroup> groups = new ArrayList<>(rows == null ? 0 : rows.size());
        if (rows != null) {
            for (ExerciseMuscleGroupRow row : rows) {
                groups.add(new ExerciseMuscleGroup(row.exerciseId, row.muscleGroupId, row.name,
                        row.sortOrder, row.role));
            }
        }
        return groups;
    }
}

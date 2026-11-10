package io.github.thiagojosetj.gym.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import io.github.thiagojosetj.gym.core.AppExecutors;
import io.github.thiagojosetj.gym.data.local.AppDatabase;
import io.github.thiagojosetj.gym.data.local.dao.SessionDao;
import io.github.thiagojosetj.gym.data.local.row.ExerciseMuscleGroupRow;
import io.github.thiagojosetj.gym.data.local.row.SessionHeaderRow;
import io.github.thiagojosetj.gym.data.local.row.TrainedDayBoundsRow;
import io.github.thiagojosetj.gym.domain.progress.ExerciseMuscleGroup;
import io.github.thiagojosetj.gym.domain.progress.PeriodStatistics;
import io.github.thiagojosetj.gym.domain.progress.ProgressSnapshot;
import io.github.thiagojosetj.gym.domain.progress.TrainingPeriod;
import io.github.thiagojosetj.gym.domain.session.ActiveSession;

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
    private final AppExecutors executors;

    public ProgressRepository(AppDatabase database, AppExecutors executors) {
        this.dao = database.sessionDao();
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
        return new ProgressSnapshot(statisticsOf(sessionIds, from, to),
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

    private PeriodStatistics statisticsOf(List<String> sessionIds, String from, String to) {
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
            sessions.add(SessionMapper.toSession(header, dao.findRows(id), null));
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

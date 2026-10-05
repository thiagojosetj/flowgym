package io.github.thiagojosetj.gym.data.repository;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import io.github.thiagojosetj.gym.core.AppExecutors;
import io.github.thiagojosetj.gym.data.local.AppDatabase;
import io.github.thiagojosetj.gym.data.local.dao.SessionDao;
import io.github.thiagojosetj.gym.data.local.entity.WorkoutSessionEntity;
import io.github.thiagojosetj.gym.data.local.row.SessionHeaderRow;
import io.github.thiagojosetj.gym.data.local.row.SessionHistoryRow;
import io.github.thiagojosetj.gym.domain.session.ActiveSession;
import io.github.thiagojosetj.gym.domain.session.SessionClock;
import io.github.thiagojosetj.gym.domain.session.SessionDetail;
import io.github.thiagojosetj.gym.domain.session.SessionExercise;
import io.github.thiagojosetj.gym.domain.session.SessionHistoryEntry;
import io.github.thiagojosetj.gym.domain.session.SessionStatus;
import io.github.thiagojosetj.gym.domain.session.SessionSummary;

/**
 * Reads finished sessions (PRODUCT_SPEC HIS-01, HIS-03 and HIS-04).
 *
 * <p>History is immutable, and the one write here does not make it otherwise: {@link #delete} takes
 * a session away WHOLE and rewrites nothing it recorded. "Immutable" means a session's numbers are
 * never edited after the fact, not that a workout logged by mistake has to be lived with for ever
 * (ADR-0041).
 *
 * <p>Two different shapes for two different questions, on purpose:
 *
 * <ul>
 *   <li>The <b>list</b> is observed, because a session finishing on another screen must make it
 *       appear. It is one query and one {@code map}, never several sources combined - a screen
 *       assembled from two queries renders whichever answered first, which is how this project
 *       already shipped three bugs. The calendar (HIS-02) obeys the same rule the only way that
 *       actually holds: {@link #observeTrainedDates()} is not a second query but a {@code map} of
 *       the very LiveData the list is made of, so the grid and the rows below it can only ever
 *       come from one emission. A {@code SELECT DISTINCT local_date} of its own would have been
 *       cheaper to read and would have put the two back in a race.</li>
 *   <li>A <b>session</b> is loaded once, not observed, because a finished session cannot change.
 *       There is no second emission to wait for, so there is no half-built state to render.</li>
 * </ul>
 */
public final class HistoryRepository {

    private final SessionDao dao;
    private final AppExecutors executors;
    /** Injected rather than System.currentTimeMillis, so a test can say when a removal happened. */
    private final Clock clock;
    /**
     * The one query the history screen runs. Held as a field, not rebuilt per call, because
     * everything else that screen shows is mapped off this same instance - see the class note.
     */
    private final LiveData<List<SessionHistoryEntry>> history;
    private final LiveData<Set<LocalDate>> trainedDates;

    public HistoryRepository(AppDatabase database, AppExecutors executors, Clock clock) {
        this.dao = database.sessionDao();
        this.executors = executors;
        this.clock = clock;
        // Nothing touches the database here: a Room LiveData runs its query the first time it is
        // observed, so building the chain up front costs an object and no disk.
        this.history = Transformations.map(dao.observeCompletedSessions(),
                HistoryRepository::toEntries);
        this.trainedDates = Transformations.map(history, HistoryRepository::toDates);
    }

    /** Every finished session, newest first. Empty while nothing has been performed yet. */
    public LiveData<List<SessionHistoryEntry>> observeHistory() {
        return history;
    }

    /**
     * Everything one session's screen shows, built from that session's own snapshots.
     *
     * @param onError receives {@link ActiveSessionRepository.SessionNotFoundException} when the id
     *                is unknown or names a session that was not completed
     */
    public void loadDetail(String sessionId, Consumer<SessionDetail> onResult,
                           Consumer<Throwable> onError) {
        executors.runOnDisk(() -> detailOf(sessionId), onResult, onError);
    }

    /**
     * Takes a finished session out of the history.
     *
     * <p>Soft, like a discard: the row stays so the removal can be synced, and every read already
     * hides it. The caller is expected to have asked first - this method does not confirm anything.
     *
     * @param onDone receives true when a session was removed, false when there was nothing to
     *               remove (unknown id, another user's, or already gone). Said rather than thrown:
     *               deleting twice is not an error, and the screen has nothing different to do.
     */
    public void delete(String sessionId, Consumer<Boolean> onDone, Consumer<Throwable> onError) {
        executors.runOnDisk(() -> dao.softDeleteSession(sessionId, clock.millis()) > 0,
                onDone, onError);
    }

    /**
     * The days with at least one finished session (PRODUCT_SPEC HIS-02).
     *
     * <p>Parsed from the stored {@code local_date}, which is the day as it was lived. A row whose
     * date cannot be parsed is dropped rather than guessed at: a square on the wrong day would be
     * a quiet lie, and there is nothing to guess from.
     */
    public LiveData<Set<LocalDate>> observeTrainedDates() {
        return trainedDates;
    }

    private static Set<LocalDate> toDates(List<SessionHistoryEntry> entries) {
        Set<LocalDate> dates = new LinkedHashSet<>();
        if (entries != null) {
            for (SessionHistoryEntry entry : entries) {
                LocalDate date = toDate(entry.localDate());
                if (date != null) {
                    dates.add(date);
                }
            }
        }
        return Collections.unmodifiableSet(dates);
    }

    @Nullable
    private static LocalDate toDate(@Nullable String stored) {
        if (stored == null || stored.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(stored);
        } catch (DateTimeParseException malformed) {
            return null;
        }
    }

    /** The lowest and highest a session can be rated (PRODUCT_SPEC HIS-06). */
    public static final int MIN_RATING = 1;
    public static final int MAX_RATING = 5;

    /**
     * Records how the session felt, 1 to 5, or clears it when given null.
     *
     * <p>A write on a finished session, and deliberately not a breach of immutability: the rating
     * is the person's own commentary, not a measurement. Nothing the session recorded is touched
     * (ADR-0041).
     *
     * @throws IllegalArgumentException for a value outside 1..5. A rating that is not on the scale
     *                                  is not a rating, and storing it would put a number on a
     *                                  screen that no scale explains.
     */
    public void rate(String sessionId, @Nullable Integer rating, Consumer<Boolean> onDone,
                     Consumer<Throwable> onError) {
        if (rating != null && (rating < MIN_RATING || rating > MAX_RATING)) {
            throw new IllegalArgumentException("Rating out of 1..5: " + rating);
        }
        executors.runOnDisk(() -> dao.rateSession(sessionId, rating, clock.millis()) > 0,
                onDone, onError);
    }

    private SessionDetail detailOf(String sessionId) throws Exception {
        WorkoutSessionEntity entity = dao.findSession(sessionId);
        SessionHeaderRow header = dao.findHeader(sessionId);
        if (entity == null || header == null || entity.status != SessionStatus.COMPLETED) {
            // A running or discarded session has no history screen: it is not a result yet, or it
            // was deliberately not one. Better to say so than to render a half-truth.
            throw new ActiveSessionRepository.SessionNotFoundException(sessionId);
        }
        ActiveSession session = SessionMapper.toSession(header, dao.findRows(sessionId),
                dao.findPreviousSets(sessionId));

        SessionSummary previousSummary = null;
        List<SessionExercise> previousExercises = null;
        long previousStartedAt = 0L;
        String previousTimeZone = entity.timeZone;
        // A session started from a template that was deleted since keeps its templateId, so the
        // comparison still works; a session with no template has nothing to compare against.
        if (entity.templateId != null) {
            String previousId = dao.findPreviousSessionOfTemplate(entity.templateId, entity.startedAt);
            if (previousId != null) {
                SessionHeaderRow previousHeader = dao.findHeader(previousId);
                WorkoutSessionEntity previousEntity = dao.findSession(previousId);
                if (previousHeader != null && previousEntity != null) {
                    // The previous session's own sets, not this one's: volume depends on that
                    // session's snapshot of load basis and laterality, which may have differed.
                    ActiveSession previous = SessionMapper.toSession(previousHeader,
                            dao.findRows(previousId), null);
                    SessionClock clock = previous.header().clock();
                    long end = clock.endedAt() == null ? clock.startedAt() : clock.endedAt();
                    previousSummary = SessionSummary.of(previous, end);
                    // The same read, not another one: the exercise-by-exercise comparison
                    // (HIS-04) needs that session's own snapshots, and they are already here.
                    previousExercises = previous.exercises();
                    previousStartedAt = previousHeader.startedAt;
                    // That session's OWN zone. Naming its day in this session's zone is how the
                    // summary ends up claiming a day the workout did not happen on - and
                    // contradicting the history list, which gets this right per row. It costs one
                    // extra query rather than a column on the row the active screen also reads.
                    previousTimeZone = previousEntity.timeZone;
                }
            }
        }
        return SessionDetail.of(session, entity.localDate, entity.timeZone, entity.rating,
                previousSummary, previousExercises, previousStartedAt, previousTimeZone);
    }

    private static List<SessionHistoryEntry> toEntries(List<SessionHistoryRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<SessionHistoryEntry> entries = new ArrayList<>(rows.size());
        for (SessionHistoryRow row : rows) {
            // Same clamp the active screen goes through: a device clock moved backwards shows a
            // zero-length session instead of throwing while the list is being drawn.
            SessionClock clock = SessionMapper.toClock(row.startedAt, row.endedAt,
                    row.closedPausedMs, null);
            long end = clock.endedAt() == null ? clock.startedAt() : clock.endedAt();
            entries.add(new SessionHistoryEntry(row.id, row.name, row.localDate, row.timeZone,
                    row.startedAt, clock.totalMs(end), clock.effectiveMs(end), row.exerciseCount,
                    row.performedSetCount));
        }
        return Collections.unmodifiableList(entries);
    }
}

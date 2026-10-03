package io.github.thiagojosetj.gym.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import java.time.Clock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
 *       already shipped three bugs.</li>
 *   <li>A <b>session</b> is loaded once, not observed, because a finished session cannot change.
 *       There is no second emission to wait for, so there is no half-built state to render.</li>
 * </ul>
 */
public final class HistoryRepository {

    private final SessionDao dao;
    private final AppExecutors executors;
    /** Injected rather than System.currentTimeMillis, so a test can say when a removal happened. */
    private final Clock clock;

    public HistoryRepository(AppDatabase database, AppExecutors executors, Clock clock) {
        this.dao = database.sessionDao();
        this.executors = executors;
        this.clock = clock;
    }

    /** Every finished session, newest first. Empty while nothing has been performed yet. */
    public LiveData<List<SessionHistoryEntry>> observeHistory() {
        return Transformations.map(dao.observeCompletedSessions(), HistoryRepository::toEntries);
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
                previousSummary, previousStartedAt, previousTimeZone);
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

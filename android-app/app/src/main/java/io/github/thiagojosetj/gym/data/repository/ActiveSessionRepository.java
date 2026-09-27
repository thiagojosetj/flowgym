package io.github.thiagojosetj.gym.data.repository;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.Transformations;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import io.github.thiagojosetj.gym.core.AppExecutors;
import io.github.thiagojosetj.gym.data.local.AppDatabase;
import io.github.thiagojosetj.gym.data.local.dao.SessionDao;
import io.github.thiagojosetj.gym.data.local.dao.TemplateDao;
import io.github.thiagojosetj.gym.data.local.entity.SessionExerciseEntity;
import io.github.thiagojosetj.gym.data.local.entity.SessionPauseEntity;
import io.github.thiagojosetj.gym.data.local.entity.SetLogEntity;
import io.github.thiagojosetj.gym.data.local.entity.SyncStatus;
import io.github.thiagojosetj.gym.data.local.entity.TemplateSetEntity;
import io.github.thiagojosetj.gym.data.local.entity.WorkoutSessionEntity;
import io.github.thiagojosetj.gym.data.local.entity.WorkoutTemplateEntity;
import io.github.thiagojosetj.gym.data.local.row.ActiveSetRow;
import io.github.thiagojosetj.gym.data.local.row.PreviousSetRow;
import io.github.thiagojosetj.gym.data.local.row.TemplateExerciseRow;
import io.github.thiagojosetj.gym.domain.session.ActiveSession;
import io.github.thiagojosetj.gym.domain.session.FinishReview;
import io.github.thiagojosetj.gym.domain.session.RestTimer;
import io.github.thiagojosetj.gym.domain.session.SessionHeader;
import io.github.thiagojosetj.gym.domain.session.SessionStatus;
import io.github.thiagojosetj.gym.domain.session.SessionSummary;
import io.github.thiagojosetj.gym.domain.session.SetValues;
import io.github.thiagojosetj.gym.domain.util.IdGenerator;

/**
 * The workout in progress. Every user action is written immediately, in one transaction, on the
 * single disk thread: there is no draft kept in memory that a killed process could take with it
 * (PRODUCT_SPEC principle 2, ADR-0031).
 *
 * <p>Reads go the other way: the screen observes the database and renders what is stored, so it can
 * never show a set that was not persisted.
 */
public final class ActiveSessionRepository {

    /** Only one session may be in progress; starting a second one is a bug, not a user error. */
    public static final class ActiveSessionExistsException extends Exception {
        private final String sessionId;

        ActiveSessionExistsException(String sessionId) {
            super("A session is already active: " + sessionId);
            this.sessionId = sessionId;
        }

        public String sessionId() {
            return sessionId;
        }
    }

    public static final class SessionNotFoundException extends Exception {
        SessionNotFoundException(String sessionId) {
            super("Session not found: " + sessionId);
        }
    }

    private final AppDatabase database;
    private final SessionDao dao;
    private final TemplateDao templates;
    private final UserRepository users;
    private final AppExecutors executors;
    private final Clock clock;
    private final ZoneId zone;
    private final IdGenerator ids;

    public ActiveSessionRepository(AppDatabase database, UserRepository users, AppExecutors executors,
                                   Clock clock, ZoneId zone, IdGenerator ids) {
        this.database = database;
        this.dao = database.sessionDao();
        this.templates = database.templateDao();
        this.users = users;
        this.executors = executors;
        this.clock = clock;
        this.zone = zone;
        this.ids = ids;
    }

    // ------------------------------------------------------------------ reads

    /**
     * The session in progress, or null when there is none. This is the only source the recovery
     * banner needs: no flag in preferences, no running service (ACT-08).
     */
    public LiveData<SessionHeader> observeActiveHeader() {
        MediatorLiveData<SessionHeader> result = new MediatorLiveData<>();
        LiveData<WorkoutSessionEntity> session = dao.observeActive();
        result.addSource(session, entity -> {
            if (entity == null) {
                result.setValue(null);
            } else {
                // The pauses of the active session are read on demand; the banner only needs the name.
                result.setValue(SessionMapper.toHeader(entity, Collections.emptyList()));
            }
        });
        return result;
    }

    public LiveData<Boolean> observeHasActiveSession() {
        return Transformations.map(dao.observeActive(), session -> session != null);
    }

    /**
     * The whole session as the screen draws it. Four sources are merged because they change at
     * different moments: the session row (status, rest), its pauses, its sets, and the previous
     * session's sets (immutable history).
     */
    public LiveData<ActiveSession> observeSession(String sessionId) {
        MediatorLiveData<ActiveSession> result = new MediatorLiveData<>();
        LiveData<WorkoutSessionEntity> sessionSource = dao.observeSession(sessionId);
        LiveData<List<SessionPauseEntity>> pauseSource = dao.observePauses(sessionId);
        LiveData<List<ActiveSetRow>> rowSource = dao.observeRows(sessionId);
        LiveData<List<PreviousSetRow>> previousSource = dao.observePreviousSets(sessionId);

        Runnable combine = () -> {
            WorkoutSessionEntity session = sessionSource.getValue();
            if (session == null) {
                result.setValue(null);
                return;
            }
            result.setValue(SessionMapper.toSession(session, pauseSource.getValue(),
                    rowSource.getValue(), previousSource.getValue()));
        };
        result.addSource(sessionSource, ignored -> combine.run());
        result.addSource(pauseSource, ignored -> combine.run());
        result.addSource(rowSource, ignored -> combine.run());
        result.addSource(previousSource, ignored -> combine.run());
        return result;
    }

    /** One-shot read, for the service and for tests. */
    public void loadSession(String sessionId, Consumer<ActiveSession> onResult, Consumer<Throwable> onError) {
        executors.runOnDisk(() -> {
            WorkoutSessionEntity session = dao.findSession(sessionId);
            if (session == null) {
                throw new SessionNotFoundException(sessionId);
            }
            return SessionMapper.toSession(session, dao.findPauses(sessionId), dao.findRows(sessionId),
                    dao.findPreviousSets(sessionId));
        }, onResult, onError);
    }

    // ------------------------------------------------------------------ lifecycle

    /**
     * Starts a session from a template, copying everything the history will need (docs/DATABASE.md
     * section 4). One transaction: a session that exists is always complete, never half-snapshotted.
     */
    public void startFromTemplate(String templateId, Consumer<String> onStarted, Consumer<Throwable> onError) {
        executors.runOnDisk(() -> {
            long now = clock.millis();
            WorkoutTemplateEntity template = templates.findById(templateId);
            if (template == null) {
                throw new TemplateRepository.TemplateNotFoundException(templateId);
            }
            List<TemplateExerciseRow> exercises = templates.findExercises(templateId);
            List<TemplateSetEntity> plannedSets = templates.findSets(templateId);
            String ownerId = users.requireCurrentUserId();
            String[] created = new String[1];
            database.runInTransaction(() -> {
                // Check and insert inside the transaction, on the single disk thread: that is what
                // makes "only one active session" true without a database constraint (ADR-0032).
                WorkoutSessionEntity active = dao.findActive();
                if (active != null) {
                    throw new IllegalStateException(new ActiveSessionExistsException(active.id));
                }
                WorkoutSessionEntity session = newSession(template, ownerId, now);
                dao.insertSession(session);

                List<SessionExerciseEntity> sessionExercises = new ArrayList<>(exercises.size());
                List<SetLogEntity> sets = new ArrayList<>(plannedSets.size());
                for (TemplateExerciseRow exercise : exercises) {
                    SessionExerciseEntity entity = newSessionExercise(session.id, exercise);
                    sessionExercises.add(entity);
                    int position = 0;
                    for (TemplateSetEntity planned : plannedSets) {
                        if (!planned.templateExerciseId.equals(exercise.id)) {
                            continue;
                        }
                        sets.add(newSetLog(entity, planned, position++));
                    }
                }
                dao.insertExercises(sessionExercises);
                dao.insertSets(sets);
                created[0] = session.id;
            });
            return created[0];
        }, onStarted, error -> onError.accept(unwrap(error)));
    }

    /** Pauses the session: the general timer and the rest both stop (PRODUCT_SPEC section 7). */
    public void pause(String sessionId, Runnable onDone, Consumer<Throwable> onError) {
        write(sessionId, (session, now) -> {
            if (session.status != SessionStatus.ACTIVE || dao.findOpenPause(sessionId) != null) {
                return; // already paused: pausing twice would corrupt the timeline
            }
            SessionPauseEntity pause = new SessionPauseEntity();
            pause.id = ids.newId();
            pause.sessionId = sessionId;
            pause.startedAt = now;
            dao.insertPause(pause);

            if (session.restEndsAt != null) {
                session.restRemainingMsWhenPaused = RestTimer.remainingMs(session.restEndsAt, now);
                session.restEndsAt = null;
            }
        }, onDone, onError);
    }

    public void resume(String sessionId, Runnable onDone, Consumer<Throwable> onError) {
        write(sessionId, (session, now) -> {
            SessionPauseEntity open = dao.findOpenPause(sessionId);
            if (open == null) {
                return;
            }
            dao.closeOpenPauses(sessionId, now);
            session.totalPausedMs = dao.sumClosedPausedMs(sessionId);
            if (session.restRemainingMsWhenPaused != null) {
                session.restEndsAt = now + Math.max(0L, session.restRemainingMsWhenPaused);
                session.restRemainingMsWhenPaused = null;
            }
        }, onDone, onError);
    }

    /**
     * Finishes the session. Sets that were filled in but not confirmed are completed; empty and
     * half-filled ones are marked as skipped with their values kept - nothing is deleted
     * (PRODUCT_SPEC section 8). The paused total is recomputed from the intervals instead of trusting
     * the cache.
     */
    public void finish(String sessionId, Consumer<SessionSummary> onFinished, Consumer<Throwable> onError) {
        executors.runOnDisk(() -> {
            long now = clock.millis();
            SessionSummary[] summary = new SessionSummary[1];
            database.runInTransaction(() -> {
                WorkoutSessionEntity session = requireSession(sessionId);
                ActiveSession snapshot = SessionMapper.toSession(session, dao.findPauses(sessionId),
                        dao.findRows(sessionId), dao.findPreviousSets(sessionId));
                FinishReview review = snapshot.finishReview();
                if (!review.setIdsToComplete().isEmpty()) {
                    dao.completeSets(review.setIdsToComplete(), now);
                }
                if (!review.setIdsToSkip().isEmpty()) {
                    dao.skipSets(review.setIdsToSkip());
                }
                dao.closeOpenPauses(sessionId, now);
                session.totalPausedMs = dao.sumClosedPausedMs(sessionId);
                session.endedAt = now;
                session.status = SessionStatus.COMPLETED;
                clearRest(session);
                touch(session, now);
                dao.updateSession(session);

                // Re-read so the summary describes what is now in the database, not what was planned.
                summary[0] = SessionSummary.of(SessionMapper.toSession(requireSession(sessionId),
                        dao.findPauses(sessionId), dao.findRows(sessionId),
                        dao.findPreviousSets(sessionId)), now);
            });
            return summary[0];
        }, onFinished, error -> onError.accept(unwrap(error)));
    }

    /** Throws the session away on purpose. The row stays so the discard can be synced. */
    public void discard(String sessionId, Runnable onDone, Consumer<Throwable> onError) {
        write(sessionId, (session, now) -> {
            dao.closeOpenPauses(sessionId, now);
            session.totalPausedMs = dao.sumClosedPausedMs(sessionId);
            session.endedAt = now;
            session.status = SessionStatus.DISCARDED;
            clearRest(session);
        }, onDone, onError);
    }

    // ------------------------------------------------------------------ sets

    /**
     * Saves what is typed without confirming it. Called when a field loses focus and when the screen
     * stops - not on every keystroke - and ignored if the set was confirmed meanwhile.
     */
    public void saveTypedValues(String setLogId, SetValues values) {
        executors.diskIO().execute(() -> dao.updateTypedValues(setLogId,
                grams(values), values.reps(), values.repsLeft(), values.repsRight(),
                values.durationSeconds()));
    }

    /**
     * Confirms a set: the values become a result and the rest starts, in the same transaction. A set
     * confirmed here is on disk before the method returns (PRODUCT_SPEC principle 2).
     */
    public void confirmSet(String sessionId, String setLogId, SetValues values, Runnable onDone,
                           Consumer<Throwable> onError) {
        write(sessionId, (session, now) -> {
            SetLogEntity set = dao.findSet(setLogId);
            if (set == null) {
                throw new IllegalStateException("Set not found: " + setLogId);
            }
            dao.completeSet(setLogId, grams(values), values.reps(), values.repsLeft(),
                    values.repsRight(), values.durationSeconds(), now);
            startRest(session, set, now);
        }, onDone, onError);
    }

    /** Undoes a confirmation. The typed values stay; the rest of that set is cleared. */
    public void unconfirmSet(String sessionId, String setLogId, Runnable onDone, Consumer<Throwable> onError) {
        write(sessionId, (session, now) -> {
            dao.uncompleteSet(setLogId);
            if (setLogId.equals(session.restSetLogId)) {
                clearRest(session);
            }
        }, onDone, onError);
    }

    /** Adds one more set, planned like the last one of that exercise. */
    public void addSet(String sessionId, String sessionExerciseId, Runnable onDone,
                       Consumer<Throwable> onError) {
        write(sessionId, (session, now) -> {
            List<ActiveSetRow> rows = dao.findRows(sessionId);
            ActiveSetRow last = null;
            for (ActiveSetRow row : rows) {
                if (row.sessionExerciseId.equals(sessionExerciseId) && row.setId != null) {
                    last = row;
                }
            }
            SetLogEntity set = new SetLogEntity();
            set.id = ids.newId();
            set.sessionExerciseId = sessionExerciseId;
            set.position = dao.maxSetPosition(sessionExerciseId) + 1;
            if (last != null) {
                set.plannedRepsMin = last.plannedRepsMin;
                set.plannedRepsMax = last.plannedRepsMax;
                set.plannedWeightGrams = last.plannedWeightGrams;
                set.plannedDurationSeconds = last.plannedDurationSeconds;
                set.plannedRestSeconds = last.plannedRestSeconds == null ? 0 : last.plannedRestSeconds;
            }
            dao.insertSet(set);
        }, onDone, onError);
    }

    /** Removes a set. The last remaining set of an exercise is kept: the plan would make no sense. */
    public void removeSet(String sessionId, String sessionExerciseId, String setLogId, Runnable onDone,
                          Consumer<Throwable> onError) {
        write(sessionId, (session, now) -> {
            if (dao.countSets(sessionExerciseId) <= 1) {
                return;
            }
            SetLogEntity set = dao.findSet(setLogId);
            if (set == null) {
                return;
            }
            dao.deleteSet(setLogId);
            dao.shiftSetsAfter(sessionExerciseId, set.position);
            if (setLogId.equals(session.restSetLogId)) {
                clearRest(session);
            }
        }, onDone, onError);
    }

    public void setSetTechnique(String sessionId, String setLogId, @Nullable String techniqueId,
                                Runnable onDone, Consumer<Throwable> onError) {
        write(sessionId, (session, now) -> dao.updateSetTechnique(setLogId, techniqueId), onDone, onError);
    }

    public void setSetNotes(String sessionId, String setLogId, @Nullable String notes, Runnable onDone,
                            Consumer<Throwable> onError) {
        write(sessionId, (session, now) -> dao.updateSetNotes(setLogId, notes), onDone, onError);
    }

    public void setExerciseNotes(String sessionId, String sessionExerciseId, @Nullable String notes,
                                 Runnable onDone, Consumer<Throwable> onError) {
        write(sessionId, (session, now) -> dao.updateExerciseNotes(sessionExerciseId, notes), onDone, onError);
    }

    // ------------------------------------------------------------------ rest

    /** Moves the end of the rest by a number of seconds (+15, +30, -15 in the UI). */
    public void adjustRest(String sessionId, int deltaSeconds, Runnable onDone, Consumer<Throwable> onError) {
        write(sessionId, (session, now) -> {
            if (session.restSetLogId == null) {
                return;
            }
            if (session.restRemainingMsWhenPaused != null) {
                long adjusted = session.restRemainingMsWhenPaused + deltaSeconds * 1000L;
                session.restRemainingMsWhenPaused =
                        Math.max(0L, Math.min(adjusted, RestTimer.MAX_REST_SECONDS * 1000L));
                return;
            }
            if (session.restEndsAt != null) {
                session.restEndsAt = RestTimer.adjusted(session.restEndsAt, deltaSeconds, now);
            }
        }, onDone, onError);
    }

    /** Ends the rest now, without waiting for it to run out. */
    public void skipRest(String sessionId, Runnable onDone, Consumer<Throwable> onError) {
        write(sessionId, (session, now) -> clearRest(session), onDone, onError);
    }

    // ------------------------------------------------------------------ internals

    /** A change to the session row plus whatever else the action needs, in one transaction. */
    private interface SessionWrite {
        void apply(WorkoutSessionEntity session, long now) throws Exception;
    }

    private void write(String sessionId, SessionWrite action, Runnable onDone, Consumer<Throwable> onError) {
        executors.runOnDisk(() -> {
            long now = clock.millis();
            database.runInTransaction(() -> {
                try {
                    WorkoutSessionEntity session = requireSession(sessionId);
                    action.apply(session, now);
                    touch(session, now);
                    dao.updateSession(session);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            return null;
        }, ignored -> onDone.run(), error -> onError.accept(unwrap(error)));
    }

    private WorkoutSessionEntity requireSession(String sessionId) {
        WorkoutSessionEntity session = dao.findSession(sessionId);
        if (session == null) {
            throw new IllegalStateException(new SessionNotFoundException(sessionId));
        }
        return session;
    }

    private void startRest(WorkoutSessionEntity session, SetLogEntity set, long now) {
        int restSeconds = set.plannedRestSeconds;
        if (restSeconds <= 0) {
            clearRest(session);
            return;
        }
        session.restSetLogId = set.id;
        if (dao.findOpenPause(session.id) != null) {
            // Resting while paused: keep the remaining time frozen until the user resumes.
            session.restEndsAt = null;
            session.restRemainingMsWhenPaused = restSeconds * 1000L;
        } else {
            session.restEndsAt = RestTimer.endsAt(now, restSeconds);
            session.restRemainingMsWhenPaused = null;
        }
    }

    private static void clearRest(WorkoutSessionEntity session) {
        session.restSetLogId = null;
        session.restEndsAt = null;
        session.restRemainingMsWhenPaused = null;
    }

    private static void touch(WorkoutSessionEntity session, long now) {
        session.updatedAt = now;
        session.syncStatus = SyncStatus.PENDING;
    }

    private WorkoutSessionEntity newSession(WorkoutTemplateEntity template, String ownerId, long now) {
        WorkoutSessionEntity session = new WorkoutSessionEntity();
        session.id = ids.newId();
        session.ownerUserId = ownerId;
        session.templateId = template.id;
        session.name = template.name;
        session.status = SessionStatus.ACTIVE;
        session.startedAt = now;
        session.timeZone = zone.getId();
        session.localDate = LocalDate.ofInstant(Instant.ofEpochMilli(now), zone).toString();
        session.createdAt = now;
        session.updatedAt = now;
        session.syncStatus = SyncStatus.PENDING;
        return session;
    }

    private SessionExerciseEntity newSessionExercise(String sessionId, TemplateExerciseRow row) {
        SessionExerciseEntity entity = new SessionExerciseEntity();
        entity.id = ids.newId();
        entity.sessionId = sessionId;
        entity.exerciseId = row.exercise.id;
        entity.templateExerciseId = row.id;
        entity.position = row.position;
        entity.exerciseName = row.exercise.name;
        entity.trackingType = row.exercise.trackingType;
        entity.loadBasis = row.exercise.loadBasis;
        entity.implementCount = row.exercise.implementCount;
        entity.laterality = row.exercise.laterality;
        entity.sideMode = row.sideMode;
        entity.restSeconds = row.restSeconds;
        entity.permanentNotes = row.notes;
        // Frozen here: "anterior" keeps showing what it showed on the day (PRODUCT_SPEC section 11).
        entity.previousSessionExerciseId = dao.findPreviousSessionExerciseId(row.exercise.id);
        return entity;
    }

    private SetLogEntity newSetLog(SessionExerciseEntity exercise, TemplateSetEntity planned, int position) {
        SetLogEntity set = new SetLogEntity();
        set.id = ids.newId();
        set.sessionExerciseId = exercise.id;
        set.position = position;
        set.techniqueId = planned.techniqueId;
        set.plannedRepsMin = planned.targetRepsMin;
        set.plannedRepsMax = planned.targetRepsMax;
        set.plannedWeightGrams = planned.targetWeightGrams;
        set.plannedDurationSeconds = planned.targetDurationSeconds;
        set.plannedRestSeconds = planned.restSeconds != null ? planned.restSeconds : exercise.restSeconds;
        return set;
    }

    @Nullable
    private static Long grams(SetValues values) {
        return values.weight() == null ? null : values.weight().grams();
    }

    /** Transactions can only throw unchecked, so the real cause is unwrapped for the caller. */
    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current instanceof IllegalStateException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}

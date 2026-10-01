package io.github.thiagojosetj.gym.data.repository;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.Transformations;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.function.Supplier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import io.github.thiagojosetj.gym.core.AppExecutors;
import io.github.thiagojosetj.gym.data.local.AppDatabase;
import io.github.thiagojosetj.gym.data.local.dao.SessionDao;
import io.github.thiagojosetj.gym.data.local.dao.TemplateDao;
import io.github.thiagojosetj.gym.data.local.entity.SessionExerciseEntity;
import io.github.thiagojosetj.gym.data.local.entity.SessionExerciseGroupEntity;
import io.github.thiagojosetj.gym.data.local.entity.SessionPauseEntity;
import io.github.thiagojosetj.gym.data.local.entity.SetLogEntity;
import io.github.thiagojosetj.gym.data.local.entity.SyncStatus;
import io.github.thiagojosetj.gym.data.local.entity.TemplateSetEntity;
import io.github.thiagojosetj.gym.data.local.entity.WorkoutSessionEntity;
import io.github.thiagojosetj.gym.data.local.entity.WorkoutTemplateEntity;
import io.github.thiagojosetj.gym.data.local.row.ActiveSetRow;
import io.github.thiagojosetj.gym.data.local.row.PreviousSetRow;
import io.github.thiagojosetj.gym.data.local.row.SessionHeaderRow;
import io.github.thiagojosetj.gym.data.local.row.TemplateExerciseRow;
import io.github.thiagojosetj.gym.data.local.row.TemplateGroupRow;
import io.github.thiagojosetj.gym.domain.session.ActiveSession;
import io.github.thiagojosetj.gym.domain.session.FinishReview;
import io.github.thiagojosetj.gym.domain.session.GroupRounds;
import io.github.thiagojosetj.gym.domain.session.RestTimer;
import io.github.thiagojosetj.gym.domain.session.SessionExercise;
import io.github.thiagojosetj.gym.domain.session.SessionGroup;
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
    private final Supplier<ZoneId> zone;
    private final IdGenerator ids;

    /**
     * @param zone resolved per session, not once per process: the app can stay in memory across a
     *             flight or a manual time-zone change, and {@code time_zone} must record the zone the
     *             workout was actually performed in.
     */
    public ActiveSessionRepository(AppDatabase database, UserRepository users, AppExecutors executors,
                                   Clock clock, Supplier<ZoneId> zone, IdGenerator ids) {
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
        return Transformations.map(dao.observeActiveHeader(),
                row -> row == null ? null : SessionMapper.toHeader(row));
    }

    public LiveData<Boolean> observeHasActiveSession() {
        return Transformations.map(dao.observeActiveHeader(), row -> row != null);
    }

    /**
     * The whole session as the screen draws it. Four sources are merged because they change at
     * different moments: the session row (status, rest), its pauses, its sets, and the previous
     * session's sets (immutable history).
     */
    public LiveData<ActiveSession> observeSession(String sessionId) {
        MediatorLiveData<ActiveSession> result = new MediatorLiveData<>();
        LiveData<SessionHeaderRow> headerSource = dao.observeHeader(sessionId);
        LiveData<List<ActiveSetRow>> rowSource = dao.observeRows(sessionId);
        LiveData<List<PreviousSetRow>> previousSource = dao.observePreviousSets(sessionId);

        // The three queries run on Room's own executor and can land in any order, so a missing
        // header must mean "not loaded yet" until it has actually delivered once. Publishing null
        // too early made the screen navigate away from a perfectly healthy session.
        boolean[] headerDelivered = {false};
        Runnable combine = () -> {
            SessionHeaderRow header = headerSource.getValue();
            if (header == null) {
                if (headerDelivered[0]) {
                    result.setValue(null); // the session really is gone
                }
                return;
            }
            result.setValue(SessionMapper.toSession(header, rowSource.getValue(),
                    previousSource.getValue()));
        };
        result.addSource(headerSource, ignored -> {
            headerDelivered[0] = true;
            combine.run();
        });
        result.addSource(rowSource, ignored -> combine.run());
        result.addSource(previousSource, ignored -> combine.run());
        return result;
    }

    /** One-shot read, for the service and for tests. */
    public void loadSession(String sessionId, Consumer<ActiveSession> onResult, Consumer<Throwable> onError) {
        executors.runOnDisk(() -> {
            SessionHeaderRow header = dao.findHeader(sessionId);
            if (header == null) {
                throw new SessionNotFoundException(sessionId);
            }
            return SessionMapper.toSession(header, dao.findRows(sessionId),
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
            List<TemplateGroupRow> plannedGroups = templates.findGroups(templateId);
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

                // The groups are copied, not referenced: from here on the session owns its group
                // (label, badge and rest), so editing or deleting the template's afterwards cannot
                // change what this session says happened.
                Map<String, SessionExerciseGroupEntity> groupCopies = new LinkedHashMap<>();
                for (TemplateGroupRow planned : plannedGroups) {
                    groupCopies.put(planned.id, newSessionGroup(session.id, planned));
                }
                // Before the exercises: each one points at its group, and the key is enforced.
                dao.insertExerciseGroups(new ArrayList<>(groupCopies.values()));

                List<SessionExerciseEntity> sessionExercises = new ArrayList<>(exercises.size());
                List<SetLogEntity> sets = new ArrayList<>(plannedSets.size());
                for (TemplateExerciseRow exercise : exercises) {
                    SessionExerciseEntity entity = newSessionExercise(session.id, exercise,
                            exercise.groupId == null ? null : groupCopies.get(exercise.groupId));
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
                if (session.status != SessionStatus.ACTIVE) {
                    // Already finished or discarded: re-finishing would move ended_at and inflate
                    // the duration. The summary is rebuilt from what is stored instead.
                    summary[0] = SessionSummary.of(SessionMapper.toSession(requireHeader(sessionId),
                            dao.findRows(sessionId), dao.findPreviousSets(sessionId)),
                            session.endedAt == null ? now : session.endedAt);
                    return;
                }
                ActiveSession snapshot = SessionMapper.toSession(requireHeader(sessionId),
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
                // Never before the start: a clock corrected backwards would otherwise store an
                // impossible session, and reading it back threw - which made the workout
                // unfinishable and, since only one may be active, blocked the next one too
                // (found in review, 28/09/2026).
                session.endedAt = Math.max(session.startedAt, now);
                session.status = SessionStatus.COMPLETED;
                clearRest(session);
                touch(session, now);
                dao.updateSession(session);

                // Re-read so the summary describes what is now in the database, not what was planned.
                summary[0] = SessionSummary.of(SessionMapper.toSession(requireHeader(sessionId),
                        dao.findRows(sessionId), dao.findPreviousSets(sessionId)), now);
            });
            return summary[0];
        }, onFinished, error -> onError.accept(unwrap(error)));
    }

    /** Throws the session away on purpose. The row stays so the discard can be synced. */
    public void discard(String sessionId, Runnable onDone, Consumer<Throwable> onError) {
        write(sessionId, (session, now) -> {
            if (session.status != SessionStatus.ACTIVE) {
                return;
            }
            dao.closeOpenPauses(sessionId, now);
            session.totalPausedMs = dao.sumClosedPausedMs(sessionId);
            session.endedAt = Math.max(session.startedAt, now);
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
                // The rows now include segments, which come right after their set. A segment has
                // no plan of its own (planned_* are null and its rest is 0), so copying "the last
                // row" after a drop-set would give the new set no plan and no rest at all.
                if (row.sessionExerciseId.equals(sessionExerciseId) && row.setId != null
                        && row.parentSetId == null) {
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

    /**
     * Removes a set, and its segments with it: the foreign key cascades. The last remaining set of
     * an exercise is kept: the plan would make no sense.
     */
    public void removeSet(String sessionId, String sessionExerciseId, String setLogId, Runnable onDone,
                          Consumer<Throwable> onError) {
        write(sessionId, (session, now) -> {
            if (dao.countSets(sessionExerciseId) <= 1) {
                return;
            }
            SetLogEntity set = dao.findSet(setLogId);
            // A segment is removed with removeSegment. Getting here with one would shift the SETS
            // after its position, and that position only orders it among its own siblings.
            if (set == null || set.parentSetId != null) {
                return;
            }
            dao.deleteSet(setLogId);
            dao.shiftSetsAfter(sessionExerciseId, set.position);
            if (setLogId.equals(session.restSetLogId)) {
                clearRest(session);
            }
        }, onDone, onError);
    }

    /**
     * Adds a drop (or a rest-pause resumption) to a set: a child row that belongs to it, not
     * another set (PRODUCT_SPEC section 9.1, ADR-0037). The set is the first step of a drop-set, so
     * three steps are one set row plus two segment rows, and the set count does not move.
     *
     * <p>The segment starts as not performed and with no plan: it is not something the template
     * asked for, it is a drop taken off the set above it. It also has no technique of its own. The
     * technique belongs to the set, and {@link #setSetTechnique} can change it at any time, so a
     * copy on every segment would keep saying "Drop-set" after the set stopped being one: two
     * definitions of one fact that can disagree. The volume rule already reads warm-up from the
     * set.
     *
     * <p>One level only. A segment under a segment is refused: nothing is created and the call
     * still reports done, like the other refusals here. The mapper, the volume rule and the screen
     * all read a single level, so a deeper row would be stored and then silently never counted.
     *
     * @param setLogId the SET receiving the segment; the new row takes the next position among the
     *                 segments of that set, counting from 0
     */
    public void addSegment(String sessionId, String setLogId, Runnable onDone,
                           Consumer<Throwable> onError) {
        write(sessionId, (session, now) -> {
            SetLogEntity parent = dao.findSet(setLogId);
            if (parent == null || parent.parentSetId != null) {
                return; // unknown, or already a segment
            }
            SetLogEntity segment = new SetLogEntity();
            segment.id = ids.newId();
            segment.sessionExerciseId = parent.sessionExerciseId;
            segment.parentSetId = parent.id;
            segment.position = dao.maxSegmentPosition(parent.id) + 1;
            // Everything else keeps the entity's defaults: no technique, no plan, rest 0, nothing
            // performed and status PENDING.
            dao.insertSet(segment);
        }, onDone, onError);
    }

    /**
     * Removes one segment and closes the gap among its siblings; the set it belonged to and its
     * other segments stay. A row that is not a segment is refused: {@link #removeSet} is for sets,
     * and deleting a set here would cascade to every segment it has.
     */
    public void removeSegment(String sessionId, String setLogId, Runnable onDone,
                              Consumer<Throwable> onError) {
        write(sessionId, (session, now) -> {
            SetLogEntity segment = dao.findSet(setLogId);
            if (segment == null || segment.parentSetId == null) {
                return; // unknown, or a set
            }
            dao.deleteSet(setLogId);
            dao.shiftSegmentsAfter(segment.parentSetId, segment.position);
            // rest_set_log_id has no foreign key: whoever deletes a row it may point at clears it.
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

    /**
     * Clears a rest that has run out. A rest expiring is the passing of an instant, not a write, so
     * without this nothing would re-emit: the bar would sit at zero and the notification would keep
     * counting down past it.
     *
     * @param setLogId the set the caller believes is resting; ignored if the rest has moved on
     */
    public void restFinished(String sessionId, String setLogId, Runnable onDone,
                             Consumer<Throwable> onError) {
        write(sessionId, (session, now) -> {
            if (!setLogId.equals(session.restSetLogId)) {
                return; // another set is resting now
            }
            if (session.restRemainingMsWhenPaused != null
                    || RestTimer.remainingMs(session.restEndsAt, now) > 0) {
                return; // frozen by a pause, or stretched with +30 s in the meantime
            }
            clearRest(session);
        }, onDone, onError);
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
                    if (session.status != SessionStatus.ACTIVE) {
                        // Finished or discarded means history: no set, note, rest or pause may be
                        // written to it, whatever screen is still open on top of it.
                        return;
                    }
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

    private SessionHeaderRow requireHeader(String sessionId) {
        SessionHeaderRow header = dao.findHeader(sessionId);
        if (header == null) {
            throw new IllegalStateException(new SessionNotFoundException(sessionId));
        }
        return header;
    }

    private WorkoutSessionEntity requireSession(String sessionId) {
        WorkoutSessionEntity session = dao.findSession(sessionId);
        if (session == null) {
            throw new IllegalStateException(new SessionNotFoundException(sessionId));
        }
        return session;
    }

    private void startRest(WorkoutSessionEntity session, SetLogEntity set, long now) {
        int restSeconds = restSecondsAfter(session.id, set);
        if (restSeconds <= 0) {
            // Nothing to wait for - and whatever rest was still counting down is over too: the user
            // has just done another set. The rest is one per SESSION, not one per exercise, so this
            // is also what ends the previous round's rest when the next round starts early.
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

    /**
     * The rest a just-confirmed set starts, in seconds; 0 means none.
     *
     * <p>An exercise that stands alone keeps what it always had: the rest planned for the set. In a
     * group the rest belongs to the ROUND (PRODUCT_SPEC section 6.3), so a set starts the group's
     * rest only when its confirmation is what ends the round. {@link GroupRounds} decides that, and
     * it must be asked AFTER the set was written, because the set being confirmed is one of the
     * things it reads. Whichever exercise is finished last starts the rest, so doing A2 before A1
     * still gets one.
     */
    private int restSecondsAfter(String sessionId, SetLogEntity set) {
        String groupId = dao.findGroupIdOfExercise(set.sessionExerciseId);
        if (groupId == null) {
            return set.plannedRestSeconds; // not in a group: exactly what it was before groups
        }
        if (set.parentSetId != null) {
            // A drop belongs to its set's round and is not one of its own (ADR-0037): GroupRounds
            // gives it no round, and it must not start one. Like any confirmation that starts no
            // rest, it ends the one that was running.
            return 0;
        }
        // The mapping the screen renders, so "which sets belong to which round" has one answer.
        ActiveSession snapshot = SessionMapper.toSession(requireHeader(sessionId),
                dao.findRows(sessionId), null);
        SessionExercise exercise = snapshot.exerciseById(set.sessionExerciseId);
        SessionGroup group = snapshot.groupOf(set.sessionExerciseId);
        if (exercise == null || group == null) {
            return 0;
        }
        int round = GroupRounds.roundOf(exercise, set.id);
        return GroupRounds.isRoundComplete(snapshot.exercisesOfGroup(group.id()), round)
                ? group.restAfterRoundSeconds()
                : 0;
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
        ZoneId current = zone.get();
        session.timeZone = current.getId();
        // Not LocalDate.ofInstant: that overload only exists from API 34 (ADR-0004).
        session.localDate = Instant.ofEpochMilli(now).atZone(current).toLocalDate().toString();
        session.createdAt = now;
        session.updatedAt = now;
        session.syncStatus = SyncStatus.PENDING;
        return session;
    }

    private SessionExerciseGroupEntity newSessionGroup(String sessionId, TemplateGroupRow row) {
        SessionExerciseGroupEntity entity = new SessionExerciseGroupEntity();
        entity.id = ids.newId();
        entity.sessionId = sessionId;
        entity.label = row.label;
        entity.techniqueId = row.techniqueId;
        // The badge is stored, not joined: a catalog edit must not rewrite a past session.
        entity.techniqueCode = row.techniqueCode;
        entity.restAfterRoundSeconds = row.restAfterRoundSeconds;
        entity.position = row.position;
        return entity;
    }

    private SessionExerciseEntity newSessionExercise(String sessionId, TemplateExerciseRow row,
                                                     @Nullable SessionExerciseGroupEntity group) {
        SessionExerciseEntity entity = new SessionExerciseEntity();
        entity.id = ids.newId();
        entity.sessionId = sessionId;
        entity.groupId = group == null ? null : group.id;
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

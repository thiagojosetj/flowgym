package io.github.thiagojosetj.gym.data.local.dao;

import static io.github.thiagojosetj.gym.data.local.entity.AppMetadataEntity.CURRENT_USER_ID_SQL;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import io.github.thiagojosetj.gym.data.local.entity.SessionExerciseEntity;
import io.github.thiagojosetj.gym.data.local.entity.SessionExerciseGroupEntity;
import io.github.thiagojosetj.gym.data.local.entity.SessionPauseEntity;
import io.github.thiagojosetj.gym.data.local.entity.SetLogEntity;
import io.github.thiagojosetj.gym.data.local.entity.WorkoutSessionEntity;
import io.github.thiagojosetj.gym.data.local.row.ActiveSetRow;
import io.github.thiagojosetj.gym.data.local.row.ExerciseMuscleGroupRow;
import io.github.thiagojosetj.gym.data.local.row.PreviousSetRow;
import io.github.thiagojosetj.gym.data.local.row.SessionHeaderRow;
import io.github.thiagojosetj.gym.data.local.row.SessionHistoryRow;
import io.github.thiagojosetj.gym.data.local.row.TrainedDayBoundsRow;

/**
 * Sessions of the current user. The current user is resolved inside the SQL (ADR-0006), so a query
 * can never leak another user's history.
 */
@Dao
public interface SessionDao {

    String MINE_SQL = "s.owner_user_id = " + CURRENT_USER_ID_SQL + " AND s.deleted_at IS NULL";

    /**
     * The exercises and sets of a session, in planned order. The previous session's values are NOT
     * joined here: they pair by ordinal among working sets (PRODUCT_SPEC section 11), which SQLite on
     * API 28 cannot express (window functions arrived later), so they come from
     * {@link #observePreviousSets(String)} and are paired in the mapper.
     *
     * <p>The group is the session's own snapshot ({@code session_exercise_group}), joined by its
     * primary key, so it adds columns and never rows: the ordering below, and the drop-set segments
     * the mapper nests, see exactly what they saw before groups existed.
     */
    String ACTIVE_ROWS_SQL =
            "SELECT se.id AS sessionExerciseId, se.position AS exercisePosition, se.exercise_id AS exerciseId,"
                    + " se.exercise_name AS exerciseName, se.tracking_type AS trackingType,"
                    + " se.load_basis AS loadBasis, se.implement_count AS implementCount,"
                    + " se.laterality AS laterality, se.side_mode AS sideMode,"
                    + " se.rest_seconds AS exerciseRestSeconds, se.permanent_notes AS permanentNotes,"
                    + " se.notes AS exerciseNotes,"
                    + " g.id AS groupId, g.label AS groupLabel, g.technique_id AS groupTechniqueId,"
                    + " g.technique_code AS groupTechniqueCode,"
                    + " g.rest_after_round_s AS groupRestAfterRoundSeconds,"
                    + " g.position AS groupPosition,"
                    + " (SELECT q.code FROM exercise_equipment ee JOIN equipment q ON q.id = ee.equipment_id"
                    + "     WHERE ee.exercise_id = se.exercise_id"
                    + "     ORDER BY ee.is_primary DESC, q.sort_order LIMIT 1) AS primaryEquipmentCode,"
                    + " sl.id AS setId, sl.position AS setPosition,"
                    + " sl.parent_set_id AS parentSetId, sl.technique_id AS techniqueId,"
                    + " t.code AS techniqueCode, t.counts_as_working_set AS techniqueCountsAsWorkingSet,"
                    + " sl.planned_reps_min AS plannedRepsMin, sl.planned_reps_max AS plannedRepsMax,"
                    + " sl.planned_weight_g AS plannedWeightGrams,"
                    + " sl.planned_duration_s AS plannedDurationSeconds,"
                    + " sl.planned_rest_seconds AS plannedRestSeconds,"
                    + " sl.weight_g AS weightGrams, sl.reps AS reps, sl.reps_left AS repsLeft,"
                    + " sl.reps_right AS repsRight, sl.duration_s AS durationSeconds,"
                    + " sl.status AS status, sl.completed_at AS completedAt, sl.notes AS setNotes,"
                    + " se.previous_session_exercise_id AS previousSessionExerciseId"
                    + " FROM session_exercise se"
                    + " LEFT JOIN session_exercise_group g ON g.id = se.group_id"
                    + " LEFT JOIN set_log sl ON sl.session_exercise_id = se.id"
                    + " LEFT JOIN set_log p ON p.id = sl.parent_set_id"
                    + " LEFT JOIN training_technique t ON t.id = sl.technique_id"
                    + " WHERE se.session_id = :sessionId"
                    // Segments follow the set they belong to: sort by the SET's position
                    // (the parent's, for a segment), then the set before its segments, then
                    // the segments among themselves.
                    + " ORDER BY se.position, COALESCE(p.position, sl.position),"
                    + " (sl.parent_set_id IS NOT NULL), sl.position";

    // ------------------------------------------------------------------ reads

    /** The session being performed right now, if any. At most one is ACTIVE per user. */
    @Query("SELECT s.* FROM workout_session s WHERE " + MINE_SQL + " AND s.status = 'ACTIVE'"
            + " ORDER BY s.started_at DESC LIMIT 1")
    LiveData<WorkoutSessionEntity> observeActive();

    @Nullable
    @Query("SELECT s.* FROM workout_session s WHERE " + MINE_SQL + " AND s.status = 'ACTIVE'"
            + " ORDER BY s.started_at DESC LIMIT 1")
    WorkoutSessionEntity findActive();

    @Query("SELECT COUNT(*) FROM workout_session s WHERE " + MINE_SQL + " AND s.status = 'ACTIVE'")
    int countActive();

    @Query("SELECT s.* FROM workout_session s WHERE s.id = :sessionId AND " + MINE_SQL)
    LiveData<WorkoutSessionEntity> observeSession(String sessionId);

    @Nullable
    @Query("SELECT s.* FROM workout_session s WHERE s.id = :sessionId AND " + MINE_SQL)
    WorkoutSessionEntity findSession(String sessionId);

    /**
     * The session and its pause arithmetic in one row. The paused totals come from the intervals,
     * never from the cached column, so a header can never claim a paused session is running.
     */
    String HEADER_SQL =
            "SELECT s.id AS id, s.template_id AS templateId, s.name AS name, s.notes AS notes,"
                    + " s.status AS status, s.started_at AS startedAt, s.ended_at AS endedAt,"
                    + " (SELECT COALESCE(SUM(p.ended_at - p.started_at), 0) FROM session_pause p"
                    + "     WHERE p.session_id = s.id AND p.ended_at IS NOT NULL) AS closedPausedMs,"
                    + " (SELECT p.started_at FROM session_pause p WHERE p.session_id = s.id"
                    + "     AND p.ended_at IS NULL ORDER BY p.started_at DESC LIMIT 1) AS openPauseStartedAt,"
                    + " s.rest_set_log_id AS restSetLogId, s.rest_ends_at AS restEndsAt,"
                    + " s.rest_remaining_ms_when_paused AS restRemainingMsWhenPaused"
                    + " FROM workout_session s WHERE ";

    @Query(HEADER_SQL + MINE_SQL + " AND s.status = 'ACTIVE' ORDER BY s.started_at DESC LIMIT 1")
    LiveData<SessionHeaderRow> observeActiveHeader();

    @Nullable
    @Query(HEADER_SQL + MINE_SQL + " AND s.status = 'ACTIVE' ORDER BY s.started_at DESC LIMIT 1")
    SessionHeaderRow findActiveHeader();

    @Query(HEADER_SQL + "s.id = :sessionId AND " + MINE_SQL)
    LiveData<SessionHeaderRow> observeHeader(String sessionId);

    @Nullable
    @Query(HEADER_SQL + "s.id = :sessionId AND " + MINE_SQL)
    SessionHeaderRow findHeader(String sessionId);

    @Query(ACTIVE_ROWS_SQL)
    LiveData<List<ActiveSetRow>> observeRows(String sessionId);

    @Query(ACTIVE_ROWS_SQL)
    List<ActiveSetRow> findRows(String sessionId);

    /**
     * Every set of the sessions this one compares itself with. Sets that were not performed are
     * included on purpose: they still occupy an ordinal among the working sets.
     */
    String PREVIOUS_SETS_SQL =
            "SELECT sl.session_exercise_id AS sessionExerciseId, sl.position AS position,"
                    + " t.counts_as_working_set AS countsAsWorkingSet, sl.status AS status,"
                    + " sl.weight_g AS weightGrams, sl.reps AS reps, sl.reps_left AS repsLeft,"
                    + " sl.reps_right AS repsRight, sl.duration_s AS durationSeconds"
                    + " FROM set_log sl LEFT JOIN training_technique t ON t.id = sl.technique_id"
                    + " WHERE sl.parent_set_id IS NULL AND sl.session_exercise_id IN ("
                    + "     SELECT se.previous_session_exercise_id FROM session_exercise se"
                    + "     WHERE se.session_id = :sessionId AND se.previous_session_exercise_id IS NOT NULL)"
                    + " ORDER BY sl.session_exercise_id, sl.position";

    @Query(PREVIOUS_SETS_SQL)
    LiveData<List<PreviousSetRow>> observePreviousSets(String sessionId);

    @Query(PREVIOUS_SETS_SQL)
    List<PreviousSetRow> findPreviousSets(String sessionId);

    @Nullable
    @Query("SELECT * FROM session_pause WHERE session_id = :sessionId AND ended_at IS NULL"
            + " ORDER BY started_at DESC LIMIT 1")
    SessionPauseEntity findOpenPause(String sessionId);

    @Nullable
    @Query("SELECT * FROM set_log WHERE id = :setId")
    SetLogEntity findSet(String setId);

    /** The group a session exercise is in, or null when it stands alone. */
    @Nullable
    @Query("SELECT group_id FROM session_exercise WHERE id = :sessionExerciseId")
    String findGroupIdOfExercise(String sessionExerciseId);

    @Query("SELECT COALESCE(MAX(position), -1) FROM set_log WHERE session_exercise_id = :sessionExerciseId"
            + " AND parent_set_id IS NULL")
    int maxSetPosition(String sessionExerciseId);

    @Query("SELECT COUNT(*) FROM set_log WHERE session_exercise_id = :sessionExerciseId"
            + " AND parent_set_id IS NULL")
    int countSets(String sessionExerciseId);

    /**
     * The highest position among the segments of ONE set, or -1 when it has none (so the first
     * segment is 0). Scoped to the parent, not to the exercise: a segment's position only orders it
     * among its own siblings, and {@link #maxSetPosition(String)} - which filters
     * {@code parent_set_id IS NULL} - never sees a segment at all.
     */
    @Query("SELECT COALESCE(MAX(position), -1) FROM set_log WHERE parent_set_id = :parentSetId")
    int maxSegmentPosition(String parentSetId);

    /**
     * The same exercise in the last finished session, so "anterior" can be frozen into the new
     * session. Sessions that were discarded or deleted are ignored.
     */
    @Nullable
    @Query("SELECT se.id FROM session_exercise se JOIN workout_session s ON s.id = se.session_id"
            + " WHERE se.exercise_id = :exerciseId AND " + MINE_SQL + " AND s.status = 'COMPLETED'"
            + " ORDER BY s.started_at DESC LIMIT 1")
    String findPreviousSessionExerciseId(String exerciseId);

    // ------------------------------------------------------------------ history (PRODUCT_SPEC HIS-03)

    /**
     * One finished session per row, with the pauses already summed and the exercises and performed
     * sets already counted. Ends in "WHERE " so a caller appends its own predicate.
     *
     * <p>{@code parent_set_id IS NULL} is not optional in the set count: drop-set and rest-pause
     * segments will be child rows of the set they belong to, and counting them would silently
     * inflate every past session the day that screen ships.
     */
    String HISTORY_SQL =
            "SELECT s.id AS id, s.template_id AS templateId, s.name AS name,"
                    + " s.started_at AS startedAt, s.ended_at AS endedAt,"
                    + " s.local_date AS localDate, s.time_zone AS timeZone, s.rating AS rating,"
                    + " (SELECT COALESCE(SUM(p.ended_at - p.started_at), 0) FROM session_pause p"
                    + "     WHERE p.session_id = s.id AND p.ended_at IS NOT NULL) AS closedPausedMs,"
                    + " (SELECT COUNT(*) FROM session_exercise se WHERE se.session_id = s.id)"
                    + "     AS exerciseCount,"
                    + " (SELECT COUNT(*) FROM set_log sl"
                    + "     JOIN session_exercise se2 ON se2.id = sl.session_exercise_id"
                    + "     WHERE se2.session_id = s.id AND sl.parent_set_id IS NULL"
                    + "     AND sl.status = 'COMPLETED') AS performedSetCount"
                    + " FROM workout_session s WHERE ";

    /**
     * The history list. {@code status = 'COMPLETED'} is explicit because a discarded session also
     * carries an {@code ended_at}: "it ended" is not the same as "it happened".
     */
    @Query(HISTORY_SQL + MINE_SQL + " AND s.status = 'COMPLETED' ORDER BY s.started_at DESC")
    LiveData<List<SessionHistoryRow>> observeCompletedSessions();

    @Query(HISTORY_SQL + MINE_SQL + " AND s.status = 'COMPLETED' ORDER BY s.started_at DESC")
    List<SessionHistoryRow> findCompletedSessions();

    /**
     * Ids of the finished sessions of a period (PRODUCT_SPEC PRG-04), by the day each was LIVED
     * on - never by {@code started_at}, which would move a workout between weeks the moment the
     * phone crossed a time zone.
     *
     * <p>Ids only. What a period adds up to is decided set by set by the section 9 rules, which
     * SQL cannot express; this query says WHICH sessions, and the domain says what they come to.
     *
     * @param from inclusive ISO day
     * @param to   inclusive ISO day
     */
    @Query("SELECT s.id FROM workout_session s WHERE " + MINE_SQL
            + " AND s.status = 'COMPLETED' AND s.local_date >= :from AND s.local_date <= :to"
            + " ORDER BY s.local_date, s.started_at")
    LiveData<List<String>> observeSessionIdsBetween(String from, String to);

    /**
     * The muscle GROUPS trained by the exercises of a period, with the role each has.
     *
     * <p>Resolved to the group ({@code COALESCE(parent_id, id)}) rather than the subgroup, because
     * "did I train back enough" is a question about the back; an exercise naming two subgroups of
     * one group did one set of it, not two. DISTINCT collapses exactly that case here, and the
     * domain decides what to do when the two subgroups have different roles.
     *
     * <p>Read from the library as it stands today rather than from the session's snapshot: a
     * muscle map is a classification, not a measurement of what happened, so correcting the
     * catalogue is allowed to correct past weeks too.
     */
    @Query("SELECT DISTINCT se.exercise_id AS exerciseId, g.id AS muscleGroupId,"
            + " g.name AS name, g.sort_order AS sortOrder, em.role AS role"
            + " FROM session_exercise se"
            + " JOIN workout_session s ON s.id = se.session_id"
            + " JOIN exercise_muscle em ON em.exercise_id = se.exercise_id"
            + " JOIN muscle m ON m.id = em.muscle_id"
            + " JOIN muscle g ON g.id = COALESCE(m.parent_id, m.id)"
            + " WHERE " + MINE_SQL + " AND s.status = 'COMPLETED'"
            + " AND s.local_date >= :from AND s.local_date <= :to")
    List<ExerciseMuscleGroupRow> findMuscleGroupsBetween(String from, String to);

    /**
     * The first and last day there is anything to see, so the period arrows know where to stop.
     *
     * <p>Read in the same pass as a period's statistics rather than observed on its own: an arrow
     * enabled by one read while the numbers beside it came from another is the disagreement
     * ARCHITECTURE section 5.2 is about.
     */
    @Query("SELECT MIN(s.local_date) AS firstDay, MAX(s.local_date) AS lastDay"
            + " FROM workout_session s WHERE " + MINE_SQL + " AND s.status = 'COMPLETED'")
    TrainedDayBoundsRow findTrainedDayBounds();

    /**
     * The previous time this same workout was performed, for the comparison in PRODUCT_SPEC
     * section 11.
     *
     * <p>This is a different question from {@link #findPreviousSessionExerciseId(String)}, which
     * answers "the last time I did this exercise, in any workout" and is frozen per set when the
     * session starts (ADR-0033). Here the template has to match and the session has to be strictly
     * older, which is also what keeps a session from comparing itself with itself.
     */
    @Nullable
    @Query("SELECT s.id FROM workout_session s WHERE " + MINE_SQL
            + " AND s.status = 'COMPLETED' AND s.template_id = :templateId"
            + " AND s.started_at < :startedAt ORDER BY s.started_at DESC LIMIT 1")
    String findPreviousSessionOfTemplate(String templateId, long startedAt);

    // ------------------------------------------------------------------ writes

    @Insert
    void insertSession(WorkoutSessionEntity session);

    @Update
    void updateSession(WorkoutSessionEntity session);

    /** Before the exercises: each one points at its group, and the foreign key is enforced. */
    @Insert
    void insertExerciseGroups(List<SessionExerciseGroupEntity> groups);

    @Insert
    void insertExercises(List<SessionExerciseEntity> exercises);

    @Insert
    void insertSets(List<SetLogEntity> sets);

    @Insert
    void insertSet(SetLogEntity set);

    @Update
    void updateSet(SetLogEntity set);

    @Query("DELETE FROM set_log WHERE id = :setId")
    void deleteSet(String setId);

    /** Closes the gap left by a removed set so positions stay 0..n-1. */
    @Query("UPDATE set_log SET position = position - 1 WHERE session_exercise_id = :sessionExerciseId"
            + " AND parent_set_id IS NULL AND position > :removedPosition")
    void shiftSetsAfter(String sessionExerciseId, int removedPosition);

    /**
     * Closes the gap left by a removed segment so its siblings stay 0..n-1. Only the siblings: the
     * segments of another set have positions of their own, and the sets themselves are not touched
     * ({@link #shiftSetsAfter(String, int)} is the one that moves sets).
     */
    @Query("UPDATE set_log SET position = position - 1 WHERE parent_set_id = :parentSetId"
            + " AND position > :removedPosition")
    void shiftSegmentsAfter(String parentSetId, int removedPosition);

    /**
     * Saves what the user typed without confirming it. The {@code status = 'PENDING'} clause is the
     * guard that matters: a write that lands after the user confirmed the set is a no-op in SQL, not
     * merely by luck of thread ordering.
     */
    @Query("UPDATE set_log SET weight_g = :weightGrams, reps = :reps, reps_left = :repsLeft,"
            + " reps_right = :repsRight, duration_s = :durationSeconds"
            + " WHERE id = :setId AND status = 'PENDING'")
    int updateTypedValues(String setId, @Nullable Long weightGrams, @Nullable Integer reps,
                          @Nullable Integer repsLeft, @Nullable Integer repsRight,
                          @Nullable Integer durationSeconds);

    @Query("UPDATE set_log SET weight_g = :weightGrams, reps = :reps, reps_left = :repsLeft,"
            + " reps_right = :repsRight, duration_s = :durationSeconds, status = 'COMPLETED',"
            + " completed_at = :completedAt WHERE id = :setId")
    int completeSet(String setId, @Nullable Long weightGrams, @Nullable Integer reps,
                    @Nullable Integer repsLeft, @Nullable Integer repsRight,
                    @Nullable Integer durationSeconds, long completedAt);

    /** Undo: the values typed stay, the set goes back to "not performed". */
    @Query("UPDATE set_log SET status = 'PENDING', completed_at = NULL WHERE id = :setId")
    int uncompleteSet(String setId);

    /**
     * Removes a finished session from the history. Soft: the row stays so the removal can be
     * synced, exactly as a discard does, and every read already filters {@code deleted_at IS NULL}
     * through MINE_SQL. Nothing recorded is rewritten - the session leaves whole (ADR-0041).
     *
     * @return 0 when the session does not exist, is not this user's, or was already removed
     */
    @Query("UPDATE workout_session SET deleted_at = :now, updated_at = :now,"
            + " sync_status = 'PENDING' WHERE id = :sessionId AND deleted_at IS NULL")
    int softDeleteSession(String sessionId, long now);

    /**
     * How the session felt, 1 to 5. Only on a finished session that still exists: rating a running
     * workout would be rating something that has not happened yet.
     *
     * @return 0 when there was nothing to rate
     */
    @Query("UPDATE workout_session SET rating = :rating, updated_at = :now,"
            + " sync_status = 'PENDING' WHERE id = :sessionId AND deleted_at IS NULL"
            + " AND status = 'COMPLETED'")
    int rateSession(String sessionId, Integer rating, long now);

    /**
     * What the person wrote about the session. Same conditions as the rating: a finished session
     * that still exists.
     *
     * @param notes null to remove the note. Empty text is not a note, and storing "" would make a
     *              deleted note indistinguishable from one nobody ever wrote
     * @return 0 when there was nothing to write to
     */
    @Query("UPDATE workout_session SET notes = :notes, updated_at = :now,"
            + " sync_status = 'PENDING' WHERE id = :sessionId AND deleted_at IS NULL"
            + " AND status = 'COMPLETED'")
    int noteSession(String sessionId, String notes, long now);


    /**
     * The drops of a set that is being undone. A drop is part of its set (ADR-0037), so leaving
     * them performed under a set that is not would make the database say reps happened while every
     * total ignores them.
     */
    @Query("UPDATE set_log SET status = 'PENDING', completed_at = NULL"
            + " WHERE parent_set_id = :parentSetId AND status = 'COMPLETED'")
    int uncompleteSegmentsOf(String parentSetId);

    /** Ids of the drops of a set, used to find out whether one of them owns the running rest. */
    @Query("SELECT id FROM set_log WHERE parent_set_id = :parentSetId")
    List<String> findSegmentIds(String parentSetId);

    @Query("UPDATE set_log SET status = 'COMPLETED', completed_at = :completedAt WHERE id IN (:setIds)")
    int completeSets(List<String> setIds, long completedAt);

    /** Skipping keeps whatever was typed: history shows it was planned and not performed. */
    @Query("UPDATE set_log SET status = 'SKIPPED', completed_at = NULL WHERE id IN (:setIds)")
    int skipSets(List<String> setIds);

    @Query("UPDATE set_log SET technique_id = :techniqueId WHERE id = :setId")
    int updateSetTechnique(String setId, @Nullable String techniqueId);

    @Query("UPDATE set_log SET notes = :notes WHERE id = :setId")
    int updateSetNotes(String setId, @Nullable String notes);

    /** Sum of the pauses that already ended - what the cached total_paused_ms should be. */
    @Query("SELECT COALESCE(SUM(ended_at - started_at), 0) FROM session_pause"
            + " WHERE session_id = :sessionId AND ended_at IS NOT NULL")
    long sumClosedPausedMs(String sessionId);

    @Insert
    void insertPause(SessionPauseEntity pause);

    @Query("UPDATE session_pause SET ended_at = :endedAt WHERE session_id = :sessionId AND ended_at IS NULL")
    int closeOpenPauses(String sessionId, long endedAt);

    @Query("UPDATE session_exercise SET notes = :notes WHERE id = :sessionExerciseId")
    void updateExerciseNotes(String sessionExerciseId, @Nullable String notes);
}

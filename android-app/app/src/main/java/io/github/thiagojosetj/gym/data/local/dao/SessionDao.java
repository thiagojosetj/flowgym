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
import io.github.thiagojosetj.gym.data.local.entity.SessionPauseEntity;
import io.github.thiagojosetj.gym.data.local.entity.SetLogEntity;
import io.github.thiagojosetj.gym.data.local.entity.WorkoutSessionEntity;
import io.github.thiagojosetj.gym.data.local.row.ActiveSetRow;
import io.github.thiagojosetj.gym.data.local.row.PreviousSetRow;

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
     */
    String ACTIVE_ROWS_SQL =
            "SELECT se.id AS sessionExerciseId, se.position AS exercisePosition, se.exercise_id AS exerciseId,"
                    + " se.exercise_name AS exerciseName, se.tracking_type AS trackingType,"
                    + " se.load_basis AS loadBasis, se.implement_count AS implementCount,"
                    + " se.laterality AS laterality, se.side_mode AS sideMode,"
                    + " se.rest_seconds AS exerciseRestSeconds, se.permanent_notes AS permanentNotes,"
                    + " se.notes AS exerciseNotes,"
                    + " (SELECT q.code FROM exercise_equipment ee JOIN equipment q ON q.id = ee.equipment_id"
                    + "     WHERE ee.exercise_id = se.exercise_id"
                    + "     ORDER BY ee.is_primary DESC, q.sort_order LIMIT 1) AS primaryEquipmentCode,"
                    + " sl.id AS setId, sl.position AS setPosition, sl.technique_id AS techniqueId,"
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
                    + " LEFT JOIN set_log sl ON sl.session_exercise_id = se.id AND sl.parent_set_id IS NULL"
                    + " LEFT JOIN training_technique t ON t.id = sl.technique_id"
                    + " WHERE se.session_id = :sessionId"
                    + " ORDER BY se.position, sl.position";

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

    @Query("SELECT * FROM session_pause WHERE session_id = :sessionId ORDER BY started_at")
    LiveData<List<SessionPauseEntity>> observePauses(String sessionId);

    @Query("SELECT * FROM session_pause WHERE session_id = :sessionId ORDER BY started_at")
    List<SessionPauseEntity> findPauses(String sessionId);

    @Nullable
    @Query("SELECT * FROM session_pause WHERE session_id = :sessionId AND ended_at IS NULL"
            + " ORDER BY started_at DESC LIMIT 1")
    SessionPauseEntity findOpenPause(String sessionId);

    @Nullable
    @Query("SELECT * FROM set_log WHERE id = :setId")
    SetLogEntity findSet(String setId);

    @Query("SELECT COALESCE(MAX(position), -1) FROM set_log WHERE session_exercise_id = :sessionExerciseId"
            + " AND parent_set_id IS NULL")
    int maxSetPosition(String sessionExerciseId);

    @Query("SELECT COUNT(*) FROM set_log WHERE session_exercise_id = :sessionExerciseId"
            + " AND parent_set_id IS NULL")
    int countSets(String sessionExerciseId);

    /**
     * The same exercise in the last finished session, so "anterior" can be frozen into the new
     * session. Sessions that were discarded or deleted are ignored.
     */
    @Nullable
    @Query("SELECT se.id FROM session_exercise se JOIN workout_session s ON s.id = se.session_id"
            + " WHERE se.exercise_id = :exerciseId AND " + MINE_SQL + " AND s.status = 'COMPLETED'"
            + " ORDER BY s.started_at DESC LIMIT 1")
    String findPreviousSessionExerciseId(String exerciseId);

    // ------------------------------------------------------------------ writes

    @Insert
    void insertSession(WorkoutSessionEntity session);

    @Update
    void updateSession(WorkoutSessionEntity session);

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

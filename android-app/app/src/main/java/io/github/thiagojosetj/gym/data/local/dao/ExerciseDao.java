package io.github.thiagojosetj.gym.data.local.dao;

import static io.github.thiagojosetj.gym.data.local.entity.AppMetadataEntity.CURRENT_USER_ID_SQL;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Query;

import java.util.List;

import io.github.thiagojosetj.gym.data.local.entity.ExerciseEntity;
import io.github.thiagojosetj.gym.data.local.row.ExerciseListRow;
import io.github.thiagojosetj.gym.data.local.row.ExerciseMuscleRow;
import io.github.thiagojosetj.gym.data.local.row.ExerciseRefRow;

@Dao
public interface ExerciseDao {

    /** Highlighted primary muscle of exercise {@code e}: the PRIMARY link with the lowest sort_order. */
    String PRIMARY_MUSCLE_NAME_SQL =
            "(SELECT m.name FROM exercise_muscle em JOIN muscle m ON m.id = em.muscle_id"
                    + " WHERE em.exercise_id = e.id AND em.role = 'PRIMARY'"
                    + " ORDER BY em.sort_order LIMIT 1)";

    String PRIMARY_GROUP_NAME_SQL =
            "(SELECT COALESCE(p.name, m.name) FROM exercise_muscle em"
                    + " JOIN muscle m ON m.id = em.muscle_id LEFT JOIN muscle p ON p.id = m.parent_id"
                    + " WHERE em.exercise_id = e.id AND em.role = 'PRIMARY'"
                    + " ORDER BY em.sort_order LIMIT 1)";

    String PRIMARY_EQUIPMENT_CODE_SQL =
            "(SELECT q.code FROM exercise_equipment ee JOIN equipment q ON q.id = ee.equipment_id"
                    + " WHERE ee.exercise_id = e.id ORDER BY ee.is_primary DESC, q.sort_order LIMIT 1)";

    String PRIMARY_EQUIPMENT_NAME_SQL =
            "(SELECT q.name FROM exercise_equipment ee JOIN equipment q ON q.id = ee.equipment_id"
                    + " WHERE ee.exercise_id = e.id ORDER BY ee.is_primary DESC, q.sort_order LIMIT 1)";

    /** System exercises plus the current user's own, never deleted ones. */
    String VISIBLE_SQL =
            "e.deleted_at IS NULL AND (e.owner_user_id IS NULL OR e.owner_user_id = " + CURRENT_USER_ID_SQL + ")";

    /**
     * The library, filtered. Every filter is optional and they combine with AND.
     *
     * @param likeQuery      normalized and LIKE-escaped query ('' = no text filter)
     * @param muscleId       group or subgroup id; a group also matches its subgroups
     * @param roleScope      PRIMARY, SECONDARY or ANY
     * @param equipmentIds   match exercises using any of these
     * @param equipmentCount size of equipmentIds (0 disables the equipment filter)
     */
    @Query("SELECT e.id, e.name, "
            + PRIMARY_MUSCLE_NAME_SQL + " AS primaryMuscleName, "
            + PRIMARY_GROUP_NAME_SQL + " AS primaryGroupName, "
            + PRIMARY_EQUIPMENT_NAME_SQL + " AS primaryEquipmentName"
            + " FROM exercise e"
            + " WHERE " + VISIBLE_SQL + " AND e.is_active = 1"
            + " AND (:likeQuery = '' OR e.search_text LIKE '%' || :likeQuery || '%' ESCAPE '\\')"
            + " AND (:muscleId IS NULL OR EXISTS ("
            + "     SELECT 1 FROM exercise_muscle em JOIN muscle m ON m.id = em.muscle_id"
            + "     WHERE em.exercise_id = e.id"
            + "       AND (m.id = :muscleId OR m.parent_id = :muscleId)"
            + "       AND (:roleScope = 'ANY' OR em.role = :roleScope)))"
            + " AND (:equipmentCount = 0 OR EXISTS ("
            + "     SELECT 1 FROM exercise_equipment ee"
            + "     WHERE ee.exercise_id = e.id AND ee.equipment_id IN (:equipmentIds)))"
            + " ORDER BY e.search_text")
    LiveData<List<ExerciseListRow>> observeLibrary(String likeQuery,
                                                   @Nullable String muscleId,
                                                   String roleScope,
                                                   List<String> equipmentIds,
                                                   int equipmentCount);

    @Nullable
    @Query("SELECT * FROM exercise e WHERE e.id = :id AND " + VISIBLE_SQL)
    ExerciseEntity findVisibleById(String id);

    @Query("SELECT m.id AS muscleId, m.name AS muscleName, p.name AS groupName,"
            + " em.role AS role, em.sort_order AS sortOrder"
            + " FROM exercise_muscle em JOIN muscle m ON m.id = em.muscle_id"
            + " LEFT JOIN muscle p ON p.id = m.parent_id"
            + " WHERE em.exercise_id = :exerciseId ORDER BY em.role, em.sort_order")
    List<ExerciseMuscleRow> findMuscles(String exerciseId);

    @Query("SELECT q.name FROM exercise_equipment ee JOIN equipment q ON q.id = ee.equipment_id"
            + " WHERE ee.exercise_id = :exerciseId ORDER BY ee.is_primary DESC, q.sort_order")
    List<String> findEquipmentNames(String exerciseId);

    @Query("SELECT e.id, e.name, e.tracking_type AS trackingType, e.load_basis AS loadBasis,"
            + " e.implement_count AS implementCount, e.laterality, "
            + PRIMARY_MUSCLE_NAME_SQL + " AS primaryMuscleName, "
            + PRIMARY_EQUIPMENT_CODE_SQL + " AS primaryEquipmentCode"
            + " FROM exercise e WHERE e.id IN (:ids) AND " + VISIBLE_SQL)
    List<ExerciseRefRow> findRefs(List<String> ids);
}

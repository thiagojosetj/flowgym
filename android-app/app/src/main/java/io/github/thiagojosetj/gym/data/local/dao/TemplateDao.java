package io.github.thiagojosetj.gym.data.local.dao;

import static io.github.thiagojosetj.gym.data.local.entity.AppMetadataEntity.CURRENT_USER_ID_SQL;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import java.util.List;

import io.github.thiagojosetj.gym.data.local.entity.TemplateExerciseEntity;
import io.github.thiagojosetj.gym.data.local.entity.TemplateSetEntity;
import io.github.thiagojosetj.gym.data.local.entity.WorkoutTemplateEntity;
import io.github.thiagojosetj.gym.data.local.row.TemplateExerciseRow;
import io.github.thiagojosetj.gym.data.local.row.TemplateSummaryRow;

@Dao
public abstract class TemplateDao {

    private static final String ACTIVE_FOR_CURRENT_USER_SQL =
            "t.owner_user_id = " + CURRENT_USER_ID_SQL + " AND t.deleted_at IS NULL AND t.archived_at IS NULL";

    @Query("SELECT t.id, t.name, t.description, t.updated_at AS updatedAt,"
            + " (SELECT COUNT(*) FROM template_exercise te WHERE te.template_id = t.id) AS exerciseCount,"
            + " (SELECT COUNT(*) FROM template_set ts JOIN template_exercise te ON te.id = ts.template_exercise_id"
            + "     WHERE te.template_id = t.id) AS setCount,"
            + " (SELECT group_concat(DISTINCT COALESCE(p.name, m.name)) FROM template_exercise te"
            + "     JOIN exercise_muscle em ON em.exercise_id = te.exercise_id AND em.role = 'PRIMARY'"
            + "     JOIN muscle m ON m.id = em.muscle_id LEFT JOIN muscle p ON p.id = m.parent_id"
            + "     WHERE te.template_id = t.id) AS muscleGroups"
            + " FROM workout_template t WHERE " + ACTIVE_FOR_CURRENT_USER_SQL
            + " ORDER BY t.sort_order, t.created_at")
    public abstract LiveData<List<TemplateSummaryRow>> observeActive();

    @Query("SELECT COUNT(*) FROM workout_template t WHERE " + ACTIVE_FOR_CURRENT_USER_SQL)
    public abstract LiveData<Integer> observeActiveCount();

    @Nullable
    @Query("SELECT * FROM workout_template t WHERE t.id = :id AND t.owner_user_id = " + CURRENT_USER_ID_SQL
            + " AND t.deleted_at IS NULL")
    public abstract WorkoutTemplateEntity findById(String id);

    @Query("SELECT te.id, te.position, te.rest_seconds AS restSeconds, te.notes, te.side_mode AS sideMode,"
            + " e.id AS ex_id, e.name AS ex_name, e.tracking_type AS ex_trackingType,"
            + " e.load_basis AS ex_loadBasis, e.implement_count AS ex_implementCount, e.laterality AS ex_laterality,"
            + " " + ExerciseDao.PRIMARY_MUSCLE_NAME_SQL + " AS ex_primaryMuscleName,"
            + " " + ExerciseDao.PRIMARY_EQUIPMENT_CODE_SQL + " AS ex_primaryEquipmentCode"
            + " FROM template_exercise te JOIN exercise e ON e.id = te.exercise_id"
            + " WHERE te.template_id = :templateId ORDER BY te.position")
    public abstract List<TemplateExerciseRow> findExercises(String templateId);

    @Query("SELECT ts.* FROM template_set ts JOIN template_exercise te ON te.id = ts.template_exercise_id"
            + " WHERE te.template_id = :templateId ORDER BY te.position, ts.position")
    public abstract List<TemplateSetEntity> findSets(String templateId);

    @Query("SELECT COALESCE(MAX(sort_order), -1) FROM workout_template WHERE owner_user_id = :ownerId")
    public abstract int maxSortOrder(String ownerId);

    /**
     * Saves the whole template aggregate atomically. Children are replaced (their UUIDs come from
     * the draft, so they are preserved across saves). Deleting the old children cascades to their
     * sets through the foreign keys.
     */
    @Transaction
    public void saveAggregate(WorkoutTemplateEntity root, boolean isNew,
                              List<TemplateExerciseEntity> exercises, List<TemplateSetEntity> sets) {
        if (isNew) {
            insertTemplate(root);
        } else {
            updateTemplate(root);
        }
        deleteExercisesOf(root.id);
        insertExercises(exercises);
        insertSets(sets);
    }

    /** Logical delete (tombstone for sync). Sessions are never touched. */
    @Query("UPDATE workout_template SET deleted_at = :now, updated_at = :now, sync_status = 'PENDING'"
            + " WHERE id = :id AND deleted_at IS NULL")
    public abstract int softDelete(String id, long now);

    @Insert
    abstract void insertTemplate(WorkoutTemplateEntity template);

    @Update
    abstract void updateTemplate(WorkoutTemplateEntity template);

    @Query("DELETE FROM template_exercise WHERE template_id = :templateId")
    abstract void deleteExercisesOf(String templateId);

    @Insert
    abstract void insertExercises(List<TemplateExerciseEntity> exercises);

    @Insert
    abstract void insertSets(List<TemplateSetEntity> sets);
}

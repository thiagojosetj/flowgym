package io.github.thiagojosetj.gym.data.local.dao;

import static io.github.thiagojosetj.gym.data.local.entity.AppMetadataEntity.CURRENT_USER_ID_SQL;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.thiagojosetj.gym.data.local.entity.TemplateExerciseEntity;
import io.github.thiagojosetj.gym.data.local.entity.TemplateExerciseGroupEntity;
import io.github.thiagojosetj.gym.data.local.entity.TemplateSetEntity;
import io.github.thiagojosetj.gym.data.local.entity.TrainingTechniqueEntity;
import io.github.thiagojosetj.gym.data.local.entity.WorkoutTemplateEntity;
import io.github.thiagojosetj.gym.data.local.row.TemplateExerciseRow;
import io.github.thiagojosetj.gym.data.local.row.TemplateGroupRow;
import io.github.thiagojosetj.gym.data.local.row.TemplateSummaryRow;
import io.github.thiagojosetj.gym.domain.template.GroupLabels;
import io.github.thiagojosetj.gym.domain.template.TemplateRules;

@Dao
public abstract class TemplateDao {

    private static final String ACTIVE_FOR_CURRENT_USER_SQL =
            "t.owner_user_id = " + CURRENT_USER_ID_SQL + " AND t.deleted_at IS NULL AND t.archived_at IS NULL";

    /** A group with the badge of its technique joined in. Ends before the WHERE, for the caller. */
    private static final String GROUP_ROWS_SQL =
            "SELECT g.id AS id, g.label AS label, g.technique_id AS techniqueId,"
                    + " t.code AS techniqueCode, g.rest_after_round_s AS restAfterRoundSeconds,"
                    + " g.position AS position"
                    + " FROM template_exercise_group g"
                    + " LEFT JOIN training_technique t ON t.id = g.technique_id";

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
            + " te.group_id AS groupId,"
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

    // ------------------------------------------------------------------ groups (PRODUCT_SPEC 6.3)

    /** The groups of a template in the order the editor shows them (A, then B, ...). */
    @Query(GROUP_ROWS_SQL + " WHERE g.template_id = :templateId ORDER BY g.position")
    public abstract List<TemplateGroupRow> findGroups(String templateId);

    @Nullable
    @Query("SELECT * FROM template_exercise_group"
            + " WHERE id = :groupId AND template_id = :templateId")
    public abstract TemplateExerciseGroupEntity findGroup(String templateId, String groupId);

    /**
     * A technique the user may put on a group: it must exist, be active, and be the system's or
     * their own. The scope is for the caller to check. Here rather than in {@link TechniqueDao}:
     * that interface is implemented by hand in tests, and a new method on it breaks every fake.
     */
    @Nullable
    @Query("SELECT * FROM training_technique WHERE id = :techniqueId"
            + " AND " + TechniqueDao.VISIBLE_SQL)
    public abstract TrainingTechniqueEntity findVisibleTechnique(String techniqueId);

    /**
     * The exercises of a template as stored, in order. Lighter than {@link #findExercises} for the
     * paths that only need to know which exercise is in which group.
     */
    @Query("SELECT * FROM template_exercise WHERE template_id = :templateId ORDER BY position")
    public abstract List<TemplateExerciseEntity> findExerciseEntities(String templateId);

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
        // The draft the editor works on knows nothing about groups, so the entities built from it
        // arrive with group_id null. Replacing the children with them as they are would ungroup
        // every exercise on ANY edit and leave the group rows behind with nobody in them. The
        // memberships are read before the delete and put back by exercise id, which the draft
        // keeps stable across saves.
        Map<String, String> groupOf = isNew ? Collections.emptyMap() : groupIdsByExercise(root.id);
        deleteExercisesOf(root.id);
        for (TemplateExerciseEntity exercise : exercises) {
            if (exercise.groupId == null) {
                exercise.groupId = groupOf.get(exercise.id);
            }
        }
        insertExercises(exercises);
        insertSets(sets);
        if (!isNew) {
            // An exercise taken out of the draft may have left its group too short to be one.
            normalizeGroups(root.id);
        }
    }

    /**
     * Creates a group over exercises of its template and puts every group of the template back in
     * order, in one transaction. The label and position the entity carries are replaced: they are
     * derived, never chosen (see {@link #normalizeGroups(String)}).
     */
    @Transaction
    public void addGroup(TemplateExerciseGroupEntity group, List<String> templateExerciseIds,
                         long now) {
        // Placeholders that only exist inside this transaction: the column is NOT NULL, but where
        // the group sits - and so its letter - depends on where its exercises sit, which is known
        // once they are assigned. normalizeGroups is the one place that works it out.
        group.label = "";
        group.position = 0;
        insertGroup(group);
        int assigned = assignToGroup(group.templateId, group.id, templateExerciseIds);
        if (assigned != templateExerciseIds.size()) {
            // Rolls the whole thing back. Without this the short group would be dissolved by the
            // normalization below and the caller would never learn its exercises were not found.
            throw new IllegalArgumentException(assigned + " of " + templateExerciseIds.size()
                    + " exercises belong to template " + group.templateId);
        }
        normalizeGroups(group.templateId);
        touch(group.templateId, now);
    }

    /** Changes the technique and the rest of a group. The label and the members do not move. */
    @Transaction
    public void updateGroup(String templateId, String groupId, @Nullable String techniqueId,
                            int restAfterRoundSeconds, long now) {
        setGroupDetails(templateId, groupId, techniqueId, restAfterRoundSeconds);
        touch(templateId, now);
    }

    /**
     * Deletes the group. Its exercises stay in the template with group_id null: that is the foreign
     * key's doing (ON DELETE SET NULL), not this method's, and no statement here deletes an
     * exercise. The groups that remain are relabelled, so deleting A turns B into A.
     */
    @Transaction
    public void removeGroup(String templateId, String groupId, long now) {
        deleteGroup(templateId, groupId);
        normalizeGroups(templateId);
        touch(templateId, now);
    }

    /**
     * Puts the groups of a template in the shape every reader relies on. It runs after every change
     * to who is in which group, so no caller has to remember to:
     * <ul>
     *   <li>a group of fewer than {@link TemplateRules#MIN_GROUP_SIZE} exercises is dissolved - its
     *       exercise, if any, stays in the template ungrouped;</li>
     *   <li>the rest are ordered by where their first exercise sits in the template, and given
     *       position 0, 1, 2... and the label {@link GroupLabels#forIndex(int)} gives that place.
     *       This is the only place a label is assigned, so two groups can never end up with the
     *       same one.</li>
     * </ul>
     */
    @Transaction
    void normalizeGroups(String templateId) {
        deleteUnderfilledGroups(templateId);
        List<TemplateExerciseGroupEntity> ordered = findGroupsInWorkoutOrder(templateId);
        for (int i = 0; i < ordered.size(); i++) {
            TemplateExerciseGroupEntity group = ordered.get(i);
            String label = GroupLabels.forIndex(i);
            if (group.position != i || !group.label.equals(label)) {
                updateGroupOrder(group.id, label, i);
            }
        }
    }

    private Map<String, String> groupIdsByExercise(String templateId) {
        Map<String, String> groupOf = new HashMap<>();
        for (TemplateExerciseEntity exercise : findExerciseEntities(templateId)) {
            if (exercise.groupId != null) {
                groupOf.put(exercise.id, exercise.groupId);
            }
        }
        return groupOf;
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

    @Insert
    abstract void insertGroup(TemplateExerciseGroupEntity group);

    /** Only exercises of this template: an id from anywhere else matches nothing. */
    @Query("UPDATE template_exercise SET group_id = :groupId"
            + " WHERE template_id = :templateId AND id IN (:templateExerciseIds)")
    abstract int assignToGroup(String templateId, String groupId, List<String> templateExerciseIds);

    @Query("UPDATE template_exercise_group SET technique_id = :techniqueId,"
            + " rest_after_round_s = :restAfterRoundSeconds"
            + " WHERE id = :groupId AND template_id = :templateId")
    abstract int setGroupDetails(String templateId, String groupId, @Nullable String techniqueId,
                                 int restAfterRoundSeconds);

    @Query("DELETE FROM template_exercise_group WHERE id = :groupId AND template_id = :templateId")
    abstract int deleteGroup(String templateId, String groupId);

    /** Groups too short to be one. Whoever was in one is released by ON DELETE SET NULL. */
    @Query("DELETE FROM template_exercise_group WHERE template_id = :templateId AND"
            + " (SELECT COUNT(*) FROM template_exercise te"
            + "     WHERE te.group_id = template_exercise_group.id)"
            + " < " + TemplateRules.MIN_GROUP_SIZE)
    abstract int deleteUnderfilledGroups(String templateId);

    @Query("SELECT g.* FROM template_exercise_group g WHERE g.template_id = :templateId"
            + " ORDER BY (SELECT MIN(te.position) FROM template_exercise te"
            + "     WHERE te.group_id = g.id), g.position")
    abstract List<TemplateExerciseGroupEntity> findGroupsInWorkoutOrder(String templateId);

    @Query("UPDATE template_exercise_group SET label = :label, position = :position"
            + " WHERE id = :groupId")
    abstract int updateGroupOrder(String groupId, String label, int position);

    /** Children travel with their root (docs/SYNC.md section 3): changing one is changing it. */
    @Query("UPDATE workout_template SET updated_at = :now, sync_status = 'PENDING'"
            + " WHERE id = :templateId")
    abstract int touch(String templateId, long now);
}

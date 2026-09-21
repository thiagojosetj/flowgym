package io.github.thiagojosetj.gym.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Upsert;

import java.util.List;

import io.github.thiagojosetj.gym.data.local.entity.EquipmentEntity;
import io.github.thiagojosetj.gym.data.local.entity.ExerciseEntity;
import io.github.thiagojosetj.gym.data.local.entity.ExerciseEquipmentEntity;
import io.github.thiagojosetj.gym.data.local.entity.ExerciseMuscleEntity;
import io.github.thiagojosetj.gym.data.local.entity.MuscleEntity;

/** Reference data (muscles, equipment, system exercises). Written only by the catalog seeder. */
@Dao
public abstract class CatalogDao {

    @Query("SELECT * FROM muscle ORDER BY sort_order")
    public abstract LiveData<List<MuscleEntity>> observeMuscles();

    @Query("SELECT * FROM equipment ORDER BY sort_order")
    public abstract LiveData<List<EquipmentEntity>> observeEquipment();

    @Query("SELECT COUNT(*) FROM exercise WHERE owner_user_id IS NULL AND is_active = 1")
    public abstract int countActiveSystemExercises();

    /**
     * Replaces the system catalog in one transaction, so observers never see a half-seeded
     * library. Rows are upserted (UPDATE on conflict, never DELETE+INSERT), which keeps templates
     * that reference exercises intact. System exercises missing from the new catalog are only
     * deactivated, never deleted, because past templates and sessions may reference them.
     *
     * @param muscles parents must come before their children (self foreign key)
     */
    @Transaction
    public void replaceSystemCatalog(List<MuscleEntity> muscles,
                                     List<EquipmentEntity> equipment,
                                     List<ExerciseEntity> exercises,
                                     List<ExerciseMuscleEntity> exerciseMuscles,
                                     List<ExerciseEquipmentEntity> exerciseEquipment,
                                     long now) {
        upsertMuscles(muscles);
        upsertEquipment(equipment);
        deactivateSystemExercises(now);
        upsertExercises(exercises);
        deleteSystemExerciseMuscles();
        deleteSystemExerciseEquipment();
        insertExerciseMuscles(exerciseMuscles);
        insertExerciseEquipment(exerciseEquipment);
    }

    @Upsert
    abstract void upsertMuscles(List<MuscleEntity> muscles);

    @Upsert
    abstract void upsertEquipment(List<EquipmentEntity> equipment);

    @Upsert
    abstract void upsertExercises(List<ExerciseEntity> exercises);

    @Query("UPDATE exercise SET is_active = 0, updated_at = :now WHERE owner_user_id IS NULL")
    abstract void deactivateSystemExercises(long now);

    @Query("DELETE FROM exercise_muscle WHERE exercise_id IN (SELECT id FROM exercise WHERE owner_user_id IS NULL)")
    abstract void deleteSystemExerciseMuscles();

    @Query("DELETE FROM exercise_equipment WHERE exercise_id IN (SELECT id FROM exercise WHERE owner_user_id IS NULL)")
    abstract void deleteSystemExerciseEquipment();

    @Insert
    abstract void insertExerciseMuscles(List<ExerciseMuscleEntity> links);

    @Insert
    abstract void insertExerciseEquipment(List<ExerciseEquipmentEntity> links);
}

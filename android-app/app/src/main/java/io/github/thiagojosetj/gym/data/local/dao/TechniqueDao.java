package io.github.thiagojosetj.gym.data.local.dao;

import static io.github.thiagojosetj.gym.data.local.entity.AppMetadataEntity.CURRENT_USER_ID_SQL;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Query;

import java.util.List;

import io.github.thiagojosetj.gym.data.local.entity.TrainingTechniqueEntity;

/** Training techniques available to the current user (system catalog + own, when they exist). */
@Dao
public interface TechniqueDao {

    String VISIBLE_SQL = "is_active = 1 AND (owner_user_id IS NULL OR owner_user_id = " + CURRENT_USER_ID_SQL + ")";

    @Query("SELECT * FROM training_technique WHERE " + VISIBLE_SQL + " ORDER BY scope, sort_order")
    LiveData<List<TrainingTechniqueEntity>> observeVisible();

    /** One-shot read for background work (validation, saving). */
    @Query("SELECT * FROM training_technique WHERE " + VISIBLE_SQL + " ORDER BY scope, sort_order")
    List<TrainingTechniqueEntity> findVisible();
}

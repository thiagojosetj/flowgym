package io.github.thiagojosetj.gym.data.local.dao;

import static io.github.thiagojosetj.gym.data.local.entity.AppMetadataEntity.CURRENT_USER_ID_SQL;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.Upsert;

import java.util.List;

import io.github.thiagojosetj.gym.data.local.entity.UserSettingEntity;

/** Account-level preferences (they will sync per key: docs/SYNC.md §6). */
@Dao
public interface UserSettingDao {

    @Query("SELECT * FROM user_setting WHERE owner_user_id = " + CURRENT_USER_ID_SQL)
    LiveData<List<UserSettingEntity>> observeCurrentUser();

    @Query("SELECT * FROM user_setting WHERE owner_user_id = " + CURRENT_USER_ID_SQL)
    List<UserSettingEntity> findCurrentUser();

    @Upsert
    void upsert(UserSettingEntity setting);
}

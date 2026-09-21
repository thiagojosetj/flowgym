package io.github.thiagojosetj.gym.data.local.dao;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import io.github.thiagojosetj.gym.data.local.entity.UserProfileEntity;

@Dao
public interface UserProfileDao {

    @Insert
    void insert(UserProfileEntity user);

    @Nullable
    @Query("SELECT * FROM user_profile WHERE id = :id")
    UserProfileEntity findById(String id);
}

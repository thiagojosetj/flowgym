package io.github.thiagojosetj.gym.data.local.dao;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.Upsert;

import io.github.thiagojosetj.gym.data.local.entity.AppMetadataEntity;

@Dao
public interface MetadataDao {

    @Nullable
    @Query("SELECT meta_value FROM app_metadata WHERE meta_key = :key")
    String get(String key);

    @Upsert
    void put(AppMetadataEntity entry);
}

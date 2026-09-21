package io.github.thiagojosetj.gym.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Catalog node of the muscle hierarchy (ADR-0013). {@code parent_id == null} is a group
 * ("Costas"); otherwise a subgroup ("Latíssimo do dorso").
 */
@Entity(
        tableName = "muscle",
        foreignKeys = @ForeignKey(
                entity = MuscleEntity.class,
                parentColumns = "id",
                childColumns = "parent_id"),
        indices = {
                @Index(value = "code", unique = true),
                @Index("parent_id")
        })
public class MuscleEntity {

    @PrimaryKey
    @NonNull
    public String id = "";

    @Nullable
    @ColumnInfo(name = "parent_id")
    public String parentId;

    @NonNull
    public String code = "";

    @NonNull
    public String name = "";

    @ColumnInfo(name = "sort_order")
    public int sortOrder;
}

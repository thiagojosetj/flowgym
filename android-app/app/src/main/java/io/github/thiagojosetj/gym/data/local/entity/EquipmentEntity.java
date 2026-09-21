package io.github.thiagojosetj.gym.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Catalog of equipment (barra, halteres, polia...). */
@Entity(tableName = "equipment", indices = @Index(value = "code", unique = true))
public class EquipmentEntity {

    @PrimaryKey
    @NonNull
    public String id = "";

    @NonNull
    public String code = "";

    @NonNull
    public String name = "";

    @ColumnInfo(name = "sort_order")
    public int sortOrder;
}

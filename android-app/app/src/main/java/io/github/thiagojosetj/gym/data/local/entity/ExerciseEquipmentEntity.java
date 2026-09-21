package io.github.thiagojosetj.gym.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;

/** Equipment used by an exercise (many-to-many). */
@Entity(
        tableName = "exercise_equipment",
        primaryKeys = {"exercise_id", "equipment_id"},
        foreignKeys = {
                @ForeignKey(entity = ExerciseEntity.class, parentColumns = "id",
                        childColumns = "exercise_id", onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = EquipmentEntity.class, parentColumns = "id",
                        childColumns = "equipment_id")
        },
        indices = @Index("equipment_id"))
public class ExerciseEquipmentEntity {

    @NonNull
    @ColumnInfo(name = "exercise_id")
    public String exerciseId = "";

    @NonNull
    @ColumnInfo(name = "equipment_id")
    public String equipmentId = "";

    @ColumnInfo(name = "is_primary")
    public boolean isPrimary;

    public ExerciseEquipmentEntity() {
    }

    @Ignore
    public ExerciseEquipmentEntity(@NonNull String exerciseId, @NonNull String equipmentId, boolean isPrimary) {
        this.exerciseId = exerciseId;
        this.equipmentId = equipmentId;
        this.isPrimary = isPrimary;
    }
}

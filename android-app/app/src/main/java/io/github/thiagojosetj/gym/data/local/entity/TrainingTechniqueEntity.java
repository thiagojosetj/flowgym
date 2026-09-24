package io.github.thiagojosetj.gym.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import io.github.thiagojosetj.gym.domain.technique.TechniqueScope;

/**
 * Training method (warm-up, drop-set, superset…) as DATA, not as an enum (ADR-0009): new methods
 * ship with a catalog update, no new app version.
 *
 * <p>{@code owner_user_id == null} means the system catalog; user-created techniques (future) will
 * carry an owner. The unique index is on {@code code} alone while only system rows exist — see
 * docs/DATABASE.md before allowing custom techniques.
 */
@Entity(
        tableName = "training_technique",
        indices = {
                @Index(value = "code", unique = true),
                @Index("owner_user_id")
        })
public class TrainingTechniqueEntity {

    @PrimaryKey
    @NonNull
    public String id = "";

    @Nullable
    @ColumnInfo(name = "owner_user_id")
    public String ownerUserId;

    /** Short badge shown next to a set: AQ, D, RP, MR, CL, F, PIR, PIV, SS, BI, TRI, GS. */
    @NonNull
    public String code = "";

    @NonNull
    public String name = "";

    @NonNull
    public TechniqueScope scope = TechniqueScope.SET;

    /** False for warm-up: those sets stay out of volume and records. */
    @ColumnInfo(name = "counts_as_working_set")
    public boolean countsAsWorkingSet = true;

    @Nullable
    public String description;

    /** Shown by the ⓘ button next to the badge. */
    @Nullable
    public String instructions;

    /** JSON with the parameters a technique may need (drops, mini-sets…); unused for now. */
    @Nullable
    @ColumnInfo(name = "params_schema")
    public String paramsSchema;

    @ColumnInfo(name = "sort_order")
    public int sortOrder;

    @ColumnInfo(name = "is_active")
    public boolean isActive = true;

    @ColumnInfo(name = "created_at")
    public long createdAt;

    @ColumnInfo(name = "updated_at")
    public long updatedAt;
}

package io.github.thiagojosetj.gym.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * A group of exercises inside a template: superset, bi-set, tri-set or giant set
 * (PRODUCT_SPEC section 6.3). Child of the template aggregate, so no sync columns of its own.
 *
 * <p>The rest belongs to the <b>round</b>, not to each exercise: in an A1/A2 superset the rest
 * starts once the round is finished, which is why it lives here and not on the exercises.
 */
@Entity(
        tableName = "template_exercise_group",
        foreignKeys = {
                @ForeignKey(entity = WorkoutTemplateEntity.class, parentColumns = "id",
                        childColumns = "template_id", onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = TrainingTechniqueEntity.class, parentColumns = "id",
                        childColumns = "technique_id")
        },
        indices = {@Index("template_id"), @Index("technique_id")})
public class TemplateExerciseGroupEntity {

    @PrimaryKey
    @NonNull
    public String id = "";

    @NonNull
    @ColumnInfo(name = "template_id")
    public String templateId = "";

    /** What the screen shows before the number: "A" gives A1, A2 (PRODUCT_SPEC section 6.3). */
    @NonNull
    public String label = "";

    /** Group-scope technique (SS, BI, TRI, GS), or null for a plain grouping. */
    @Nullable
    @ColumnInfo(name = "technique_id")
    public String techniqueId;

    /** Rest after the whole round, in seconds. */
    @ColumnInfo(name = "rest_after_round_s")
    public int restAfterRoundSeconds;

    public int position;
}

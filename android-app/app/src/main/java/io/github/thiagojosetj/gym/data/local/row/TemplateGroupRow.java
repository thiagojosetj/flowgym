package io.github.thiagojosetj.gym.data.local.row;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A group of a template with the badge of its technique already joined in (maps to the domain
 * ExerciseGroup). The code is read here to be shown by the editor and to be copied into the session
 * snapshot - after that the session never looks it up again (docs/DATABASE.md section 2b).
 */
public class TemplateGroupRow {

    @NonNull
    public String id = "";

    /** The letter of the group; "A" gives A1, A2 (PRODUCT_SPEC section 6.3). */
    @NonNull
    public String label = "";

    @Nullable
    public String techniqueId;

    /** Badge of the group technique (SS, BI, TRI, GS); null for a plain grouping. */
    @Nullable
    public String techniqueCode;

    public int restAfterRoundSeconds;

    public int position;
}

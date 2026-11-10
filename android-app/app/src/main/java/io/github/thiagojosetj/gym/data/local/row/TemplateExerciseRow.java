package io.github.thiagojosetj.gym.data.local.row;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Embedded;

import io.github.thiagojosetj.gym.domain.model.SideMode;

/** A template exercise joined with the exercise fields the editor needs. */
public class TemplateExerciseRow {

    @NonNull
    public String id = "";

    public int position;

    public int restSeconds;

    @Nullable
    public String notes;

    @NonNull
    public SideMode sideMode = SideMode.COMBINED;

    /** The group this exercise is in (A1, A2), or null when it stands alone. */
    @Nullable
    public String groupId;

    @Embedded(prefix = "ex_")
    @NonNull
    public ExerciseRefRow exercise = new ExerciseRefRow();
}

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

    @Embedded(prefix = "ex_")
    @NonNull
    public ExerciseRefRow exercise = new ExerciseRefRow();
}

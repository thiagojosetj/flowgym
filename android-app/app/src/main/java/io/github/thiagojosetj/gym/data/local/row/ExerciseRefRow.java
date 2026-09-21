package io.github.thiagojosetj.gym.data.local.row;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.thiagojosetj.gym.domain.model.Laterality;
import io.github.thiagojosetj.gym.domain.model.LoadBasis;
import io.github.thiagojosetj.gym.domain.model.TrackingType;

/** What a template needs to know about an exercise (maps to the domain ExerciseRef). */
public class ExerciseRefRow {

    @NonNull
    public String id = "";

    @NonNull
    public String name = "";

    @NonNull
    public TrackingType trackingType = TrackingType.WEIGHT_REPS;

    @NonNull
    public LoadBasis loadBasis = LoadBasis.TOTAL;

    public int implementCount = 1;

    @NonNull
    public Laterality laterality = Laterality.BILATERAL;

    @Nullable
    public String primaryMuscleName;
}

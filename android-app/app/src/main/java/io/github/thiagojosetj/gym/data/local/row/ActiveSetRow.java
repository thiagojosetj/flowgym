package io.github.thiagojosetj.gym.data.local.row;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.thiagojosetj.gym.domain.model.Laterality;
import io.github.thiagojosetj.gym.domain.model.LoadBasis;
import io.github.thiagojosetj.gym.domain.model.SideMode;
import io.github.thiagojosetj.gym.domain.model.TrackingType;
import io.github.thiagojosetj.gym.domain.session.SetStatus;

/**
 * One row of the active-workout query: the session exercise (with its snapshots) and one of its sets.
 *
 * <p>Flat on purpose: one query for the whole list instead of a nested graph of relations. The set
 * fields are boxed because the join is a LEFT JOIN — an exercise with no sets still shows up.
 */
public class ActiveSetRow {

    // ---- session_exercise

    @NonNull
    public String sessionExerciseId = "";

    public int exercisePosition;

    @NonNull
    public String exerciseId = "";

    @NonNull
    public String exerciseName = "";

    @NonNull
    public TrackingType trackingType = TrackingType.WEIGHT_REPS;

    @NonNull
    public LoadBasis loadBasis = LoadBasis.TOTAL;

    public int implementCount;

    @NonNull
    public Laterality laterality = Laterality.BILATERAL;

    @NonNull
    public SideMode sideMode = SideMode.COMBINED;

    public int exerciseRestSeconds;

    @Nullable
    public String permanentNotes;

    @Nullable
    public String exerciseNotes;

    /** Catalog code of the primary equipment, so the UI can say "kg por halter". */
    @Nullable
    public String primaryEquipmentCode;

    // ---- set_log

    @Nullable
    public String setId;

    @Nullable
    public Integer setPosition;

    @Nullable
    public String techniqueId;

    @Nullable
    public String techniqueCode;

    /** 0 for a warm-up: it stays out of volume and records. */
    @Nullable
    public Integer techniqueCountsAsWorkingSet;

    @Nullable
    public Integer plannedRepsMin;

    @Nullable
    public Integer plannedRepsMax;

    @Nullable
    public Long plannedWeightGrams;

    @Nullable
    public Integer plannedDurationSeconds;

    @Nullable
    public Integer plannedRestSeconds;

    @Nullable
    public Long weightGrams;

    @Nullable
    public Integer reps;

    @Nullable
    public Integer repsLeft;

    @Nullable
    public Integer repsRight;

    @Nullable
    public Integer durationSeconds;

    @Nullable
    public SetStatus status;

    @Nullable
    public Long completedAt;

    @Nullable
    public String setNotes;

    /**
     * Frozen pointer to the same exercise in the previous session. Its sets are read separately
     * (see PreviousSetRow) because they pair by ordinal among working sets, not by raw position.
     */
    @Nullable
    public String previousSessionExerciseId;
}

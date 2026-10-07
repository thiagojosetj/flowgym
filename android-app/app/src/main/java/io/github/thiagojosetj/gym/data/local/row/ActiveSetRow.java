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

    // ---- session_exercise_group (all null when the exercise stands alone)

    /**
     * The group this exercise was in when the session started. Every field below it comes from the
     * session's own snapshot of the group, never from the template: editing or deleting the
     * template's group afterwards must not change what this session says happened.
     */
    @Nullable
    public String groupId;

    /** What the screen shows before the exercise number: "A" gives A1, A2. */
    @Nullable
    public String groupLabel;

    @Nullable
    public String groupTechniqueId;

    /** The badge as it was stored with the group, not looked up in the catalog. */
    @Nullable
    public String groupTechniqueCode;

    /** The rest that belongs to the round, once every exercise of the group has settled it. */
    @Nullable
    public Integer groupRestAfterRoundSeconds;

    @Nullable
    public Integer groupPosition;

    // ---- set_log

    @Nullable
    public String setId;

    @Nullable
    public Integer setPosition;

    /**
     * Null for a set; the owning set's id for a drop-set or rest-pause segment (ADR-0037). The
     * mapper nests the segments into their set - they are never rows of their own on screen, and
     * they must never reach the working-set numbering, which counts sets.
     */
    @Nullable
    public String parentSetId;

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

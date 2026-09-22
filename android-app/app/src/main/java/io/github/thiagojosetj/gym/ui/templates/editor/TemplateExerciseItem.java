package io.github.thiagojosetj.gym.ui.templates.editor;

import io.github.thiagojosetj.gym.domain.model.LoadBasis;
import io.github.thiagojosetj.gym.domain.model.RepRange;
import io.github.thiagojosetj.gym.domain.model.SideMode;
import io.github.thiagojosetj.gym.domain.model.TrackingType;
import io.github.thiagojosetj.gym.domain.model.Weight;
import io.github.thiagojosetj.gym.domain.template.TemplateExerciseDraft;

/**
 * Immutable snapshot of one template exercise for the UI. The domain draft is mutable, so the list
 * adapter must never hold it directly: DiffUtil compares old and new snapshots.
 *
 * @param uniformPlan false when sets differ (future per-set editing); the card then says "variados"
 */
public record TemplateExerciseItem(
        String id,
        String exerciseId,
        String name,
        String primaryMuscleName,
        TrackingType trackingType,
        LoadBasis loadBasis,
        String primaryEquipmentCode,
        boolean unilateral,
        int setCount,
        RepRange reps,
        Weight weight,
        Integer durationSeconds,
        boolean uniformPlan,
        int restSeconds,
        String notes,
        SideMode sideMode) {

    static TemplateExerciseItem from(TemplateExerciseDraft draft) {
        return new TemplateExerciseItem(
                draft.id(),
                draft.exercise().id(),
                draft.exercise().name(),
                draft.exercise().primaryMuscleName(),
                draft.exercise().trackingType(),
                draft.exercise().loadBasis(),
                draft.exercise().primaryEquipmentCode(),
                draft.exercise().isUnilateral(),
                draft.setCount(),
                draft.uniformReps(),
                draft.uniformWeight(),
                draft.uniformDurationSeconds(),
                draft.hasUniformPlan(),
                draft.restSeconds(),
                draft.notes(),
                draft.sideMode());
    }
}

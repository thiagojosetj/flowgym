package io.github.thiagojosetj.gym.ui.templates.editor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.thiagojosetj.gym.domain.model.LoadBasis;
import io.github.thiagojosetj.gym.domain.model.RepRange;
import io.github.thiagojosetj.gym.domain.model.SideMode;
import io.github.thiagojosetj.gym.domain.model.TrackingType;
import io.github.thiagojosetj.gym.domain.model.Weight;
import io.github.thiagojosetj.gym.domain.technique.TechniqueCatalog;
import io.github.thiagojosetj.gym.domain.technique.TrainingTechnique;
import io.github.thiagojosetj.gym.domain.template.SetPlan;
import io.github.thiagojosetj.gym.domain.template.TemplateExerciseDraft;

/**
 * Immutable snapshot of one template exercise for the UI. The domain draft is mutable, so the list
 * adapter must never hold it directly: DiffUtil compares old and new snapshots.
 *
 * @param uniformPlan false when sets differ; the card then says "valores variados"
 * @param sets        every planned set, so the plan sheet can edit them one by one
 * @param badges      distinct technique codes in order, for the card ("AQ", "D")
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
        SideMode sideMode,
        List<TemplateSetItem> sets,
        List<String> badges) {

    public TemplateExerciseItem {
        sets = sets == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(sets));
        badges = badges == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(badges));
    }

    static TemplateExerciseItem from(TemplateExerciseDraft draft, TechniqueCatalog techniques) {
        List<TemplateSetItem> sets = new ArrayList<>(draft.setCount());
        List<String> badges = new ArrayList<>();
        for (SetPlan set : draft.sets()) {
            TrainingTechnique technique = techniques.byId(set.techniqueId());
            String code = technique == null ? null : technique.code();
            sets.add(new TemplateSetItem(set.id(), set.reps(), set.weight(), set.durationSeconds(),
                    set.techniqueId(), code));
            if (code != null && !badges.contains(code)) {
                badges.add(code);
            }
        }
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
                draft.sideMode(),
                sets,
                badges);
    }
}

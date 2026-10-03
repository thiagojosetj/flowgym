package io.github.thiagojosetj.gym.ui.templates.editor;

import androidx.annotation.Nullable;

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
 * @param badges      distinct techniques in use, in order, for the card ("AQ", "D")
 * @param group       where the exercise sits in its group (superset), or null when it stands alone
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
        List<Badge> badges,
        @Nullable GroupMark group) {

    /** A technique shown on the card: the short code is displayed, the name is what TalkBack reads. */
    public record Badge(String code, String name) {
    }

    /**
     * Where an exercise sits in its group (PRODUCT_SPEC section 6.3): label "A" and place 2 read
     * "A2". Both come from the data layer's answer and the order of the cards, so nothing here
     * decides a letter.
     *
     * @param groupId               what "ungroup" acts on
     * @param label                 the group's letter, as the data layer derived it
     * @param number                1-based place among the members, in the order of the cards
     * @param restAfterRoundSeconds the rest that belongs to the round, which is the rest a grouped
     *                              exercise really gets: its own is not used while in a group
     */
    public record GroupMark(String groupId, String label, int number, int restAfterRoundSeconds) {

        /** "A1", "A2": what is written before the name. */
        public String code() {
            return label + number;
        }
    }

    public TemplateExerciseItem {
        sets = sets == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(sets));
        badges = badges == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(badges));
    }

    /** An exercise that stands alone: the shape every card had before groups. */
    public TemplateExerciseItem(String id, String exerciseId, String name,
                                String primaryMuscleName, TrackingType trackingType,
                                LoadBasis loadBasis, String primaryEquipmentCode,
                                boolean unilateral, int setCount, RepRange reps, Weight weight,
                                Integer durationSeconds, boolean uniformPlan, int restSeconds,
                                String notes, SideMode sideMode, List<TemplateSetItem> sets,
                                List<Badge> badges) {
        this(id, exerciseId, name, primaryMuscleName, trackingType, loadBasis,
                primaryEquipmentCode, unilateral, setCount, reps, weight, durationSeconds,
                uniformPlan, restSeconds, notes, sideMode, sets, badges, null);
    }

    static TemplateExerciseItem from(TemplateExerciseDraft draft, TechniqueCatalog techniques,
                                     @Nullable GroupMark group) {
        List<TemplateSetItem> sets = new ArrayList<>(draft.setCount());
        List<Badge> badges = new ArrayList<>();
        for (SetPlan set : draft.sets()) {
            TrainingTechnique technique = techniques.byId(set.techniqueId());
            String code = technique == null ? null : technique.code();
            sets.add(new TemplateSetItem(set.id(), set.reps(), set.weight(), set.durationSeconds(),
                    set.techniqueId(), code));
            if (technique != null) {
                Badge badge = new Badge(code, technique.name());
                if (!badges.contains(badge)) {
                    badges.add(badge);
                }
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
                badges,
                group);
    }
}

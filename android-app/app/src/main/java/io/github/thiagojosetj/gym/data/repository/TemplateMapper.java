package io.github.thiagojosetj.gym.data.repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.thiagojosetj.gym.data.local.entity.TemplateExerciseEntity;
import io.github.thiagojosetj.gym.data.local.entity.TemplateSetEntity;
import io.github.thiagojosetj.gym.data.local.entity.WorkoutTemplateEntity;
import io.github.thiagojosetj.gym.data.local.row.ExerciseRefRow;
import io.github.thiagojosetj.gym.data.local.row.TemplateExerciseRow;
import io.github.thiagojosetj.gym.data.local.row.TemplateSummaryRow;
import io.github.thiagojosetj.gym.domain.model.RepRange;
import io.github.thiagojosetj.gym.domain.model.Weight;
import io.github.thiagojosetj.gym.domain.template.ExerciseRef;
import io.github.thiagojosetj.gym.domain.template.SetPlan;
import io.github.thiagojosetj.gym.domain.template.TemplateDraft;
import io.github.thiagojosetj.gym.domain.template.TemplateExerciseDraft;
import io.github.thiagojosetj.gym.domain.template.TemplateSummary;
import io.github.thiagojosetj.gym.domain.util.IdGenerator;

/** Converts between Room rows/entities and domain objects for the template aggregate. */
final class TemplateMapper {

    private TemplateMapper() {
    }

    static ExerciseRef toRef(ExerciseRefRow row) {
        return new ExerciseRef(row.id, row.name, row.trackingType, row.loadBasis, row.implementCount,
                row.laterality, row.primaryMuscleName, row.primaryEquipmentCode);
    }

    static TemplateSummary toSummary(TemplateSummaryRow row) {
        List<String> groups = new ArrayList<>();
        if (row.muscleGroups != null) {
            for (String name : row.muscleGroups.split(",")) {
                String trimmed = name.trim();
                if (!trimmed.isEmpty()) {
                    groups.add(trimmed);
                }
            }
        }
        return new TemplateSummary(row.id, row.name, row.description, row.exerciseCount, row.setCount, groups);
    }

    static TemplateDraft toDraft(WorkoutTemplateEntity root, List<TemplateExerciseRow> exerciseRows,
                                 List<TemplateSetEntity> setRows, IdGenerator ids) {
        Map<String, List<SetPlan>> setsByExercise = new LinkedHashMap<>();
        for (TemplateSetEntity s : setRows) {
            setsByExercise.computeIfAbsent(s.templateExerciseId, key -> new ArrayList<>()).add(toSetPlan(s));
        }
        List<TemplateExerciseDraft> exercises = new ArrayList<>(exerciseRows.size());
        for (TemplateExerciseRow row : exerciseRows) {
            List<SetPlan> sets = setsByExercise.get(row.id);
            exercises.add(TemplateExerciseDraft.restore(row.id, toRef(row.exercise), row.restSeconds, row.notes,
                    row.sideMode, sets == null ? new ArrayList<>() : sets));
        }
        return TemplateDraft.existing(root.id, root.name, root.description, root.notes, exercises, ids);
    }

    private static SetPlan toSetPlan(TemplateSetEntity s) {
        RepRange reps = null;
        if (s.targetRepsMin != null) {
            int max = s.targetRepsMax != null ? s.targetRepsMax : s.targetRepsMin;
            reps = RepRange.between(s.targetRepsMin, max);
        }
        Weight weight = s.targetWeightGrams != null ? Weight.ofGrams(s.targetWeightGrams) : null;
        return new SetPlan(s.id, reps, weight, s.targetDurationSeconds, s.restSeconds, s.techniqueId);
    }

    /** Children of the aggregate with positions from the draft order. Pure: safe on any thread. */
    static List<TemplateExerciseEntity> toExerciseEntities(TemplateDraft draft) {
        List<TemplateExerciseEntity> result = new ArrayList<>();
        int position = 0;
        for (TemplateExerciseDraft e : draft.exercises()) {
            TemplateExerciseEntity entity = new TemplateExerciseEntity();
            entity.id = e.id();
            entity.templateId = draft.id();
            entity.exerciseId = e.exercise().id();
            entity.position = position++;
            entity.restSeconds = e.restSeconds();
            entity.notes = e.notes();
            entity.sideMode = e.sideMode();
            result.add(entity);
        }
        return result;
    }

    static List<TemplateSetEntity> toSetEntities(TemplateDraft draft) {
        List<TemplateSetEntity> result = new ArrayList<>();
        for (TemplateExerciseDraft e : draft.exercises()) {
            int position = 0;
            for (SetPlan set : e.sets()) {
                TemplateSetEntity entity = new TemplateSetEntity();
                entity.id = set.id();
                entity.templateExerciseId = e.id();
                entity.position = position++;
                entity.targetRepsMin = set.reps() != null ? set.reps().min() : null;
                entity.targetRepsMax = set.reps() != null ? set.reps().max() : null;
                entity.targetWeightGrams = set.weight() != null ? set.weight().grams() : null;
                entity.targetDurationSeconds = set.durationSeconds();
                entity.restSeconds = set.restSecondsOverride();
                entity.techniqueId = set.techniqueId();
                result.add(entity);
            }
        }
        return result;
    }
}

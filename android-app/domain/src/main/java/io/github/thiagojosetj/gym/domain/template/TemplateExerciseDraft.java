package io.github.thiagojosetj.gym.domain.template;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import io.github.thiagojosetj.gym.domain.model.RepRange;
import io.github.thiagojosetj.gym.domain.model.SideMode;
import io.github.thiagojosetj.gym.domain.model.Weight;

/**
 * One exercise inside a {@link TemplateDraft}. Mutations are package-private on purpose: they must
 * go through the aggregate root, which enforces the invariants and tracks unsaved changes.
 */
public final class TemplateExerciseDraft {

    private final String id;
    private final ExerciseRef exercise;
    private int restSeconds;
    private String notes;
    private SideMode sideMode;
    private final List<SetPlan> sets;

    TemplateExerciseDraft(String id, ExerciseRef exercise, int restSeconds, String notes,
                          SideMode sideMode, List<SetPlan> sets) {
        this.id = Objects.requireNonNull(id, "id");
        this.exercise = Objects.requireNonNull(exercise, "exercise");
        this.restSeconds = restSeconds;
        this.notes = notes;
        this.sideMode = Objects.requireNonNull(sideMode, "sideMode");
        this.sets = new ArrayList<>(sets);
    }

    /** Rebuilds a persisted exercise (used by the data layer when loading a template). */
    public static TemplateExerciseDraft restore(String id, ExerciseRef exercise, int restSeconds,
                                                String notes, SideMode sideMode, List<SetPlan> sets) {
        return new TemplateExerciseDraft(id, exercise, restSeconds, notes, sideMode, sets);
    }

    public String id() {
        return id;
    }

    public ExerciseRef exercise() {
        return exercise;
    }

    public int restSeconds() {
        return restSeconds;
    }

    public String notes() {
        return notes;
    }

    public SideMode sideMode() {
        return sideMode;
    }

    public List<SetPlan> sets() {
        return Collections.unmodifiableList(sets);
    }

    public int setCount() {
        return sets.size();
    }

    /** The rep target shared by every set, or null if sets differ or have no target. */
    public RepRange uniformReps() {
        if (sets.isEmpty()) {
            return null;
        }
        RepRange first = sets.get(0).reps();
        for (SetPlan set : sets) {
            if (!Objects.equals(first, set.reps())) {
                return null;
            }
        }
        return first;
    }

    /** The planned load shared by every set, or null if sets differ or have no load. */
    public Weight uniformWeight() {
        if (sets.isEmpty()) {
            return null;
        }
        Weight first = sets.get(0).weight();
        for (SetPlan set : sets) {
            if (!Objects.equals(first, set.weight())) {
                return null;
            }
        }
        return first;
    }

    /** The planned duration shared by every set, or null. */
    public Integer uniformDurationSeconds() {
        if (sets.isEmpty()) {
            return null;
        }
        Integer first = sets.get(0).durationSeconds();
        for (SetPlan set : sets) {
            if (!Objects.equals(first, set.durationSeconds())) {
                return null;
            }
        }
        return first;
    }

    /**
     * Applies the same plan to all sets. Existing set ids are reused in order so references stay
     * stable; extra sets get new ids; surplus sets are dropped.
     */
    void applyUniformPlan(ExercisePlanUpdate update, List<String> newIds) {
        List<SetPlan> rebuilt = new ArrayList<>(update.setCount());
        int nextNewId = 0;
        for (int i = 0; i < update.setCount(); i++) {
            String setId = i < sets.size() ? sets.get(i).id() : newIds.get(nextNewId++);
            rebuilt.add(new SetPlan(setId, update.reps(), update.weight(), update.durationSeconds(), null));
        }
        sets.clear();
        sets.addAll(rebuilt);
        restSeconds = update.restSeconds();
        notes = blankToNull(update.notes());
        sideMode = update.sideMode();
    }

    static String blankToNull(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

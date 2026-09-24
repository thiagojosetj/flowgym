package io.github.thiagojosetj.gym.domain.template;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import io.github.thiagojosetj.gym.domain.model.SideMode;
import io.github.thiagojosetj.gym.domain.technique.TechniqueCatalog;
import io.github.thiagojosetj.gym.domain.util.IdGenerator;

/**
 * Editable workout template: the aggregate root used by the template editor (ADR-0019).
 *
 * <p>All changes go through this class so that the rules live in one place and are tested on the
 * JVM: defaults when adding exercises (3 × 12), stable ids for children, ordering, limits and
 * validation. The data layer saves the whole aggregate at once.
 */
public final class TemplateDraft {

    /** Aggregate-level validation problems. The UI maps each code to a localized message. */
    public enum Error {
        NAME_BLANK,
        NAME_TOO_LONG,
        DESCRIPTION_TOO_LONG,
        NOTES_TOO_LONG,
        NO_EXERCISES,
        TOO_MANY_EXERCISES
    }

    private final String id;
    private boolean isNew;
    private final IdGenerator ids;
    private String name;
    private String description;
    private String notes;
    private final List<TemplateExerciseDraft> exercises;
    private boolean modified;

    private TemplateDraft(String id, boolean isNew, IdGenerator ids, String name, String description,
                          String notes, List<TemplateExerciseDraft> exercises) {
        this.id = Objects.requireNonNull(id, "id");
        this.isNew = isNew;
        this.ids = Objects.requireNonNull(ids, "ids");
        this.name = name == null ? "" : name;
        this.description = description;
        this.notes = notes;
        this.exercises = new ArrayList<>(exercises);
    }

    /** A brand-new, empty template that has never been saved. */
    public static TemplateDraft newTemplate(IdGenerator ids) {
        return new TemplateDraft(ids.newId(), true, ids, "", null, null, Collections.emptyList());
    }

    /** Rebuilds a persisted template (used by the data layer when loading). */
    public static TemplateDraft existing(String id, String name, String description, String notes,
                                         List<TemplateExerciseDraft> exercises, IdGenerator ids) {
        return new TemplateDraft(id, false, ids, name, description, notes, exercises);
    }

    // ------------------------------------------------------------------ reading

    public String id() {
        return id;
    }

    /** True until the first save; decides between "create" and "update" in the repository. */
    public boolean isNew() {
        return isNew;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public String notes() {
        return notes;
    }

    public List<TemplateExerciseDraft> exercises() {
        return Collections.unmodifiableList(exercises);
    }

    public TemplateExerciseDraft findExercise(String templateExerciseId) {
        for (TemplateExerciseDraft exercise : exercises) {
            if (exercise.id().equals(templateExerciseId)) {
                return exercise;
            }
        }
        return null;
    }

    /** True when there are changes since loading or the last {@link #markSaved()}. */
    public boolean isModified() {
        return modified;
    }

    /** Called after a successful save: the draft now exists in storage and has no pending changes. */
    public void markSaved() {
        isNew = false;
        modified = false;
    }

    // ------------------------------------------------------------------ editing

    public void rename(String newName) {
        String value = newName == null ? "" : newName.trim();
        if (!value.equals(name)) {
            name = value;
            modified = true;
        }
    }

    public void setDescription(String text) {
        String value = TemplateExerciseDraft.blankToNull(text);
        if (!Objects.equals(value, description)) {
            description = value;
            modified = true;
        }
    }

    public void setNotes(String text) {
        String value = TemplateExerciseDraft.blankToNull(text);
        if (!Objects.equals(value, notes)) {
            notes = value;
            modified = true;
        }
    }

    /**
     * Appends exercises with the default plan (PRODUCT_SPEC TPL-02). The same library exercise may
     * appear more than once (e.g. a back-off block at the end), so duplicates are allowed.
     *
     * @return the created entries, in order
     * @throws IllegalStateException if the template would exceed {@link TemplateRules#MAX_EXERCISES}
     */
    public List<TemplateExerciseDraft> addExercises(List<ExerciseRef> refs, TemplateDefaults defaults) {
        if (exercises.size() + refs.size() > TemplateRules.MAX_EXERCISES) {
            throw new IllegalStateException("Too many exercises");
        }
        List<TemplateExerciseDraft> added = new ArrayList<>(refs.size());
        for (ExerciseRef ref : refs) {
            List<SetPlan> sets = new ArrayList<>(defaults.setCount());
            for (int i = 0; i < defaults.setCount(); i++) {
                sets.add(defaultSet(ref, defaults));
            }
            TemplateExerciseDraft entry = new TemplateExerciseDraft(
                    ids.newId(), ref, defaults.restSeconds(), null, SideMode.COMBINED, sets);
            exercises.add(entry);
            added.add(entry);
        }
        if (!added.isEmpty()) {
            modified = true;
        }
        return Collections.unmodifiableList(added);
    }

    private SetPlan defaultSet(ExerciseRef ref, TemplateDefaults defaults) {
        boolean timed = ref.trackingType().usesDuration() && !ref.trackingType().usesReps();
        return new SetPlan(
                ids.newId(),
                ref.trackingType().usesReps() ? defaults.reps() : null,
                null,
                timed ? defaults.durationSeconds() : null,
                null,
                null);
    }

    public void removeExercise(String templateExerciseId) {
        if (exercises.removeIf(e -> e.id().equals(templateExerciseId))) {
            modified = true;
        }
    }

    /** Moves an exercise (drag and drop). Indexes are positions in {@link #exercises()}. */
    public void moveExercise(int fromIndex, int toIndex) {
        if (fromIndex < 0 || fromIndex >= exercises.size() || toIndex < 0 || toIndex >= exercises.size()) {
            throw new IndexOutOfBoundsException("Cannot move " + fromIndex + " -> " + toIndex);
        }
        if (fromIndex == toIndex) {
            return;
        }
        TemplateExerciseDraft moved = exercises.remove(fromIndex);
        exercises.add(toIndex, moved);
        modified = true;
    }

    /**
     * Applies a new plan (one spec per set) to one exercise.
     *
     * @param techniques techniques available for validation (see {@link TechniqueCatalog})
     * @return validation errors; the draft is only changed when the list is empty
     */
    public List<ExercisePlanUpdate.Error> updateExercisePlan(String templateExerciseId,
                                                             ExercisePlanUpdate update,
                                                             TechniqueCatalog techniques) {
        TemplateExerciseDraft target = findExercise(templateExerciseId);
        if (target == null) {
            throw new IllegalArgumentException("Unknown template exercise: " + templateExerciseId);
        }
        List<ExercisePlanUpdate.Error> errors = update.validateFor(target.exercise(), techniques);
        if (!errors.isEmpty()) {
            return errors;
        }
        int missing = Math.max(0, update.setCount() - target.setCount());
        List<String> newIds = new ArrayList<>(missing);
        for (int i = 0; i < missing; i++) {
            newIds.add(ids.newId());
        }
        if (target.applyPlan(update, newIds)) {
            modified = true; // re-applying the same plan is not an unsaved change
        }
        return errors;
    }

    /**
     * A new, unsaved copy with fresh ids for the template, its exercises and sets. The copy is
     * independent: later edits to either template never affect the other.
     *
     * @param copySuffix localized suffix appended to this template's name, e.g. " (cópia)". The
     *                   base name is shortened when needed so the copy still respects
     *                   {@link TemplateRules#MAX_NAME_LENGTH}: a duplicate is always a valid draft.
     */
    public TemplateDraft duplicate(String copySuffix) {
        String copyName = copyName(name, copySuffix);
        List<TemplateExerciseDraft> copies = new ArrayList<>(exercises.size());
        for (TemplateExerciseDraft source : exercises) {
            List<SetPlan> sets = new ArrayList<>(source.setCount());
            for (SetPlan set : source.sets()) {
                sets.add(new SetPlan(ids.newId(), set.reps(), set.weight(), set.durationSeconds(),
                        set.restSecondsOverride(), set.techniqueId()));
            }
            copies.add(new TemplateExerciseDraft(ids.newId(), source.exercise(), source.restSeconds(),
                    source.notes(), source.sideMode(), sets));
        }
        TemplateDraft copy = new TemplateDraft(ids.newId(), true, ids, copyName, description, notes, copies);
        copy.modified = true;
        return copy;
    }

    /** base + suffix, cutting the base (never the suffix) to fit the name limit. */
    static String copyName(String base, String suffix) {
        String safeBase = base == null ? "" : base.trim();
        String safeSuffix = suffix == null ? "" : suffix;
        int max = TemplateRules.MAX_NAME_LENGTH;
        if (safeSuffix.trim().length() >= max) {
            return truncate(safeSuffix.trim(), max).trim();
        }
        String fittedBase = truncate(safeBase, max - safeSuffix.length()).trim();
        return (fittedBase + safeSuffix).trim();
    }

    /** Cuts to at most {@code maxChars} UTF-16 units without splitting a surrogate pair (emoji). */
    private static String truncate(String text, int maxChars) {
        if (text.length() <= maxChars) {
            return text;
        }
        int end = Math.max(0, maxChars);
        if (end > 0 && Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end);
    }

    // ------------------------------------------------------------------ validation

    /** Aggregate-level validation. Empty list = can be saved. */
    public List<Error> validate() {
        List<Error> errors = new ArrayList<>();
        if (name.isEmpty()) {
            errors.add(Error.NAME_BLANK);
        } else if (name.length() > TemplateRules.MAX_NAME_LENGTH) {
            errors.add(Error.NAME_TOO_LONG);
        }
        if (description != null && description.length() > TemplateRules.MAX_DESCRIPTION_LENGTH) {
            errors.add(Error.DESCRIPTION_TOO_LONG);
        }
        if (notes != null && notes.length() > TemplateRules.MAX_NOTES_LENGTH) {
            errors.add(Error.NOTES_TOO_LONG);
        }
        if (exercises.isEmpty()) {
            errors.add(Error.NO_EXERCISES);
        } else if (exercises.size() > TemplateRules.MAX_EXERCISES) {
            errors.add(Error.TOO_MANY_EXERCISES);
        }
        return Collections.unmodifiableList(errors);
    }
}

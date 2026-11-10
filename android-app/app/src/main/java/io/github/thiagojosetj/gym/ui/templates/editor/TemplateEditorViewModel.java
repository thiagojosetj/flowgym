package io.github.thiagojosetj.gym.ui.templates.editor;

import android.util.Log;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.thiagojosetj.gym.core.Event;
import io.github.thiagojosetj.gym.data.repository.ExerciseRepository;
import io.github.thiagojosetj.gym.data.repository.SettingsRepository;
import io.github.thiagojosetj.gym.data.repository.TechniqueRepository;
import io.github.thiagojosetj.gym.data.repository.TemplateRepository;
import io.github.thiagojosetj.gym.domain.template.ExerciseGroup;
import io.github.thiagojosetj.gym.domain.template.ExercisePlanUpdate;
import io.github.thiagojosetj.gym.domain.technique.TechniqueCatalog;
import io.github.thiagojosetj.gym.domain.template.ExerciseRef;
import io.github.thiagojosetj.gym.domain.template.TemplateDefaults;
import io.github.thiagojosetj.gym.domain.template.TemplateDraft;
import io.github.thiagojosetj.gym.domain.template.TemplateExerciseDraft;
import io.github.thiagojosetj.gym.domain.template.TemplateRules;
import io.github.thiagojosetj.gym.domain.util.IdGenerator;
import io.github.thiagojosetj.gym.ui.templates.editor.TemplateEditorState.Status;

/**
 * Owns the {@link TemplateDraft} being edited (ADR-0019). Every change goes through the domain
 * aggregate, which applies the rules (3 × 12 defaults, limits, validation); this class only turns
 * the draft into immutable UI state and talks to the repositories.
 *
 * <p>Groups (supersets, PRODUCT_SPEC section 6.3) are the one thing that is NOT part of the draft.
 * The data layer makes and removes them at once, on exercises that are already saved, so the
 * explicit "Salvar" does not apply to them: they are read back from it into {@code groups}, and
 * the letter shown for each is whatever it answers.
 */
public final class TemplateEditorViewModel extends ViewModel {

    private static final String TAG = "TemplateEditorVM";

    /** One-shot outcomes for the screen. */
    public enum EditorEvent {
        SAVED, SAVE_FAILED, ADD_FAILED, EXERCISE_LIMIT_REACHED,
        /** A group was made, and is already stored: it does not wait for "Salvar". */
        GROUPED,
        /** A group is gone, also at once, and its exercises are still in the template. */
        UNGROUPED,
        /** Fewer than {@link TemplateRules#MIN_GROUP_SIZE} different exercises were picked. */
        GROUP_TOO_SMALL,
        /** An exercise to group exists only in this draft, and a group is made in the database. */
        GROUP_NEEDS_SAVE,
        /** The data layer refused the group, or it could not be made, removed or read back. */
        GROUP_FAILED
    }

    private final TemplateRepository templates;
    private final ExerciseRepository exercises;
    private final SettingsRepository settingsRepository;
    /** null while loading, and again if the load failed; empty only if the catalog really is empty. */
    private final MutableLiveData<TechniqueCatalog> techniqueCatalog = new MutableLiveData<>();

    private final MutableLiveData<TemplateEditorState> state = new MutableLiveData<>();
    private final MutableLiveData<Event<EditorEvent>> events = new MutableLiveData<>();

    @Nullable
    private TemplateDraft draft;
    private Status status;
    private List<TemplateExerciseItem> items = Collections.emptyList();
    private List<TemplateDraft.Error> visibleErrors = Collections.emptyList();
    /** The group of every grouped exercise, by template exercise id, as the database has it. */
    private Map<String, ExerciseGroup> groups = Collections.emptyMap();
    /** The exercises that exist in the database. A new template has none, whatever it lists. */
    private Set<String> savedExerciseIds = Collections.emptySet();
    /** Where a new group's rest starts: the user's default, until the settings say otherwise. */
    private int defaultRestSeconds = TemplateDefaults.DEFAULT_REST_SECONDS;

    public TemplateEditorViewModel(TemplateRepository templates, ExerciseRepository exercises,
                                   TechniqueRepository techniques, SettingsRepository settings,
                                   IdGenerator ids, @Nullable String templateId) {
        this.templates = templates;
        this.exercises = exercises;
        this.settingsRepository = settings;
        // Techniques are needed to validate set plans and to show badges; load them once.
        techniques.loadCatalog(techniqueCatalog::setValue, error -> {
            // Without the catalog a set that already has a technique cannot be validated, so the
            // plan sheet says so instead of rejecting the plan as "invalid technique".
            Log.w(TAG, "Could not load the technique catalog", error);
            techniqueCatalog.setValue(null);
        });
        settings.loadSettings(loaded -> defaultRestSeconds = loaded.defaultRestSeconds(),
                error -> Log.w(TAG, "Could not read the default rest", error));
        if (templateId == null) {
            draft = TemplateDraft.newTemplate(ids);
            status = Status.READY;
            publish(true);
        } else {
            status = Status.LOADING;
            publish(false);
            // The groups are read with the draft, so no card opens without the label of its group.
            templates.loadDraft(templateId, loaded -> templates.loadGroups(templateId, stored -> {
                draft = loaded;
                groups = stored;
                savedExerciseIds = exerciseIdsOf(loaded);
                status = Status.READY;
                publish(true);
            }, error -> loadFailed()), error -> loadFailed());
        }
    }

    private void loadFailed() {
        status = Status.LOAD_FAILED;
        publish(false);
    }

    public LiveData<TemplateEditorState> state() {
        return state;
    }

    public LiveData<Event<EditorEvent>> events() {
        return events;
    }

    /** Techniques available for a single set, for the plan sheet picker. */
    public LiveData<TechniqueCatalog> techniqueCatalog() {
        return techniqueCatalog;
    }

    public boolean hasUnsavedChanges() {
        return draft != null && draft.isModified();
    }

    // ------------------------------------------------------------------ edits

    public void rename(String name) {
        if (editable()) {
            draft.rename(name);
            publish(false);
        }
    }

    public void setDescription(String description) {
        if (editable()) {
            draft.setDescription(description);
            publish(false);
        }
    }

    public void setNotes(String notes) {
        if (editable()) {
            draft.setNotes(notes);
            publish(false);
        }
    }

    /** Adds exercises picked in the library, in the order they were picked, with the defaults. */
    public void addExercises(List<String> exerciseIds) {
        if (!editable() || exerciseIds == null || exerciseIds.isEmpty()) {
            return;
        }
        // The default rest comes from the user's settings (PRODUCT_SPEC §16), not from a constant.
        settingsRepository.loadSettings(settings -> exercises.loadRefs(exerciseIds, refs -> {
            if (!editable()) {
                return;
            }
            int room = TemplateRules.MAX_EXERCISES - draft.exercises().size();
            List<ExerciseRef> accepted = refs.size() > room ? new ArrayList<>(refs.subList(0, room)) : refs;
            if (accepted.size() < refs.size()) {
                events.setValue(new Event<>(EditorEvent.EXERCISE_LIMIT_REACHED));
            }
            draft.addExercises(accepted, settings.templateDefaults());
            publish(true);
        }, error -> events.setValue(new Event<>(EditorEvent.ADD_FAILED))),
                error -> events.setValue(new Event<>(EditorEvent.ADD_FAILED)));
    }

    public void removeExercise(String templateExerciseId) {
        if (editable()) {
            draft.removeExercise(templateExerciseId);
            publish(true);
        }
    }

    public void moveExercise(int fromIndex, int toIndex) {
        if (editable() && fromIndex != toIndex) {
            draft.moveExercise(fromIndex, toIndex);
            publish(true);
        }
    }

    /** @return validation errors; empty means the plan was applied */
    public List<ExercisePlanUpdate.Error> updatePlan(String templateExerciseId, ExercisePlanUpdate update) {
        if (!editable()) {
            return Collections.emptyList();
        }
        List<ExercisePlanUpdate.Error> errors =
                draft.updateExercisePlan(templateExerciseId, update, currentCatalog());
        if (errors.isEmpty()) {
            publish(true);
        }
        return errors;
    }

    @Nullable
    public TemplateExerciseItem findItem(String templateExerciseId) {
        for (TemplateExerciseItem item : items) {
            if (item.id().equals(templateExerciseId)) {
                return item;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ groups

    /** The rest a new group starts with, shown to the user before the group is made. */
    public int defaultGroupRestSeconds() {
        return defaultRestSeconds;
    }

    /**
     * True when the exercise is already in the database. A group is made there, at once, so an
     * exercise added in this editing session has to be saved before it can join one.
     */
    public boolean isSaved(String templateExerciseId) {
        return savedExerciseIds.contains(templateExerciseId);
    }

    /** The other exercises that are in no group, in the order the cards are shown. */
    public List<TemplateExerciseItem> groupCandidates(String templateExerciseId) {
        List<TemplateExerciseItem> candidates = new ArrayList<>();
        for (TemplateExerciseItem item : items) {
            if (!item.id().equals(templateExerciseId) && item.group() == null) {
                candidates.add(item);
            }
        }
        return candidates;
    }

    /**
     * Puts the exercises in a new group, a plain grouping with no technique, and writes it now.
     * The label is not an input: it comes back from the data layer, which letters every group of
     * the template.
     *
     * <p>Nothing is written, and the screen is told why, when fewer than
     * {@link TemplateRules#MIN_GROUP_SIZE} different exercises are given or one of them is not
     * saved yet. Whatever the data layer refuses on top of that ends in
     * {@link EditorEvent#GROUP_FAILED}.
     *
     * @param restAfterRoundSeconds rest that starts when the whole round is over, 0 for none
     */
    public void createGroup(List<String> templateExerciseIds, int restAfterRoundSeconds) {
        if (!editable()) {
            return;
        }
        List<String> members = new ArrayList<>(new LinkedHashSet<>(templateExerciseIds));
        if (members.size() < TemplateRules.MIN_GROUP_SIZE) {
            events.setValue(new Event<>(EditorEvent.GROUP_TOO_SMALL));
            return;
        }
        if (!savedExerciseIds.containsAll(members)) {
            events.setValue(new Event<>(EditorEvent.GROUP_NEEDS_SAVE));
            return;
        }
        templates.createGroup(draft.id(), members, null, restAfterRoundSeconds,
                groupId -> reloadGroups(EditorEvent.GROUPED),
                error -> groupFailed("create", error));
    }

    /** Ungroups: the group goes and its exercises stay in the template. Written now, as well. */
    public void removeGroup(String groupId) {
        if (!editable()) {
            return;
        }
        templates.removeGroup(draft.id(), groupId, () -> reloadGroups(EditorEvent.UNGROUPED),
                error -> groupFailed("remove", error));
    }

    private void groupFailed(String what, Throwable error) {
        Log.w(TAG, "Could not " + what + " the group", error);
        events.setValue(new Event<>(EditorEvent.GROUP_FAILED));
    }

    /**
     * Reads the groups back and redraws the cards. The data layer relettered them, so this is the
     * only way a letter gets onto the screen.
     *
     * @param done what to tell the screen once it shows the new groups, or null to say nothing
     *             (and to keep quiet if the read fails: the write it follows already succeeded)
     */
    private void reloadGroups(@Nullable EditorEvent done) {
        templates.loadGroups(draft.id(), stored -> {
            groups = stored;
            publish(true);
            if (done != null) {
                events.setValue(new Event<>(done));
            }
        }, error -> {
            Log.w(TAG, "Could not read the groups back", error);
            if (done != null) {
                events.setValue(new Event<>(EditorEvent.GROUP_FAILED));
            }
        });
    }

    // ------------------------------------------------------------------ save

    public void save() {
        if (!editable()) {
            return;
        }
        List<TemplateDraft.Error> errors = draft.validate();
        if (!errors.isEmpty()) {
            visibleErrors = errors;
            publish(false);
            return;
        }
        visibleErrors = Collections.emptyList();
        status = Status.SAVING;
        publish(false);
        templates.save(draft, savedId -> {
            draft.markSaved();
            savedExerciseIds = exerciseIdsOf(draft);
            status = Status.READY;
            publish(false);
            reloadGroups(null); // saving relettered them, and dissolved any left with one exercise
            events.setValue(new Event<>(EditorEvent.SAVED));
        }, error -> {
            status = Status.READY;
            publish(false);
            events.setValue(new Event<>(EditorEvent.SAVE_FAILED));
        });
    }

    // ------------------------------------------------------------------ internals

    private static Set<String> exerciseIdsOf(TemplateDraft template) {
        Set<String> ids = new HashSet<>();
        for (TemplateExerciseDraft exercise : template.exercises()) {
            ids.add(exercise.id());
        }
        return Collections.unmodifiableSet(ids);
    }

    private TechniqueCatalog currentCatalog() {
        TechniqueCatalog catalog = techniqueCatalog.getValue();
        return catalog == null ? TechniqueCatalog.empty() : catalog;
    }

    private boolean editable() {
        return draft != null && status == Status.READY;
    }

    /**
     * @param exercisesChanged rebuild the (cached) exercise list; text edits reuse the same list
     *                         instance, so the adapter does no work while the user types
     */
    private void publish(boolean exercisesChanged) {
        if (draft == null) {
            state.setValue(new TemplateEditorState(status, false, "", null, null,
                    Collections.emptyList(), Collections.emptyList()));
            return;
        }
        if (exercisesChanged) {
            List<TemplateExerciseItem> rebuilt = new ArrayList<>(draft.exercises().size());
            // A card reads A1 or A2 by how many of its group are drawn above it, so the numbers
            // follow the order of the cards, which a drag changes before anything is saved. The
            // letter is only ever the data layer's.
            Map<String, Integer> drawn = new HashMap<>();
            for (TemplateExerciseDraft e : draft.exercises()) {
                ExerciseGroup group = groups.get(e.id());
                TemplateExerciseItem.GroupMark mark = group == null ? null
                        : new TemplateExerciseItem.GroupMark(group.id(), group.label(),
                                drawn.merge(group.id(), 1, Integer::sum),
                                group.restAfterRoundSeconds());
                rebuilt.add(TemplateExerciseItem.from(e, currentCatalog(), mark));
            }
            items = Collections.unmodifiableList(rebuilt);
        }
        if (!visibleErrors.isEmpty()) {
            visibleErrors = draft.validate(); // errors disappear as soon as they are fixed
        }
        state.setValue(new TemplateEditorState(status, draft.isNew(), draft.name(), draft.description(),
                draft.notes(), items, visibleErrors));
    }
}

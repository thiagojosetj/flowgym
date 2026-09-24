package io.github.thiagojosetj.gym.ui.templates.editor;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.thiagojosetj.gym.core.Event;
import io.github.thiagojosetj.gym.data.repository.ExerciseRepository;
import io.github.thiagojosetj.gym.data.repository.SettingsRepository;
import io.github.thiagojosetj.gym.data.repository.TechniqueRepository;
import io.github.thiagojosetj.gym.data.repository.TemplateRepository;
import io.github.thiagojosetj.gym.domain.template.ExercisePlanUpdate;
import io.github.thiagojosetj.gym.domain.technique.TechniqueCatalog;
import io.github.thiagojosetj.gym.domain.template.ExerciseRef;
import io.github.thiagojosetj.gym.domain.template.TemplateDraft;
import io.github.thiagojosetj.gym.domain.template.TemplateExerciseDraft;
import io.github.thiagojosetj.gym.domain.template.TemplateRules;
import io.github.thiagojosetj.gym.domain.util.IdGenerator;
import io.github.thiagojosetj.gym.ui.templates.editor.TemplateEditorState.Status;

/**
 * Owns the {@link TemplateDraft} being edited (ADR-0019). Every change goes through the domain
 * aggregate, which applies the rules (3 × 12 defaults, limits, validation); this class only turns
 * the draft into immutable UI state and talks to the repositories.
 */
public final class TemplateEditorViewModel extends ViewModel {

    /** One-shot outcomes for the screen. */
    public enum EditorEvent { SAVED, SAVE_FAILED, ADD_FAILED, EXERCISE_LIMIT_REACHED }

    private final TemplateRepository templates;
    private final ExerciseRepository exercises;
    private final SettingsRepository settingsRepository;
    private final MutableLiveData<TechniqueCatalog> techniqueCatalog = new MutableLiveData<>(TechniqueCatalog.empty());

    private final MutableLiveData<TemplateEditorState> state = new MutableLiveData<>();
    private final MutableLiveData<Event<EditorEvent>> events = new MutableLiveData<>();

    @Nullable
    private TemplateDraft draft;
    private Status status;
    private List<TemplateExerciseItem> items = Collections.emptyList();
    private List<TemplateDraft.Error> visibleErrors = Collections.emptyList();

    public TemplateEditorViewModel(TemplateRepository templates, ExerciseRepository exercises,
                                   TechniqueRepository techniques, SettingsRepository settings,
                                   IdGenerator ids, @Nullable String templateId) {
        this.templates = templates;
        this.exercises = exercises;
        this.settingsRepository = settings;
        // Techniques are needed to validate set plans and to show badges; load them once.
        techniques.loadCatalog(techniqueCatalog::setValue, error -> { /* keep the empty catalog */ });
        if (templateId == null) {
            draft = TemplateDraft.newTemplate(ids);
            status = Status.READY;
            publish(true);
        } else {
            status = Status.LOADING;
            publish(false);
            templates.loadDraft(templateId, loaded -> {
                draft = loaded;
                status = Status.READY;
                publish(true);
            }, error -> {
                status = Status.LOAD_FAILED;
                publish(false);
            });
        }
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
            status = Status.READY;
            publish(false);
            events.setValue(new Event<>(EditorEvent.SAVED));
        }, error -> {
            status = Status.READY;
            publish(false);
            events.setValue(new Event<>(EditorEvent.SAVE_FAILED));
        });
    }

    // ------------------------------------------------------------------ internals

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
            for (TemplateExerciseDraft e : draft.exercises()) {
                rebuilt.add(TemplateExerciseItem.from(e, currentCatalog()));
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

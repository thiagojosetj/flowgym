package io.github.thiagojosetj.gym.ui.templates.editor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.github.thiagojosetj.gym.AppContainer;
import io.github.thiagojosetj.gym.core.Event;
import io.github.thiagojosetj.gym.data.local.AppDatabase;
import io.github.thiagojosetj.gym.data.local.dao.TechniqueDao;
import io.github.thiagojosetj.gym.data.local.entity.TrainingTechniqueEntity;
import io.github.thiagojosetj.gym.data.repository.TechniqueRepository;
import io.github.thiagojosetj.gym.domain.library.ExerciseFilter;
import io.github.thiagojosetj.gym.domain.library.ExerciseSummary;
import io.github.thiagojosetj.gym.domain.model.RepRange;
import io.github.thiagojosetj.gym.domain.model.SideMode;
import io.github.thiagojosetj.gym.domain.model.Weight;
import io.github.thiagojosetj.gym.domain.model.WeightUnit;
import io.github.thiagojosetj.gym.domain.template.ExercisePlanUpdate;
import io.github.thiagojosetj.gym.domain.template.TemplateDraft;
import io.github.thiagojosetj.gym.domain.template.TemplateSummary;
import io.github.thiagojosetj.gym.testutil.LiveDataTestUtil;
import io.github.thiagojosetj.gym.testutil.TestContainers;

@RunWith(AndroidJUnit4.class)
public class TemplateEditorViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private AppDatabase database;
    private AppContainer app;

    @Before
    public void setUp() {
        database = TestContainers.inMemoryDatabase();
        app = TestContainers.create(database);
        app.start();
    }

    @After
    public void tearDown() {
        database.close();
    }

    @Test
    public void newTemplateFlowAddsDefaultsAndSaves() throws Exception {
        TemplateEditorViewModel vm = newViewModel(null);
        assertTrue(state(vm).isNew());
        assertTrue(state(vm).editable());

        vm.rename("Pull");
        vm.addExercises(Arrays.asList(idOf("Puxada frontal na polia"), idOf("Rosca martelo")));

        List<TemplateExerciseItem> items = state(vm).exercises();
        assertEquals(2, items.size());
        assertEquals("Puxada frontal na polia", items.get(0).name()); // pick order preserved
        assertEquals(3, items.get(0).setCount());
        assertEquals(RepRange.exactly(12), items.get(0).reps());
        assertTrue(vm.hasUnsavedChanges());

        vm.save();

        assertEquals(TemplateEditorViewModel.EditorEvent.SAVED, lastEvent(vm));
        assertFalse(vm.hasUnsavedChanges());
        List<TemplateSummary> saved = LiveDataTestUtil.getOrAwaitValue(app.templates.observeTemplates());
        assertEquals("Pull", saved.get(0).name());
    }

    @Test
    public void savingInvalidDraftShowsErrorsAndThenClearsThemWhenFixed() throws Exception {
        TemplateEditorViewModel vm = newViewModel(null);

        vm.save();

        assertTrue(state(vm).errors().contains(TemplateDraft.Error.NAME_BLANK));
        assertTrue(state(vm).errors().contains(TemplateDraft.Error.NO_EXERCISES));
        assertTrue(LiveDataTestUtil.getOrAwaitValue(app.templates.observeTemplates()).isEmpty());

        vm.rename("Legs");
        assertFalse(state(vm).errors().contains(TemplateDraft.Error.NAME_BLANK));
        assertTrue(state(vm).errors().contains(TemplateDraft.Error.NO_EXERCISES));
    }

    @Test
    public void existingTemplateIsLoadedAndPlanEditsArePersisted() throws Exception {
        TemplateEditorViewModel creator = newViewModel(null);
        creator.rename("Push A");
        creator.addExercises(Collections.singletonList(idOf("Supino reto com barra")));
        creator.save();
        String templateId = LiveDataTestUtil.getOrAwaitValue(app.templates.observeTemplates()).get(0).id();

        TemplateEditorViewModel editor = newViewModel(templateId);
        assertFalse(state(editor).isNew());
        assertEquals("Push A", state(editor).name());
        String itemId = state(editor).exercises().get(0).id();

        List<ExercisePlanUpdate.Error> errors = editor.updatePlan(itemId, ExercisePlanUpdate.uniform(4,
                RepRange.between(8, 10), Weight.of(40, WeightUnit.KILOGRAM), null, 120, "Banco no 3º encaixe",
                SideMode.COMBINED));
        assertTrue(errors.isEmpty());
        editor.save();

        TemplateEditorViewModel reopened = newViewModel(templateId);
        TemplateExerciseItem item = reopened.findItem(itemId);
        assertNotNull(item);
        assertEquals(4, item.setCount());
        assertEquals(RepRange.between(8, 10), item.reps());
        assertEquals(Weight.ofGrams(40_000), item.weight());
        assertEquals(120, item.restSeconds());
        assertEquals("Banco no 3º encaixe", item.notes());
    }

    @Test
    public void missingTemplateEndsInLoadFailed() throws Exception {
        TemplateEditorViewModel vm = newViewModel("does-not-exist");
        assertEquals(TemplateEditorState.Status.LOAD_FAILED, state(vm).status());
        assertFalse(state(vm).editable());
    }

    @Test
    public void moveAndRemoveUpdateTheList() throws Exception {
        TemplateEditorViewModel vm = newViewModel(null);
        vm.addExercises(Arrays.asList(idOf("Supino reto com barra"), idOf("Crucifixo com halteres"),
                idOf("Tríceps na polia")));

        vm.moveExercise(2, 0);
        assertEquals("Tríceps na polia", state(vm).exercises().get(0).name());

        vm.removeExercise(state(vm).exercises().get(1).id());
        assertEquals(Arrays.asList("Tríceps na polia", "Crucifixo com halteres"),
                Arrays.asList(state(vm).exercises().get(0).name(), state(vm).exercises().get(1).name()));
    }

    @Test
    public void aFailedTechniqueLoadIsReportedAsUnavailableInsteadOfEmpty() throws Exception {
        // An empty catalog and an unreadable one must not look the same: with an empty catalog a set
        // simply has no technique to offer, while a failed read means the plan cannot be validated
        // and the screen has to say so (ExercisePlanSheet).
        TechniqueRepository broken = new TechniqueRepository(new TechniqueDao() {
            @Override
            public LiveData<List<TrainingTechniqueEntity>> observeVisible() {
                throw new IllegalStateException("unreadable");
            }

            @Override
            public List<TrainingTechniqueEntity> findVisible() {
                throw new IllegalStateException("unreadable");
            }
        }, app.executors);

        TemplateEditorViewModel vm = new TemplateEditorViewModel(app.templates, app.exercises, broken,
                app.settings, app.ids, null);

        assertTrue(vm.techniqueCatalog().isInitialized());
        assertNull(vm.techniqueCatalog().getValue());
    }

    // ------------------------------------------------------------------ helpers

    private TemplateEditorViewModel newViewModel(String templateId) {
        return new TemplateEditorViewModel(app.templates, app.exercises, app.techniques, app.settings,
                app.ids, templateId);
    }

    private static TemplateEditorState state(TemplateEditorViewModel vm) throws Exception {
        return LiveDataTestUtil.getOrAwaitValue(vm.state());
    }

    private static TemplateEditorViewModel.EditorEvent lastEvent(TemplateEditorViewModel vm) throws Exception {
        Event<TemplateEditorViewModel.EditorEvent> event = LiveDataTestUtil.getOrAwaitValue(vm.events());
        return event.peek();
    }

    private String idOf(String name) throws Exception {
        for (ExerciseSummary s : LiveDataTestUtil.getOrAwaitValue(app.exercises.observeLibrary(ExerciseFilter.none()))) {
            if (s.name().equals(name)) {
                return s.id();
            }
        }
        throw new AssertionError("No exercise " + name);
    }
}

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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import io.github.thiagojosetj.gym.AppContainer;
import io.github.thiagojosetj.gym.core.Event;
import io.github.thiagojosetj.gym.data.local.AppDatabase;
import io.github.thiagojosetj.gym.data.local.dao.TechniqueDao;
import io.github.thiagojosetj.gym.data.local.entity.TrainingTechniqueEntity;
import io.github.thiagojosetj.gym.data.repository.TechniqueRepository;
import io.github.thiagojosetj.gym.domain.library.ExerciseFilter;
import io.github.thiagojosetj.gym.domain.technique.TrainingTechnique;
import io.github.thiagojosetj.gym.domain.library.ExerciseSummary;
import io.github.thiagojosetj.gym.domain.model.RepRange;
import io.github.thiagojosetj.gym.domain.model.SideMode;
import io.github.thiagojosetj.gym.domain.model.Weight;
import io.github.thiagojosetj.gym.domain.model.WeightUnit;
import io.github.thiagojosetj.gym.domain.template.ExerciseGroup;
import io.github.thiagojosetj.gym.domain.template.ExercisePlanUpdate;
import io.github.thiagojosetj.gym.domain.template.TemplateDraft;
import io.github.thiagojosetj.gym.domain.template.TemplateSummary;
import io.github.thiagojosetj.gym.testutil.LiveDataTestUtil;
import io.github.thiagojosetj.gym.testutil.TestContainers;

@RunWith(AndroidJUnit4.class)
public class TemplateEditorViewModelTest {

    private static final String SUPINO = "Supino reto com barra";
    private static final String CRUCIFIXO = "Crucifixo com halteres";
    private static final String TRICEPS = "Tríceps na polia";
    private static final String ROSCA = "Rosca martelo";

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

    // ------------------------------------------------------------------ groups (PRODUCT_SPEC 6.3)

    @Test
    public void groupingTwoExercisesMakesBothShowTheirLabel() throws Exception {
        String templateId = savedTemplate("Push A", SUPINO, CRUCIFIXO, TRICEPS);
        TemplateEditorViewModel vm = newViewModel(templateId);
        List<TemplateExerciseItem> before = state(vm).exercises();
        for (TemplateExerciseItem item : before) {
            assertNull("nothing is grouped yet", item.group());
        }

        vm.createGroup(Arrays.asList(before.get(0).id(), before.get(1).id()), null, 75);

        assertEquals(TemplateEditorViewModel.EditorEvent.GROUPED, lastEvent(vm));
        List<TemplateExerciseItem> items = state(vm).exercises();
        assertEquals("A1", items.get(0).group().code());
        assertEquals("A2", items.get(1).group().code());
        assertNull("the third one stands alone", items.get(2).group());
        assertEquals(items.get(0).group().groupId(), items.get(1).group().groupId());
        // The rest belongs to the round, so both cards carry the group's.
        assertEquals(75, items.get(0).group().restAfterRoundSeconds());
        assertEquals(75, items.get(1).group().restAfterRoundSeconds());
        // It is in the database, not only on the screen, and the letter is the data layer's.
        Map<String, ExerciseGroup> stored = loadGroups(templateId);
        assertEquals(2, stored.size());
        assertEquals("A", stored.get(before.get(0).id()).label());
        assertEquals(75, stored.get(before.get(1).id()).restAfterRoundSeconds());
    }

    @Test
    public void ungroupingTakesTheLabelsOffAndKeepsBothExercises() throws Exception {
        String templateId = savedTemplate("Push A", SUPINO, CRUCIFIXO, TRICEPS);
        TemplateEditorViewModel vm = newViewModel(templateId);
        groupTheFirstTwo(vm, 90);
        String groupId = state(vm).exercises().get(0).group().groupId();

        vm.removeGroup(groupId);

        assertEquals(TemplateEditorViewModel.EditorEvent.UNGROUPED, lastEvent(vm));
        List<TemplateExerciseItem> items = state(vm).exercises();
        assertEquals(Arrays.asList(SUPINO, CRUCIFIXO, TRICEPS), namesOf(items));
        for (TemplateExerciseItem item : items) {
            assertNull(item.name() + " is out of the group", item.group());
        }
        assertTrue(loadGroups(templateId).isEmpty());
        // And the template itself did not lose anything: the three are still stored.
        assertEquals(3, loadDraft(templateId).exercises().size());
    }

    @Test
    public void takingAwayTheFirstGroupLetsTheSecondTakeItsLetter() throws Exception {
        String templateId = savedTemplate("Full body", SUPINO, CRUCIFIXO, TRICEPS, ROSCA);
        TemplateEditorViewModel vm = newViewModel(templateId);
        List<TemplateExerciseItem> items = state(vm).exercises();
        vm.createGroup(Arrays.asList(items.get(2).id(), items.get(3).id()), null, 60);
        assertEquals("A1", state(vm).exercises().get(2).group().code());

        // A group made over EARLIER exercises takes A, and the one that was A becomes B...
        vm.createGroup(Arrays.asList(items.get(0).id(), items.get(1).id()), null, 60);
        assertEquals(Arrays.asList("A1", "A2", "B1", "B2"), labelsOf(state(vm).exercises()));

        // ...and removing A promotes B: two groups never share a letter.
        vm.removeGroup(state(vm).exercises().get(0).group().groupId());
        assertEquals(Arrays.asList(null, null, "A1", "A2"), labelsOf(state(vm).exercises()));
    }

    @Test
    public void aGroupOfOneIsRefusedWithAMessageAndNothingIsWritten() throws Exception {
        String templateId = savedTemplate("Push A", SUPINO, CRUCIFIXO);
        TemplateEditorViewModel vm = newViewModel(templateId);
        String only = state(vm).exercises().get(0).id();

        vm.createGroup(Collections.singletonList(only), null, 90);

        assertEquals(TemplateEditorViewModel.EditorEvent.GROUP_TOO_SMALL, lastEvent(vm));
        assertTrue("nothing was written", loadGroups(templateId).isEmpty());
        assertNull(state(vm).exercises().get(0).group());
    }

    @Test
    public void theSameExerciseTwiceIsStillAGroupOfOne() throws Exception {
        String templateId = savedTemplate("Push A", SUPINO, CRUCIFIXO);
        TemplateEditorViewModel vm = newViewModel(templateId);
        String only = state(vm).exercises().get(0).id();

        vm.createGroup(Arrays.asList(only, only), null, 90);

        assertEquals(TemplateEditorViewModel.EditorEvent.GROUP_TOO_SMALL, lastEvent(vm));
        assertTrue(loadGroups(templateId).isEmpty());
    }

    @Test
    public void whatTheDataLayerRefusesIsReportedInsteadOfSwallowed() throws Exception {
        String templateId = savedTemplate("Push A", SUPINO, CRUCIFIXO);
        TemplateEditorViewModel vm = newViewModel(templateId);
        List<TemplateExerciseItem> items = state(vm).exercises();

        // The longest rest there is plus one: only the repository knows the limit.
        vm.createGroup(Arrays.asList(items.get(0).id(), items.get(1).id()), null, 3601);

        assertEquals(TemplateEditorViewModel.EditorEvent.GROUP_FAILED, lastEvent(vm));
        assertTrue(loadGroups(templateId).isEmpty());
        assertNull(state(vm).exercises().get(0).group());
    }

    @Test
    public void savingAfterGroupingKeepsTheGroupWhateverElseWasEdited() throws Exception {
        String templateId = savedTemplate("Push A", SUPINO, CRUCIFIXO, TRICEPS);
        TemplateEditorViewModel vm = newViewModel(templateId);
        groupTheFirstTwo(vm, 75);

        vm.rename("Push A v2");
        vm.moveExercise(2, 0); // the third one to the top: the group is no longer first
        vm.save();

        assertEquals(TemplateEditorViewModel.EditorEvent.SAVED, lastEvent(vm));
        TemplateEditorViewModel reopened = newViewModel(templateId);
        List<TemplateExerciseItem> items = state(reopened).exercises();
        assertEquals(Arrays.asList(TRICEPS, SUPINO, CRUCIFIXO), namesOf(items));
        assertEquals(Arrays.asList(null, "A1", "A2"), labelsOf(items));
        assertEquals(75, items.get(1).group().restAfterRoundSeconds());
        assertEquals(1, groupIdsOf(loadGroups(templateId)).size());
    }

    @Test
    public void theNumberFollowsTheOrderOfTheCardsEvenBeforeSaving() throws Exception {
        String templateId = savedTemplate("Push A", SUPINO, CRUCIFIXO, TRICEPS);
        TemplateEditorViewModel vm = newViewModel(templateId);
        groupTheFirstTwo(vm, 90);

        vm.moveExercise(1, 0); // the crucifixo above the supino

        List<TemplateExerciseItem> items = state(vm).exercises();
        assertEquals(Arrays.asList(CRUCIFIXO, SUPINO, TRICEPS), namesOf(items));
        assertEquals(Arrays.asList("A1", "A2", null), labelsOf(items));
    }

    @Test
    public void anExerciseAddedNowCannotBeGroupedUntilTheTemplateIsSaved() throws Exception {
        String templateId = savedTemplate("Push A", SUPINO, CRUCIFIXO);
        TemplateEditorViewModel vm = newViewModel(templateId);
        vm.addExercises(Collections.singletonList(idOf(TRICEPS)));
        List<TemplateExerciseItem> items = state(vm).exercises();
        assertTrue(vm.isSaved(items.get(0).id()));
        assertFalse("it only exists in this draft", vm.isSaved(items.get(2).id()));

        vm.createGroup(Arrays.asList(items.get(0).id(), items.get(2).id()), null, 90);

        assertEquals(TemplateEditorViewModel.EditorEvent.GROUP_NEEDS_SAVE, lastEvent(vm));
        assertTrue(loadGroups(templateId).isEmpty());

        // Once the template is saved the same exercise is in the database, and the group works.
        vm.save();
        assertTrue(vm.isSaved(items.get(2).id()));
        vm.createGroup(Arrays.asList(items.get(0).id(), items.get(2).id()), null, 90);
        assertEquals(TemplateEditorViewModel.EditorEvent.GROUPED, lastEvent(vm));
        assertEquals(Arrays.asList("A1", null, "A2"), labelsOf(state(vm).exercises()));
    }

    @Test
    public void aTemplateThatWasNeverSavedCannotGroupAnything() throws Exception {
        TemplateEditorViewModel vm = newViewModel(null);
        vm.addExercises(Arrays.asList(idOf(SUPINO), idOf(CRUCIFIXO)));
        List<TemplateExerciseItem> items = state(vm).exercises();
        assertFalse(vm.isSaved(items.get(0).id()));

        vm.createGroup(Arrays.asList(items.get(0).id(), items.get(1).id()), null, 90);

        assertEquals(TemplateEditorViewModel.EditorEvent.GROUP_NEEDS_SAVE, lastEvent(vm));
        assertNull(state(vm).exercises().get(0).group());
        assertTrue("it never reached the database",
                LiveDataTestUtil.getOrAwaitValue(app.templates.observeTemplates()).isEmpty());
    }

    @Test
    public void onlyTheOtherFreeExercisesAreOfferedToJoinAGroup() throws Exception {
        String templateId = savedTemplate("Full body", SUPINO, CRUCIFIXO, TRICEPS, ROSCA);
        TemplateEditorViewModel vm = newViewModel(templateId);
        List<TemplateExerciseItem> items = state(vm).exercises();
        assertEquals(Arrays.asList(CRUCIFIXO, TRICEPS, ROSCA),
                namesOf(vm.groupCandidates(items.get(0).id())));

        vm.createGroup(Arrays.asList(items.get(0).id(), items.get(1).id()), null, 90);

        // The two that are taken are no candidates for the third, and it is not its own.
        assertEquals(Collections.singletonList(ROSCA),
                namesOf(vm.groupCandidates(state(vm).exercises().get(2).id())));
    }

    @Test
    public void aNewGroupStartsWithTheRestTheUserSetAsDefault() throws Exception {
        assertEquals(90, newViewModel(null).defaultGroupRestSeconds());

        app.settings.setDefaultRestSeconds(120);

        assertEquals(120, newViewModel(null).defaultGroupRestSeconds());
    }

    @Test
    public void onlyTheMethodsOfAGroupAreOffered() throws Exception {
        TemplateEditorViewModel vm = newViewModel(savedTemplate("Push A", SUPINO, CRUCIFIXO));

        List<String> codes = new ArrayList<>();
        for (TrainingTechnique technique : vm.groupTechniques()) {
            codes.add(technique.code());
        }

        // Supersérie, bi-set, tri-set e giant set. Drop-set e aquecimento são de SÉRIE e não
        // podem aparecer aqui: oferecê-los seria o app dizendo que um grupo pode ser um drop-set.
        assertEquals(Arrays.asList("SS", "BI", "TRI", "GS"), codes);
    }

    @Test
    public void umGrupoGuardaOMetodoComQueFoiCriadoEMostraEmTodosOsCards() throws Exception {
        String templateId = savedTemplate("Push A", SUPINO, CRUCIFIXO, TRICEPS);
        TemplateEditorViewModel vm = newViewModel(templateId);
        List<TemplateExerciseItem> before = state(vm).exercises();
        String superserie = groupTechniqueId(vm, "SS");

        vm.createGroup(Arrays.asList(before.get(0).id(), before.get(1).id()), superserie, 75);

        List<TemplateExerciseItem> items = state(vm).exercises();
        // O método é do GRUPO, então os dois cards o dizem: cada card tem de informar do que faz
        // parte sem obrigar a rolar até o outro.
        assertEquals("SS", items.get(0).group().technique().code());
        assertEquals("Supersérie", items.get(0).group().technique().name());
        assertEquals("SS", items.get(1).group().technique().code());
        assertNull("quem está fora do grupo não ganha método", items.get(2).group());
        // E está no banco, não só na tela.
        assertEquals(superserie, loadGroups(templateId).get(before.get(0).id()).techniqueId());
    }

    @Test
    public void editarUmGrupoTrocaMetodoEDescansoSemMexerEmQuemEstaDentro() throws Exception {
        String templateId = savedTemplate("Push A", SUPINO, CRUCIFIXO, TRICEPS);
        TemplateEditorViewModel vm = newViewModel(templateId);
        groupTheFirstTwo(vm, 75);
        String groupId = state(vm).exercises().get(0).group().groupId();
        List<String> antes = namesOf(state(vm).exercises());

        vm.updateGroup(groupId, groupTechniqueId(vm, "BI"), 45);

        assertEquals(TemplateEditorViewModel.EditorEvent.GROUP_UPDATED, lastEvent(vm));
        List<TemplateExerciseItem> items = state(vm).exercises();
        assertEquals("BI", items.get(0).group().technique().code());
        assertEquals(45, items.get(0).group().restAfterRoundSeconds());
        assertEquals("o descanso é da rodada, então vale para os dois",
                45, items.get(1).group().restAfterRoundSeconds());
        // O que esta folha NÃO decide: quem está no grupo, a letra e a ordem dos cards.
        assertEquals(Arrays.asList("A1", "A2", null), labelsOf(items));
        assertEquals(antes, namesOf(items));
        assertEquals(groupId, items.get(1).group().groupId());
        assertEquals(45, loadGroups(templateId).get(items.get(0).id()).restAfterRoundSeconds());
    }

    @Test
    public void tirarOMetodoDeixaUmAgrupamentoSimplesEOGrupoContinuaDePe() throws Exception {
        String templateId = savedTemplate("Push A", SUPINO, CRUCIFIXO);
        TemplateEditorViewModel vm = newViewModel(templateId);
        List<TemplateExerciseItem> before = state(vm).exercises();
        vm.createGroup(Arrays.asList(before.get(0).id(), before.get(1).id()),
                groupTechniqueId(vm, "TRI"), 60);
        String groupId = state(vm).exercises().get(0).group().groupId();

        vm.updateGroup(groupId, null, 60);

        List<TemplateExerciseItem> items = state(vm).exercises();
        assertNull("sem método, mas ainda um grupo", items.get(0).group().technique());
        assertNull(items.get(0).group().techniqueId());
        assertEquals("A1", items.get(0).group().code());
        assertEquals("A2", items.get(1).group().code());
        assertNull(loadGroups(templateId).get(items.get(0).id()).techniqueId());
    }

    /** O id da técnica de grupo com este código, como o seletor da folha a entrega. */
    private static String groupTechniqueId(TemplateEditorViewModel vm, String code) {
        for (TrainingTechnique technique : vm.groupTechniques()) {
            if (technique.code().equals(code)) {
                return technique.id();
            }
        }
        throw new AssertionError("Sem técnica de grupo " + code);
    }

    // ------------------------------------------------------------------ helpers

    /** Creates and saves a template through the editor itself, and returns its id. */
    private String savedTemplate(String name, String... exerciseNames) throws Exception {
        TemplateEditorViewModel creator = newViewModel(null);
        creator.rename(name);
        List<String> ids = new ArrayList<>();
        for (String exerciseName : exerciseNames) {
            ids.add(idOf(exerciseName));
        }
        creator.addExercises(ids);
        creator.save();
        for (TemplateSummary summary
                : LiveDataTestUtil.getOrAwaitValue(app.templates.observeTemplates())) {
            if (summary.name().equals(name)) {
                return summary.id();
            }
        }
        throw new AssertionError("The template was not saved: " + name);
    }

    private void groupTheFirstTwo(TemplateEditorViewModel vm, int restSeconds) throws Exception {
        List<TemplateExerciseItem> items = state(vm).exercises();
        vm.createGroup(Arrays.asList(items.get(0).id(), items.get(1).id()), null, restSeconds);
        assertEquals(TemplateEditorViewModel.EditorEvent.GROUPED, lastEvent(vm));
    }

    private static List<String> namesOf(List<TemplateExerciseItem> items) {
        List<String> names = new ArrayList<>();
        for (TemplateExerciseItem item : items) {
            names.add(item.name());
        }
        return names;
    }

    /** What each card writes before its name: "A1", "A2", or null for an exercise on its own. */
    private static List<String> labelsOf(List<TemplateExerciseItem> items) {
        List<String> labels = new ArrayList<>();
        for (TemplateExerciseItem item : items) {
            labels.add(item.group() == null ? null : item.group().code());
        }
        return labels;
    }

    private static Set<String> groupIdsOf(Map<String, ExerciseGroup> groups) {
        Set<String> ids = new HashSet<>();
        for (ExerciseGroup group : groups.values()) {
            ids.add(group.id());
        }
        return ids;
    }

    private Map<String, ExerciseGroup> loadGroups(String templateId) {
        AtomicReference<Map<String, ExerciseGroup>> groups = new AtomicReference<>();
        app.templates.loadGroups(templateId, groups::set, error -> {
            throw new AssertionError(error);
        });
        assertNotNull("the groups should have been read", groups.get());
        return groups.get();
    }

    private TemplateDraft loadDraft(String templateId) {
        AtomicReference<TemplateDraft> draft = new AtomicReference<>();
        app.templates.loadDraft(templateId, draft::set, error -> {
            throw new AssertionError(error);
        });
        assertNotNull("the template should have been read", draft.get());
        return draft.get();
    }

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

package io.github.thiagojosetj.gym.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.database.Cursor;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import io.github.thiagojosetj.gym.AppContainer;
import io.github.thiagojosetj.gym.data.local.AppDatabase;
import io.github.thiagojosetj.gym.domain.library.ExerciseFilter;
import io.github.thiagojosetj.gym.domain.library.ExerciseSummary;
import io.github.thiagojosetj.gym.domain.model.RepRange;
import io.github.thiagojosetj.gym.domain.model.SideMode;
import io.github.thiagojosetj.gym.domain.model.Weight;
import io.github.thiagojosetj.gym.domain.settings.AppSettings;
import io.github.thiagojosetj.gym.domain.technique.TechniqueCatalog;
import io.github.thiagojosetj.gym.domain.model.WeightUnit;
import io.github.thiagojosetj.gym.domain.template.ExercisePlanUpdate;
import io.github.thiagojosetj.gym.domain.template.ExerciseRef;
import io.github.thiagojosetj.gym.domain.template.SetPlan;
import io.github.thiagojosetj.gym.domain.template.SetSpec;
import io.github.thiagojosetj.gym.domain.template.TemplateDraft;
import io.github.thiagojosetj.gym.domain.template.TemplateExerciseDraft;
import io.github.thiagojosetj.gym.domain.template.TemplateRules;
import io.github.thiagojosetj.gym.domain.template.TemplateSummary;
import io.github.thiagojosetj.gym.domain.util.IdGenerator;
import io.github.thiagojosetj.gym.testutil.LiveDataTestUtil;
import io.github.thiagojosetj.gym.testutil.MutableClock;
import io.github.thiagojosetj.gym.testutil.TestContainers;

/** The first vertical slice at the data level: library → template → save → reopen. */
@RunWith(AndroidJUnit4.class)
public class TemplateRepositoryTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private AppDatabase database;
    private AppContainer container;
    private TemplateRepository templates;
    /** Movable, so "archived a minute later" is a real ordering and not two identical timestamps. */
    private MutableClock clock;

    @Before
    public void setUp() {
        database = TestContainers.inMemoryDatabase();
        clock = MutableClock.at("2026-09-21T12:00:00Z");
        container = TestContainers.create(ApplicationProvider.getApplicationContext(), database, clock);
        container.start();
        templates = container.templates;
    }

    @After
    public void tearDown() {
        database.close();
    }

    @Test
    public void savedTemplateReopensWithTheSamePlan() throws Exception {
        TemplateDraft draft = newPushTemplate();
        TemplateExerciseDraft bench = draft.exercises().get(0);
        draft.updateExercisePlan(bench.id(), ExercisePlanUpdate.uniform(4, RepRange.between(8, 10),
                        Weight.of(40, WeightUnit.KILOGRAM), null, 120, "Banco no terceiro encaixe", SideMode.COMBINED),
                TechniqueCatalog.empty());

        String id = save(draft);
        TemplateDraft reopened = load(id);

        assertFalse(reopened.isNew());
        assertEquals("Push A", reopened.name());
        assertEquals(draft.exercises().size(), reopened.exercises().size());
        TemplateExerciseDraft reBench = reopened.exercises().get(0);
        assertEquals(bench.id(), reBench.id());
        assertEquals(4, reBench.setCount());
        assertEquals(RepRange.between(8, 10), reBench.uniformReps());
        assertEquals(Weight.ofGrams(40_000), reBench.uniformWeight());
        assertEquals(120, reBench.restSeconds());
        assertEquals("Banco no terceiro encaixe", reBench.notes());
        assertEquals(setIds(draft.findExercise(bench.id())), setIds(reBench));

        TemplateExerciseDraft row = reopened.exercises().get(1);
        assertTrue(row.exercise().isLoadPerImplement());
        assertEquals(RepRange.exactly(12), row.uniformReps()); // default 3 x 12
        assertEquals(3, row.setCount());
    }

    @Test
    public void setTechniquesSurviveSaveAndReopen() throws Exception {
        TemplateDraft draft = newPushTemplate();
        TemplateExerciseDraft bench = draft.exercises().get(0);
        TechniqueCatalog catalog = LiveDataTestUtil.getOrAwaitValue(container.techniques.observeCatalog());
        String warmUpId = idOfTechnique(catalog, "AQ");
        String dropSetId = idOfTechnique(catalog, "D");

        draft.updateExercisePlan(bench.id(), new ExercisePlanUpdate(Arrays.asList(
                new SetSpec(RepRange.exactly(12), Weight.of(20, WeightUnit.KILOGRAM), null, warmUpId),
                new SetSpec(RepRange.between(8, 10), Weight.of(40, WeightUnit.KILOGRAM), null, null),
                new SetSpec(RepRange.between(8, 10), Weight.of(40, WeightUnit.KILOGRAM), null, dropSetId)),
                120, null, SideMode.COMBINED), catalog);
        String id = save(draft);

        TemplateExerciseDraft reopened = load(id).exercises().get(0);
        assertEquals(Arrays.asList(warmUpId, null, dropSetId), reopened.techniqueIds());
        assertEquals(Weight.ofGrams(20_000), reopened.sets().get(0).weight());
        assertFalse(reopened.hasUniformPlan());
    }

    private static String idOfTechnique(TechniqueCatalog catalog, String code) {
        return catalog.all().stream()
                .filter(t -> t.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No technique " + code))
                .id();
    }

    @Test
    public void newTemplateAppearsInTheObservedList() throws Exception {
        save(newPushTemplate());

        List<TemplateSummary> list = LiveDataTestUtil.getOrAwaitValue(templates.observeTemplates());
        assertEquals(1, list.size());
        TemplateSummary summary = list.get(0);
        assertEquals("Push A", summary.name());
        assertEquals(2, summary.exerciseCount());
        assertEquals(6, summary.setCount());
        assertTrue(summary.muscleGroups().containsAll(Arrays.asList("Peito", "Costas")));
        assertEquals(Integer.valueOf(1), LiveDataTestUtil.getOrAwaitValue(templates.observeTemplateCount()));
    }

    @Test
    public void updatingReplacesChildrenWithoutOrphans() throws Exception {
        String id = save(newPushTemplate());

        TemplateDraft reopened = load(id);
        reopened.removeExercise(reopened.exercises().get(1).id());
        save(reopened);

        assertEquals(1, load(id).exercises().size());
        assertEquals(1, count("SELECT COUNT(*) FROM template_exercise"));
        assertEquals(3, count("SELECT COUNT(*) FROM template_set")); // cascade removed the other 3 sets
    }

    @Test
    public void reorderIsPersisted() throws Exception {
        String id = save(newPushTemplate());
        TemplateDraft reopened = load(id);
        String secondExercise = reopened.exercises().get(1).exercise().id();

        reopened.moveExercise(1, 0);
        save(reopened);

        assertEquals(secondExercise, load(id).exercises().get(0).exercise().id());
    }

    @Test
    public void savingTwiceAfterMarkSavedUpdatesInsteadOfInserting() throws Exception {
        TemplateDraft draft = newPushTemplate();
        save(draft);
        draft.markSaved();
        draft.rename("Push B");
        save(draft);

        List<TemplateSummary> list = LiveDataTestUtil.getOrAwaitValue(templates.observeTemplates());
        assertEquals(1, list.size());
        assertEquals("Push B", list.get(0).name());
    }

    @Test
    public void duplicateIsAnIndependentCopy() throws Exception {
        String originalId = save(newPushTemplate());
        AtomicReference<String> copyId = new AtomicReference<>();
        templates.duplicate(originalId, " (cópia)", copyId::set, e -> { throw new AssertionError(e); });

        assertNotNull(copyId.get());
        assertNotEquals(originalId, copyId.get());
        TemplateDraft copy = load(copyId.get());
        TemplateDraft original = load(originalId);
        assertEquals("Push A (cópia)", copy.name());
        assertNotEquals(original.exercises().get(0).id(), copy.exercises().get(0).id());

        copy.removeExercise(copy.exercises().get(0).id());
        save(copy);
        assertEquals(2, load(originalId).exercises().size());
        assertEquals("DUPLICATED", string("SELECT origin FROM workout_template WHERE id = '" + copyId.get() + "'"));
    }

    @Test
    public void duplicatingALongNameStoresAValidName() throws Exception {
        // Regression (review 2026-09-22): a 60-char name + " (cópia)" used to be stored as 68 chars,
        // and the copy could then never be saved from the editor.
        TemplateDraft draft = newPushTemplate();
        draft.rename("Treino de hipertrofia para membros superiores com foco em ombros"
                .substring(0, TemplateRules.MAX_NAME_LENGTH));
        String originalId = save(draft);
        AtomicReference<String> copyId = new AtomicReference<>();
        templates.duplicate(originalId, " (cópia)", copyId::set, e -> { throw new AssertionError(e); });

        TemplateDraft copy = load(copyId.get());
        assertTrue(copy.name().length() <= TemplateRules.MAX_NAME_LENGTH);
        assertTrue(copy.name().endsWith(" (cópia)"));
        assertTrue(copy.validate().isEmpty());
    }

    @Test
    public void deletedTemplateLeavesTheListButKeepsItsRow() throws Exception {
        String id = save(newPushTemplate());
        boolean[] done = {false};
        templates.delete(id, () -> done[0] = true, e -> { throw new AssertionError(e); });

        assertTrue(done[0]);
        assertTrue(LiveDataTestUtil.getOrAwaitValue(templates.observeTemplates()).isEmpty());
        assertEquals(1, count("SELECT COUNT(*) FROM workout_template WHERE deleted_at IS NOT NULL"));
        AtomicReference<Throwable> error = new AtomicReference<>();
        templates.loadDraft(id, d -> { }, error::set);
        assertTrue(error.get() instanceof TemplateRepository.TemplateNotFoundException);
    }

    // ------------------------------------------------------------------ arquivar (PRODUCT_SPEC 5.6)

    @Test
    public void archivedTemplateLeavesTheActiveListAndEntersTheArchivedOne() throws Exception {
        String id = save(newPushTemplate());

        archive(id, true);

        assertTrue(LiveDataTestUtil.getOrAwaitValue(templates.observeTemplates()).isEmpty());
        List<TemplateSummary> archived = LiveDataTestUtil.getOrAwaitValue(templates.observeArchived());
        assertEquals(1, archived.size());
        assertEquals("Push A", archived.get(0).name());
        // The card says the same thing in both lists: archiving is not editing.
        assertEquals(2, archived.get(0).exerciseCount());
        assertEquals(6, archived.get(0).setCount());
        assertEquals(Integer.valueOf(0), LiveDataTestUtil.getOrAwaitValue(templates.observeTemplateCount()));
        assertEquals(Integer.valueOf(1), LiveDataTestUtil.getOrAwaitValue(templates.observeArchivedCount()));
    }

    @Test
    public void unarchivingBringsTheTemplateBackWholeAndClearsTheTimestamp() throws Exception {
        String id = save(newPushTemplate());
        archive(id, true);

        archive(id, false);

        assertTrue(LiveDataTestUtil.getOrAwaitValue(templates.observeArchived()).isEmpty());
        List<TemplateSummary> active = LiveDataTestUtil.getOrAwaitValue(templates.observeTemplates());
        assertEquals(1, active.size());
        assertEquals(0, count("SELECT COUNT(*) FROM workout_template WHERE archived_at IS NOT NULL"));
        // Nothing was lost on the way out and back: the plan reopens exactly as it was.
        TemplateDraft reopened = load(id);
        assertEquals(2, reopened.exercises().size());
        assertEquals(3, reopened.exercises().get(0).setCount());
    }

    @Test
    public void archivedTemplateStillOpensInTheEditor() throws Exception {
        // Unarchiving is only safe if the aggregate survived being put away. Reading it directly
        // proves the archived row keeps its exercises and sets, not just its name.
        String id = save(newPushTemplate());
        archive(id, true);

        TemplateDraft draft = load(id);

        assertEquals("Push A", draft.name());
        assertEquals(2, draft.exercises().size());
        assertEquals(2, count("SELECT COUNT(*) FROM template_exercise"));
        assertEquals(6, count("SELECT COUNT(*) FROM template_set"));
    }

    @Test
    public void deletedTemplateIsInNeitherList() throws Exception {
        // Deleted is not archived: a soft-deleted row has archived_at NULL, so an archived list
        // that only asked "archived_at IS NOT NULL" would be right by accident. Archive first, so
        // the row carries both timestamps and only the delete filter can keep it out.
        String id = save(newPushTemplate());
        archive(id, true);
        boolean[] deleted = {false};
        templates.delete(id, () -> deleted[0] = true, e -> { throw new AssertionError(e); });

        assertTrue(deleted[0]);
        assertTrue(LiveDataTestUtil.getOrAwaitValue(templates.observeTemplates()).isEmpty());
        assertTrue(LiveDataTestUtil.getOrAwaitValue(templates.observeArchived()).isEmpty());
        assertEquals(Integer.valueOf(0), LiveDataTestUtil.getOrAwaitValue(templates.observeArchivedCount()));
    }

    @Test
    public void archivingADeletedTemplateChangesNothing() throws Exception {
        String id = save(newPushTemplate());
        templates.delete(id, () -> { }, e -> { throw new AssertionError(e); });

        archive(id, true);

        assertEquals(0, count("SELECT COUNT(*) FROM workout_template WHERE archived_at IS NOT NULL"));
    }

    @Test
    public void mostRecentlyArchivedComesFirst() throws Exception {
        String first = save(newPushTemplate());
        TemplateDraft second = newPushTemplate();
        second.rename("Pull A");
        String secondId = save(second);

        archive(first, true);
        clock.advanceMinutes(1);
        archive(secondId, true);

        List<TemplateSummary> archived = LiveDataTestUtil.getOrAwaitValue(templates.observeArchived());
        assertEquals(Arrays.asList("Pull A", "Push A"), Arrays.asList(
                archived.get(0).name(), archived.get(1).name()));
    }

    @Test
    public void archivingMarksTheRowForSync() throws Exception {
        String id = save(newPushTemplate());
        database.getOpenHelper().getWritableDatabase().execSQL(
                "UPDATE workout_template SET sync_status = 'SYNCED' WHERE id = '" + id + "'");

        archive(id, true);

        assertEquals("PENDING", string("SELECT sync_status FROM workout_template WHERE id = '" + id + "'"));
    }

    @Test
    public void deletingTwiceKeepsTheFirstTombstone() throws Exception {
        // Found by mutation while testing archiving: dropping "deleted_at IS NULL" from the delete
        // broke no test. A second delete would move the tombstone, which is a lie about when the
        // template left - and for sync, a row that keeps looking newly deleted.
        String id = save(newPushTemplate());
        templates.delete(id, () -> { }, e -> { throw new AssertionError(e); });
        String first = string("SELECT deleted_at FROM workout_template WHERE id = '" + id + "'");

        clock.advanceMinutes(5);
        templates.delete(id, () -> { }, e -> { throw new AssertionError(e); });

        assertEquals(first, string("SELECT deleted_at FROM workout_template WHERE id = '" + id + "'"));
    }

    @Test
    public void newTemplatesAreOwnedByTheLocalUserAndPendingSync() throws Exception {
        String id = save(newPushTemplate());
        String owner = string("SELECT owner_user_id FROM workout_template WHERE id = '" + id + "'");
        assertEquals(container.users.requireCurrentUserId(), owner);
        assertEquals("PENDING", string("SELECT sync_status FROM workout_template WHERE id = '" + id + "'"));
    }

    @Test
    public void invalidDraftIsRejectedBeforeTouchingTheDatabase() {
        TemplateDraft empty = TemplateDraft.newTemplate(IdGenerator.UUID_V7);
        assertThrows(IllegalArgumentException.class, () -> templates.save(empty, id -> { }, e -> { }));
    }

    // ------------------------------------------------------------------ helpers

    private TemplateDraft newPushTemplate() throws Exception {
        List<ExerciseSummary> library = LiveDataTestUtil.getOrAwaitValue(
                container.exercises.observeLibrary(ExerciseFilter.none()));
        String bench = idByName(library, "Supino reto com barra");
        String row = idByName(library, "Remada unilateral com halter (serrote)");
        AtomicReference<List<ExerciseRef>> refs = new AtomicReference<>();
        container.exercises.loadRefs(Arrays.asList(bench, row), refs::set, e -> { throw new AssertionError(e); });

        TemplateDraft draft = TemplateDraft.newTemplate(IdGenerator.UUID_V7);
        draft.rename("Push A");
        draft.addExercises(refs.get(), AppSettings.standard().templateDefaults());
        return draft;
    }

    private String save(TemplateDraft draft) {
        AtomicReference<String> saved = new AtomicReference<>();
        templates.save(draft, saved::set, e -> { throw new AssertionError(e); });
        assertNotNull(saved.get());
        return saved.get();
    }

    private void archive(String id, boolean archived) {
        boolean[] done = {false};
        templates.setArchived(id, archived, () -> done[0] = true, e -> { throw new AssertionError(e); });
        assertTrue("setArchived never reported done", done[0]);
    }

    private TemplateDraft load(String id) {
        AtomicReference<TemplateDraft> loaded = new AtomicReference<>();
        templates.loadDraft(id, loaded::set, e -> { throw new AssertionError(e); });
        return loaded.get();
    }

    private int count(String sql) {
        try (Cursor c = database.getOpenHelper().getReadableDatabase().query(sql)) {
            c.moveToFirst();
            return c.getInt(0);
        }
    }

    private String string(String sql) {
        try (Cursor c = database.getOpenHelper().getReadableDatabase().query(sql)) {
            c.moveToFirst();
            return c.getString(0);
        }
    }

    private static String idByName(List<ExerciseSummary> library, String name) {
        for (ExerciseSummary s : library) {
            if (s.name().equals(name)) {
                return s.id();
            }
        }
        throw new AssertionError("Missing " + name);
    }

    private static List<String> setIds(TemplateExerciseDraft exercise) {
        List<String> ids = new ArrayList<>();
        for (SetPlan s : exercise.sets()) {
            ids.add(s.id());
        }
        return ids;
    }
}

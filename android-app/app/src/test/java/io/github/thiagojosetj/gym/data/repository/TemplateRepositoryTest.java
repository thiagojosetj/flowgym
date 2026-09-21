package io.github.thiagojosetj.gym.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.database.Cursor;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
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
import io.github.thiagojosetj.gym.domain.model.WeightUnit;
import io.github.thiagojosetj.gym.domain.template.ExercisePlanUpdate;
import io.github.thiagojosetj.gym.domain.template.ExerciseRef;
import io.github.thiagojosetj.gym.domain.template.SetPlan;
import io.github.thiagojosetj.gym.domain.template.TemplateDraft;
import io.github.thiagojosetj.gym.domain.template.TemplateExerciseDraft;
import io.github.thiagojosetj.gym.domain.template.TemplateSummary;
import io.github.thiagojosetj.gym.domain.util.IdGenerator;
import io.github.thiagojosetj.gym.testutil.LiveDataTestUtil;
import io.github.thiagojosetj.gym.testutil.TestContainers;

/** The first vertical slice at the data level: library → template → save → reopen. */
@RunWith(AndroidJUnit4.class)
public class TemplateRepositoryTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private AppDatabase database;
    private AppContainer container;
    private TemplateRepository templates;

    @Before
    public void setUp() {
        database = TestContainers.inMemoryDatabase();
        container = TestContainers.create(database);
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
        draft.updateExercisePlan(bench.id(), new ExercisePlanUpdate(4, RepRange.between(8, 10),
                Weight.of(40, WeightUnit.KILOGRAM), null, 120, "Banco no terceiro encaixe", SideMode.COMBINED));

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
        templates.duplicate(originalId, "Push A (cópia)", copyId::set, e -> { throw new AssertionError(e); });

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
        draft.addExercises(refs.get(), container.templateDefaults);
        return draft;
    }

    private String save(TemplateDraft draft) {
        AtomicReference<String> saved = new AtomicReference<>();
        templates.save(draft, saved::set, e -> { throw new AssertionError(e); });
        assertNotNull(saved.get());
        return saved.get();
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

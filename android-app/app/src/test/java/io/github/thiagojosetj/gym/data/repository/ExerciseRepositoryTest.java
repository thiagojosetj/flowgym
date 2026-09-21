package io.github.thiagojosetj.gym.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
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
import java.util.concurrent.atomic.AtomicReference;

import io.github.thiagojosetj.gym.AppContainer;
import io.github.thiagojosetj.gym.data.local.AppDatabase;
import io.github.thiagojosetj.gym.data.local.entity.ExerciseMuscleEntity;
import io.github.thiagojosetj.gym.domain.library.Equipment;
import io.github.thiagojosetj.gym.domain.library.ExerciseDetail;
import io.github.thiagojosetj.gym.domain.library.ExerciseFilter;
import io.github.thiagojosetj.gym.domain.library.ExerciseSummary;
import io.github.thiagojosetj.gym.domain.library.MuscleGroup;
import io.github.thiagojosetj.gym.domain.library.MuscleNode;
import io.github.thiagojosetj.gym.domain.library.MuscleRoleScope;
import io.github.thiagojosetj.gym.domain.model.MuscleRole;
import io.github.thiagojosetj.gym.domain.template.ExerciseRef;
import io.github.thiagojosetj.gym.testutil.LiveDataTestUtil;
import io.github.thiagojosetj.gym.testutil.TestContainers;

@RunWith(AndroidJUnit4.class)
public class ExerciseRepositoryTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private AppDatabase database;
    private ExerciseRepository repository;
    private List<MuscleGroup> groups;
    private List<Equipment> equipment;

    @Before
    public void setUp() throws Exception {
        database = TestContainers.inMemoryDatabase();
        AppContainer container = TestContainers.create(database);
        container.start(); // creates the local user and seeds the catalog (synchronously here)
        repository = container.exercises;
        groups = LiveDataTestUtil.getOrAwaitValue(repository.observeMuscleGroups());
        equipment = LiveDataTestUtil.getOrAwaitValue(repository.observeEquipment());
    }

    @After
    public void tearDown() {
        database.close();
    }

    @Test
    public void hierarchyHasGroupsWithSubgroups() {
        MuscleGroup back = group("back");
        List<String> codes = new ArrayList<>();
        for (MuscleNode node : back.subgroups()) {
            codes.add(node.code());
        }
        assertTrue(codes.containsAll(Arrays.asList(
                "back.lats", "back.upper_traps", "back.mid_lower_traps", "back.rhomboids")));
        assertEquals("Peito", groups.get(0).group().name());
    }

    @Test
    public void searchIgnoresCaseAccentsAndMatchesPartOfTheName() throws Exception {
        assertContains(library(ExerciseFilter.none().withQuery("ELEVAÇÃO")), "Elevação lateral com halteres");
        assertContains(library(ExerciseFilter.none().withQuery("elevacao")), "Elevação lateral com halteres");
        assertContains(library(ExerciseFilter.none().withQuery("pino ret")), "Supino reto com barra");
    }

    @Test
    public void searchMatchesGymAliases() throws Exception {
        assertContains(library(ExerciseFilter.none().withQuery("puxador")), "Puxada frontal na polia");
        assertContains(library(ExerciseFilter.none().withQuery("stiff")), "Levantamento terra romeno (stiff)");
    }

    @Test
    public void punctuationInQueriesNeverActsAsWildcard() throws Exception {
        // "%" and "_" are removed by normalization; SqlLike.escape is a second line of defence.
        assertEquals(library(ExerciseFilter.none()).size(), library(ExerciseFilter.none().withQuery("%")).size());
        assertEquals(0, library(ExerciseFilter.none().withQuery("zzzz")).size());
    }

    @Test
    public void groupFilterIncludesExercisesLinkedToItsSubgroups() throws Exception {
        List<ExerciseSummary> back = library(ExerciseFilter.none().withMuscleGroup(group("back").group().id()));
        assertContains(back, "Puxada frontal na polia");   // linked to back.lats
        assertContains(back, "Encolhimento com barra");    // linked to back.upper_traps
        for (ExerciseSummary s : back) {
            assertEquals("Costas", s.primaryGroupName());
        }
    }

    @Test
    public void subgroupFilterNarrowsTheGroup() throws Exception {
        MuscleGroup back = group("back");
        String traps = subgroup(back, "back.upper_traps").id();
        List<ExerciseSummary> result = library(ExerciseFilter.none()
                .withMuscleGroup(back.group().id()).withMuscleSubgroup(traps));
        assertContains(result, "Encolhimento com barra");
        assertFalse(names(result).contains("Puxada frontal na polia"));
    }

    @Test
    public void roleScopeSelectsPrimaryOrSecondaryLinks() throws Exception {
        String triceps = group("triceps").group().id();
        List<ExerciseSummary> primary = library(ExerciseFilter.none().withMuscleGroup(triceps));
        List<ExerciseSummary> secondary = library(ExerciseFilter.none().withMuscleGroup(triceps)
                .withRoleScope(MuscleRoleScope.SECONDARY));
        List<ExerciseSummary> any = library(ExerciseFilter.none().withMuscleGroup(triceps)
                .withRoleScope(MuscleRoleScope.ANY));

        assertContains(primary, "Tríceps na polia");
        assertFalse(names(primary).contains("Supino reto com barra"));
        assertContains(secondary, "Supino reto com barra");
        // "Any" is the UNION: an exercise can hit one triceps head as primary and another as
        // secondary (e.g. skull crusher), so it appears in both lists but only once here.
        HashSet<String> union = new HashSet<>(names(primary));
        union.addAll(names(secondary));
        assertEquals(union, new HashSet<>(names(any)));
        assertContains(primary, "Tríceps testa com barra W");
        assertContains(secondary, "Tríceps testa com barra W");
    }

    @Test
    public void filtersCombineWithAnd() throws Exception {
        MuscleGroup back = group("back");
        String lats = subgroup(back, "back.lats").id();
        String cable = equipmentId("cable");
        List<ExerciseSummary> result = library(ExerciseFilter.none()
                .withMuscleGroup(back.group().id()).withMuscleSubgroup(lats)
                .withEquipment(Collections.singleton(cable)));

        assertContains(result, "Puxada frontal na polia");
        assertFalse(names(result).contains("Barra fixa (pegada pronada)")); // lats, but not cable
        assertFalse(names(result).contains("Face pull na polia"));          // cable, but not lats
    }

    @Test
    public void equipmentFilterMatchesAnyOfTheSelected() throws Exception {
        List<ExerciseSummary> result = library(ExerciseFilter.none()
                .withEquipment(new HashSet<>(Arrays.asList(equipmentId("pull_up_bar"), equipmentId("dip_station")))));
        assertContains(result, "Barra fixa supinada");
        assertContains(result, "Mergulho nas paralelas");
    }

    @Test
    public void customExercisesOfOtherUsersAreHidden() throws Exception {
        database.getOpenHelper().getWritableDatabase().execSQL(
                "INSERT INTO exercise (id, owner_user_id, name, search_text, tracking_type, load_basis,"
                        + " implement_count, laterality, is_active, created_at, updated_at, sync_status)"
                        + " VALUES ('foreign-ex', 'someone-else', 'Exercício de outra pessoa',"
                        + " 'exercicio de outra pessoa', 'WEIGHT_REPS', 'TOTAL', 1, 'BILATERAL', 1, 0, 0, 'PENDING')");

        assertEquals(0, library(ExerciseFilter.none().withQuery("outra pessoa")).size());
    }

    @Test
    public void detailSeparatesPrimaryAndSecondaryMuscles() {
        String benchId = idOf("Supino reto com barra");
        AtomicReference<ExerciseDetail> detail = new AtomicReference<>();
        repository.loadDetail(benchId, detail::set, e -> { throw new AssertionError(e); });

        ExerciseDetail d = detail.get();
        assertNotNull(d);
        assertEquals("Peito · Peitoral médio (porção esternal)", d.primaryMuscles().get(0).qualifiedName());
        assertEquals(2, d.secondaryMuscles().size());
        assertEquals(Arrays.asList("Barra", "Banco"), d.equipmentNames());
        assertEquals(4, d.instructionSteps().size());
        assertFalse(d.custom());
    }

    @Test
    public void detailOfUnknownExerciseIsNull() {
        AtomicReference<ExerciseDetail> detail = new AtomicReference<>(null);
        boolean[] called = {false};
        repository.loadDetail("missing", d -> { detail.set(d); called[0] = true; }, e -> { throw new AssertionError(e); });
        assertTrue(called[0]);
        assertNull(detail.get());
    }

    @Test
    public void refsKeepTheRequestedOrderAndSkipUnknownIds() {
        String bench = idOf("Supino reto com barra");
        String row = idOf("Remada unilateral com halter (serrote)");
        AtomicReference<List<ExerciseRef>> refs = new AtomicReference<>();
        repository.loadRefs(Arrays.asList(row, "missing", bench), refs::set, e -> { throw new AssertionError(e); });

        assertEquals(2, refs.get().size());
        assertEquals(row, refs.get().get(0).id());
        assertTrue(refs.get().get(0).isUnilateral());
        assertTrue(refs.get().get(0).isLoadPerImplement());
        assertEquals(bench, refs.get().get(1).id());
    }

    @Test
    public void everyExerciseHasExactlyOneHighlightedPrimaryMuscle() {
        // sort_order 0 must be a PRIMARY link for every catalog exercise.
        for (ExerciseMuscleEntity link : allLinksWithSortZero()) {
            assertEquals(MuscleRole.PRIMARY, link.role);
        }
    }

    // ------------------------------------------------------------------ helpers

    private List<ExerciseSummary> library(ExerciseFilter filter) throws Exception {
        return LiveDataTestUtil.getOrAwaitValue(repository.observeLibrary(filter));
    }

    private MuscleGroup group(String code) {
        for (MuscleGroup g : groups) {
            if (g.group().code().equals(code)) {
                return g;
            }
        }
        throw new AssertionError("No group " + code);
    }

    private static MuscleNode subgroup(MuscleGroup group, String code) {
        for (MuscleNode n : group.subgroups()) {
            if (n.code().equals(code)) {
                return n;
            }
        }
        throw new AssertionError("No subgroup " + code);
    }

    private String equipmentId(String code) {
        for (Equipment e : equipment) {
            if (e.code().equals(code)) {
                return e.id();
            }
        }
        throw new AssertionError("No equipment " + code);
    }

    private String idOf(String name) {
        try {
            for (ExerciseSummary s : library(ExerciseFilter.none())) {
                if (s.name().equals(name)) {
                    return s.id();
                }
            }
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        throw new AssertionError("No exercise " + name);
    }

    private List<ExerciseMuscleEntity> allLinksWithSortZero() {
        List<ExerciseMuscleEntity> result = new ArrayList<>();
        try (android.database.Cursor c = database.getOpenHelper().getReadableDatabase()
                .query("SELECT exercise_id, muscle_id, role FROM exercise_muscle WHERE sort_order = 0")) {
            while (c.moveToNext()) {
                result.add(new ExerciseMuscleEntity(c.getString(0), c.getString(1),
                        MuscleRole.valueOf(c.getString(2)), 0));
            }
        }
        assertFalse(result.isEmpty());
        return result;
    }

    private static List<String> names(List<ExerciseSummary> list) {
        List<String> names = new ArrayList<>();
        for (ExerciseSummary s : list) {
            names.add(s.name());
        }
        return names;
    }

    private static void assertContains(List<ExerciseSummary> list, String name) {
        assertTrue("Expected '" + name + "' in " + names(list), names(list).contains(name));
    }
}

package io.github.thiagojosetj.gym.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import io.github.thiagojosetj.gym.AppContainer;
import io.github.thiagojosetj.gym.data.local.AppDatabase;
import io.github.thiagojosetj.gym.domain.library.ExerciseFilter;
import io.github.thiagojosetj.gym.domain.library.ExerciseSummary;
import io.github.thiagojosetj.gym.domain.model.RepRange;
import io.github.thiagojosetj.gym.domain.model.Weight;
import io.github.thiagojosetj.gym.domain.model.WeightUnit;
import io.github.thiagojosetj.gym.domain.progress.MuscleWorkload;
import io.github.thiagojosetj.gym.domain.progress.PeriodStatistics;
import io.github.thiagojosetj.gym.domain.progress.ProgressSnapshot;
import io.github.thiagojosetj.gym.domain.progress.TrainingPeriod;
import io.github.thiagojosetj.gym.domain.session.ActiveSession;
import io.github.thiagojosetj.gym.domain.session.LoggedSet;
import io.github.thiagojosetj.gym.domain.session.SetValues;
import io.github.thiagojosetj.gym.domain.template.ExerciseRef;
import io.github.thiagojosetj.gym.domain.template.TemplateDefaults;
import io.github.thiagojosetj.gym.domain.template.TemplateDraft;
import io.github.thiagojosetj.gym.testutil.LiveDataTestUtil;
import io.github.thiagojosetj.gym.testutil.MutableClock;
import io.github.thiagojosetj.gym.testutil.TestContainers;

/**
 * The statistics of a period (PRODUCT_SPEC PRG-04), built through the real repositories and the
 * real catalogue: a template is saved, a workout performed and finished, and only then is the
 * period asked what it came to.
 *
 * <p>The muscle rows in particular are worth exercising against the shipped catalogue rather than
 * a fixture: the whole point of the query is that it resolves a subgroup ({@code chest.middle}) to
 * its group, and a fixture would be free to agree with whatever the query happens to do.
 */
@RunWith(AndroidJUnit4.class)
public class ProgressRepositoryTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private static final String SUPINO = "Supino reto com barra";
    private static final String REMADA = "Remada curvada com barra";

    private AppDatabase database;
    private AppContainer app;
    private MutableClock clock;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = TestContainers.inMemoryDatabase();
        clock = MutableClock.at("2026-09-28T10:00:00Z");
        app = TestContainers.create(context, database, clock);
        app.start();
    }

    @After
    public void tearDown() {
        database.close();
    }

    @Test
    public void aPeriodWithNoSessionsComesBackEmptyRatherThanNull() throws Exception {
        PeriodStatistics statistics = period("2026-09-28", "2026-10-04");

        assertNotNull(statistics);
        assertTrue(statistics.isEmpty());
        assertTrue(statistics.muscles().isEmpty());
    }

    @Test
    public void thePeriodAddsUpTheSessionsOfItsDays() throws Exception {
        performWorkout(SUPINO, 40, 10);
        clock.advanceDays(1);
        performWorkout(SUPINO, 45, 10);

        PeriodStatistics statistics = period("2026-09-28", "2026-10-04");

        assertEquals(2, statistics.sessions());
        // Three sets per session at the template default, all confirmed with the same values.
        assertEquals(6, statistics.performedSets());
        assertEquals(60, statistics.totalReps());
        assertEquals(2_550_000L, statistics.volumeGrams());
    }

    @Test
    public void bothEndsOfThePeriodAreInsideIt() throws Exception {
        performWorkout(SUPINO, 40, 10);
        clock.advanceDays(6);
        performWorkout(SUPINO, 40, 10);

        assertEquals("os dois extremos contam", 2,
                period("2026-09-28", "2026-10-04").sessions());
        assertEquals("o dia anterior ao inicio nao", 1,
                period("2026-09-29", "2026-10-05").sessions());
        assertEquals("nem o dia seguinte ao fim", 1,
                period("2026-09-27", "2026-10-03").sessions());
    }

    @Test
    public void thePeriodUsesTheDayTheWorkoutWasLivedOnAndNotTheInstant() throws Exception {
        // The rule of section 11, which the calendar already follows: a workout at 23:30 in Sao
        // Paulo belongs to that day even after the phone lands somewhere the instant reads as the
        // next one. Grouping by started_at would move it between weeks mid-flight.
        String sessionId = performWorkout(SUPINO, 40, 10);
        database.getOpenHelper().getWritableDatabase().execSQL(
                "UPDATE workout_session SET local_date = '2026-09-27', started_at = "
                        + Instant.parse("2026-09-28T02:00:00Z").toEpochMilli()
                        + " WHERE id = '" + sessionId + "'");

        assertEquals("a semana e a do dia vivido", 1,
                period("2026-09-21", "2026-09-27").sessions());
        assertEquals(0, period("2026-09-28", "2026-10-04").sessions());
    }

    @Test
    public void aSessionRemovedFromTheHistoryLeavesThePeriodToo() throws Exception {
        String sessionId = performWorkout(SUPINO, 40, 10);
        assertEquals(1, period("2026-09-28", "2026-10-04").sessions());

        AtomicReference<Boolean> removed = new AtomicReference<>();
        app.history.delete(sessionId, removed::set, this::fail);
        assertTrue(Boolean.TRUE.equals(removed.get()));

        assertTrue("uma sessao excluida nao conta mais em nada",
                period("2026-09-28", "2026-10-04").isEmpty());
    }

    @Test
    public void theMuscleRowsComeFromTheCatalogueResolvedToTheGroup() throws Exception {
        // The catalogue files the bench press under chest.middle, a SUBGROUP. The row has to read
        // "Peito": the question this screen answers is about the chest, not about its thirds.
        performWorkout(SUPINO, 40, 10);

        List<MuscleWorkload> muscles = period("2026-09-28", "2026-10-04").muscles();

        MuscleWorkload chest = byName(muscles, "Peito");
        assertEquals(3, chest.primarySets());
        assertEquals(0, chest.secondarySets());
        assertTrue("o supino tambem usa triceps e ombro, como secundarios",
                muscles.size() > 1);
        for (MuscleWorkload workload : muscles) {
            if (!workload.name().equals("Peito")) {
                assertEquals(workload.name() + " devia ser secundario", 0,
                        workload.primarySets());
            }
        }
    }

    @Test
    public void twoExercisesThatShareAMuscleAddUpOnTheSameRow() throws Exception {
        performWorkout(new String[] {SUPINO, REMADA}, 40, 10);

        List<MuscleWorkload> muscles = period("2026-09-28", "2026-10-04").muscles();

        assertEquals(3, byName(muscles, "Peito").primarySets());
        assertEquals(3, byName(muscles, "Costas").primarySets());
        // Six sets were performed, and no row may claim more than the exercise that caused it.
        for (MuscleWorkload workload : muscles) {
            assertTrue(workload.name() + " passou do total de series",
                    workload.totalSets() <= 6);
        }
    }

    @Test
    public void theBoundsComeFromTheSameReadAsTheNumbers() throws Exception {
        // The arrows that move between periods stop where the training does, and they have to
        // agree with the figures beside them. One read answers both.
        performWorkout(SUPINO, 40, 10);
        clock.advanceDays(40);
        performWorkout(SUPINO, 40, 10);

        ProgressSnapshot snapshot = snapshot("2026-09-28", "2026-10-04");

        assertEquals(LocalDate.of(2026, 9, 28), snapshot.firstTrainedDay());
        assertEquals(LocalDate.of(2026, 11, 7), snapshot.lastTrainedDay());
        assertEquals("a semana mostrada continua sendo so a dela", 1,
                snapshot.statistics().sessions());
    }

    @Test
    public void withNoHistoryAtAllThereIsNowhereToGo() throws Exception {
        ProgressSnapshot snapshot = snapshot("2026-09-28", "2026-10-04");

        assertTrue(snapshot.hasNoHistory());
        assertNull(snapshot.firstTrainedDay());
        assertNull(snapshot.lastTrainedDay());
    }

    @Test
    public void aPeriodWithNothingInItStillKnowsWhereTheTrainingIs() throws Exception {
        // Standing on an empty week has to leave the arrows able to reach the weeks that are not.
        performWorkout(SUPINO, 40, 10);

        ProgressSnapshot empty = snapshot("2026-10-05", "2026-10-11");

        assertTrue(empty.statistics().isEmpty());
        assertEquals(LocalDate.of(2026, 9, 28), empty.firstTrainedDay());
        assertEquals(LocalDate.of(2026, 9, 28), empty.lastTrainedDay());
    }

    // ------------------------------------------------------------------ helpers

    private PeriodStatistics period(String from, String to) throws Exception {
        return snapshot(from, to).statistics();
    }

    private ProgressSnapshot snapshot(String from, String to) throws Exception {
        return LiveDataTestUtil.getOrAwaitValue(app.progress.observePeriod(
                new TrainingPeriod(TrainingPeriod.Kind.WEEK, LocalDate.parse(from),
                        LocalDate.parse(to))));
    }

    private static MuscleWorkload byName(List<MuscleWorkload> muscles, String name) {
        for (MuscleWorkload workload : muscles) {
            if (workload.name().equals(name)) {
                return workload;
            }
        }
        throw new AssertionError("Sem o grupo " + name + " em " + muscles);
    }

    private String performWorkout(String exerciseName, double kilos, int reps) throws Exception {
        return performWorkout(new String[] {exerciseName}, kilos, reps);
    }

    /** A whole workout: save the template, start it, confirm every set, finish. */
    private String performWorkout(String[] exerciseNames, double kilos, int reps) throws Exception {
        String templateId = createTemplate("Push " + exerciseNames.length, exerciseNames);
        AtomicReference<String> sessionId = new AtomicReference<>();
        app.activeSessions.startFromTemplate(templateId, sessionId::set, this::fail);
        assertNotNull("a sessao devia ter comecado", sessionId.get());

        AtomicReference<ActiveSession> session = new AtomicReference<>();
        app.activeSessions.loadSession(sessionId.get(), session::set, this::fail);
        for (int i = 0; i < session.get().exercises().size(); i++) {
            for (LoggedSet set : session.get().exercises().get(i).sets()) {
                AtomicReference<Boolean> done = new AtomicReference<>(false);
                app.activeSessions.confirmSet(sessionId.get(), set.id(),
                        new SetValues(Weight.of(kilos, WeightUnit.KILOGRAM), reps, null, null, null),
                        () -> done.set(true), this::fail);
                assertTrue("a serie devia ter sido confirmada", done.get());
            }
        }
        app.activeSessions.finish(sessionId.get(), summary -> { }, this::fail);
        return sessionId.get();
    }

    private String createTemplate(String name, String... exerciseNames) throws Exception {
        List<String> ids = new ArrayList<>();
        for (String exerciseName : exerciseNames) {
            ids.add(idOf(exerciseName));
        }
        AtomicReference<List<ExerciseRef>> refs = new AtomicReference<>();
        app.exercises.loadRefs(ids, refs::set, this::fail);
        TemplateDraft draft = TemplateDraft.newTemplate(app.ids);
        draft.rename(name);
        draft.addExercises(refs.get(), new TemplateDefaults(3, RepRange.exactly(10), 40, 90));
        AtomicReference<String> saved = new AtomicReference<>();
        app.templates.save(draft, saved::set, this::fail);
        assertNotNull("o treino devia ter sido salvo", saved.get());
        return saved.get();
    }

    private String idOf(String exerciseName) throws Exception {
        List<ExerciseSummary> library = LiveDataTestUtil.getOrAwaitValue(
                app.exercises.observeLibrary(ExerciseFilter.none()));
        for (ExerciseSummary summary : library) {
            if (summary.name().equals(exerciseName)) {
                return summary.id();
            }
        }
        throw new AssertionError("Sem exercicio " + exerciseName);
    }

    private void fail(Throwable error) {
        throw new AssertionError(error);
    }
}

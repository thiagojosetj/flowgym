package io.github.thiagojosetj.gym.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import io.github.thiagojosetj.gym.AppContainer;
import io.github.thiagojosetj.gym.data.local.AppDatabase;
import io.github.thiagojosetj.gym.domain.library.ExerciseFilter;
import io.github.thiagojosetj.gym.domain.library.ExerciseSummary;
import io.github.thiagojosetj.gym.domain.model.Laterality;
import io.github.thiagojosetj.gym.domain.model.RepRange;
import io.github.thiagojosetj.gym.domain.model.SideMode;
import io.github.thiagojosetj.gym.domain.model.Weight;
import io.github.thiagojosetj.gym.domain.model.WeightUnit;
import io.github.thiagojosetj.gym.domain.session.ActiveSession;
import io.github.thiagojosetj.gym.domain.session.LoggedSet;
import io.github.thiagojosetj.gym.domain.session.SessionExercise;
import io.github.thiagojosetj.gym.domain.session.SessionSummary;
import io.github.thiagojosetj.gym.domain.session.SessionVolume;
import io.github.thiagojosetj.gym.domain.session.SetStatus;
import io.github.thiagojosetj.gym.domain.session.SetValues;
import io.github.thiagojosetj.gym.domain.technique.TechniqueCatalog;
import io.github.thiagojosetj.gym.domain.template.ExercisePlanUpdate;
import io.github.thiagojosetj.gym.domain.template.ExerciseRef;
import io.github.thiagojosetj.gym.domain.template.TemplateDefaults;
import io.github.thiagojosetj.gym.domain.template.TemplateDraft;
import io.github.thiagojosetj.gym.domain.template.TemplateExerciseDraft;
import io.github.thiagojosetj.gym.testutil.LiveDataTestUtil;
import io.github.thiagojosetj.gym.testutil.MutableClock;
import io.github.thiagojosetj.gym.testutil.TestContainers;

/**
 * Per-side repetitions on disk (PRODUCT_SPEC 6.4): a unilateral exercise logged "E 10 / D 9" keeps
 * each side in its own column, and every total built on top of it counts both.
 *
 * <p>Nothing else in the app tests reps_left and reps_right at all: the write paths (confirm, a
 * typed draft, finishing) each have their own query, so each one gets a test here.
 */
@RunWith(AndroidJUnit4.class)
public class PerSideLoggingTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    /** Unilateral in the bundled catalog. One dumbbell, so its load counts once in the volume. */
    private static final String UNILATERAL_EXERCISE = "Remada unilateral com halter (serrote)";

    private AppDatabase database;
    private AppContainer app;
    private MutableClock clock;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = TestContainers.inMemoryDatabase();
        clock = MutableClock.at("2026-09-27T10:00:00Z");
        app = TestContainers.create(context, database, clock);
        app.start();
    }

    @After
    public void tearDown() {
        database.close();
    }

    @Test
    public void eachSideIsStoredInItsOwnColumnAndTheCombinedOneStaysEmpty() throws Exception {
        String sessionId = start(createPerSideTemplate("Costas A"));
        SessionExercise before = loadSession(sessionId).exercises().get(0);
        // The plan reached the session as a snapshot: a per-side unilateral exercise.
        assertEquals(Laterality.UNILATERAL, before.laterality());
        assertEquals(SideMode.PER_SIDE, before.sideMode());
        LoggedSet set = before.sets().get(0);

        confirm(sessionId, set.id(), new SetValues(kg(20), null, 10, 9, null));

        // The columns themselves, not only what the mapper reads back: a write and a read that
        // were swapped in the same way would still round-trip, and put E on the wrong side.
        assertEquals(Long.valueOf(10), column("reps_left", set.id()));
        assertEquals(Long.valueOf(9), column("reps_right", set.id()));
        assertNull(column("reps", set.id()));

        LoggedSet done = loadSession(sessionId).exercises().get(0).sets().get(0);
        assertEquals(SetStatus.COMPLETED, done.status());
        assertEquals(kg(20), done.values().weight());
        assertEquals(Integer.valueOf(10), done.values().repsLeft());
        assertEquals(Integer.valueOf(9), done.values().repsRight());
        assertNull(done.values().reps());
    }

    @Test
    public void typedSidesAreSavedWithoutConfirmingTheSet() throws Exception {
        String sessionId = start(createPerSideTemplate("Costas A"));
        LoggedSet set = loadSession(sessionId).exercises().get(0).sets().get(0);

        // The draft path is a different query from confirming: a field lost focus with only the
        // left side typed.
        app.activeSessions.saveTypedValues(set.id(), new SetValues(kg(20), null, 7, null, null));

        ActiveSession session = loadSession(sessionId);
        LoggedSet typed = session.exercises().get(0).sets().get(0);
        assertEquals(Long.valueOf(7), column("reps_left", set.id()));
        assertNull(column("reps_right", set.id()));
        assertEquals(Integer.valueOf(7), typed.values().repsLeft());
        assertNull(typed.values().repsRight());
        // Saved is not done: half a set is text to come back to, never a result.
        assertEquals(SetStatus.PENDING, typed.status());
        assertEquals(0, session.totals().totalReps());
        assertEquals(0L, session.totals().loadGrams());
    }

    @Test
    public void bothSidesCountTowardsTheRepetitionsAndTheVolume() throws Exception {
        String sessionId = start(createPerSideTemplate("Costas A"));
        LoggedSet set = loadSession(sessionId).exercises().get(0).sets().get(0);
        confirm(sessionId, set.id(), new SetValues(kg(20), null, 10, 9, null));

        ActiveSession session = loadSession(sessionId);
        SessionExercise exercise = session.exercises().get(0);

        // 10 + 9: not 10, and not 20. The expected volume is written out by hand instead of being
        // derived from the code under test: 20 kg on one dumbbell x 19 repetitions = 380 kg.
        assertEquals(Integer.valueOf(19), exercise.totalRepsOf(exercise.sets().get(0)));
        SessionVolume.Totals totals = SessionVolume.ofExercise(exercise);
        assertEquals(19, totals.totalReps());
        assertEquals(380_000L, totals.loadGrams());
        assertEquals(1, totals.countedSets());
        assertEquals(0, totals.excludedSets());
        // The whole-session route gives the same numbers...
        assertEquals(totals, session.totals());

        // ...and so does the summary the user is shown when finishing.
        SessionSummary summary = finish(sessionId);
        assertEquals(1, summary.performedSets());
        assertEquals(19, summary.totalReps());
        assertEquals(380_000L, summary.volumeGrams());
    }

    @Test
    public void aSetWithBothSidesIsCountedAsLeftPlusRightNeverAsTheCombinedNumber()
            throws Exception {
        // The bug the per-side screen fixed. A per-side exercise logged in the one combined field
        // kept the number in `reps`, and because its side mode was PER_SIDE the domain did not
        // double it: 10 repetitions on each side were reported as 10, half the work and half the
        // volume. The per-side branch has to be the one that counts - and it has to win over a
        // combined number stored next to it, or that number would count in its place.
        String sessionId = start(createPerSideTemplate("Costas A"));
        List<LoggedSet> sets = loadSession(sessionId).exercises().get(0).sets();

        // What the screen writes now: the two sides, and no combined number.
        confirm(sessionId, sets.get(0).id(), new SetValues(kg(20), null, 10, 9, null));
        // A combined 10 stored next to the same two sides, whatever put it there. It must count
        // neither as it is (10, the halved number) nor doubled (20, as for a set logged together).
        confirm(sessionId, sets.get(1).id(), new SetValues(kg(20), 10, 10, 9, null));

        SessionExercise exercise = loadSession(sessionId).exercises().get(0);
        assertEquals(Integer.valueOf(19), exercise.totalRepsOf(exercise.sets().get(0)));
        assertEquals(Integer.valueOf(19), exercise.totalRepsOf(exercise.sets().get(1)));
        // The volume follows the same number: two sets of 20 kg x 19 repetitions.
        assertEquals(2 * 380_000L, SessionVolume.ofExercise(exercise).loadGrams());
    }

    @Test
    public void finishingCompletesAFullyTypedPerSideSetAndSkipsAOneSidedOne() throws Exception {
        String sessionId = start(createPerSideTemplate("Costas A"));
        List<LoggedSet> sets = loadSession(sessionId).exercises().get(0).sets();
        // Set 1: both sides typed but never confirmed. Set 2: only the left side. Set 3: untouched.
        app.activeSessions.saveTypedValues(sets.get(0).id(),
                new SetValues(kg(20), null, 10, 9, null));
        app.activeSessions.saveTypedValues(sets.get(1).id(),
                new SetValues(kg(20), null, 8, null, null));

        SessionSummary summary = finish(sessionId);

        List<LoggedSet> after = loadSession(sessionId).exercises().get(0).sets();
        assertEquals(SetStatus.COMPLETED, after.get(0).status()); // both sides: a result
        assertEquals(SetStatus.SKIPPED, after.get(1).status());   // half a set is not
        assertEquals(SetStatus.SKIPPED, after.get(2).status());
        // Nothing is deleted: the eight repetitions typed on the left are still stored.
        assertEquals(Integer.valueOf(8), after.get(1).values().repsLeft());
        assertNull(after.get(1).values().repsRight());
        // Only the set that was a result is counted.
        assertEquals(1, summary.performedSets());
        assertEquals(2, summary.skippedSets());
        assertEquals(19, summary.totalReps());
        assertEquals(380_000L, summary.volumeGrams());
    }

    // ------------------------------------------------------------------ helpers

    /**
     * A template with the unilateral exercise switched to "por lado" - the state the template
     * editor's side toggle produces. Three sets of 12, as the editor creates them by default.
     */
    private String createPerSideTemplate(String name) throws Exception {
        AtomicReference<List<ExerciseRef>> refs = new AtomicReference<>();
        app.exercises.loadRefs(Collections.singletonList(idOf(UNILATERAL_EXERCISE)), refs::set,
                this::fail);
        TemplateDraft draft = TemplateDraft.newTemplate(app.ids);
        draft.rename(name);
        List<TemplateExerciseDraft> added = draft.addExercises(refs.get(),
                new TemplateDefaults(3, RepRange.exactly(12), 30, 90));
        List<ExercisePlanUpdate.Error> errors = draft.updateExercisePlan(added.get(0).id(),
                ExercisePlanUpdate.uniform(3, RepRange.exactly(12), null, null, 90, null,
                        SideMode.PER_SIDE),
                TechniqueCatalog.empty());
        assertTrue("the per-side plan should be accepted: " + errors, errors.isEmpty());
        AtomicReference<String> saved = new AtomicReference<>();
        app.templates.save(draft, saved::set, this::fail);
        return saved.get();
    }

    private String start(String templateId) {
        AtomicReference<String> id = new AtomicReference<>();
        app.activeSessions.startFromTemplate(templateId, id::set, this::fail);
        assertNotNull("session should have started", id.get());
        return id.get();
    }

    private ActiveSession loadSession(String sessionId) {
        AtomicReference<ActiveSession> session = new AtomicReference<>();
        app.activeSessions.loadSession(sessionId, session::set, this::fail);
        assertNotNull(session.get());
        return session.get();
    }

    private void confirm(String sessionId, String setId, SetValues values) {
        AtomicReference<Boolean> done = new AtomicReference<>(false);
        app.activeSessions.confirmSet(sessionId, setId, values, () -> done.set(true), this::fail);
        assertTrue("confirm should have completed", done.get());
    }

    private SessionSummary finish(String sessionId) {
        AtomicReference<SessionSummary> summary = new AtomicReference<>();
        app.activeSessions.finish(sessionId, summary::set, this::fail);
        assertNotNull("finish should have produced a summary", summary.get());
        return summary.get();
    }

    private String idOf(String name) throws Exception {
        List<ExerciseSummary> library = LiveDataTestUtil.getOrAwaitValue(
                app.exercises.observeLibrary(ExerciseFilter.none()));
        for (ExerciseSummary summary : library) {
            if (summary.name().equals(name)) {
                return summary.id();
            }
        }
        throw new AssertionError("No exercise " + name);
    }

    private static Weight kg(double value) {
        return Weight.of(value, WeightUnit.KILOGRAM);
    }

    /** The raw value of one column of a set, or null when the column is NULL. */
    private Long column(String column, String setId) {
        try (Cursor cursor = database.getOpenHelper().getReadableDatabase()
                .query("SELECT " + column + " FROM set_log WHERE id = '" + setId + "'")) {
            assertTrue(cursor.moveToFirst());
            return cursor.isNull(0) ? null : cursor.getLong(0);
        }
    }

    private void fail(Throwable error) {
        throw new AssertionError(error);
    }
}

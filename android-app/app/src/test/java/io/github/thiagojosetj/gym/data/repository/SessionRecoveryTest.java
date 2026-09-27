package io.github.thiagojosetj.gym.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import io.github.thiagojosetj.gym.AppContainer;
import io.github.thiagojosetj.gym.data.local.AppDatabase;
import io.github.thiagojosetj.gym.data.prefs.UiPreferences;
import io.github.thiagojosetj.gym.data.seed.CatalogSeeder;
import io.github.thiagojosetj.gym.domain.library.ExerciseFilter;
import io.github.thiagojosetj.gym.domain.library.ExerciseSummary;
import io.github.thiagojosetj.gym.domain.model.RepRange;
import io.github.thiagojosetj.gym.domain.model.Weight;
import io.github.thiagojosetj.gym.domain.model.WeightUnit;
import io.github.thiagojosetj.gym.domain.session.ActiveSession;
import io.github.thiagojosetj.gym.domain.session.LoggedSet;
import io.github.thiagojosetj.gym.domain.session.SessionStatus;
import io.github.thiagojosetj.gym.domain.session.SetStatus;
import io.github.thiagojosetj.gym.domain.session.SetValues;
import io.github.thiagojosetj.gym.domain.template.ExerciseRef;
import io.github.thiagojosetj.gym.domain.template.TemplateDefaults;
import io.github.thiagojosetj.gym.domain.template.TemplateDraft;
import io.github.thiagojosetj.gym.domain.util.IdGenerator;
import io.github.thiagojosetj.gym.testutil.LiveDataTestUtil;
import io.github.thiagojosetj.gym.testutil.MutableClock;
import io.github.thiagojosetj.gym.testutil.TestContainers;

/**
 * The durability claim, proven the only way it can be: on a real database FILE, closed and reopened
 * with a brand new AppContainer - which is what the process being killed mid-workout looks like
 * (ACT-08, PRODUCT_SPEC principle 2).
 */
@RunWith(AndroidJUnit4.class)
public class SessionRecoveryTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private static final String DB_NAME = "recovery-test.db";

    private AppDatabase database;
    private final MutableClock clock = MutableClock.at("2026-09-27T09:00:00Z");

    @After
    public void tearDown() {
        if (database != null) {
            database.close();
        }
        context().getDatabasePath(DB_NAME).delete();
    }

    @Test
    public void aWorkoutInProgressSurvivesTheProcessBeingKilled() throws Exception {
        AppContainer app = openApp();
        String templateId = createTemplate(app, "Push A", "Supino reto com barra");
        String sessionId = start(app, templateId);
        List<LoggedSet> sets = loadSession(app, sessionId).exercises().get(0).sets();
        clock.advanceMinutes(3);
        confirm(app, sessionId, sets.get(0).id(), new SetValues(kg(42.5), 10, null, null, null));
        clock.advanceSeconds(30); // 60 s of the 90 s rest still to go
        pause(app, sessionId);
        clock.advanceMinutes(5); // paused while the process dies

        // The process is killed: the database is closed and every object is dropped.
        database.close();
        database = null;
        AppContainer restarted = openApp();

        // Nothing but SQLite told us there was a workout going on.
        assertNotNull(restarted.database.sessionDao().findActive());
        assertEquals(sessionId, restarted.database.sessionDao().findActive().id);
        ActiveSession recovered = loadSession(restarted, sessionId);
        assertEquals(SessionStatus.ACTIVE, recovered.header().status());
        assertEquals("Push A", recovered.header().name());
        assertTrue(recovered.header().isPaused());

        LoggedSet first = recovered.exercises().get(0).sets().get(0);
        assertEquals(SetStatus.COMPLETED, first.status());
        assertEquals(kg(42.5), first.values().weight());
        assertEquals(Integer.valueOf(10), first.values().reps());

        // The elapsed time is recomputed from the timestamps, so the pause still counts.
        assertEquals(8 * 60_000L + 30_000L, recovered.header().clock().totalMs(clock.millis()));
        assertEquals(3 * 60_000L + 30_000L, recovered.header().clock().effectiveMs(clock.millis()));
        // The rest was frozen by the pause and is still frozen after the restart.
        assertEquals(60_000L, recovered.header().restRemainingMs(clock.millis()));

        // And the workout continues where it was.
        resume(restarted, sessionId);
        clock.advanceMinutes(1);
        ActiveSession resumed = loadSession(restarted, sessionId);
        assertEquals(4 * 60_000L + 30_000L, resumed.header().clock().effectiveMs(clock.millis()));
    }

    // ------------------------------------------------------------------ helpers

    private static Context context() {
        return ApplicationProvider.getApplicationContext();
    }

    /** A container over the same database file, as if the app had just been launched. */
    private AppContainer openApp() {
        database = Room.databaseBuilder(context(), AppDatabase.class, DB_NAME)
                .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
                .allowMainThreadQueries()
                .setQueryExecutor(Runnable::run)
                .setTransactionExecutor(Runnable::run)
                .build();
        AppContainer app = new AppContainer(database, TestContainers.directExecutors(), clock,
                clock.getZone(), IdGenerator.UUID_V7,
                () -> context().getAssets().open(CatalogSeeder.ASSET_PATH),
                new UiPreferences(context().getSharedPreferences("recovery_prefs", Context.MODE_PRIVATE)));
        app.start();
        return app;
    }

    private String createTemplate(AppContainer app, String name, String exerciseName) throws Exception {
        String exerciseId = null;
        for (ExerciseSummary summary
                : LiveDataTestUtil.getOrAwaitValue(app.exercises.observeLibrary(ExerciseFilter.none()))) {
            if (summary.name().equals(exerciseName)) {
                exerciseId = summary.id();
            }
        }
        assertNotNull(exerciseId);
        AtomicReference<List<ExerciseRef>> refs = new AtomicReference<>();
        app.exercises.loadRefs(java.util.Collections.singletonList(exerciseId), refs::set, this::fail);
        TemplateDraft draft = TemplateDraft.newTemplate(app.ids);
        draft.rename(name);
        draft.addExercises(refs.get(), new TemplateDefaults(3, RepRange.exactly(12), 30, 90));
        AtomicReference<String> saved = new AtomicReference<>();
        app.templates.save(draft, saved::set, this::fail);
        return saved.get();
    }

    private String start(AppContainer app, String templateId) {
        AtomicReference<String> id = new AtomicReference<>();
        app.activeSessions.startFromTemplate(templateId, id::set, this::fail);
        assertNotNull(id.get());
        return id.get();
    }

    private ActiveSession loadSession(AppContainer app, String sessionId) {
        AtomicReference<ActiveSession> session = new AtomicReference<>();
        app.activeSessions.loadSession(sessionId, session::set, this::fail);
        assertNotNull(session.get());
        return session.get();
    }

    private void confirm(AppContainer app, String sessionId, String setId, SetValues values) {
        AtomicReference<Boolean> done = new AtomicReference<>(false);
        app.activeSessions.confirmSet(sessionId, setId, values, () -> done.set(true), this::fail);
        assertTrue(done.get());
    }

    private void pause(AppContainer app, String sessionId) {
        AtomicReference<Boolean> done = new AtomicReference<>(false);
        app.activeSessions.pause(sessionId, () -> done.set(true), this::fail);
        assertTrue(done.get());
    }

    private void resume(AppContainer app, String sessionId) {
        AtomicReference<Boolean> done = new AtomicReference<>(false);
        app.activeSessions.resume(sessionId, () -> done.set(true), this::fail);
        assertTrue(done.get());
    }

    private static Weight kg(double value) {
        return Weight.of(value, WeightUnit.KILOGRAM);
    }

    private void fail(Throwable error) {
        throw new AssertionError(error);
    }
}

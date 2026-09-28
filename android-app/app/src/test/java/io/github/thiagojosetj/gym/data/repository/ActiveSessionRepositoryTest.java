package io.github.thiagojosetj.gym.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import io.github.thiagojosetj.gym.AppContainer;
import io.github.thiagojosetj.gym.data.local.AppDatabase;
import io.github.thiagojosetj.gym.domain.library.ExerciseFilter;
import io.github.thiagojosetj.gym.domain.library.ExerciseSummary;
import io.github.thiagojosetj.gym.domain.model.RepRange;
import io.github.thiagojosetj.gym.domain.model.Weight;
import io.github.thiagojosetj.gym.domain.model.WeightUnit;
import io.github.thiagojosetj.gym.domain.session.ActiveSession;
import io.github.thiagojosetj.gym.domain.session.LoggedSet;
import io.github.thiagojosetj.gym.domain.session.SessionExercise;
import io.github.thiagojosetj.gym.domain.session.SessionStatus;
import io.github.thiagojosetj.gym.domain.session.SessionSummary;
import io.github.thiagojosetj.gym.domain.session.SetStatus;
import io.github.thiagojosetj.gym.domain.session.SetValues;
import io.github.thiagojosetj.gym.domain.template.ExerciseRef;
import io.github.thiagojosetj.gym.domain.template.TemplateDefaults;
import io.github.thiagojosetj.gym.domain.template.TemplateDraft;
import io.github.thiagojosetj.gym.testutil.LiveDataTestUtil;
import io.github.thiagojosetj.gym.testutil.MutableClock;
import io.github.thiagojosetj.gym.testutil.TestContainers;

/** The write path of a workout in progress: every action is on disk when it returns. */
@RunWith(AndroidJUnit4.class)
public class ActiveSessionRepositoryTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private static final long MINUTE = 60_000L;

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
    public void startingCopiesThePlanSoLaterTemplateEditsDoNotRewriteHistory() throws Exception {
        String templateId = createTemplate("Push A", "Supino reto com barra");
        String sessionId = start(templateId);

        // The template changes after the session started: renamed, and the plan doubled.
        TemplateDraft draft = loadDraft(templateId);
        draft.rename("Push A v2");
        AtomicReference<String> saved = new AtomicReference<>();
        app.templates.save(draft, saved::set, this::fail);

        ActiveSession session = loadSession(sessionId);
        assertEquals("Push A", session.header().name());
        SessionExercise exercise = session.exercises().get(0);
        assertEquals("Supino reto com barra", exercise.name());
        assertEquals(3, exercise.sets().size());
        assertEquals(RepRange.exactly(12), exercise.sets().get(0).plannedReps());
        assertEquals(90, exercise.sets().get(0).plannedRestSeconds());
        assertEquals(SetStatus.PENDING, exercise.sets().get(0).status());
        // Nothing performed yet: a plan is not a result.
        assertTrue(exercise.sets().get(0).values().isEmpty());
    }

    @Test
    public void onlyOneSessionCanBeActiveAtATime() throws Exception {
        String templateId = createTemplate("Push A", "Supino reto com barra");
        String first = start(templateId);

        AtomicReference<Throwable> error = new AtomicReference<>();
        app.activeSessions.startFromTemplate(templateId,
                id -> {
                    throw new AssertionError("a second session must not start: " + id);
                },
                error::set);

        assertTrue(error.get() instanceof ActiveSessionRepository.ActiveSessionExistsException);
        assertEquals(first, ((ActiveSessionRepository.ActiveSessionExistsException) error.get()).sessionId());
        assertEquals(1, count("SELECT COUNT(*) FROM workout_session WHERE status = 'ACTIVE'"));
        // The failed attempt must not leave orphan children behind.
        assertEquals(1, count("SELECT COUNT(*) FROM session_exercise"));
    }

    @Test
    public void confirmingASetWritesTheResultAndStartsTheRestInOneGo() throws Exception {
        String sessionId = start(createTemplate("Push A", "Supino reto com barra"));
        LoggedSet first = loadSession(sessionId).exercises().get(0).sets().get(0);

        clock.advanceMinutes(2);
        confirm(sessionId, first.id(), new SetValues(kg(42.5), 10, null, null, null));

        ActiveSession session = loadSession(sessionId);
        LoggedSet done = session.exercises().get(0).sets().get(0);
        assertEquals(SetStatus.COMPLETED, done.status());
        assertEquals(kg(42.5), done.values().weight());
        assertEquals(Integer.valueOf(10), done.values().reps());
        assertNotNull(done.completedAt());
        // The rest started in the same transaction, as an instant.
        assertEquals(first.id(), session.header().restSetLogId());
        assertEquals(90_000L, session.header().restRemainingMs(clock.millis()));
        assertTrue(session.header().isResting(clock.millis()));
    }

    @Test
    public void aTypedValueLandingAfterTheConfirmationIsIgnored() throws Exception {
        String sessionId = start(createTemplate("Push A", "Supino reto com barra"));
        LoggedSet set = loadSession(sessionId).exercises().get(0).sets().get(0);

        confirm(sessionId, set.id(), new SetValues(kg(40), 10, null, null, null));
        // A debounced draft write arrives late, with stale values.
        app.activeSessions.saveTypedValues(set.id(), new SetValues(kg(30), 5, null, null, null));

        LoggedSet after = loadSession(sessionId).exercises().get(0).sets().get(0);
        assertEquals(kg(40), after.values().weight());
        assertEquals(Integer.valueOf(10), after.values().reps());
    }

    @Test
    public void pausingFreezesTheRestAndResumingGivesItBack() throws Exception {
        String sessionId = start(createTemplate("Push A", "Supino reto com barra"));
        LoggedSet set = loadSession(sessionId).exercises().get(0).sets().get(0);
        confirm(sessionId, set.id(), new SetValues(kg(40), 10, null, null, null));

        clock.advanceSeconds(30); // 60 s of rest left
        pause(sessionId);
        long frozen = loadSession(sessionId).header().restRemainingMs(clock.millis());
        clock.advanceMinutes(10); // a phone call
        ActiveSession paused = loadSession(sessionId);

        assertEquals(60_000L, frozen);
        assertEquals(60_000L, paused.header().restRemainingMs(clock.millis()));
        assertTrue(paused.header().isPaused());
        // The effective time stands still while the total keeps running.
        assertEquals(30_000L, paused.header().clock().effectiveMs(clock.millis()));
        assertEquals(10 * MINUTE + 30_000L, paused.header().clock().totalMs(clock.millis()));

        resume(sessionId);
        ActiveSession resumed = loadSession(sessionId);
        assertFalse(resumed.header().isPaused());
        assertEquals(60_000L, resumed.header().restRemainingMs(clock.millis()));
        clock.advanceSeconds(20);
        assertEquals(40_000L, loadSession(sessionId).header().restRemainingMs(clock.millis()));
    }

    @Test
    public void restCanBeStretchedAndCutButNeverGoesNegative() throws Exception {
        String sessionId = start(createTemplate("Push A", "Supino reto com barra"));
        LoggedSet set = loadSession(sessionId).exercises().get(0).sets().get(0);
        confirm(sessionId, set.id(), new SetValues(kg(40), 10, null, null, null));

        adjustRest(sessionId, 30);
        assertEquals(120_000L, loadSession(sessionId).header().restRemainingMs(clock.millis()));
        adjustRest(sessionId, -15);
        assertEquals(105_000L, loadSession(sessionId).header().restRemainingMs(clock.millis()));

        clock.advanceSeconds(100); // 5 s left
        adjustRest(sessionId, -15);
        assertEquals(0L, loadSession(sessionId).header().restRemainingMs(clock.millis()));

        skipRest(sessionId);
        assertNull(loadSession(sessionId).header().restSetLogId());
    }

    @Test
    public void addingAndRemovingSetsKeepsThePositionsContiguous() throws Exception {
        String sessionId = start(createTemplate("Push A", "Supino reto com barra"));
        SessionExercise exercise = loadSession(sessionId).exercises().get(0);

        addSet(sessionId, exercise.id());
        List<LoggedSet> four = loadSession(sessionId).exercises().get(0).sets();
        assertEquals(4, four.size());
        assertEquals(Arrays.asList(0, 1, 2, 3), positionsOf(four));
        // The new set copies the plan of the last one, and starts as not performed.
        assertEquals(four.get(2).plannedReps(), four.get(3).plannedReps());
        assertEquals(SetStatus.PENDING, four.get(3).status());

        removeSet(sessionId, exercise.id(), four.get(1).id());
        List<LoggedSet> three = loadSession(sessionId).exercises().get(0).sets();
        assertEquals(3, three.size());
        assertEquals(Arrays.asList(0, 1, 2), positionsOf(three));
        assertEquals(Arrays.asList(1, 2, 3), workingNumbersOf(three));
    }

    @Test
    public void theLastSetOfAnExerciseIsKept() throws Exception {
        String sessionId = start(createTemplate("Push A", "Supino reto com barra"));
        SessionExercise exercise = loadSession(sessionId).exercises().get(0);
        for (LoggedSet set : exercise.sets()) {
            removeSet(sessionId, exercise.id(), set.id());
        }

        assertEquals(1, loadSession(sessionId).exercises().get(0).sets().size());
    }

    @Test
    public void finishingCompletesWhatWasFilledSkipsTheRestAndNeverDeletes() throws Exception {
        String sessionId = start(createTemplate("Push A", "Supino reto com barra"));
        List<LoggedSet> sets = loadSession(sessionId).exercises().get(0).sets();
        confirm(sessionId, sets.get(0).id(), new SetValues(kg(40), 10, null, null, null));
        // Set 2 was filled in but never confirmed; set 3 was never touched.
        app.activeSessions.saveTypedValues(sets.get(1).id(), new SetValues(kg(40), 8, null, null, null));

        clock.advanceMinutes(40);
        SessionSummary summary = finish(sessionId);

        ActiveSession finished = loadSession(sessionId);
        assertEquals(SessionStatus.COMPLETED, finished.header().status());
        List<LoggedSet> after = finished.exercises().get(0).sets();
        assertEquals(3, after.size()); // nothing was deleted
        assertEquals(SetStatus.COMPLETED, after.get(0).status());
        assertEquals(SetStatus.COMPLETED, after.get(1).status()); // filled in, so it counts
        assertEquals(SetStatus.SKIPPED, after.get(2).status());   // empty, kept as planned-not-done
        assertNull(finished.header().restSetLogId());

        assertEquals(2, summary.performedSets());
        assertEquals(1, summary.skippedSets());
        assertEquals(18, summary.totalReps());
        assertEquals(40 * 18 * 1000L, summary.volumeGrams());
        assertEquals(40 * MINUTE, summary.totalMs());
        assertEquals(40 * MINUTE, summary.effectiveMs());
    }

    @Test
    public void finishingRecomputesThePausedTotalFromTheIntervals() throws Exception {
        String sessionId = start(createTemplate("Push A", "Supino reto com barra"));
        clock.advanceMinutes(5);
        pause(sessionId);
        clock.advanceMinutes(3);
        resume(sessionId);
        clock.advanceMinutes(2);

        // A stale cache (a crash between pause and resume) must not reach the history.
        database.getOpenHelper().getWritableDatabase()
                .execSQL("UPDATE workout_session SET total_paused_ms = 999999 WHERE id = '" + sessionId + "'");

        SessionSummary summary = finish(sessionId);

        assertEquals(10 * MINUTE, summary.totalMs());
        assertEquals(7 * MINUTE, summary.effectiveMs());
        assertEquals(3 * MINUTE, count("SELECT total_paused_ms FROM workout_session WHERE id = '"
                + sessionId + "'"));
    }

    @Test
    public void finishingWhilePausedClosesThePause() throws Exception {
        String sessionId = start(createTemplate("Push A", "Supino reto com barra"));
        clock.advanceMinutes(5);
        pause(sessionId);
        clock.advanceMinutes(5);

        finish(sessionId);

        assertEquals(0, count("SELECT COUNT(*) FROM session_pause WHERE ended_at IS NULL"));
        assertEquals(5 * MINUTE, count("SELECT total_paused_ms FROM workout_session WHERE id = '"
                + sessionId + "'"));
    }

    @Test
    public void finishingAnAlreadyFinishedSessionDoesNotMoveItsDuration() throws Exception {
        // Regression (review 2026-09-28): the screen kept offering "Finalizar" on a closed session,
        // and a second tap rewrote ended_at - a 45-minute workout became 65 minutes.
        String sessionId = start(createTemplate("Push A", "Supino reto com barra"));
        List<LoggedSet> sets = loadSession(sessionId).exercises().get(0).sets();
        confirm(sessionId, sets.get(0).id(), new SetValues(kg(40), 10, null, null, null));
        clock.advanceMinutes(45);
        SessionSummary first = finish(sessionId);

        clock.advanceMinutes(20);
        SessionSummary second = finish(sessionId);

        assertEquals(45 * MINUTE, first.totalMs());
        assertEquals(45 * MINUTE, second.totalMs());
        assertEquals(first.performedSets(), second.performedSets());
        assertEquals(45 * MINUTE, count("SELECT ended_at - started_at FROM workout_session WHERE id = '"
                + sessionId + "'"));
    }

    @Test
    public void aRestThatRanOutIsClearedSoNothingKeepsCountingPastZero() throws Exception {
        // Regression (review 2026-09-28): a rest expiring is an instant passing, not a write, so
        // nothing re-emitted: the bar sat at zero and the notification counted past it.
        String sessionId = start(createTemplate("Push A", "Supino reto com barra"));
        LoggedSet set = loadSession(sessionId).exercises().get(0).sets().get(0);
        confirm(sessionId, set.id(), new SetValues(kg(40), 10, null, null, null));

        // Not over yet: the call must do nothing.
        clock.advanceSeconds(30);
        restFinished(sessionId, set.id());
        assertEquals(set.id(), loadSession(sessionId).header().restSetLogId());

        clock.advanceSeconds(60);
        restFinished(sessionId, set.id());
        assertNull(loadSession(sessionId).header().restSetLogId());
    }

    @Test
    public void aStretchedRestIsNotClearedByALateAlert() throws Exception {
        String sessionId = start(createTemplate("Push A", "Supino reto com barra"));
        LoggedSet set = loadSession(sessionId).exercises().get(0).sets().get(0);
        confirm(sessionId, set.id(), new SetValues(kg(40), 10, null, null, null));
        clock.advanceSeconds(85);
        adjustRest(sessionId, 30); // the user asked for more time just before the alert fired

        restFinished(sessionId, set.id());

        assertEquals(set.id(), loadSession(sessionId).header().restSetLogId());
        assertEquals(35_000L, loadSession(sessionId).header().restRemainingMs(clock.millis()));
    }

    @Test
    public void aClockCorrectedBackwardsStillLetsTheWorkoutBeFinished() throws Exception {
        // Regression (review 2026-09-28): the device clock moving back past the start made finish()
        // throw, so the session stayed ACTIVE - and with one active session allowed, the user could
        // not start another workout either. The only way out was discarding the session.
        String sessionId = start(createTemplate("Push A", "Supino reto com barra"));
        LoggedSet set = loadSession(sessionId).exercises().get(0).sets().get(0);
        clock.advanceMinutes(10);
        confirm(sessionId, set.id(), new SetValues(kg(40), 10, null, null, null));

        // The network corrects an RTC that was 15 minutes ahead.
        clock.set(clock.instant().minusSeconds(15 * 60));
        SessionSummary summary = finish(sessionId);

        assertEquals(0L, summary.totalMs());
        assertEquals(0L, summary.effectiveMs());
        assertEquals(1, summary.performedSets());
        assertEquals(SessionStatus.COMPLETED, loadSession(sessionId).header().status());
        // And a new workout can start right away.
        assertNotNull(start(createTemplate("Pull A", "Rosca martelo")));
    }

    @Test
    public void aFinishedSessionCannotBeChangedAnyMore() throws Exception {
        // Regression (review 2026-09-28): a screen left open on a finished session could still
        // confirm sets, rewriting history that the user had already been shown.
        String sessionId = start(createTemplate("Push A", "Supino reto com barra"));
        List<LoggedSet> sets = loadSession(sessionId).exercises().get(0).sets();
        confirm(sessionId, sets.get(0).id(), new SetValues(kg(40), 10, null, null, null));
        finish(sessionId);

        confirm(sessionId, sets.get(1).id(), new SetValues(kg(40), 8, null, null, null));
        addSet(sessionId, loadSession(sessionId).exercises().get(0).id());
        setTechnique(sessionId, sets.get(0).id(), warmUpTechniqueId());

        ActiveSession after = loadSession(sessionId);
        assertEquals(1, after.completedSets());
        assertEquals(3, after.exercises().get(0).sets().size());
        assertNull(after.exercises().get(0).sets().get(0).techniqueId());
    }

    @Test
    public void theSessionRecordsTheTimeZoneItWasPerformedIn() throws Exception {
        // The app can stay in memory across a flight, so the zone is read per session.
        String sessionId = start(createTemplate("Push A", "Supino reto com barra"));

        assertEquals("Z", count("SELECT time_zone FROM workout_session WHERE id = '" + sessionId + "'",
                String.class));
        assertEquals("2026-09-27", count("SELECT local_date FROM workout_session WHERE id = '"
                + sessionId + "'", String.class));
    }

    @Test
    public void discardingKeepsTheRowSoTheDiscardCanBeSynced() throws Exception {
        String sessionId = start(createTemplate("Push A", "Supino reto com barra"));

        AtomicReference<Boolean> done = new AtomicReference<>(false);
        app.activeSessions.discard(sessionId, () -> done.set(true), this::fail);

        assertTrue(done.get());
        assertEquals(1, count("SELECT COUNT(*) FROM workout_session WHERE status = 'DISCARDED'"));
        assertNull(database.sessionDao().findActive());
        assertEquals(0, count("SELECT COUNT(*) FROM workout_session WHERE status = 'ACTIVE'"));
        // And a new session can start right away.
        assertNotNull(start(createTemplate("Pull A", "Rosca martelo")));
    }

    @Test
    public void thePreviousSessionIsTheLastCompletedOneWithThatExercise() throws Exception {
        String templateId = createTemplate("Push A", "Supino reto com barra");

        String first = start(templateId);
        List<LoggedSet> firstSets = loadSession(first).exercises().get(0).sets();
        confirm(first, firstSets.get(0).id(), new SetValues(kg(40), 10, null, null, null));
        finish(first);

        clock.advanceMinutes(60);
        String discarded = start(templateId);
        List<LoggedSet> discardedSets = loadSession(discarded).exercises().get(0).sets();
        confirm(discarded, discardedSets.get(0).id(), new SetValues(kg(100), 1, null, null, null));
        AtomicReference<Boolean> done = new AtomicReference<>(false);
        app.activeSessions.discard(discarded, () -> done.set(true), this::fail);
        assertTrue(done.get());

        clock.advanceMinutes(60);
        String third = start(templateId);
        LoggedSet set = loadSession(third).exercises().get(0).sets().get(0);

        // The discarded session is ignored: the suggestion comes from the finished one.
        assertNotNull(set.previous());
        assertEquals(kg(40), set.previous().weight());
        assertEquals(Integer.valueOf(10), set.previous().reps());
        assertEquals(kg(40), set.suggestion().weight());
    }

    @Test
    public void aWarmUpAddedTodayDoesNotShiftWhatTheAnteriorColumnShows() throws Exception {
        String templateId = createTemplate("Push A", "Supino reto com barra");
        String first = start(templateId);
        List<LoggedSet> firstSets = loadSession(first).exercises().get(0).sets();
        confirm(first, firstSets.get(0).id(), new SetValues(kg(40), 10, null, null, null));
        confirm(first, firstSets.get(1).id(), new SetValues(kg(40), 9, null, null, null));
        finish(first);

        clock.advanceMinutes(60);
        String second = start(templateId);
        SessionExercise exercise = loadSession(second).exercises().get(0);
        // Today the first set becomes a warm-up.
        setTechnique(second, exercise.sets().get(0).id(), warmUpTechniqueId());

        List<LoggedSet> sets = loadSession(second).exercises().get(0).sets();
        assertTrue(sets.get(0).isWarmUp());
        assertNull(sets.get(0).workingNumber());
        assertNull(sets.get(0).previous()); // a warm-up is not compared with anything
        // Working set 1 of today is compared with working set 1 of last time, not with its neighbour.
        assertEquals(Integer.valueOf(1), sets.get(1).workingNumber());
        assertEquals(Integer.valueOf(10), sets.get(1).previous().reps());
        assertEquals(Integer.valueOf(9), sets.get(2).previous().reps());
    }

    // ------------------------------------------------------------------ helpers

    private String createTemplate(String name, String... exerciseNames) throws Exception {
        List<String> ids = new ArrayList<>();
        for (String exerciseName : exerciseNames) {
            ids.add(idOf(exerciseName));
        }
        AtomicReference<List<ExerciseRef>> refs = new AtomicReference<>();
        app.exercises.loadRefs(ids, refs::set, this::fail);
        TemplateDraft draft = TemplateDraft.newTemplate(app.ids);
        draft.rename(name);
        draft.addExercises(refs.get(), new TemplateDefaults(3, RepRange.exactly(12), 30, 90));
        AtomicReference<String> saved = new AtomicReference<>();
        app.templates.save(draft, saved::set, this::fail);
        return saved.get();
    }

    private TemplateDraft loadDraft(String templateId) {
        AtomicReference<TemplateDraft> draft = new AtomicReference<>();
        app.templates.loadDraft(templateId, draft::set, this::fail);
        return draft.get();
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

    private void pause(String sessionId) {
        AtomicReference<Boolean> done = new AtomicReference<>(false);
        app.activeSessions.pause(sessionId, () -> done.set(true), this::fail);
        assertTrue(done.get());
    }

    private void resume(String sessionId) {
        AtomicReference<Boolean> done = new AtomicReference<>(false);
        app.activeSessions.resume(sessionId, () -> done.set(true), this::fail);
        assertTrue(done.get());
    }

    private void adjustRest(String sessionId, int deltaSeconds) {
        AtomicReference<Boolean> done = new AtomicReference<>(false);
        app.activeSessions.adjustRest(sessionId, deltaSeconds, () -> done.set(true), this::fail);
        assertTrue(done.get());
    }

    private void restFinished(String sessionId, String setId) {
        AtomicReference<Boolean> done = new AtomicReference<>(false);
        app.activeSessions.restFinished(sessionId, setId, () -> done.set(true), this::fail);
        assertTrue(done.get());
    }

    private void skipRest(String sessionId) {
        AtomicReference<Boolean> done = new AtomicReference<>(false);
        app.activeSessions.skipRest(sessionId, () -> done.set(true), this::fail);
        assertTrue(done.get());
    }

    private void addSet(String sessionId, String sessionExerciseId) {
        AtomicReference<Boolean> done = new AtomicReference<>(false);
        app.activeSessions.addSet(sessionId, sessionExerciseId, () -> done.set(true), this::fail);
        assertTrue(done.get());
    }

    private void removeSet(String sessionId, String sessionExerciseId, String setId) {
        AtomicReference<Boolean> done = new AtomicReference<>(false);
        app.activeSessions.removeSet(sessionId, sessionExerciseId, setId, () -> done.set(true), this::fail);
        assertTrue(done.get());
    }

    private void setTechnique(String sessionId, String setId, String techniqueId) {
        AtomicReference<Boolean> done = new AtomicReference<>(false);
        app.activeSessions.setSetTechnique(sessionId, setId, techniqueId, () -> done.set(true), this::fail);
        assertTrue(done.get());
    }

    private SessionSummary finish(String sessionId) {
        AtomicReference<SessionSummary> summary = new AtomicReference<>();
        app.activeSessions.finish(sessionId, summary::set, this::fail);
        assertNotNull("finish should have produced a summary", summary.get());
        return summary.get();
    }

    private String warmUpTechniqueId() {
        try (Cursor cursor = database.getOpenHelper().getReadableDatabase()
                .query("SELECT id FROM training_technique WHERE code = 'AQ'")) {
            assertTrue(cursor.moveToFirst());
            return cursor.getString(0);
        }
    }

    private String idOf(String name) throws Exception {
        for (ExerciseSummary summary
                : LiveDataTestUtil.getOrAwaitValue(app.exercises.observeLibrary(ExerciseFilter.none()))) {
            if (summary.name().equals(name)) {
                return summary.id();
            }
        }
        throw new AssertionError("No exercise " + name);
    }

    private static List<Integer> positionsOf(List<LoggedSet> sets) {
        List<Integer> positions = new ArrayList<>(sets.size());
        for (LoggedSet set : sets) {
            positions.add(set.position());
        }
        return positions;
    }

    private static List<Integer> workingNumbersOf(List<LoggedSet> sets) {
        List<Integer> numbers = new ArrayList<>(sets.size());
        for (LoggedSet set : sets) {
            numbers.add(set.workingNumber());
        }
        return numbers;
    }

    private static Weight kg(double value) {
        return Weight.of(value, WeightUnit.KILOGRAM);
    }

    private String count(String sql, Class<String> asText) {
        try (Cursor cursor = database.getOpenHelper().getReadableDatabase().query(sql)) {
            assertTrue(cursor.moveToFirst());
            return cursor.getString(0);
        }
    }

    private long count(String sql) {
        try (Cursor cursor = database.getOpenHelper().getReadableDatabase().query(sql)) {
            assertTrue(cursor.moveToFirst());
            return cursor.getLong(0);
        }
    }

    private void fail(Throwable error) {
        throw new AssertionError(error);
    }
}

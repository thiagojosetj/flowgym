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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import io.github.thiagojosetj.gym.AppContainer;
import io.github.thiagojosetj.gym.data.local.AppDatabase;
import io.github.thiagojosetj.gym.domain.library.ExerciseFilter;
import io.github.thiagojosetj.gym.domain.library.ExerciseSummary;
import io.github.thiagojosetj.gym.domain.model.RepRange;
import io.github.thiagojosetj.gym.domain.model.SideMode;
import io.github.thiagojosetj.gym.domain.model.Weight;
import io.github.thiagojosetj.gym.domain.model.WeightUnit;
import io.github.thiagojosetj.gym.domain.session.ActiveSession;
import io.github.thiagojosetj.gym.domain.session.LoggedSet;
import io.github.thiagojosetj.gym.domain.session.MetricChange;
import io.github.thiagojosetj.gym.domain.session.SessionComparison;
import io.github.thiagojosetj.gym.domain.session.SessionDetail;
import io.github.thiagojosetj.gym.domain.session.SessionExercise;
import io.github.thiagojosetj.gym.domain.session.SessionExerciseSummary;
import io.github.thiagojosetj.gym.domain.session.SessionHistoryEntry;
import io.github.thiagojosetj.gym.domain.session.SessionSummary;
import io.github.thiagojosetj.gym.domain.session.SetValues;
import io.github.thiagojosetj.gym.domain.technique.TechniqueCatalog;
import io.github.thiagojosetj.gym.domain.template.ExercisePlanUpdate;
import io.github.thiagojosetj.gym.domain.template.ExerciseRef;
import io.github.thiagojosetj.gym.domain.template.TemplateDefaults;
import io.github.thiagojosetj.gym.domain.template.TemplateDraft;
import io.github.thiagojosetj.gym.testutil.LiveDataTestUtil;
import io.github.thiagojosetj.gym.testutil.MutableClock;
import io.github.thiagojosetj.gym.testutil.TestContainers;

/**
 * The read path of finished workouts: the list and the detail show what happened, as it happened.
 * Every scenario is built through the real repositories (a template is saved, a workout is started,
 * performed and finished), so a test covers the whole way from a set being confirmed to a line of
 * the history instead of one query in isolation.
 */
@RunWith(AndroidJUnit4.class)
public class HistoryRepositoryTest {

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

    // ------------------------------------------------------------------ the list

    @Test
    public void anEmptyHistoryIsAnEmptyListNotNull() throws Exception {
        List<SessionHistoryEntry> entries = history();

        assertNotNull(entries);
        assertTrue("nothing was performed yet, so nothing is listed: " + entries,
                entries.isEmpty());
    }

    @Test
    public void aWorkoutStillInProgressIsNotInTheHistoryUntilItIsFinished() throws Exception {
        String sessionId = start(createTemplate("Push A", "Supino reto com barra"));
        // Even with a set already confirmed, a workout that is not over is not a result yet.
        confirm(sessionId, firstSetOf(sessionId).id(), lifted(40, 10));

        List<SessionHistoryEntry> whileRunning = history();
        assertTrue("a workout in progress must not be listed: " + whileRunning,
                whileRunning.isEmpty());

        // And the list is not simply broken: the same session shows up once it is finished.
        finish(sessionId);
        assertEquals(sessionId, onlyEntry().sessionId());
    }

    @Test
    public void aDiscardedSessionIsNotInTheHistoryEvenThoughItHasAnEndTime() throws Exception {
        // Discarding stamps ended_at exactly like finishing does, so a query that meant "the
        // session ended" would list a workout the person deliberately threw away. "It ended" is
        // not "it happened": only COMPLETED sessions are results.
        String templateId = createTemplate("Push A", "Supino reto com barra");
        String kept = performAndFinish(templateId, lifted(40, 10));
        nextDay();
        String thrownAway = start(templateId);
        confirm(thrownAway, firstSetOf(thrownAway).id(), lifted(100, 1)); // it even had a set done
        clock.advanceMinutes(15);
        discard(thrownAway);

        // Without this the assertion below could pass for the wrong reason.
        assertEquals(1, count("SELECT COUNT(*) FROM workout_session WHERE id = '" + thrownAway
                + "' AND status = 'DISCARDED' AND ended_at IS NOT NULL"));
        assertEquals(kept, onlyEntry().sessionId());
    }

    @Test
    public void finishedSessionsComeBackNewestFirst() throws Exception {
        String templateId = createTemplate("Push A", "Supino reto com barra");
        String oldest = performAndFinish(templateId, lifted(40, 10));
        nextDay();
        String middle = performAndFinish(templateId, lifted(42.5, 10));
        nextDay();
        String newest = performAndFinish(templateId, lifted(45, 10));

        assertEquals(Arrays.asList(newest, middle, oldest), sessionIdsOf(history()));
    }

    @Test
    public void theEntryCountsConfirmedSetsWarmUpsIncludedAndNeverSkippedOnes() throws Exception {
        String templateId = createTemplate("Push A", "Supino reto com barra", "Rosca martelo");
        String sessionId = start(templateId);
        List<SessionExercise> exercises = loadSession(sessionId).exercises();
        List<LoggedSet> bench = exercises.get(0).sets();
        List<LoggedSet> curl = exercises.get(1).sets();
        confirm(sessionId, bench.get(0).id(), lifted(40, 10));
        confirm(sessionId, bench.get(1).id(), lifted(40, 8));
        // The third bench set is never touched: finishing marks it as skipped, not as performed.
        setTechnique(sessionId, curl.get(0).id(), warmUpTechniqueId());
        confirm(sessionId, curl.get(0).id(), lifted(6, 12)); // the warm-up was performed
        confirm(sessionId, curl.get(1).id(), lifted(12, 10));
        // The third curl set is skipped as well.
        SessionSummary summary = finish(sessionId);

        SessionHistoryEntry entry = onlyEntry();

        assertEquals(2, entry.exercises());
        // Two bench sets, the curl warm-up and one working curl set. The untouched ones are out.
        assertEquals(4, entry.performedSets());
        assertEquals(2, summary.skippedSets()); // the premise: two sets really were skipped
        // The list and the "finished" screen must agree on what was performed.
        assertEquals(summary.performedSets(), entry.performedSets());
    }

    @Test
    public void theEntryTimesComeFromTheStoredTimestampsAndTheEffectiveOneLeavesThePauseOut()
            throws Exception {
        String sessionId = start(createTemplate("Push A", "Supino reto com barra"));
        long startedAt = clock.millis();
        clock.advanceMinutes(10);
        pause(sessionId);
        clock.advanceMinutes(5); // a phone call
        resume(sessionId);
        clock.advanceMinutes(15);
        finish(sessionId);
        // A stale cache (a crash between pause and resume) must not reach the list: the pause is
        // summed from the stored intervals.
        database.getOpenHelper().getWritableDatabase()
                .execSQL("UPDATE workout_session SET total_paused_ms = 999999 WHERE id = '"
                        + sessionId + "'");
        // The list is opened long after the workout: nothing in it may depend on "now".
        nextDay();

        SessionHistoryEntry entry = onlyEntry();

        assertEquals(startedAt, entry.startedAt());
        assertEquals(30 * MINUTE, entry.totalMs());
        assertEquals(25 * MINUTE, entry.effectiveMs());
    }

    // ------------------------------------------------------------------ the detail

    @Test
    public void loadingAnUnknownSessionFailsWithSessionNotFound() {
        assertSessionNotFound(failedDetail("no-such-session"));
    }

    @Test
    public void loadingAWorkoutStillInProgressFailsWithSessionNotFound() throws Exception {
        // History only shows results: a workout in progress has its own screen, and drawing it here
        // would present a half-finished session as if it had happened.
        String sessionId = start(createTemplate("Push A", "Supino reto com barra"));
        confirm(sessionId, firstSetOf(sessionId).id(), lifted(40, 10));

        assertSessionNotFound(failedDetail(sessionId));
    }

    @Test
    public void aDiscardedSessionHasNoDetailEither() throws Exception {
        // Same reasoning as the list: it has an end time, but it was deliberately not a result.
        String sessionId = start(createTemplate("Push A", "Supino reto com barra"));
        confirm(sessionId, firstSetOf(sessionId).id(), lifted(40, 10));
        clock.advanceMinutes(10);
        discard(sessionId);

        assertSessionNotFound(failedDetail(sessionId));
    }

    @Test
    public void theDetailOfAFinishedSessionHasItsVolumeAndOneRollupPerExerciseInOrder()
            throws Exception {
        // Bench first and curl second on purpose: that is not alphabetical order.
        String templateId = createTemplate("Push A", "Supino reto com barra", "Rosca martelo");
        String sessionId = start(templateId);
        List<SessionExercise> exercises = loadSession(sessionId).exercises();
        List<LoggedSet> bench = exercises.get(0).sets();
        List<LoggedSet> curl = exercises.get(1).sets();
        confirm(sessionId, bench.get(0).id(), lifted(40, 10)); // 40 kg x 10 = 400 kg
        confirm(sessionId, bench.get(1).id(), lifted(40, 8)); // 40 kg x 8 = 320 kg
        confirm(sessionId, curl.get(0).id(), lifted(12, 12)); // 2 dumbbells x 12 kg x 12 = 288 kg
        SessionSummary finished = finish(sessionId);

        SessionDetail detail = loadDetail(sessionId);

        assertEquals(sessionId, detail.session().id());
        assertEquals(1_008_000L, detail.summary().volumeGrams());
        // The number shown when the workout was finished is the number the history shows.
        assertEquals(finished.volumeGrams(), detail.summary().volumeGrams());
        List<SessionExerciseSummary> rollups = detail.exercises();
        assertEquals(2, rollups.size());
        SessionExerciseSummary benchRollup = rollups.get(0);
        assertEquals("Supino reto com barra", benchRollup.name());
        assertEquals(0, benchRollup.position());
        assertEquals(2, benchRollup.performedSets());
        assertEquals(1, benchRollup.skippedSets());
        assertEquals(720_000L, benchRollup.totals().loadGrams());
        SessionExerciseSummary curlRollup = rollups.get(1);
        assertEquals("Rosca martelo", curlRollup.name());
        assertEquals(1, curlRollup.position());
        assertEquals(1, curlRollup.performedSets());
        assertEquals(2, curlRollup.skippedSets());
        assertEquals(288_000L, curlRollup.totals().loadGrams());
        // The day is the one it was lived in, and nobody rated it: null, not zero.
        assertEquals("2026-09-27", detail.localDate());
        assertEquals("Z", detail.timeZone());
        assertNull(detail.rating());
    }

    @Test
    public void theDetailIsBuiltFromTheSessionSnapshotNotFromTheTemplateAsItIsNow()
            throws Exception {
        // PRODUCT_SPEC section 2.3: history is immutable. Editing a workout today must not rewrite
        // what happened last week - not its name, not its exercises, not what was planned.
        String templateId = createTemplate("Push A", "Supino reto com barra", "Rosca martelo");
        editTemplate(templateId, draft -> draft.updateExercisePlan(draft.exercises().get(0).id(),
                plan(4, RepRange.between(6, 8), 40, 120), TechniqueCatalog.empty()));
        String sessionId = performAndFinish(templateId, lifted(40, 8));

        // The template moves on: renamed, a new plan for the bench, and the curl dropped.
        editTemplate(templateId, draft -> {
            draft.rename("Push A v2");
            draft.updateExercisePlan(draft.exercises().get(0).id(),
                    plan(5, RepRange.between(10, 12), 100, 45), TechniqueCatalog.empty());
            draft.removeExercise(draft.exercises().get(1).id());
        });
        // Without this the assertions below could pass because the edit never happened.
        TemplateDraft today = loadDraft(templateId);
        assertEquals("Push A v2", today.name());
        assertEquals(1, today.exercises().size());
        assertEquals(5, today.exercises().get(0).setCount());

        SessionDetail detail = loadDetail(sessionId);

        assertEquals("Push A", detail.session().header().name());
        assertEquals("Push A", detail.summary().name());
        assertEquals(2, detail.exercises().size());
        assertEquals("Supino reto com barra", detail.exercises().get(0).name());
        assertEquals("Rosca martelo", detail.exercises().get(1).name());
        List<LoggedSet> sets = detail.session().exercises().get(0).sets();
        assertEquals(4, sets.size());
        assertEquals(RepRange.between(6, 8), sets.get(0).plannedReps());
        assertEquals(kg(40), sets.get(0).plannedWeight());
        assertEquals(120, sets.get(0).plannedRestSeconds());
        // The list is drawn from the same snapshot.
        assertEquals("Push A", onlyEntry().name());
        assertEquals(2, onlyEntry().exercises());
    }

    @Test
    public void renamingAnExerciseInTheLibraryDoesNotRenameItInAPastSession() throws Exception {
        // PRODUCT_SPEC section 2.3 covers "the template or the exercise". Nothing in the app
        // renames a library exercise yet, so the rename is done in SQL.
        String sessionId = performAndFinish(createTemplate("Push A", "Supino reto com barra"),
                lifted(40, 10));

        database.getOpenHelper().getWritableDatabase().execSQL(
                "UPDATE exercise SET name = 'Supino reto (renomeado)'"
                        + " WHERE name = 'Supino reto com barra'");

        // Without this the assertion below could pass because the rename never happened.
        assertEquals(1, count(
                "SELECT COUNT(*) FROM exercise WHERE name = 'Supino reto (renomeado)'"));
        assertEquals("Supino reto com barra", loadDetail(sessionId).exercises().get(0).name());
    }

    // ------------------------------------------------------------------ the comparison

    @Test
    public void theFirstSessionEverPerformedFromATemplateHasNothingToCompareWith()
            throws Exception {
        String sessionId = performAndFinish(createTemplate("Push A", "Supino reto com barra"),
                lifted(40, 10));

        SessionDetail detail = loadDetail(sessionId);

        assertNull(detail.comparison());
        assertFalse(detail.hasComparison());
    }

    @Test
    public void aSecondSessionOfTheSameTemplateIsComparedWithTheFirst() throws Exception {
        String templateId = createTemplate("Push A", "Supino reto com barra");
        long firstStartedAt = clock.millis();
        String first = performAndFinish(templateId, lifted(40, 10));
        nextDay();
        String second = performAndFinish(templateId, lifted(50, 10));

        SessionComparison comparison = loadDetail(second).comparison();

        assertNotNull(comparison);
        assertEquals(first, comparison.previousSessionId());
        assertEquals(firstStartedAt, comparison.previousStartedAt());
        MetricChange volume = comparison.volumeGrams();
        assertEquals(500_000L, volume.current()); // 50 kg x 10
        assertEquals(400_000L, volume.previous()); // 40 kg x 10
        assertEquals(MetricChange.Direction.UP, volume.direction());
        assertTrue(volume.hasPercent());
        assertEquals(25.0, volume.percent(), 0.001);
        // The comparison only looks back: the first session is not compared with the one after it.
        assertNull(loadDetail(first).comparison());
    }

    @Test
    public void aMoreRecentSessionOfAnotherTemplateIsNotTheComparison() throws Exception {
        // Both workouts contain the bench press, so the full-body session is a tempting wrong
        // answer: it is the most recent completed session with that exercise. The comparison of
        // the summary is with the same WORKOUT, not with the same exercise (PRODUCT_SPEC
        // section 11).
        String push = createTemplate("Push A", "Supino reto com barra");
        String fullBody = createTemplate("Full body", "Supino reto com barra",
                "Agachamento livre com barra");
        String firstPush = performAndFinish(push, lifted(40, 10));
        nextDay();
        performAndFinish(fullBody, lifted(100, 10));
        nextDay();
        String secondPush = performAndFinish(push, lifted(50, 10));

        SessionDetail detail = loadDetail(secondPush);

        SessionComparison comparison = detail.comparison();
        assertNotNull(comparison);
        assertEquals(firstPush, comparison.previousSessionId());
        assertEquals(400_000L, comparison.volumeGrams().previous());
        // The "anterior" of each set is the other question - the last session with that exercise,
        // from any workout - so it does come from the full-body session. The two differ on purpose.
        LoggedSet firstBenchSet = detail.session().exercises().get(0).sets().get(0);
        assertNotNull(firstBenchSet.previous());
        assertEquals(kg(100), firstBenchSet.previous().weight());
    }

    @Test
    public void aDiscardedSessionOfTheSameTemplateIsNotTheComparisonEither() throws Exception {
        // Same rule as the list: a discarded workout has an end time, but it did not happen, so it
        // cannot be the baseline the next one is measured against.
        String templateId = createTemplate("Push A", "Supino reto com barra");
        String kept = performAndFinish(templateId, lifted(40, 10));
        nextDay();
        String thrownAway = start(templateId);
        confirm(thrownAway, firstSetOf(thrownAway).id(), lifted(200, 10));
        discard(thrownAway);
        nextDay();
        String latest = performAndFinish(templateId, lifted(50, 10));

        SessionComparison comparison = loadDetail(latest).comparison();

        assertNotNull(comparison);
        assertEquals(kept, comparison.previousSessionId());
        assertEquals(400_000L, comparison.volumeGrams().previous());
    }

    @Test
    public void aPreviousSessionWithNoLoadVolumeStillGivesADirectionButNoPercent()
            throws Exception {
        String templateId = createTemplate("Push A", "Supino reto com barra");
        // Repetitions were logged but the load was not: that day has no load volume at all.
        performAndFinish(templateId, new SetValues(null, 10, null, null, null));
        nextDay();
        String second = performAndFinish(templateId, lifted(40, 10));

        SessionComparison comparison = loadDetail(second).comparison();

        assertNotNull(comparison);
        MetricChange volume = comparison.volumeGrams();
        assertEquals(0L, volume.previous());
        assertEquals(400_000L, volume.current());
        assertEquals(MetricChange.Direction.UP, volume.direction());
        // A percentage of growth from zero would be a number invented to fill the screen.
        assertFalse(volume.hasPercent());
        // Only the metric that starts from zero loses it: one set against one set has a percentage.
        assertTrue(comparison.performedSets().hasPercent());
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
        return saveTemplate(draft);
    }

    private String saveTemplate(TemplateDraft draft) {
        AtomicReference<String> saved = new AtomicReference<>();
        app.templates.save(draft, saved::set, this::fail);
        assertNotNull("template should have been saved", saved.get());
        return saved.get();
    }

    private TemplateDraft loadDraft(String templateId) {
        AtomicReference<TemplateDraft> draft = new AtomicReference<>();
        app.templates.loadDraft(templateId, draft::set, this::fail);
        assertNotNull("template should have loaded", draft.get());
        return draft.get();
    }

    /** Reopens the template, changes it the way the editor would, and saves it again. */
    private void editTemplate(String templateId, Consumer<TemplateDraft> edit) {
        TemplateDraft draft = loadDraft(templateId);
        edit.accept(draft);
        saveTemplate(draft);
    }

    private static ExercisePlanUpdate plan(int sets, RepRange reps, double kilos, int restSeconds) {
        return ExercisePlanUpdate.uniform(sets, reps, kg(kilos), null, restSeconds, null,
                SideMode.COMBINED);
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

    private LoggedSet firstSetOf(String sessionId) {
        return loadSession(sessionId).exercises().get(0).sets().get(0);
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

    private void discard(String sessionId) {
        AtomicReference<Boolean> done = new AtomicReference<>(false);
        app.activeSessions.discard(sessionId, () -> done.set(true), this::fail);
        assertTrue("discard should have completed", done.get());
    }

    private void setTechnique(String sessionId, String setId, String techniqueId) {
        AtomicReference<Boolean> done = new AtomicReference<>(false);
        app.activeSessions.setSetTechnique(sessionId, setId, techniqueId, () -> done.set(true),
                this::fail);
        assertTrue(done.get());
    }

    private SessionSummary finish(String sessionId) {
        AtomicReference<SessionSummary> summary = new AtomicReference<>();
        app.activeSessions.finish(sessionId, summary::set, this::fail);
        assertNotNull("finish should have produced a summary", summary.get());
        return summary.get();
    }

    /**
     * A whole workout in a few lines: start it, perform the first set of the first exercise with
     * these values, finish it. The clock does not move, so it lasts zero milliseconds.
     */
    private String performAndFinish(String templateId, SetValues firstSet) {
        String sessionId = start(templateId);
        confirm(sessionId, firstSetOf(sessionId).id(), firstSet);
        finish(sessionId);
        return sessionId;
    }

    /** A later workout has to start strictly after the earlier one to be "previous" to it. */
    private void nextDay() {
        clock.advance(Duration.ofDays(1));
    }

    private List<SessionHistoryEntry> history() throws Exception {
        return LiveDataTestUtil.getOrAwaitValue(app.history.observeHistory());
    }

    /** The history, when exactly one session is expected to be in it. */
    private SessionHistoryEntry onlyEntry() throws Exception {
        List<SessionHistoryEntry> entries = history();
        assertEquals(1, entries.size());
        return entries.get(0);
    }

    private SessionDetail loadDetail(String sessionId) {
        AtomicReference<SessionDetail> detail = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        app.history.loadDetail(sessionId, detail::set, error::set);
        assertNull("detail should have loaded, but failed with " + error.get(), error.get());
        assertNotNull("detail should have loaded", detail.get());
        return detail.get();
    }

    /** Asks for a detail that must not exist and hands back the error it was refused with. */
    private Throwable failedDetail(String sessionId) {
        AtomicReference<SessionDetail> detail = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        app.history.loadDetail(sessionId, detail::set, error::set);
        assertNull("no detail should be built for " + sessionId, detail.get());
        assertNotNull("loadDetail should have reported an error", error.get());
        return error.get();
    }

    private static void assertSessionNotFound(Throwable error) {
        assertTrue("expected SessionNotFoundException but got " + error,
                error instanceof ActiveSessionRepository.SessionNotFoundException);
    }

    private static List<String> sessionIdsOf(List<SessionHistoryEntry> entries) {
        List<String> ids = new ArrayList<>(entries.size());
        for (SessionHistoryEntry entry : entries) {
            ids.add(entry.sessionId());
        }
        return ids;
    }

    private String warmUpTechniqueId() {
        try (Cursor cursor = database.getOpenHelper().getReadableDatabase()
                .query("SELECT id FROM training_technique WHERE code = 'AQ'")) {
            assertTrue(cursor.moveToFirst());
            return cursor.getString(0);
        }
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

    private static SetValues lifted(double kilos, int reps) {
        return new SetValues(kg(kilos), reps, null, null, null);
    }

    private static Weight kg(double value) {
        return Weight.of(value, WeightUnit.KILOGRAM);
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

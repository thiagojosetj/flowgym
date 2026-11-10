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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import io.github.thiagojosetj.gym.AppContainer;
import io.github.thiagojosetj.gym.data.local.AppDatabase;
import io.github.thiagojosetj.gym.data.local.entity.SetLogEntity;
import io.github.thiagojosetj.gym.domain.library.ExerciseFilter;
import io.github.thiagojosetj.gym.domain.library.ExerciseSummary;
import io.github.thiagojosetj.gym.domain.model.RepRange;
import io.github.thiagojosetj.gym.domain.model.Weight;
import io.github.thiagojosetj.gym.domain.model.WeightUnit;
import io.github.thiagojosetj.gym.domain.session.ActiveSession;
import io.github.thiagojosetj.gym.domain.session.LoggedSet;
import io.github.thiagojosetj.gym.domain.session.SessionDetail;
import io.github.thiagojosetj.gym.domain.session.SessionExercise;
import io.github.thiagojosetj.gym.domain.session.SessionHistoryEntry;
import io.github.thiagojosetj.gym.domain.session.SessionSummary;
import io.github.thiagojosetj.gym.domain.session.SessionVolume;
import io.github.thiagojosetj.gym.domain.session.SetStatus;
import io.github.thiagojosetj.gym.domain.session.SetValues;
import io.github.thiagojosetj.gym.domain.template.ExerciseRef;
import io.github.thiagojosetj.gym.domain.template.TemplateDefaults;
import io.github.thiagojosetj.gym.domain.template.TemplateDraft;
import io.github.thiagojosetj.gym.testutil.LiveDataTestUtil;
import io.github.thiagojosetj.gym.testutil.MutableClock;
import io.github.thiagojosetj.gym.testutil.TestContainers;

/**
 * Drop-set and rest-pause segments: how they are written, and the rule that they add to the volume
 * and to the repetitions but never to the number of sets (PRODUCT_SPEC section 9.1, ADR-0037).
 *
 * <p>Several assertions read the table itself instead of the mapped session. The mapper nests each
 * segment under its set and quietly drops a row it cannot place, so a segment nested two levels
 * deep, or one left behind by a delete that did not cascade, would be invisible from the screen's
 * side of the data.
 */
@RunWith(AndroidJUnit4.class)
public class SegmentLoggingTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

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

    // ------------------------------------------------------------------ writing a segment

    @Test
    public void addingASegmentCreatesAChildRowAndDoesNotAddASet() throws Exception {
        String sessionId = start(createTemplate("Push A", "Supino reto com barra"));
        SessionExercise exercise = loadSession(sessionId).exercises().get(0);
        LoggedSet set = exercise.sets().get(0);
        // The set is what carries the technique: this is a Drop-set, so the drop is added to it.
        setTechnique(sessionId, set.id(), techniqueId("D"));
        int setsBefore = database.sessionDao().countSets(exercise.id());

        addSegment(sessionId, set.id());

        LoggedSet withDrop = setAt(sessionId, 0);
        assertEquals("D", withDrop.techniqueCode());
        assertEquals(1, withDrop.segments().size());
        SetLogEntity row = database.sessionDao().findSet(withDrop.segments().get(0).id());
        assertEquals(set.id(), row.parentSetId);
        assertEquals(exercise.id(), row.sessionExerciseId);
        assertEquals(0, row.position);
        assertEquals(SetStatus.PENDING, row.status);
        // Nothing planned, nothing performed, and no technique of its own: the set's technique
        // can change later, and a copy here would go on saying "Drop-set" after it did.
        assertNull(row.techniqueId);
        assertNull(row.plannedRepsMin);
        assertNull(row.plannedRepsMax);
        assertNull(row.plannedWeightGrams);
        assertNull(row.plannedDurationSeconds);
        assertEquals(0, row.plannedRestSeconds);
        assertNull(row.weightGrams);
        assertNull(row.reps);
        // ADR-0037: a drop is not a set. The count, the last position and the screen agree.
        assertEquals(3, setsBefore);
        assertEquals(setsBefore, database.sessionDao().countSets(exercise.id()));
        assertEquals(2, database.sessionDao().maxSetPosition(exercise.id()));
        assertEquals(setsBefore, loadSession(sessionId).exercises().get(0).sets().size());
    }

    @Test
    public void segmentsComeBackNestedInsideTheirSetInPositionOrder() throws Exception {
        String sessionId = start(createTemplate("Push A", "Supino reto com barra"));
        List<LoggedSet> sets = loadSession(sessionId).exercises().get(0).sets();
        // Three drops under the first set, typed with different loads so their order shows...
        addSegment(sessionId, sets.get(0).id());
        addSegment(sessionId, sets.get(0).id());
        addSegment(sessionId, sets.get(0).id());
        // ...and one under the second, so a drop of another set cannot end up in the first.
        addSegment(sessionId, sets.get(1).id());
        List<LoggedSet> drops = setAt(sessionId, 0).segments();
        type(drops.get(0).id(), 30, 8);
        type(drops.get(1).id(), 20, 8);
        type(drops.get(2).id(), 10, 8);

        // The one-shot read and the observed one go through the same mapper, but they are two
        // separate paths into it, and the screen uses the second.
        for (ActiveSession view : Arrays.asList(loadSession(sessionId), observed(sessionId))) {
            List<LoggedSet> top = view.exercises().get(0).sets();
            // The top level holds the three sets and nothing else.
            assertEquals(idsOf(sets), idsOf(top));
            assertEquals(3, view.totalSets());
            assertEquals(Arrays.asList(0, 1, 2), positionsOf(top));
            assertEquals(Arrays.asList(1, 2, 3), workingNumbersOf(top));
            // Each segment sits under its own set, in position order.
            assertTrue(top.get(0).hasSegments());
            assertEquals(Arrays.asList(0, 1, 2), positionsOf(top.get(0).segments()));
            assertEquals(Arrays.asList(kg(30), kg(20), kg(10)), weightsOf(top.get(0).segments()));
            assertEquals(1, top.get(1).segments().size());
            assertTrue(top.get(2).segments().isEmpty());
            // A drop is not numbered: it is not a working set.
            assertNull(top.get(0).segments().get(0).workingNumber());
        }
    }

    @Test
    public void aSegmentCanOnlyHangFromASet() throws Exception {
        String sessionId = start(createTemplate("Push A", "Supino reto com barra"));
        addSegment(sessionId, setId(sessionId, 0));
        LoggedSet segment = setAt(sessionId, 0).segments().get(0);
        long rows = count("SELECT COUNT(*) FROM set_log");

        addSegment(sessionId, segment.id()); // under a segment: one level only
        addSegment(sessionId, "no-such-set"); // under nothing

        // Read from the table: a row nested two levels deep would not show up on the screen side.
        assertEquals(rows, count("SELECT COUNT(*) FROM set_log"));
        assertEquals(0, count("SELECT COUNT(*) FROM set_log WHERE parent_set_id = '"
                + segment.id() + "'"));
        LoggedSet after = setAt(sessionId, 0);
        assertEquals(1, after.segments().size());
        assertTrue(after.segments().get(0).segments().isEmpty());
    }

    @Test
    public void removingASegmentKeepsTheSetAndItsOtherSegmentsAndClosesTheGap() throws Exception {
        String sessionId = start(createTemplate("Push A", "Supino reto com barra"));
        String first = setId(sessionId, 0);
        String second = setId(sessionId, 1);
        confirm(sessionId, first, values(40, 10));
        // Three drops under each of two sets, told apart by their load.
        for (int i = 0; i < 3; i++) {
            addSegment(sessionId, first);
            addSegment(sessionId, second);
        }
        List<LoggedSet> firstDrops = setAt(sessionId, 0).segments();
        List<LoggedSet> secondDrops = setAt(sessionId, 1).segments();
        type(firstDrops.get(0).id(), 30, 8);
        type(firstDrops.get(1).id(), 20, 8);
        type(firstDrops.get(2).id(), 10, 8);
        type(secondDrops.get(0).id(), 35, 8);
        type(secondDrops.get(1).id(), 25, 8);
        type(secondDrops.get(2).id(), 15, 8);

        removeSegment(sessionId, firstDrops.get(1).id()); // the middle one, 20 kg

        List<LoggedSet> sets = loadSession(sessionId).exercises().get(0).sets();
        // The set itself is untouched: same id, still confirmed, same values.
        assertEquals(first, sets.get(0).id());
        assertEquals(SetStatus.COMPLETED, sets.get(0).status());
        assertEquals(kg(40), sets.get(0).values().weight());
        // Its other drops closed up: 30 stays first, and 10 moves from position 2 to 1.
        assertEquals(Arrays.asList(0, 1), positionsOf(sets.get(0).segments()));
        assertEquals(Arrays.asList(kg(30), kg(10)), weightsOf(sets.get(0).segments()));
        // The drops of another set are not siblings of the removed one: they did not move.
        assertEquals(Arrays.asList(0, 1, 2), positionsOf(sets.get(1).segments()));
        assertEquals(Arrays.asList(kg(35), kg(25), kg(15)), weightsOf(sets.get(1).segments()));
        // The row is really gone, and the sets were not touched.
        assertNull(database.sessionDao().findSet(firstDrops.get(1).id()));
        assertEquals(Arrays.asList(0, 1, 2), positionsOf(sets));

        // The first drop of a set is the case that moves every sibling.
        removeSegment(sessionId, secondDrops.get(0).id());

        List<LoggedSet> afterSecond = setAt(sessionId, 1).segments();
        assertEquals(Arrays.asList(0, 1), positionsOf(afterSecond));
        assertEquals(Arrays.asList(kg(25), kg(15)), weightsOf(afterSecond));
    }

    @Test
    public void removingASetTakesItsSegmentsWithIt() throws Exception {
        String sessionId = start(createTemplate("Push A", "Supino reto com barra"));
        SessionExercise exercise = loadSession(sessionId).exercises().get(0);
        String doomed = exercise.sets().get(0).id();
        String keep = exercise.sets().get(1).id();
        addSegment(sessionId, doomed);
        addSegment(sessionId, doomed);
        addSegment(sessionId, keep);
        addSegment(sessionId, keep);
        List<LoggedSet> doomedSegments = setAt(sessionId, 0).segments();
        assertEquals(2, doomedSegments.size());
        assertEquals(7, count("SELECT COUNT(*) FROM set_log")); // 3 sets and 4 segments

        removeSet(sessionId, exercise.id(), doomed);

        // Gone from the table, not just hidden: the mapper would silently drop an orphan, so the
        // screen alone could never tell a cascade from a leak.
        assertEquals(0, count("SELECT COUNT(*) FROM set_log WHERE parent_set_id = '"
                + doomed + "'"));
        assertNull(database.sessionDao().findSet(doomed));
        for (LoggedSet segment : doomedSegments) {
            assertNull(database.sessionDao().findSet(segment.id()));
        }
        assertEquals(4, count("SELECT COUNT(*) FROM set_log")); // 2 sets and 2 segments
        // The sets closed up, and the survivor's drops did not move with them: its second drop is
        // at position 1, above the removed set's own position 0, and that must not matter.
        List<LoggedSet> after = loadSession(sessionId).exercises().get(0).sets();
        assertEquals(Arrays.asList(0, 1), positionsOf(after));
        assertEquals(keep, after.get(0).id());
        assertEquals(Arrays.asList(0, 1), positionsOf(after.get(0).segments()));
    }

    @Test
    public void removingASegmentNeverDeletesASet() throws Exception {
        String sessionId = start(createTemplate("Push A", "Supino reto com barra"));
        String set = setId(sessionId, 0);
        addSegment(sessionId, set);
        long rows = count("SELECT COUNT(*) FROM set_log");

        removeSegment(sessionId, set); // a set: removeSet is for those, and deleting it cascades
        removeSegment(sessionId, "no-such-row");

        assertEquals(rows, count("SELECT COUNT(*) FROM set_log"));
        LoggedSet after = setAt(sessionId, 0);
        assertEquals(set, after.id());
        assertEquals(1, after.segments().size());
        assertEquals(3, loadSession(sessionId).exercises().get(0).sets().size());
    }

    @Test
    public void removingASetNeverDeletesASegmentNorShiftsTheSets() throws Exception {
        String sessionId = start(createTemplate("Push A", "Supino reto com barra"));
        SessionExercise exercise = loadSession(sessionId).exercises().get(0);
        addSegment(sessionId, exercise.sets().get(0).id());
        String segment = setAt(sessionId, 0).segments().get(0).id();

        removeSet(sessionId, exercise.id(), segment);

        // A segment's position is a position among its siblings. Using it to close a gap among the
        // SETS would have moved sets 1 and 2 down onto positions 0 and 1.
        List<LoggedSet> sets = loadSession(sessionId).exercises().get(0).sets();
        assertEquals(Arrays.asList(0, 1, 2), positionsOf(sets));
        assertEquals(1, sets.get(0).segments().size());
        assertEquals(segment, sets.get(0).segments().get(0).id());
    }

    @Test
    public void aFinishedSessionCannotGainOrLoseSegments() throws Exception {
        // History is immutable, and a segment is history as much as a set is.
        String sessionId = start(createTemplate("Push A", "Supino reto com barra"));
        performDropSet(sessionId, 0, values(40, 10), values(30, 8));
        finish(sessionId);
        String segment = setAt(sessionId, 0).segments().get(0).id();
        long rows = count("SELECT COUNT(*) FROM set_log");

        // One at a time: an add and a remove that were both accepted would cancel out in a count.
        addSegment(sessionId, setId(sessionId, 0));
        assertEquals("nothing was added", rows, count("SELECT COUNT(*) FROM set_log"));

        removeSegment(sessionId, segment);
        assertEquals("nothing was removed", rows, count("SELECT COUNT(*) FROM set_log"));
        assertEquals(segment, setAt(sessionId, 0).segments().get(0).id());
    }

    @Test
    public void removingTheSegmentThatOwnsTheRestClearsTheRest() throws Exception {
        // rest_set_log_id has no foreign key, so a delete has to clear it by hand. A segment is
        // created without a rest and cannot own one today; give it one directly so the invariant
        // is proven now and not on the day rest-pause needs it.
        String sessionId = start(createTemplate("Push A", "Supino reto com barra"));
        addSegment(sessionId, setId(sessionId, 0));
        String segment = setAt(sessionId, 0).segments().get(0).id();
        database.getOpenHelper().getWritableDatabase().execSQL(
                "UPDATE set_log SET planned_rest_seconds = 15 WHERE id = '" + segment + "'");
        confirm(sessionId, segment, values(30, 8));
        assertEquals(segment, loadSession(sessionId).header().restSetLogId());

        removeSegment(sessionId, segment);

        assertNull(loadSession(sessionId).header().restSetLogId());
    }

    // ------------------------------------------------------------------ the numbers

    @Test
    public void aConfirmedDropSetSumsEveryStepIntoTheVolume() throws Exception {
        String sessionId = start(createTemplate("Push A", "Supino reto com barra"));

        performDropSet(sessionId, 0, values(40, 10), values(30, 8), values(20, 6));

        SessionVolume.Totals totals = loadSession(sessionId).totals();
        // 40 x 10 + 30 x 8 + 20 x 6 = 400 + 240 + 120 kg, in grams.
        assertEquals(760_000L, totals.loadGrams());
        assertEquals(1, totals.countedSets());
        assertEquals(0, totals.excludedSets());
        assertEquals(24, totals.totalReps());
    }

    @Test
    public void aDropSetIsOneSetInTheFinishSummaryNotThree() throws Exception {
        String sessionId = start(createTemplate("Push A", "Supino reto com barra"));
        performDropSet(sessionId, 0, values(40, 10), values(30, 8), values(20, 6));

        SessionSummary summary = finish(sessionId);

        // Three steps were performed and one set was: "series feitas" must not be inflated.
        assertEquals(1, summary.performedSets());
        assertEquals(2, summary.skippedSets()); // the two sets that were never touched
        assertEquals(24, summary.totalReps());
        assertEquals(760_000L, summary.volumeGrams());
        assertEquals(0, summary.setsOutsideVolume());
        assertEquals(3, loadSession(sessionId).totalSets());
    }

    @Test
    public void aDropThatWasNeverConfirmedAddsNothingToTheVolume() throws Exception {
        String sessionId = start(createTemplate("Push A", "Supino reto com barra"));
        String set = setId(sessionId, 0);
        confirm(sessionId, set, values(40, 10));
        addSegment(sessionId, set);
        addSegment(sessionId, set);
        List<LoggedSet> drops = setAt(sessionId, 0).segments();
        // The first drop is typed but never confirmed; the second was never touched.
        type(drops.get(0).id(), 30, 8);

        LoggedSet typed = setAt(sessionId, 0).segments().get(0);
        assertEquals(SetStatus.PENDING, typed.status());
        assertEquals(kg(30), typed.values().weight()); // the values ARE stored...
        SessionVolume.Totals pending = loadSession(sessionId).totals();
        assertEquals(400_000L, pending.loadGrams()); // ...and still not a result
        assertEquals(1, pending.countedSets());
        assertEquals(10, pending.totalReps());

        // Confirming is what makes a drop count.
        confirm(sessionId, drops.get(0).id(), values(30, 8));
        SessionVolume.Totals confirmed = loadSession(sessionId).totals();
        assertEquals(640_000L, confirmed.loadGrams());
        assertEquals(1, confirmed.countedSets());
        assertEquals(18, confirmed.totalReps());
    }

    @Test
    public void theHistoryCountsADropSetAsOneSetAndKeepsItsVolume() throws Exception {
        String sessionId = start(createTemplate("Push A", "Supino reto com barra"));
        performDropSet(sessionId, 0, values(40, 10), values(30, 8), values(20, 6));
        finish(sessionId);

        // The list counts in SQL, so it is the query's parent_set_id filter that is on trial here.
        List<SessionHistoryEntry> list =
                LiveDataTestUtil.getOrAwaitValue(app.history.observeHistory());
        assertEquals(1, list.size());
        assertEquals(1, list.get(0).performedSets());

        AtomicReference<SessionDetail> detail = new AtomicReference<>();
        app.history.loadDetail(sessionId, detail::set, this::fail);
        assertNotNull(detail.get());
        assertEquals(1, detail.get().summary().performedSets());
        assertEquals(1, detail.get().exercises().get(0).performedSets());
        assertEquals(760_000L, detail.get().summary().volumeGrams());
        assertEquals(760_000L, detail.get().exercises().get(0).totals().loadGrams());
    }

    @Test
    public void segmentsDoNotShiftTheNumberingNorThePairingWithThePreviousSession()
            throws Exception {
        // ADR-0033: "anterior" pairs the Nth working SET with the Nth working SET. A drop that
        // reached either side would move every set after it, and the column would show the wrong
        // set's numbers without any error.
        String templateId = createTemplate("Push A", "Supino reto com barra");

        // Last time the first set was a drop-set (40x10, 30x8, 20x6), then 40x9 and 40x8.
        String last = start(templateId);
        performDropSet(last, 0, values(40, 10), values(30, 8), values(20, 6));
        confirm(last, setId(last, 1), values(40, 9));
        confirm(last, setId(last, 2), values(40, 8));
        finish(last);

        clock.advanceMinutes(60);
        String today = start(templateId);
        // Today the first two sets carry drops as well, so segments sit on both sides.
        addSegment(today, setId(today, 0));
        addSegment(today, setId(today, 0));
        addSegment(today, setId(today, 1));

        List<LoggedSet> sets = loadSession(today).exercises().get(0).sets();

        // Numbering counts sets: 1, 2, 3. It does not run 1..6 with the drops in between.
        assertEquals(3, sets.size());
        assertEquals(Arrays.asList(1, 2, 3), workingNumbersOf(sets));
        // Set 1 pairs with set 1, set 2 with set 2, set 3 with set 3. Were a drop counted on
        // either side, set 2 would be compared with a drop (8 or 6 repetitions) instead of 9.
        assertEquals(kg(40), sets.get(0).previous().weight());
        assertEquals(Integer.valueOf(10), sets.get(0).previous().reps());
        assertEquals(kg(40), sets.get(1).previous().weight());
        assertEquals(Integer.valueOf(9), sets.get(1).previous().reps());
        assertEquals(kg(40), sets.get(2).previous().weight());
        assertEquals(Integer.valueOf(8), sets.get(2).previous().reps());
        // A drop has nothing to be compared with and no number of its own.
        assertNull(sets.get(0).segments().get(0).previous());
        assertNull(sets.get(0).segments().get(0).workingNumber());
    }

    // ------------------------------------------------------------------ existing writes

    @Test
    public void aSetAddedAfterADropSetCopiesThePlanOfTheSetNotOfItsLastDrop() throws Exception {
        String sessionId = start(createTemplate("Push A", "Supino reto com barra"));
        SessionExercise exercise = loadSession(sessionId).exercises().get(0);
        // The LAST set carries the drops, so a drop is the last row the exercise has. Four of
        // them, so the highest segment position (3) is above the highest set position (2).
        performDropSet(sessionId, 2, values(40, 10), values(35, 9), values(30, 8), values(25, 7),
                values(20, 6));

        addSet(sessionId, exercise.id());

        List<LoggedSet> sets = loadSession(sessionId).exercises().get(0).sets();
        assertEquals(Arrays.asList(0, 1, 2, 3), positionsOf(sets));
        LoggedSet added = sets.get(3);
        // A drop has no plan, so copying from it would leave the new set with no reps and no rest.
        assertEquals(RepRange.exactly(12), added.plannedReps());
        assertEquals(90, added.plannedRestSeconds());
        assertEquals(SetStatus.PENDING, added.status());
        assertTrue(added.segments().isEmpty());
    }

    @Test
    public void theWritesKeyedBySetIdAlsoReachASegment() throws Exception {
        // Typing, confirming and undoing go through updateTypedValues, completeSet and
        // uncompleteSet, which look a row up by id and never by parent_set_id. Proven, not assumed
        // (deleteSet is proven by the removeSegment tests above).
        String sessionId = start(createTemplate("Push A", "Supino reto com barra"));
        addSegment(sessionId, setId(sessionId, 0));
        String segment = setAt(sessionId, 0).segments().get(0).id();

        type(segment, 30, 8);
        LoggedSet typed = setAt(sessionId, 0).segments().get(0);
        assertEquals(kg(30), typed.values().weight());
        assertEquals(Integer.valueOf(8), typed.values().reps());
        assertEquals(SetStatus.PENDING, typed.status());

        confirm(sessionId, segment, values(25, 7));
        LoggedSet done = setAt(sessionId, 0).segments().get(0);
        assertEquals(SetStatus.COMPLETED, done.status());
        assertEquals(kg(25), done.values().weight());
        assertNotNull(done.completedAt());

        // A late draft write cannot overwrite a confirmed drop: same guard as for a set.
        type(segment, 1, 1);
        assertEquals(kg(25), setAt(sessionId, 0).segments().get(0).values().weight());

        unconfirm(sessionId, segment);
        LoggedSet undone = setAt(sessionId, 0).segments().get(0);
        assertEquals(SetStatus.PENDING, undone.status());
        assertEquals(kg(25), undone.values().weight()); // what was typed stays
        assertNull(undone.completedAt());
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

    private ActiveSession observed(String sessionId) throws Exception {
        ActiveSession session =
                LiveDataTestUtil.getOrAwaitValue(app.activeSessions.observeSession(sessionId));
        assertNotNull(session);
        return session;
    }


    @Test
    public void finishingResolvesADropThatWasFilledInAndNeverConfirmed() throws Exception {
        // Section 8's promise applied to a drop. Before this, finishing touched only the parent
        // sets, so a drop stayed PENDING inside a COMPLETED session: its repetitions counted for
        // nothing and the dialog never mentioned it (found in review, 03/10/2026).
        String sessionId = start(createTemplate("Push A", "Supino reto com barra"));
        String setId = setId(sessionId, 0);
        setTechnique(sessionId, setId, techniqueId("D"));
        addSegment(sessionId, setId);
        String dropId = setAt(sessionId, 0).segments().get(0).id();
        confirm(sessionId, setId, values(40, 10));
        type(dropId, 30, 8); // typed, never confirmed

        SessionSummary summary = finish(sessionId);

        assertEquals(SetStatus.COMPLETED, database.sessionDao().findSet(dropId).status);
        // And the work it holds now reaches the numbers: 40x10 + 30x8 = 640 kg.
        assertEquals(640_000L, summary.volumeGrams());
        assertEquals("o drop-set continua valendo UMA serie", 1, summary.performedSets());
        assertEquals(18, summary.totalReps());
    }

    @Test
    public void finishingSkipsADropThatWasNeverFilledIn() throws Exception {
        String sessionId = start(createTemplate("Push A", "Supino reto com barra"));
        String setId = setId(sessionId, 0);
        setTechnique(sessionId, setId, techniqueId("D"));
        addSegment(sessionId, setId);
        String dropId = setAt(sessionId, 0).segments().get(0).id();
        confirm(sessionId, setId, values(40, 10));

        SessionSummary summary = finish(sessionId);

        assertEquals(SetStatus.SKIPPED, database.sessionDao().findSet(dropId).status);
        assertEquals(400_000L, summary.volumeGrams());
        assertEquals(1, summary.performedSets());
    }


    @Test
    public void afterTheLastDropThereIsStillARest() throws Exception {
        // The flow this describes is the normal one: do the set, tap confirm, do the drop, tap
        // confirm. A drop-set is ONE set (ADR-0037), so the rest belongs after the whole thing -
        // and a drop-set is the most tiring set in the workout to be left without one.
        String sessionId = start(createTemplate("Push A", "Supino reto com barra"));
        String setId = setId(sessionId, 0);
        setTechnique(sessionId, setId, techniqueId("D"));
        addSegment(sessionId, setId);
        String dropId = setAt(sessionId, 0).segments().get(0).id();

        confirm(sessionId, setId, values(40, 10));
        assertEquals("a serie inicia o descanso", setId,
                loadSession(sessionId).header().restSetLogId());

        confirm(sessionId, dropId, values(30, 8));

        assertNotNull("confirmar o drop nao pode deixar o treino sem descanso nenhum",
                loadSession(sessionId).header().restSetLogId());
    }


    @Test
    public void undoingASetAlsoUndoesItsDrops() throws Exception {
        // A drop is part of its set. Leaving the drops marked as performed under a set that is no
        // longer performed makes the database say the reps happened while every total ignores
        // them - and if the set then ends as skipped, that work is gone without a word.
        String sessionId = start(createTemplate("Push A", "Supino reto com barra"));
        String setId = setId(sessionId, 0);
        setTechnique(sessionId, setId, techniqueId("D"));
        addSegment(sessionId, setId);
        String dropId = setAt(sessionId, 0).segments().get(0).id();
        confirm(sessionId, setId, values(40, 10));
        confirm(sessionId, dropId, values(30, 8));
        assertEquals(SetStatus.COMPLETED, database.sessionDao().findSet(dropId).status);

        unconfirm(sessionId, setId);

        assertEquals("a etapa nao pode continuar feita sob uma serie desfeita",
                SetStatus.PENDING, database.sessionDao().findSet(dropId).status);
        // The typed values stay: undoing is not erasing (ADR-0031).
        assertNotNull(database.sessionDao().findSet(dropId).weightGrams);
    }


    @Test
    public void undoingASetAlsoClearsARestThatOneOfItsDropsOwns() throws Exception {
        // Since the rest after a drop-set starts on the last drop, the rest is owned by a drop and
        // not by the set. Checking only the set's own id would leave a countdown running for work
        // that was just undone.
        String sessionId = start(createTemplate("Push A", "Supino reto com barra"));
        String setId = setId(sessionId, 0);
        setTechnique(sessionId, setId, techniqueId("D"));
        addSegment(sessionId, setId);
        String dropId = setAt(sessionId, 0).segments().get(0).id();
        confirm(sessionId, setId, values(40, 10));
        confirm(sessionId, dropId, values(30, 8));
        assertEquals(dropId, loadSession(sessionId).header().restSetLogId());

        unconfirm(sessionId, setId);

        assertNull("o descanso ficou correndo por um trabalho desfeito",
                loadSession(sessionId).header().restSetLogId());
    }

    private void confirm(String sessionId, String setId, SetValues values) {
        AtomicReference<Boolean> done = new AtomicReference<>(false);
        app.activeSessions.confirmSet(sessionId, setId, values, () -> done.set(true), this::fail);
        assertTrue("confirm should have completed", done.get());
    }

    private void unconfirm(String sessionId, String setId) {
        AtomicReference<Boolean> done = new AtomicReference<>(false);
        app.activeSessions.unconfirmSet(sessionId, setId, () -> done.set(true), this::fail);
        assertTrue(done.get());
    }

    private void addSet(String sessionId, String sessionExerciseId) {
        AtomicReference<Boolean> done = new AtomicReference<>(false);
        app.activeSessions.addSet(sessionId, sessionExerciseId, () -> done.set(true), this::fail);
        assertTrue(done.get());
    }

    private void removeSet(String sessionId, String sessionExerciseId, String setId) {
        AtomicReference<Boolean> done = new AtomicReference<>(false);
        app.activeSessions.removeSet(sessionId, sessionExerciseId, setId, () -> done.set(true),
                this::fail);
        assertTrue(done.get());
    }

    private void addSegment(String sessionId, String setId) {
        AtomicReference<Boolean> done = new AtomicReference<>(false);
        app.activeSessions.addSegment(sessionId, setId, () -> done.set(true), this::fail);
        assertTrue("a refusal still reports done", done.get());
    }

    private void removeSegment(String sessionId, String setId) {
        AtomicReference<Boolean> done = new AtomicReference<>(false);
        app.activeSessions.removeSegment(sessionId, setId, () -> done.set(true), this::fail);
        assertTrue("a refusal still reports done", done.get());
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

    /** The set at this position among the SETS of the first exercise, drops nested inside. */
    private LoggedSet setAt(String sessionId, int index) {
        return loadSession(sessionId).exercises().get(0).sets().get(index);
    }

    private String setId(String sessionId, int index) {
        return setAt(sessionId, index).id();
    }

    /** Saves what is typed, without confirming: the draft write the screen does on focus loss. */
    private void type(String setId, double kilos, int reps) {
        app.activeSessions.saveTypedValues(setId, values(kilos, reps));
    }

    /**
     * Performs a set with its drops in the order the screen will: confirm the set, then add each
     * drop and confirm it. The index counts SETS, so drops never move it.
     */
    private void performDropSet(String sessionId, int setIndex, SetValues first,
                                SetValues... drops) {
        String setId = setId(sessionId, setIndex);
        confirm(sessionId, setId, first);
        for (SetValues drop : drops) {
            addSegment(sessionId, setId);
            List<LoggedSet> segments = setAt(sessionId, setIndex).segments();
            confirm(sessionId, segments.get(segments.size() - 1).id(), drop);
        }
    }

    private String techniqueId(String code) {
        try (Cursor cursor = database.getOpenHelper().getReadableDatabase()
                .query("SELECT id FROM training_technique WHERE code = '" + code + "'")) {
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

    private static List<String> idsOf(List<LoggedSet> sets) {
        List<String> ids = new ArrayList<>(sets.size());
        for (LoggedSet set : sets) {
            ids.add(set.id());
        }
        return ids;
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

    private static List<Weight> weightsOf(List<LoggedSet> sets) {
        List<Weight> weights = new ArrayList<>(sets.size());
        for (LoggedSet set : sets) {
            weights.add(set.values().weight());
        }
        return weights;
    }

    private static SetValues values(double kilos, int reps) {
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

package io.github.thiagojosetj.gym.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import io.github.thiagojosetj.gym.AppContainer;
import io.github.thiagojosetj.gym.data.local.AppDatabase;
import io.github.thiagojosetj.gym.data.local.entity.TemplateExerciseEntity;
import io.github.thiagojosetj.gym.data.local.entity.TemplateExerciseGroupEntity;
import io.github.thiagojosetj.gym.data.local.row.TemplateExerciseRow;
import io.github.thiagojosetj.gym.domain.library.ExerciseFilter;
import io.github.thiagojosetj.gym.domain.library.ExerciseSummary;
import io.github.thiagojosetj.gym.domain.model.RepRange;
import io.github.thiagojosetj.gym.domain.model.Weight;
import io.github.thiagojosetj.gym.domain.model.WeightUnit;
import io.github.thiagojosetj.gym.domain.session.ActiveSession;
import io.github.thiagojosetj.gym.domain.session.LoggedSet;
import io.github.thiagojosetj.gym.domain.session.SessionExercise;
import io.github.thiagojosetj.gym.domain.session.SessionGroup;
import io.github.thiagojosetj.gym.domain.session.SessionHeader;
import io.github.thiagojosetj.gym.domain.session.SetValues;
import io.github.thiagojosetj.gym.domain.template.ExerciseGroup;
import io.github.thiagojosetj.gym.domain.template.ExerciseRef;
import io.github.thiagojosetj.gym.domain.template.TemplateDefaults;
import io.github.thiagojosetj.gym.domain.template.TemplateDraft;
import io.github.thiagojosetj.gym.testutil.LiveDataTestUtil;
import io.github.thiagojosetj.gym.testutil.MutableClock;
import io.github.thiagojosetj.gym.testutil.TestContainers;

/**
 * Exercise groups (supersets) through the data layer: made in a template, copied into a session as
 * a snapshot, and deciding when the rest starts (PRODUCT_SPEC section 6.3, docs/DATABASE.md 2b).
 *
 * <p>Every exercise below plans 3 sets and a 90 s rest of its own, and the groups use 75 s. The
 * numbers differ on purpose: an assertion that reads 75 can only have come from the group.
 */
@RunWith(AndroidJUnit4.class)
public class ExerciseGroupTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private static final int ROUND_REST = 75;
    private static final long ROUND_REST_MS = ROUND_REST * 1000L;
    private static final long EXERCISE_REST_MS = 90_000L;

    private static final String BENCH = "Supino reto com barra";
    private static final String ROW = "Remada curvada com barra";
    private static final String CURL = "Rosca martelo";
    private static final String SQUAT = "Agachamento livre com barra";
    private static final String LEG_PRESS = "Leg press 45°";

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

    // ------------------------------------------------------------------ template: group, ungroup

    @Test
    public void groupingTwoExercisesGivesThemTheSameGroupAndTheLabelTheEditorShows()
            throws Exception {
        String templateId = createTemplate("Push A", BENCH, ROW, CURL);
        List<String> exercises = exerciseIds(templateId);

        String groupId = createGroup(templateId, "SS", ROUND_REST, exercises.get(0),
                exercises.get(1));

        assertEquals(groupId, groupIdOf(exercises.get(0)));
        assertEquals(groupId, groupIdOf(exercises.get(1)));
        assertNull("the third exercise was not grouped", groupIdOf(exercises.get(2)));

        // The label the editor shows: the group's letter, then the place inside the group.
        assertEquals(Arrays.asList("A1", "A2", null), editorLabels(templateId));
        ExerciseGroup group = loadGroups(templateId).get(exercises.get(0));
        assertEquals(groupId, group.id());
        assertEquals("A", group.label());
        assertEquals("SS", group.techniqueCode());
        assertEquals(techniqueId("SS"), group.techniqueId());
        assertEquals(ROUND_REST, group.restAfterRoundSeconds());
        assertEquals(0, group.position());
        assertEquals(group, loadGroups(templateId).get(exercises.get(1)));

        // The template read that the editor and the session start use carries each group id.
        List<TemplateExerciseRow> rows = database.templateDao().findExercises(templateId);
        assertEquals(groupId, rows.get(0).groupId);
        assertEquals(groupId, rows.get(1).groupId);
        assertNull(rows.get(2).groupId);
    }

    @Test
    public void removingAGroupLeavesBothExercisesInTheTemplateWithNoGroup() throws Exception {
        String templateId = createTemplate("Push A", BENCH, ROW, CURL);
        List<String> exercises = exerciseIds(templateId);
        String groupId = createGroup(templateId, "SS", ROUND_REST, exercises.get(0),
                exercises.get(1));
        long setsBefore = count("SELECT COUNT(*) FROM template_set");

        removeGroup(templateId, groupId);

        assertEquals(3, count("SELECT COUNT(*) FROM template_exercise WHERE template_id = '"
                + templateId + "'"));
        assertEquals(setsBefore, count("SELECT COUNT(*) FROM template_set"));
        assertNull(groupIdOf(exercises.get(0)));
        assertNull(groupIdOf(exercises.get(1)));
        assertEquals(0, count("SELECT COUNT(*) FROM template_exercise_group"));
        assertTrue(loadGroups(templateId).isEmpty());
        // And the template still opens, with everything it had.
        assertEquals(exercises, draftExerciseIds(loadDraft(templateId)));
    }

    @Test
    public void theForeignKeyAloneKeepsTheExercisesWhenTheGroupRowIsDeleted() throws Exception {
        // removeGroup relies on ON DELETE SET NULL, not on a statement of its own. Proven here
        // without the repository: whoever deletes the row, the exercises stay and are released.
        String templateId = createTemplate("Push A", BENCH, ROW);
        List<String> exercises = exerciseIds(templateId);
        createGroup(templateId, "SS", ROUND_REST, exercises.get(0), exercises.get(1));

        execute("DELETE FROM template_exercise_group");

        assertEquals(2, count("SELECT COUNT(*) FROM template_exercise"));
        assertEquals(6, count("SELECT COUNT(*) FROM template_set"));
        assertNull(groupIdOf(exercises.get(0)));
        assertNull(groupIdOf(exercises.get(1)));
    }

    @Test
    public void lettersFollowTheOrderTheGroupsAppearInTheTemplate() throws Exception {
        String templateId = createTemplate("Full body", BENCH, ROW, CURL, SQUAT);
        List<String> e = exerciseIds(templateId);

        createGroup(templateId, "SS", ROUND_REST, e.get(2), e.get(3));
        assertEquals(Arrays.asList(null, null, "A1", "A2"), editorLabels(templateId));

        // A group made later over EARLIER exercises takes A: the letters read down the workout.
        String first = createGroup(templateId, "BI", 30, e.get(0), e.get(1));
        assertEquals(Arrays.asList("A1", "A2", "B1", "B2"), editorLabels(templateId));
        assertEquals("A", loadGroups(templateId).get(e.get(0)).label());
        assertEquals("B", loadGroups(templateId).get(e.get(2)).label());
        assertEquals(1, loadGroups(templateId).get(e.get(2)).position());

        // Removing A leaves B alone with its letter: two groups must never share one.
        removeGroup(templateId, first);
        assertEquals(Arrays.asList(null, null, "A1", "A2"), editorLabels(templateId));
        assertEquals(0, loadGroups(templateId).get(e.get(2)).position());
    }

    @Test
    public void aGroupNeedsTwoDifferentFreeExercisesOfThatTemplateAndAGroupTechnique()
            throws Exception {
        String templateId = createTemplate("Full body", BENCH, ROW, CURL, SQUAT, LEG_PRESS);
        String foreign = exerciseIds(createTemplate("Pull A", ROW)).get(0);
        List<String> e = exerciseIds(templateId);
        createGroup(templateId, null, 30, e.get(0), e.get(1)); // e0 and e1 are taken from now on
        String ss = techniqueId("SS");

        assertRefused(templateId, Arrays.asList(e.get(2)), ss, 60);            // one is no group
        assertRefused(templateId, Arrays.asList(e.get(2), e.get(2)), ss, 60);  // one, twice
        assertRefused(templateId, Arrays.asList(e.get(2), foreign), ss, 60);   // another template
        assertRefused(templateId, Arrays.asList(e.get(2), "no-such-id"), ss, 60);
        assertRefused(templateId, Arrays.asList(e.get(1), e.get(2)), ss, 60);  // already grouped
        assertRefused(templateId, Arrays.asList(e.get(2), e.get(3)), techniqueId("AQ"), 60);
        assertRefused(templateId, Arrays.asList(e.get(2), e.get(3)), "no-such-technique", 60);
        assertRefused(templateId, Arrays.asList(e.get(2), e.get(3)), ss, -1);
        assertRefused(templateId, Arrays.asList(e.get(2), e.get(3)), ss, 3601);

        // Nothing leaked out of the refusals: still the one group made above, e2 and e3 still free.
        assertEquals(1, count("SELECT COUNT(*) FROM template_exercise_group"));
        assertNull(groupIdOf(e.get(2)));
        assertNull(groupIdOf(e.get(3)));
        // The edges themselves are fine: no rest at all, and the longest rest there is.
        createGroup(templateId, ss, 0, e.get(2), e.get(3));
        removeGroup(templateId, loadGroups(templateId).get(e.get(2)).id());
        createGroup(templateId, ss, 3600, e.get(2), e.get(3));

        AtomicReference<Throwable> error = new AtomicReference<>();
        app.templates.createGroup("no-such-template", Arrays.asList(e.get(2), e.get(3)), ss, 60,
                id -> {
                    throw new AssertionError("a missing template cannot get a group: " + id);
                }, error::set);
        assertTrue(error.get() instanceof TemplateRepository.TemplateNotFoundException);
    }

    @Test
    public void theDaoRollsAGroupBackWhenAnExerciseIsNotInTheTemplate() throws Exception {
        // The repository checks first, but the DAO is what writes. Called with an id that does not
        // belong, it would otherwise leave a group the normalization then dissolves, and nobody
        // would learn that an exercise was not found.
        String templateId = createTemplate("Push A", BENCH, ROW);
        List<String> exercises = exerciseIds(templateId);
        TemplateExerciseGroupEntity group = new TemplateExerciseGroupEntity();
        group.id = "group-by-hand";
        group.templateId = templateId;
        group.restAfterRoundSeconds = 60;

        assertThrows(IllegalArgumentException.class, () -> database.templateDao().addGroup(group,
                Arrays.asList(exercises.get(0), "not-in-this-template"), clock.millis()));

        assertEquals(0, count("SELECT COUNT(*) FROM template_exercise_group"));
        assertNull("the exercise that WAS found is not left half assigned",
                groupIdOf(exercises.get(0)));
    }

    @Test
    public void changingAGroupMarksTheTemplateForSyncLikeAnyOtherChangeToIt() throws Exception {
        // Groups are children of the template aggregate (docs/SYNC.md section 3): children travel
        // with their root, so a change that leaves the root looking synced would never be sent.
        String templateId = createTemplate("Push A", BENCH, ROW);
        List<String> exercises = exerciseIds(templateId);

        markSynced(templateId);
        clock.advanceMinutes(5);
        String groupId = createGroup(templateId, "SS", ROUND_REST, exercises.get(0),
                exercises.get(1));
        assertMarkedForSync(templateId);

        markSynced(templateId);
        clock.advanceMinutes(5);
        updateGroup(templateId, groupId, "BI", 60);
        assertMarkedForSync(templateId);

        markSynced(templateId);
        clock.advanceMinutes(5);
        removeGroup(templateId, groupId);
        assertMarkedForSync(templateId);
    }

    @Test
    public void updatingOrRemovingAGroupThatIsNotThereChangesNothingAndStillReportsDone()
            throws Exception {
        String templateId = createTemplate("Push A", BENCH, ROW);
        markSynced(templateId);

        removeGroup(templateId, "no-such-group"); // a double tap on "ungroup"
        updateGroup(templateId, "no-such-group", null, 10);

        assertEquals("SYNCED", text("SELECT sync_status FROM workout_template WHERE id = '"
                + templateId + "'"));
    }

    // ------------------------------------------------------------------ template: editor saves

    @Test
    public void savingTheTemplateFromTheEditorKeepsItsGroups() throws Exception {
        // The editor changes the draft and saves the whole aggregate, which REPLACES the exercise
        // rows. The draft knows nothing about groups, so without care every edit would ungroup
        // everything and leave the group rows behind with nobody in them.
        String templateId = createTemplate("Push A", BENCH, ROW, CURL);
        List<String> e = exerciseIds(templateId);
        String groupId = createGroup(templateId, "SS", ROUND_REST, e.get(0), e.get(1));

        TemplateDraft draft = loadDraft(templateId);
        draft.rename("Push A v2");
        draft.moveExercise(2, 0); // CURL to the top; BENCH and ROW are now at positions 1 and 2
        save(draft);

        assertEquals(groupId, groupIdOf(e.get(0)));
        assertEquals(groupId, groupIdOf(e.get(1)));
        assertNull(groupIdOf(e.get(2)));
        assertEquals(1, count("SELECT COUNT(*) FROM template_exercise_group"));
        ExerciseGroup group = loadGroups(templateId).get(e.get(0));
        assertEquals("A", group.label());
        assertEquals("SS", group.techniqueCode());
        assertEquals(ROUND_REST, group.restAfterRoundSeconds());
        assertEquals(Arrays.asList(null, "A1", "A2"), editorLabels(templateId));
    }

    @Test
    public void reorderingInTheEditorRelettersTheGroupsByWhereTheyNowAre() throws Exception {
        String templateId = createTemplate("Full body", BENCH, ROW, CURL, SQUAT);
        List<String> e = exerciseIds(templateId);
        createGroup(templateId, "SS", ROUND_REST, e.get(0), e.get(1));
        createGroup(templateId, "BI", 30, e.get(2), e.get(3));
        assertEquals(Arrays.asList("A1", "A2", "B1", "B2"), editorLabels(templateId));

        TemplateDraft draft = loadDraft(templateId);
        draft.moveExercise(3, 0); // SQUAT (in the second group) to the top
        draft.moveExercise(3, 1); // then CURL beside it: the second group now comes first
        save(draft);

        assertEquals(Arrays.asList("A1", "A2", "B1", "B2"), editorLabels(templateId));
        assertEquals("BI", loadGroups(templateId).get(e.get(2)).techniqueCode());
        assertEquals("A", loadGroups(templateId).get(e.get(2)).label());
        assertEquals("B", loadGroups(templateId).get(e.get(0)).label());
    }

    @Test
    public void takingAnExerciseOutOfAGroupInTheEditorKeepsTheGroupOnlyWhileTwoRemain()
            throws Exception {
        String templateId = createTemplate("Full body", BENCH, ROW, CURL, SQUAT);
        List<String> e = exerciseIds(templateId);
        createGroup(templateId, "TRI", ROUND_REST, e.get(0), e.get(1), e.get(2));

        TemplateDraft draft = loadDraft(templateId);
        draft.removeExercise(e.get(2));
        save(draft);
        // Two are left: still a group, and the exercise that was removed took nobody with it.
        assertEquals(Arrays.asList("A1", "A2", null), editorLabels(templateId));

        draft = loadDraft(templateId);
        draft.removeExercise(e.get(1));
        save(draft);
        // One is left, which is no group: it dissolves and its exercise stays in the template.
        assertEquals(Arrays.asList(null, null), editorLabels(templateId));
        assertEquals(0, count("SELECT COUNT(*) FROM template_exercise_group"));
        assertEquals(Arrays.asList(e.get(0), e.get(3)), exerciseIds(templateId));
        assertNull(groupIdOf(e.get(0)));
    }

    @Test
    public void duplicatingATemplateCopiesItsGroupsOntoTheCopysOwnExercises() throws Exception {
        String templateId = createTemplate("Full body", BENCH, ROW, CURL, SQUAT);
        List<String> e = exerciseIds(templateId);
        String firstGroup = createGroup(templateId, "SS", ROUND_REST, e.get(0), e.get(1));
        createGroup(templateId, "BI", 30, e.get(2), e.get(3));

        AtomicReference<String> copyId = new AtomicReference<>();
        app.templates.duplicate(templateId, " (cópia)", copyId::set, this::fail);

        List<String> copy = exerciseIds(copyId.get());
        assertEquals(4, copy.size());
        assertTrue("the copy has exercises of its own", Collections.disjoint(copy, e));
        assertEquals(Arrays.asList("A1", "A2", "B1", "B2"), editorLabels(copyId.get()));
        ExerciseGroup copiedFirst = loadGroups(copyId.get()).get(copy.get(0));
        assertEquals("SS", copiedFirst.techniqueCode());
        assertEquals(ROUND_REST, copiedFirst.restAfterRoundSeconds());
        assertEquals(30, loadGroups(copyId.get()).get(copy.get(2)).restAfterRoundSeconds());
        assertNotEquals("a group of its own, not the original's", firstGroup, copiedFirst.id());

        // Independent from then on: ungrouping the copy leaves the original as it was.
        removeGroup(copyId.get(), copiedFirst.id());
        assertEquals(Arrays.asList(null, null, "A1", "A2"), editorLabels(copyId.get()));
        assertEquals(Arrays.asList("A1", "A2", "B1", "B2"), editorLabels(templateId));
    }

    // ------------------------------------------------------------------ session: the snapshot

    @Test
    public void startingASessionCopiesTheGroupAndSnapshotsItsBadge() throws Exception {
        String templateId = createTemplate("Push A", BENCH, ROW, CURL);
        List<String> exercises = exerciseIds(templateId);
        String templateGroup = createGroup(templateId, "SS", ROUND_REST, exercises.get(0),
                exercises.get(1));

        String sessionId = start(templateId);

        // Two exercises, one row: they share it. The third was never in a group.
        assertEquals(1, count("SELECT COUNT(*) FROM session_exercise_group WHERE session_id = '"
                + sessionId + "'"));
        List<String> groupIds = sessionExerciseGroupIds(sessionId);
        String sessionGroup = groupIds.get(0);
        assertNotNull(sessionGroup);
        assertEquals(sessionGroup, groupIds.get(1));
        assertNull(groupIds.get(2));
        assertNotEquals("a copy of the group, not the template's row", templateGroup, sessionGroup);

        String row = "FROM session_exercise_group WHERE id = '" + sessionGroup + "'";
        assertEquals("A", text("SELECT label " + row));
        assertEquals("SS", text("SELECT technique_code " + row)); // the badge, stored
        assertEquals(techniqueId("SS"), text("SELECT technique_id " + row));
        assertEquals(ROUND_REST, count("SELECT rest_after_round_s " + row));
        assertEquals(0, count("SELECT position " + row));

        // What the screen reads, through the one-shot path and through the observed one.
        ActiveSession session = loadSession(sessionId);
        SessionGroup group = new SessionGroup(sessionGroup, "A", techniqueId("SS"), "SS",
                ROUND_REST, 0);
        SessionExercise first = session.exercises().get(0);
        SessionExercise second = session.exercises().get(1);
        assertEquals(group, session.groupOf(first.id()));
        assertEquals(group, session.groupOf(second.id()));
        assertNull(session.groupOf(session.exercises().get(2).id()));
        assertEquals(Arrays.asList(first.id(), second.id()),
                idsOf(session.exercisesOfGroup(sessionGroup)));
        assertEquals(session.groupByExerciseId(), observed(sessionId).groupByExerciseId());
    }

    @Test
    public void editingOrDeletingTheTemplateGroupAfterStartingDoesNotChangeTheSession()
            throws Exception {
        String templateId = createTemplate("Full body", BENCH, ROW, CURL, SQUAT);
        List<String> e = exerciseIds(templateId);
        String templateGroup = createGroup(templateId, "SS", ROUND_REST, e.get(2), e.get(3));
        String sessionId = start(templateId);
        Map<String, SessionGroup> recorded = loadSession(sessionId).groupByExerciseId();
        assertEquals(2, recorded.size());
        assertEquals("A", recorded.values().iterator().next().label());

        // Edited: another technique, another rest.
        updateGroup(templateId, templateGroup, "BI", 5);
        assertEquals("BI", loadGroups(templateId).get(e.get(2)).techniqueCode());
        assertEquals(recorded, loadSession(sessionId).groupByExerciseId());

        // Relettered: a new group over the first two exercises takes "A" and this one becomes "B".
        createGroup(templateId, "TRI", 10, e.get(0), e.get(1));
        assertEquals("B", loadGroups(templateId).get(e.get(2)).label());
        assertEquals(recorded, loadSession(sessionId).groupByExerciseId());

        // Deleted, and then the whole template.
        removeGroup(templateId, templateGroup);
        assertNull(groupIdOf(e.get(2)));
        assertEquals(recorded, loadSession(sessionId).groupByExerciseId());
        AtomicReference<Boolean> deleted = new AtomicReference<>(false);
        app.templates.delete(templateId, () -> deleted.set(true), this::fail);
        assertTrue(deleted.get());
        assertEquals(recorded, loadSession(sessionId).groupByExerciseId());

        // The recorded row itself, and the session still rests by what it recorded: 75 s, not 5 s.
        SessionGroup group = recorded.values().iterator().next();
        assertEquals("SS", text("SELECT technique_code FROM session_exercise_group WHERE id = '"
                + group.id() + "'"));
        List<SessionExercise> exercises = loadSession(sessionId).exercises();
        confirm(sessionId, exercises.get(2).sets().get(0).id(), values(40, 10));
        confirm(sessionId, exercises.get(3).sets().get(0).id(), values(60, 10));
        assertEquals(ROUND_REST_MS, restLeftMs(sessionId));
    }

    @Test
    public void aCatalogEditToTheTechniqueDoesNotRewriteTheBadgeOfAPastSession()
            throws Exception {
        // The badge is stored with the session group, not joined from the catalog, so a later
        // release that renames it cannot change what a session recorded the day it was done.
        String templateId = createTemplate("Push A", BENCH, ROW);
        List<String> exercises = exerciseIds(templateId);
        createGroup(templateId, "SS", ROUND_REST, exercises.get(0), exercises.get(1));
        String sessionId = start(templateId);
        String firstExercise = loadSession(sessionId).exercises().get(0).id();

        execute("UPDATE training_technique SET code = 'SUP' WHERE code = 'SS'");

        assertEquals("SS", loadSession(sessionId).groupOf(firstExercise).techniqueCode());
        // The template is the live one: the editor shows the catalog as it is now.
        assertEquals("SUP", loadGroups(templateId).get(exercises.get(0)).techniqueCode());
    }

    @Test
    public void deletingTheSessionGroupRowNeverTakesTheExercisesOrTheirSetsWithIt()
            throws Exception {
        String templateId = createTemplate("Push A", BENCH, ROW);
        List<String> exercises = exerciseIds(templateId);
        createGroup(templateId, "SS", ROUND_REST, exercises.get(0), exercises.get(1));
        String sessionId = start(templateId);

        execute("DELETE FROM session_exercise_group");

        // History is never lost to fix a label: the exercises and every set stay.
        assertEquals(2, count("SELECT COUNT(*) FROM session_exercise"));
        assertEquals(6, count("SELECT COUNT(*) FROM set_log"));
        assertEquals(Arrays.asList(null, null), sessionExerciseGroupIds(sessionId));
        assertTrue(loadSession(sessionId).groupByExerciseId().isEmpty());
    }

    @Test
    public void aPlainGroupingHasNoBadgeAndStillRestsAfterTheRound() throws Exception {
        String templateId = createTemplate("Push A", BENCH, ROW);
        List<String> exercises = exerciseIds(templateId);
        createGroup(templateId, null, 45, exercises.get(0), exercises.get(1));

        String sessionId = start(templateId);

        SessionGroup group = loadSession(sessionId).groupOf(
                loadSession(sessionId).exercises().get(0).id());
        assertNotNull(group);
        assertEquals("A", group.label());
        assertNull(group.techniqueId());
        assertNull(group.techniqueCode());
        confirmRound(sessionId, 0);
        assertEquals(45_000L, restLeftMs(sessionId));
    }

    // ------------------------------------------------------------------ session: the rest

    @Test
    public void confirmingTheFirstExerciseOfASupersetStartsNoRestAndTheLastOneDoes()
            throws Exception {
        String sessionId = startSuperset();
        LoggedSet a1 = setOf(sessionId, 0, 0);
        LoggedSet a2 = setOf(sessionId, 1, 0);

        confirm(sessionId, a1.id(), values(40, 10));

        // A2 still owes its set, so the round is not over and there is nothing to rest after.
        assertNull(restOwner(sessionId));

        clock.advanceSeconds(40);
        confirm(sessionId, a2.id(), values(60, 10));

        // The last one to finish starts the rest, and it is the group's: 75 s, not the 90 s that
        // each exercise plans for itself.
        SessionHeader header = loadSession(sessionId).header();
        assertEquals(a2.id(), header.restSetLogId());
        assertEquals(ROUND_REST_MS, header.restRemainingMs(clock.millis()));
        assertTrue(header.isResting(clock.millis()));
    }

    @Test
    public void theLastExerciseFinishedStartsTheRestWhicheverItIs() throws Exception {
        // ACT-01: the planned order is not binding, so A2 before A1 must still get its rest.
        String sessionId = startSuperset();
        LoggedSet a1 = setOf(sessionId, 0, 0);
        LoggedSet a2 = setOf(sessionId, 1, 0);

        confirm(sessionId, a2.id(), values(60, 10));
        assertNull(restOwner(sessionId));

        confirm(sessionId, a1.id(), values(40, 10));
        assertEquals(a1.id(), restOwner(sessionId));
        assertEquals(ROUND_REST_MS, restLeftMs(sessionId));
    }

    @Test
    public void everyRoundOfASupersetRestsOnItsOwn() throws Exception {
        String sessionId = startSuperset();

        for (int round = 0; round < 3; round++) {
            LoggedSet a1 = setOf(sessionId, 0, round);
            LoggedSet a2 = setOf(sessionId, 1, round);
            confirm(sessionId, a1.id(), values(40, 10));
            assertNull("round " + (round + 1) + ": A2 is still owed", restOwner(sessionId));
            confirm(sessionId, a2.id(), values(60, 10));
            assertEquals(a2.id(), restOwner(sessionId));
            clock.advanceSeconds(ROUND_REST + 1);
            restFinished(sessionId, a2.id()); // what the service does when the rest runs out
            assertNull(restOwner(sessionId));
        }
    }

    @Test
    public void aLongerExerciseRestsAfterTheRoundsTheShorterOneDoesNotHave() throws Exception {
        String sessionId = startSuperset();
        addSet(sessionId, loadSession(sessionId).exercises().get(0).id()); // A1: 4 sets, A2: 3
        for (int round = 0; round < 3; round++) {
            confirmRound(sessionId, round);
            skipRest(sessionId);
        }

        // Round 4 exists for A1 only. A2 owes nothing, so it must not hold the round open.
        LoggedSet a1Fourth = setOf(sessionId, 0, 3);
        confirm(sessionId, a1Fourth.id(), values(40, 8));

        assertEquals(a1Fourth.id(), restOwner(sessionId));
        assertEquals(ROUND_REST_MS, restLeftMs(sessionId));
    }

    @Test
    public void startingTheNextRoundBeforeTheRestRunsOutEndsIt() throws Exception {
        // The rest is one per SESSION, not per exercise. Confirming a set that starts no rest must
        // still end the one still counting down: otherwise round 1's alert rings in the middle of
        // round 2, and the screen keeps saying "resting" while the user is lifting.
        String sessionId = startSuperset();
        confirmRound(sessionId, 0);
        assertNotNull(restOwner(sessionId));

        clock.advanceSeconds(20);
        confirm(sessionId, setOf(sessionId, 0, 1).id(), values(40, 10));

        assertNull(restOwner(sessionId));
    }

    @Test
    public void aGroupWithNoRestStartsNoRestAfterTheRound() throws Exception {
        String templateId = createTemplate("Push A", BENCH, ROW);
        List<String> exercises = exerciseIds(templateId);
        createGroup(templateId, "SS", 0, exercises.get(0), exercises.get(1));
        String sessionId = start(templateId);

        confirmRound(sessionId, 0);

        assertNull(restOwner(sessionId));
    }

    @Test
    public void anUngroupedExerciseStillStartsItsOwnRestOnEveryConfirmedSet() throws Exception {
        // Regression guard for what existed before groups: nothing here may change for it.
        String sessionId = start(createTemplate("Push A", BENCH, CURL));
        assertEquals(0, count("SELECT COUNT(*) FROM session_exercise_group"));
        assertEquals(0, count("SELECT COUNT(*) FROM session_exercise WHERE group_id IS NOT NULL"));

        for (int index = 0; index < 3; index++) {
            LoggedSet set = setOf(sessionId, 0, index);
            clock.advanceSeconds(20); // before the previous rest could run out: it is replaced
            confirm(sessionId, set.id(), values(40, 10));

            SessionHeader header = loadSession(sessionId).header();
            assertEquals(set.id(), header.restSetLogId());
            assertEquals(EXERCISE_REST_MS, header.restRemainingMs(clock.millis()));
        }

        // The next exercise is no different: its own set, its own rest.
        LoggedSet curl = setOf(sessionId, 1, 0);
        confirm(sessionId, curl.id(), values(15, 10));
        assertEquals(curl.id(), restOwner(sessionId));
        assertEquals(EXERCISE_REST_MS, restLeftMs(sessionId));
    }

    @Test
    public void anUngroupedExerciseBesideAGroupKeepsItsOwnRest() throws Exception {
        String templateId = createTemplate("Push A", BENCH, ROW, CURL);
        List<String> exercises = exerciseIds(templateId);
        createGroup(templateId, "SS", ROUND_REST, exercises.get(0), exercises.get(1));
        String sessionId = start(templateId);

        LoggedSet curl = setOf(sessionId, 2, 0);
        confirm(sessionId, curl.id(), values(15, 10));

        // Nothing of the group is touched, and the third exercise rests the way it always did.
        assertEquals(curl.id(), restOwner(sessionId));
        assertEquals(EXERCISE_REST_MS, restLeftMs(sessionId));
    }

    @Test
    public void aDropSetInsideAGroupedExerciseStartsNoRestPerDropAndDoesNotShiftTheRound()
            throws Exception {
        String sessionId = startSuperset();
        LoggedSet a1First = setOf(sessionId, 0, 0);
        setTechnique(sessionId, a1First.id(), techniqueId("D"));

        confirm(sessionId, a1First.id(), values(40, 10));
        for (SetValues drop : Arrays.asList(values(30, 8), values(20, 6))) {
            addSegment(sessionId, a1First.id());
            List<LoggedSet> drops = setOf(sessionId, 0, 0).segments();
            confirm(sessionId, drops.get(drops.size() - 1).id(), drop);
            // A drop is not a round, and it does not end one: no rest per drop.
            assertNull(restOwner(sessionId));
        }

        // The drops are nested in their set: A1 still has three sets, numbered 1 to 3.
        List<LoggedSet> a1Sets = loadSession(sessionId).exercises().get(0).sets();
        assertEquals(3, a1Sets.size());
        assertEquals(2, a1Sets.get(0).segments().size());
        assertEquals(Arrays.asList(1, 2, 3), workingNumbersOf(a1Sets));

        // Round 1 ends with A2's first set, and the rest is the group's.
        LoggedSet a2First = setOf(sessionId, 1, 0);
        confirm(sessionId, a2First.id(), values(60, 10));
        assertEquals(a2First.id(), restOwner(sessionId));
        assertEquals(ROUND_REST_MS, restLeftMs(sessionId));
        clock.advanceSeconds(ROUND_REST + 1);
        restFinished(sessionId, a2First.id());

        // Round 2. A1's second set comes after two drops, which must not push it to round 4: were
        // they counted it would look like the last set of its round, and rest with A2's still owed.
        confirm(sessionId, setOf(sessionId, 0, 1).id(), values(40, 9));
        assertNull(restOwner(sessionId));
        LoggedSet a2Second = setOf(sessionId, 1, 1);
        confirm(sessionId, a2Second.id(), values(60, 9));
        assertEquals(a2Second.id(), restOwner(sessionId));
    }

    // ------------------------------------------------------------------ helpers

    /** BENCH (A1) and ROW (A2) grouped as a superset resting 75 s after the round. */
    private String startSuperset() throws Exception {
        String templateId = createTemplate("Push A", BENCH, ROW);
        List<String> exercises = exerciseIds(templateId);
        createGroup(templateId, "SS", ROUND_REST, exercises.get(0), exercises.get(1));
        return start(templateId);
    }

    /** Confirms set {@code index} of both exercises, the way a superset is lifted. */
    private void confirmRound(String sessionId, int index) {
        confirm(sessionId, setOf(sessionId, 0, index).id(), values(40, 10));
        confirm(sessionId, setOf(sessionId, 1, index).id(), values(60, 10));
    }

    /** The set whose rest is running, or null when nobody is resting. */
    private String restOwner(String sessionId) {
        return loadSession(sessionId).header().restSetLogId();
    }

    private long restLeftMs(String sessionId) {
        return loadSession(sessionId).header().restRemainingMs(clock.millis());
    }

    private LoggedSet setOf(String sessionId, int exerciseIndex, int setIndex) {
        return loadSession(sessionId).exercises().get(exerciseIndex).sets().get(setIndex);
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
        draft.addExercises(refs.get(), new TemplateDefaults(3, RepRange.exactly(12), 30, 90));
        return save(draft);
    }

    private String save(TemplateDraft draft) {
        AtomicReference<String> saved = new AtomicReference<>();
        app.templates.save(draft, saved::set, this::fail);
        assertNotNull("the template should have been saved", saved.get());
        return saved.get();
    }

    private TemplateDraft loadDraft(String templateId) {
        AtomicReference<TemplateDraft> draft = new AtomicReference<>();
        app.templates.loadDraft(templateId, draft::set, this::fail);
        assertNotNull(draft.get());
        return draft.get();
    }

    /** The template exercise ids, in the order the template shows them. */
    private List<String> exerciseIds(String templateId) {
        List<String> ids = new ArrayList<>();
        for (TemplateExerciseEntity exercise
                : database.templateDao().findExerciseEntities(templateId)) {
            ids.add(exercise.id);
        }
        return ids;
    }

    private static List<String> draftExerciseIds(TemplateDraft draft) {
        List<String> ids = new ArrayList<>();
        draft.exercises().forEach(exercise -> ids.add(exercise.id()));
        return ids;
    }

    private String createGroup(String templateId, String techniqueCode, int restSeconds,
                               String... templateExerciseIds) {
        AtomicReference<String> created = new AtomicReference<>();
        app.templates.createGroup(templateId, Arrays.asList(templateExerciseIds),
                techniqueCode == null ? null : techniqueId(techniqueCode), restSeconds,
                created::set, this::fail);
        assertNotNull("the group should have been created", created.get());
        return created.get();
    }

    private void assertRefused(String templateId, List<String> exercises, String techniqueId,
                               int restSeconds) {
        AtomicReference<Throwable> error = new AtomicReference<>();
        app.templates.createGroup(templateId, exercises, techniqueId, restSeconds,
                id -> {
                    throw new AssertionError("should have been refused, got group " + id);
                }, error::set);
        assertTrue("expected a refusal, got " + error.get(),
                error.get() instanceof IllegalArgumentException);
    }

    private void updateGroup(String templateId, String groupId, String techniqueCode,
                             int restSeconds) {
        AtomicReference<Boolean> done = new AtomicReference<>(false);
        app.templates.updateGroup(templateId, groupId,
                techniqueCode == null ? null : techniqueId(techniqueCode), restSeconds,
                () -> done.set(true), this::fail);
        assertTrue(done.get());
    }

    private void removeGroup(String templateId, String groupId) {
        AtomicReference<Boolean> done = new AtomicReference<>(false);
        app.templates.removeGroup(templateId, groupId, () -> done.set(true), this::fail);
        assertTrue(done.get());
    }

    private Map<String, ExerciseGroup> loadGroups(String templateId) {
        AtomicReference<Map<String, ExerciseGroup>> groups = new AtomicReference<>();
        app.templates.loadGroups(templateId, groups::set, this::fail);
        assertNotNull(groups.get());
        return groups.get();
    }

    /**
     * What the editor writes beside each exercise, built the way the editor will: walk the
     * exercises in order, and for each one say its group's label and how many it has now seen of
     * that group. Null for an exercise that stands alone.
     */
    private List<String> editorLabels(String templateId) {
        Map<String, ExerciseGroup> groups = loadGroups(templateId);
        Map<String, Integer> seen = new HashMap<>();
        List<String> labels = new ArrayList<>();
        for (String exerciseId : exerciseIds(templateId)) {
            ExerciseGroup group = groups.get(exerciseId);
            if (group == null) {
                labels.add(null);
            } else {
                labels.add(group.label() + seen.merge(group.id(), 1, Integer::sum));
            }
        }
        return labels;
    }

    private String groupIdOf(String templateExerciseId) {
        return text("SELECT group_id FROM template_exercise WHERE id = '" + templateExerciseId
                + "'");
    }

    private List<String> sessionExerciseGroupIds(String sessionId) {
        List<String> groupIds = new ArrayList<>();
        try (Cursor cursor = database.getOpenHelper().getReadableDatabase().query(
                "SELECT group_id FROM session_exercise WHERE session_id = '" + sessionId
                        + "' ORDER BY position")) {
            while (cursor.moveToNext()) {
                groupIds.add(cursor.isNull(0) ? null : cursor.getString(0));
            }
        }
        return groupIds;
    }

    private void markSynced(String templateId) {
        execute("UPDATE workout_template SET sync_status = 'SYNCED' WHERE id = '" + templateId
                + "'");
    }

    private void assertMarkedForSync(String templateId) {
        assertEquals("PENDING", text("SELECT sync_status FROM workout_template WHERE id = '"
                + templateId + "'"));
        assertEquals(clock.millis(), count("SELECT updated_at FROM workout_template WHERE id = '"
                + templateId + "'"));
    }


    @Test
    public void aDropOnTheLastExerciseOfTheRoundRestartsTheRoundRestInsteadOfKillingIt()
            throws Exception {
        // The round is not over while the drops of it are still being done. Before this, the drop
        // read its own row - a segment has no plan of its own - and ended the round's rest with
        // nothing in its place (found in review, 03/10/2026).
        String sessionId = startSuperset();
        LoggedSet a1 = setOf(sessionId, 0, 0);
        LoggedSet a2 = setOf(sessionId, 1, 0);
        confirm(sessionId, a1.id(), values(40, 10));
        confirm(sessionId, a2.id(), values(60, 10));
        assertEquals(a2.id(), restOwner(sessionId));

        addSegment(sessionId, a2.id());
        String drop = setOf(sessionId, 1, 0).segments().get(0).id();
        clock.advanceSeconds(20);
        confirm(sessionId, drop, values(45, 6));

        SessionHeader header = loadSession(sessionId).header();
        assertEquals("o descanso passa a ser do drop, nao some", drop, header.restSetLogId());
        // Restarted, not continued: the work ended now, so the round's rest starts now.
        assertEquals(ROUND_REST_MS, header.restRemainingMs(clock.millis()));
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

    private void confirm(String sessionId, String setId, SetValues values) {
        AtomicReference<Boolean> done = new AtomicReference<>(false);
        app.activeSessions.confirmSet(sessionId, setId, values, () -> done.set(true), this::fail);
        assertTrue("confirm should have completed", done.get());
    }

    private void addSet(String sessionId, String sessionExerciseId) {
        AtomicReference<Boolean> done = new AtomicReference<>(false);
        app.activeSessions.addSet(sessionId, sessionExerciseId, () -> done.set(true), this::fail);
        assertTrue(done.get());
    }

    private void addSegment(String sessionId, String setId) {
        AtomicReference<Boolean> done = new AtomicReference<>(false);
        app.activeSessions.addSegment(sessionId, setId, () -> done.set(true), this::fail);
        assertTrue(done.get());
    }

    private void setTechnique(String sessionId, String setId, String techniqueId) {
        AtomicReference<Boolean> done = new AtomicReference<>(false);
        app.activeSessions.setSetTechnique(sessionId, setId, techniqueId, () -> done.set(true),
                this::fail);
        assertTrue(done.get());
    }

    private void skipRest(String sessionId) {
        AtomicReference<Boolean> done = new AtomicReference<>(false);
        app.activeSessions.skipRest(sessionId, () -> done.set(true), this::fail);
        assertTrue(done.get());
    }

    private void restFinished(String sessionId, String setId) {
        AtomicReference<Boolean> done = new AtomicReference<>(false);
        app.activeSessions.restFinished(sessionId, setId, () -> done.set(true), this::fail);
        assertTrue(done.get());
    }

    private String techniqueId(String code) {
        return text("SELECT id FROM training_technique WHERE code = '" + code + "'");
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

    private static List<String> idsOf(List<SessionExercise> exercises) {
        List<String> ids = new ArrayList<>(exercises.size());
        for (SessionExercise exercise : exercises) {
            ids.add(exercise.id());
        }
        return ids;
    }

    private static List<Integer> workingNumbersOf(List<LoggedSet> sets) {
        List<Integer> numbers = new ArrayList<>(sets.size());
        for (LoggedSet set : sets) {
            numbers.add(set.workingNumber());
        }
        return numbers;
    }

    private static SetValues values(double kilos, int reps) {
        return new SetValues(Weight.of(kilos, WeightUnit.KILOGRAM), reps, null, null, null);
    }

    /** The first column of the first row as text; null for no row or a NULL value. */
    private String text(String sql) {
        try (Cursor cursor = database.getOpenHelper().getReadableDatabase().query(sql)) {
            if (!cursor.moveToFirst() || cursor.isNull(0)) {
                return null;
            }
            return cursor.getString(0);
        }
    }

    private long count(String sql) {
        try (Cursor cursor = database.getOpenHelper().getReadableDatabase().query(sql)) {
            assertTrue(cursor.moveToFirst());
            return cursor.getLong(0);
        }
    }

    private void execute(String sql) {
        database.getOpenHelper().getWritableDatabase().execSQL(sql);
    }

    private void fail(Throwable error) {
        throw new AssertionError(error);
    }
}

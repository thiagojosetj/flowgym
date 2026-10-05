package io.github.thiagojosetj.gym.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.contrib.RecyclerViewActions.scrollTo;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.not;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.matcher.ViewMatchers.isChecked;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.database.Cursor;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import io.github.thiagojosetj.gym.AppContainer;
import io.github.thiagojosetj.gym.GymApplication;
import io.github.thiagojosetj.gym.R;
import io.github.thiagojosetj.gym.domain.library.ExerciseFilter;
import io.github.thiagojosetj.gym.domain.library.ExerciseSummary;
import io.github.thiagojosetj.gym.domain.model.RepRange;
import io.github.thiagojosetj.gym.domain.model.Weight;
import io.github.thiagojosetj.gym.domain.model.WeightUnit;
import io.github.thiagojosetj.gym.domain.session.ActiveSession;
import io.github.thiagojosetj.gym.domain.session.LoggedSet;
import io.github.thiagojosetj.gym.domain.session.SetValues;
import io.github.thiagojosetj.gym.domain.template.ExerciseRef;
import io.github.thiagojosetj.gym.domain.template.TemplateDefaults;
import io.github.thiagojosetj.gym.domain.template.TemplateDraft;
import io.github.thiagojosetj.gym.testutil.LiveDataTestUtil;
import io.github.thiagojosetj.gym.testutil.TestGymApplication;

/**
 * The exercise-by-exercise comparison on a finished session (PRODUCT_SPEC HIS-04).
 *
 * <p>Two sessions of the same workout, the second heavier than the first, so every row has a
 * direction that can be read and checked rather than merely rendered.
 */
@RunWith(AndroidJUnit4.class)
@Config(application = TestGymApplication.class, qualifiers = "pt-rBR-w411dp-h891dp")
public class HistoryComparisonFlowTest {

    private static final String SUPINO = "Supino reto com barra";
    private static final String REMADA = "Remada curvada com barra";
    private static final String FLEXAO = "Flexão de braços";
    /**
     * The two sessions are named apart so the history list can be tapped unambiguously. Renaming
     * the workout between them also says something worth saying: the comparison follows the
     * template, not what it happens to be called today.
     */
    private static final String FIRST = "Push A";
    private static final String SECOND = "Push A revisado";

    @Test
    public void theComparisonNamesEachExerciseAndSaysWhichWayItWent() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            String templateId = template(FIRST, SUPINO);
            performWorkout(templateId, 40, 10);
            rename(templateId, SECOND);
            aWeekLater();
            performWorkout(templateId, 45, 10);
            openNewestSession();

            scrollToText(R.string.history_comparison_by_exercise);

            onView(withId(R.id.comparison_exercise_name)).check(matches(withText(SUPINO)));
            // 45 x 10 against 40 x 10: 450 kg against 400 kg. Matched on the exercise row's own
            // view: the session total says exactly the same thing here, because the workout has
            // only this one exercise.
            onView(withId(R.id.comparison_exercise_volume))
                    .check(matches(withText("Volume: 450 kg (↑ 50 kg · 12,5%)")));
        }
    }

    @Test
    public void openingAnExerciseShowsItsSetsSideBySide() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            String templateId = template(FIRST, SUPINO);
            performWorkout(templateId, 40, 10);
            rename(templateId, SECOND);
            aWeekLater();
            performWorkout(templateId, 45, 10);
            openNewestSession();

            openTheSets();

            onView(withId(R.id.comparison_set_number)).check(matches(withText("Série 1")));
            onView(withId(R.id.comparison_set_previous))
                    .check(matches(withText("10 reps · 40 kg")));
            onView(withId(R.id.comparison_set_current))
                    .check(matches(withText("10 reps · 45 kg")));
            onView(withId(R.id.comparison_set_change)).check(matches(withText("↑")));
        }
    }

    @Test
    public void closingItAgainTakesTheSetsBackOut() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            String templateId = template(FIRST, SUPINO);
            performWorkout(templateId, 40, 10);
            rename(templateId, SECOND);
            aWeekLater();
            performWorkout(templateId, 45, 10);
            openNewestSession();

            openTheSets();
            onView(withId(R.id.button_comparison_sets)).perform(click());

            onView(withId(R.id.comparison_set_number)).check(doesNotExist());
            onView(withId(R.id.button_comparison_sets)).check(matches(withText("Ver a série")));
        }
    }

    @Test
    public void anExerciseOnlyThisSessionHadIsNamedInsteadOfComparedWithZero() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            // The workout gained a second exercise between the two sessions. Comparing it against
            // zero would print a gain that is really "you did something new".
            String templateId = template(FIRST, SUPINO);
            performWorkout(templateId, 40, 10);
            addExerciseTo(templateId, REMADA);
            rename(templateId, SECOND);
            aWeekLater();
            performWorkout(templateId, 45, 10);
            openNewestSession();

            scrollToText(R.string.history_comparison_by_exercise);
            onView(withId(R.id.list)).perform(scrollTo(withText("Só nesta sessão: " + REMADA)));

            onView(withText("Só nesta sessão: " + REMADA)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void theEarlierSetIsReadWithTheSnapshotTheSessionTookThatDay() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            // The same exercise logged with dumbbells then and a barbell now. 20 kg per dumbbell
            // and 20 kg on a bar are different amounts of work and read differently on screen;
            // formatting last time's set with today's snapshot would quietly drop "/halter",
            // which this app never does (PRODUCT_SPEC section 6.4).
            String templateId = template(FIRST, SUPINO);
            performWorkout(templateId, 20, 10);
            loggedWithDumbbellsBackThen();
            rename(templateId, SECOND);
            aWeekLater();
            performWorkout(templateId, 45, 10);
            openNewestSession();

            openTheSets();

            onView(withId(R.id.comparison_set_previous))
                    .check(matches(withText("10 reps · 20 kg/halter")));
            onView(withId(R.id.comparison_set_current))
                    .check(matches(withText("10 reps · 45 kg")));
        }
    }

    @Test
    public void anExerciseWithNoLoadSaysSoInsteadOfReportingNoChange() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            // Push-ups. "Volume: 0 kg (= igual)" would read as "the same amount of load", when
            // the truth is that load is not what this exercise measures (PRODUCT_SPEC section 9).
            String templateId = template(FIRST, FLEXAO);
            performWorkout(templateId, 0, 12);
            rename(templateId, SECOND);
            aWeekLater();
            performWorkout(templateId, 0, 15);
            openNewestSession();

            scrollToText(R.string.history_comparison_by_exercise);

            onView(withId(R.id.comparison_exercise_no_volume))
                    .check(matches(withText(R.string.history_exercise_no_volume)));
            // And the volume line is not there at all. Saying both would be the screen
            // contradicting itself in two consecutive rows.
            onView(withId(R.id.comparison_exercise_volume)).check(matches(not(isDisplayed())));
            onView(withId(R.id.comparison_exercise_reps))
                    .check(matches(withText("Repetições: 15 (↑ 3 · 25,0%)")));
        }
    }

    @Test
    public void theFirstTimeAWorkoutIsDoneThereIsNoTableAtAll() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            performWorkout(template(SECOND, SUPINO), 40, 10);
            openNewestSession();

            onView(withText(R.string.history_comparison_none)).check(matches(isDisplayed()));
            onView(withText(R.string.history_comparison_by_exercise)).check(doesNotExist());
        }
    }

    @Test
    public void aSessionCanBeRatedLaterFromItsOwnScreen() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            // Nobody rates every workout the moment it ends; the dialog is dismissed on the way
            // out of the gym as often as it is answered (PRODUCT_SPEC HIS-06).
            performWorkout(template(SECOND, SUPINO), 40, 10);
            openNewestSession();
            onView(withId(R.id.rating_4)).check(matches(not(isChecked())));

            onView(withId(R.id.rating_4)).perform(click());

            assertEquals("a nota devia estar gravada, nao so na tela",
                    Integer.valueOf(4), storedRating());
            onView(withId(R.id.rating_4)).check(matches(isChecked()));
        }
    }

    @Test
    public void tappingTheChosenNumberAgainTakesTheRatingAwayRatherThanZeroingIt() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            performWorkout(template(SECOND, SUPINO), 40, 10);
            openNewestSession();
            onView(withId(R.id.rating_4)).perform(click());

            onView(withId(R.id.rating_4)).perform(click());

            assertNull("ausencia de nota nao e nota zero", storedRating());
            onView(withId(R.id.rating_4)).check(matches(not(isChecked())));
        }
    }

    @Test
    public void aRatingWrittenOnThisScreenIsStillThereWhenItIsReopened() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            performWorkout(template(SECOND, SUPINO), 40, 10);
            openNewestSession();
            onView(withId(R.id.rating_2)).perform(click());

            pressBack();
            onView(withText(SECOND)).perform(click());

            onView(withId(R.id.rating_2)).check(matches(isChecked()));
        }
    }

    // ------------------------------------------------------------------ helpers

    /** What the database holds, which is the only thing that survives the screen. */
    private static Integer storedRating() {
        try (Cursor cursor = container().database.getOpenHelper().getReadableDatabase()
                .query("SELECT rating FROM workout_session WHERE deleted_at IS NULL")) {
            assertTrue("devia haver exatamente uma sessao", cursor.moveToFirst());
            return cursor.isNull(0) ? null : cursor.getInt(0);
        }
    }

    /** Scrolls the one expandable exercise into view and opens its sets. */
    private static void openTheSets() {
        onView(withId(R.id.list)).perform(
                scrollTo(hasDescendant(withId(R.id.button_comparison_sets))));
        onView(withId(R.id.button_comparison_sets)).perform(click());
    }

    /**
     * Brings the row carrying this text into view. Matched both ways because some rows ARE the
     * text (a section title is one TextView) and some only contain it.
     */
    private static void scrollToText(int stringId) {
        String text = ApplicationProvider.getApplicationContext().getString(stringId);
        onView(withId(R.id.list))
                .perform(scrollTo(anyOf(withText(text), hasDescendant(withText(text)))));
    }

    /** Opens the newest session from the history list. */
    private static void openNewestSession() {
        onView(withId(R.id.historyListFragment)).perform(click());
        onView(withText(SECOND)).perform(click());
    }

    private static void rename(String templateId, String name) {
        AppContainer app = container();
        AtomicReference<TemplateDraft> draft = new AtomicReference<>();
        app.templates.loadDraft(templateId, draft::set, HistoryComparisonFlowTest::rethrow);
        assertNotNull(draft.get());
        draft.get().rename(name);
        AtomicReference<String> saved = new AtomicReference<>();
        app.templates.save(draft.get(), saved::set, HistoryComparisonFlowTest::rethrow);
        assertNotNull(saved.get());
    }

    private static String template(String name, String... exercises) {
        AppContainer app = container();
        TemplateDraft draft = TemplateDraft.newTemplate(app.ids);
        draft.rename(name);
        draft.addExercises(refsOf(app, exercises),
                new TemplateDefaults(1, RepRange.exactly(10), 40, 90));
        AtomicReference<String> id = new AtomicReference<>();
        app.templates.save(draft, id::set, HistoryComparisonFlowTest::rethrow);
        assertNotNull("o treino devia ter sido criado", id.get());
        return id.get();
    }

    private static void addExerciseTo(String templateId, String exerciseName) {
        AppContainer app = container();
        AtomicReference<TemplateDraft> draft = new AtomicReference<>();
        app.templates.loadDraft(templateId, draft::set, HistoryComparisonFlowTest::rethrow);
        assertNotNull(draft.get());
        draft.get().addExercises(refsOf(app, exerciseName),
                new TemplateDefaults(1, RepRange.exactly(10), 40, 90));
        AtomicReference<String> saved = new AtomicReference<>();
        app.templates.save(draft.get(), saved::set, HistoryComparisonFlowTest::rethrow);
        assertNotNull(saved.get());
    }

    /** Start, perform every first set at this weight, finish. */
    private static void performWorkout(String templateId, double kilos, int reps) {
        AppContainer app = container();
        AtomicReference<String> sessionId = new AtomicReference<>();
        app.activeSessions.startFromTemplate(templateId, sessionId::set,
                HistoryComparisonFlowTest::rethrow);
        assertNotNull("a sessao devia ter comecado", sessionId.get());

        AtomicReference<ActiveSession> session = new AtomicReference<>();
        app.activeSessions.loadSession(sessionId.get(), session::set,
                HistoryComparisonFlowTest::rethrow);
        assertNotNull(session.get());
        for (int i = 0; i < session.get().exercises().size(); i++) {
            for (LoggedSet set : session.get().exercises().get(i).sets()) {
                AtomicReference<Boolean> done = new AtomicReference<>(false);
                app.activeSessions.confirmSet(sessionId.get(), set.id(),
                        new SetValues(Weight.of(kilos, WeightUnit.KILOGRAM), reps, null, null, null),
                        () -> done.set(true), HistoryComparisonFlowTest::rethrow);
                assertTrue("a serie devia ter sido confirmada", done.get());
            }
        }
        app.activeSessions.finish(sessionId.get(), summary -> { },
                HistoryComparisonFlowTest::rethrow);
    }

    /**
     * Rewrites the only session on record so that it was performed with two dumbbells.
     *
     * <p>Straight into the snapshot, because that is the thing under test: what the session
     * recorded about the exercise that day. The library cannot be made to change mid-test, and
     * nothing in the app rewrites a finished session's snapshot - which is the point.
     */
    private static void loggedWithDumbbellsBackThen() {
        container().database.getOpenHelper().getWritableDatabase().execSQL(
                "UPDATE session_exercise SET load_basis = 'PER_IMPLEMENT', implement_count = 2");
    }

    /**
     * Moves the app's clock on. The comparison looks for a STRICTLY older session of the same
     * workout, so two sessions sharing an instant are not each other's previous.
     */
    private static void aWeekLater() {
        ((TestGymApplication) ApplicationProvider.getApplicationContext()).clock()
                .advance(Duration.ofDays(7));
    }

    private static AppContainer container() {
        return ((GymApplication) ApplicationProvider.getApplicationContext()).container();
    }

    private static List<ExerciseRef> refsOf(AppContainer app, String... names) {
        List<ExerciseSummary> library;
        try {
            library = LiveDataTestUtil.getOrAwaitValue(
                    app.exercises.observeLibrary(ExerciseFilter.none()));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        List<String> wanted = Arrays.asList(names);
        List<String> ids = new ArrayList<>();
        for (String name : wanted) {
            String found = null;
            for (ExerciseSummary summary : library) {
                if (summary.name().equals(name)) {
                    found = summary.id();
                    break;
                }
            }
            if (found == null) {
                throw new AssertionError("Sem exercicio " + name);
            }
            ids.add(found);
        }
        AtomicReference<List<ExerciseRef>> refs = new AtomicReference<>();
        app.exercises.loadRefs(Collections.unmodifiableList(ids), refs::set,
                HistoryComparisonFlowTest::rethrow);
        return refs.get();
    }

    private static void rethrow(Throwable error) {
        throw new AssertionError(error);
    }
}

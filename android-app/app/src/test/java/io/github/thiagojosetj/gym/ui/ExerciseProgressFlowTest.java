package io.github.thiagojosetj.gym.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.hasSibling;
import static androidx.test.espresso.matcher.ViewMatchers.isChecked;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.Nullable;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.contrib.RecyclerViewActions;
import androidx.test.espresso.matcher.ViewMatchers.Visibility;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.time.Duration;
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
 * One exercise across its history, as the screen shows it (PRODUCT_SPEC PRG-03 and section 10).
 *
 * <p>The records are the part of this app most likely to be believed without checking, so the
 * tests here are about what the screen CLAIMS: that a load is stated on the basis it was logged
 * on, that beating one record does not quietly move the others, that the first time says it was
 * the first time, and that an exercise with no load says so instead of showing zeroes.
 *
 * <p>The test clock stands on Monday 21 September 2026, as in {@link ProgressFlowTest}.
 */
@RunWith(AndroidJUnit4.class)
@Config(application = TestGymApplication.class, qualifiers = "pt-rBR-w411dp-h891dp")
public class ExerciseProgressFlowTest {

    private static final String BARBELL_BENCH = "Supino reto com barra";
    private static final String DUMBBELL_BENCH = "Supino reto com halteres";
    private static final String PUSH_UPS = "Flexão de braços";

    @Test
    public void anExerciseNeverPerformedSaysSoInsteadOfShowingEmptyRecords() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            openProgressOf(BARBELL_BENCH);

            onView(withText(R.string.exercise_progress_empty_title))
                    .check(matches(isDisplayed()));
            onView(withId(R.id.records_card)).check(matches(not(isDisplayed())));
            onView(withText(R.string.exercise_progress_sessions_title))
                    .check(matches(not(isDisplayed())));
        }
    }

    @Test
    public void beatingTheLoadRecordDoesNotDragTheVolumeRecordsWithIt() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            // 40 kg x 10 three times: 400 kg in a set, 1.200 kg in the session.
            performWorkout(BARBELL_BENCH, 40d, 10);
            aWeekLater();
            // Heavier but shorter: 45 kg beats the load record, and 360 kg beats neither volume.
            performWorkout(BARBELL_BENCH, 45d, 8);
            openProgressOf(BARBELL_BENCH);

            onView(valueOf(R.string.exercise_progress_record_max_load))
                    .check(matches(withText("45 kg · 28 de set. de 2026")));
            onView(valueOf(R.string.exercise_progress_record_set_volume))
                    .check(matches(withText("400 kg · 21 de set. de 2026 · primeira vez")));
            onView(valueOf(R.string.exercise_progress_record_session_volume))
                    .check(matches(withText("1200 kg · 21 de set. de 2026 · primeira vez")));
        }
    }

    @Test
    public void theFirstTimeIsCalledTheFirstTimeAndNotARecordBeaten() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            performWorkout(BARBELL_BENCH, 40d, 10);
            openProgressOf(BARBELL_BENCH);

            // Section 10: the first occurrence is a baseline. Nobody beat anything by turning up.
            onView(valueOf(R.string.exercise_progress_record_max_load))
                    .check(matches(withText("40 kg · 21 de set. de 2026 · primeira vez")));
            onView(withText("1 sessão · 21 de set. de 2026")).check(matches(isDisplayed()));
        }
    }

    @Test
    public void aSessionThatHoldsNoRecordCarriesNoBadge() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            performWorkout(BARBELL_BENCH, 40d, 10);
            aWeekLater();
            performWorkout(BARBELL_BENCH, 45d, 8);
            aWeekLater();
            // Lighter than both: beats nothing, so it is just a session.
            performWorkout(BARBELL_BENCH, 30d, 10);
            openProgressOf(BARBELL_BENCH);

            // Asserted on the badge's own visibility rather than on isDisplayed(): with three
            // sessions the oldest row is below the fold, and whether a row carries a badge is not
            // a question about how far the screen is scrolled.
            onView(badgeOf("21 de set. de 2026"))
                    .check(matches(withEffectiveVisibility(Visibility.VISIBLE)));
            onView(badgeOf("28 de set. de 2026"))
                    .check(matches(withEffectiveVisibility(Visibility.VISIBLE)));
            onView(badgeOf("5 de out. de 2026"))
                    .check(matches(withEffectiveVisibility(Visibility.GONE)));
        }
    }

    @Test
    public void theSessionsAreListedNewestFirst() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            performWorkout(BARBELL_BENCH, 40d, 10);
            aWeekLater();
            performWorkout(BARBELL_BENCH, 45d, 8);
            openProgressOf(BARBELL_BENCH);

            // The domain hands the sessions over oldest first, because that is the order a chart
            // reads and the order that decides a baseline. A list is scanned the other way.
            assertTrue("a sessao mais nova devia estar em cima",
                    indexOfRow("28 de set. de 2026") < indexOfRow("21 de set. de 2026"));
        }
    }

    @Test
    public void aDumbbellRecordIsStatedPerDumbbell() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            // 20 kg in each hand. Section 6.4: the load is what was typed, per implement - and
            // the volume still doubles it, as section 9 requires. Printing "20 kg" for the record
            // and 1.200 kg for the volume would be two numbers that cannot both be about the
            // same sets.
            performWorkout(DUMBBELL_BENCH, 20d, 10);
            openProgressOf(DUMBBELL_BENCH);

            onView(valueOf(R.string.exercise_progress_record_max_load))
                    .check(matches(withText("20 kg/halter · 21 de set. de 2026 · primeira vez")));
            onView(valueOf(R.string.exercise_progress_record_session_volume))
                    .check(matches(withText("1200 kg · 21 de set. de 2026 · primeira vez")));
            onView(withText(R.string.exercise_progress_reps_title))
                    .check(matches(isDisplayed()));
            onView(valueOf("20 kg/halter")).check(matches(withText("10 repetições · 21 de set. de 2026")));
        }
    }

    @Test
    public void anExerciseWithNoLoadSaysThereAreNoLoadRecords() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            // Push-ups with nothing added. There is no load and no load volume, and the screen
            // says that rather than printing "0 kg" three times - a zero would be a measurement.
            performWorkout(PUSH_UPS, null, 12);
            openProgressOf(PUSH_UPS);

            onView(withId(R.id.records_card)).check(matches(not(isDisplayed())));
            onView(withId(R.id.no_records)).check(matches(isDisplayed()));
            onView(withText(R.string.exercise_progress_reps_title))
                    .check(matches(not(isDisplayed())));
            // The repetitions are still there: no volume is not "no work".
            onView(withId(R.id.session_counts)).check(matches(withText(
                    "Séries: 3 · reps: 36 · Sem carga registrada · Sem volume de carga")));
        }
    }

    @Test
    public void theLoadsThatDoNotFitTheListAreCountedOutLoud() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            // Six different loads, five rows. Section 10 keeps one record per exact load and a
            // year of training has dozens, so the list has to stop somewhere - but what it leaves
            // out is said, never dropped in silence.
            for (double kilos : new double[] {20d, 25d, 30d, 35d, 40d, 45d}) {
                performWorkout(BARBELL_BENCH, kilos, 10);
                aWeekLater();
            }
            openProgressOf(BARBELL_BENCH);

            onView(valueOf("45 kg")).check(matches(isDisplayed()));
            onView(withId(R.id.more_loads)).check(matches(allOf(
                    withEffectiveVisibility(Visibility.VISIBLE),
                    withText("E mais 1 carga, fora desta lista."))));
            // The sixth load has a row nowhere, and the screen does not pretend otherwise.
            onView(valueOf("20 kg")).check(doesNotExist());
        }
    }

    @Test
    public void theScreenOpensShowingEverySession() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            sevenSessions();
            openProgressOf(BARBELL_BENCH);

            // Opening already filtered would be answering a question nobody asked, and the count
            // of what is left out is only honest once somebody chose to leave it out.
            onView(withId(R.id.showing)).check(matches(withEffectiveVisibility(Visibility.GONE)));
            assertEquals(7, rowCount());
            onView(withId(R.id.window_all)).check(matches(isChecked()));
        }
    }

    @Test
    public void aWindowThatWouldHideNothingIsOfferedDead() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            sevenSessions();
            openProgressOf(BARBELL_BENCH);

            // Seven sessions: "the last 5" is a real choice, "the last 10" and "the last 30" show
            // exactly what "Tudo" shows. A control that looks live and does nothing reads as a
            // defect, so they are disabled - the same rule the calendar's arrows follow.
            onView(withId(R.id.window_5)).check(matches(isEnabled()));
            onView(withId(R.id.window_10)).check(matches(not(isEnabled())));
            onView(withId(R.id.window_30)).check(matches(not(isEnabled())));
            onView(withId(R.id.window_all)).check(matches(isEnabled()));
        }
    }

    @Test
    public void narrowingTheWindowSaysHowManySessionsAreLeftOut() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            sevenSessions();
            openProgressOf(BARBELL_BENCH);

            onView(withId(R.id.window_5)).perform(scrollTo(), click());

            assertEquals(5, rowCount());
            onView(withId(R.id.showing)).check(matches(allOf(
                    withEffectiveVisibility(Visibility.VISIBLE),
                    withText("Mostrando as últimas 5 de 7 sessões."))));
            // "Últimas" has to mean últimas. The two oldest days are the ones that go, and the
            // newest is still there - a window that kept the FIRST five would also show five rows
            // and also say "5 de 7", so the count alone proves nothing.
            onView(withText("21 de set. de 2026")).check(doesNotExist());
            onView(withText("28 de set. de 2026")).check(doesNotExist());
            // Asserted by position rather than by isDisplayed(): the fifth row is below the fold
            // and where a row sits is not a question about how far the screen is scrolled.
            assertEquals("a mais nova em cima", 0, indexOfRow("2 de nov. de 2026"));
            assertEquals("e a mais antiga que sobrou embaixo", 4, indexOfRow("5 de out. de 2026"));
        }
    }

    @Test
    public void theRecordsDoNotMoveWhenTheWindowDoes() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            // The heaviest session is the OLDEST, so narrowing to the last five takes it off the
            // list. The record stays: "your best in the last five sessions" is not a record, and
            // recomputing it per window would publish a different claim under the same word.
            sevenSessions();
            openProgressOf(BARBELL_BENCH);
            onView(valueOf(R.string.exercise_progress_record_max_load))
                    .check(matches(withText("60 kg · 21 de set. de 2026 · primeira vez")));

            onView(withId(R.id.window_5)).perform(scrollTo(), click());

            onView(valueOf(R.string.exercise_progress_record_max_load))
                    .check(matches(withText("60 kg · 21 de set. de 2026 · primeira vez")));
            assertEquals("e a sessao de 60 kg saiu da lista", 5, rowCount());
        }
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Seven weekly sessions, the heaviest first.
     *
     * <p>60 kg on the oldest day and 40 kg on the other six, so narrowing the window removes the
     * session that holds every record and the records can be seen not to follow it.
     */
    private static void sevenSessions() {
        performWorkout(BARBELL_BENCH, 60d, 10);
        for (int i = 0; i < 6; i++) {
            aWeekLater();
            performWorkout(BARBELL_BENCH, 40d, 10);
        }
    }

    /** How many session rows the list is drawing. */
    private static int rowCount() {
        AtomicReference<Integer> count = new AtomicReference<>(-1);
        onView(withId(R.id.session_rows)).check((view, error) ->
                count.set(((android.view.ViewGroup) view).getChildCount()));
        return count.get();
    }

    /** The figure of the record row whose label is this string. */
    private static org.hamcrest.Matcher<android.view.View> valueOf(int labelId) {
        return allOf(withId(R.id.record_value),
                hasSibling(allOf(withId(R.id.record_label), withText(labelId))));
    }

    private static org.hamcrest.Matcher<android.view.View> valueOf(String label) {
        return allOf(withId(R.id.record_value),
                hasSibling(allOf(withId(R.id.record_label), withText(label))));
    }

    /** The "Recorde" badge of the session row for this day. */
    private static org.hamcrest.Matcher<android.view.View> badgeOf(String day) {
        return allOf(withId(R.id.session_record),
                hasSibling(allOf(withId(R.id.session_day), withText(day))));
    }

    /** Where a day's row sits in the list, so the order can be asserted and not assumed. */
    private static int indexOfRow(String day) {
        AtomicReference<Integer> index = new AtomicReference<>(-1);
        onView(withId(R.id.session_rows)).check((view, error) -> {
            android.view.ViewGroup rows = (android.view.ViewGroup) view;
            for (int i = 0; i < rows.getChildCount(); i++) {
                android.widget.TextView label =
                        rows.getChildAt(i).findViewById(R.id.session_day);
                if (label != null && day.contentEquals(label.getText())) {
                    index.set(i);
                    return;
                }
            }
        });
        assertTrue("sem linha para " + day, index.get() >= 0);
        return index.get();
    }

    private static void openProgressOf(String exerciseName) {
        onView(withId(R.id.exerciseLibraryFragment)).perform(click());
        onView(withId(R.id.list))
                .perform(RecyclerViewActions.scrollTo(hasDescendant(withText(exerciseName))));
        onView(withText(exerciseName)).perform(click());
        onView(withId(R.id.open_progress)).perform(click());
    }

    private static void aWeekLater() {
        ((TestGymApplication) ApplicationProvider.getApplicationContext()).clock()
                .advance(Duration.ofDays(7));
    }

    /**
     * A whole workout through the repositories: the point here is the screen, not the logging.
     *
     * @param kilos the load to confirm every set with, or {@code null} for no load at all
     */
    private static void performWorkout(String exerciseName, @Nullable Double kilos, int reps) {
        AppContainer app = container();
        TemplateDraft draft = TemplateDraft.newTemplate(app.ids);
        draft.rename("Push " + exerciseName);
        draft.addExercises(refsOf(app, exerciseName),
                new TemplateDefaults(3, RepRange.exactly(reps),
                        TemplateDefaults.DEFAULT_DURATION_SECONDS, 90));
        AtomicReference<String> templateId = new AtomicReference<>();
        app.templates.save(draft, templateId::set, ExerciseProgressFlowTest::rethrow);
        assertNotNull("o treino devia ter sido criado", templateId.get());

        AtomicReference<String> sessionId = new AtomicReference<>();
        app.activeSessions.startFromTemplate(templateId.get(), sessionId::set,
                ExerciseProgressFlowTest::rethrow);
        assertNotNull("a sessao devia ter comecado", sessionId.get());

        AtomicReference<ActiveSession> session = new AtomicReference<>();
        app.activeSessions.loadSession(sessionId.get(), session::set,
                ExerciseProgressFlowTest::rethrow);
        Weight weight = kilos == null ? null : Weight.of(kilos, WeightUnit.KILOGRAM);
        for (LoggedSet set : session.get().exercises().get(0).sets()) {
            AtomicReference<Boolean> done = new AtomicReference<>(false);
            app.activeSessions.confirmSet(sessionId.get(), set.id(),
                    new SetValues(weight, reps, null, null, null),
                    () -> done.set(true), ExerciseProgressFlowTest::rethrow);
            assertTrue("a serie devia ter sido confirmada", done.get());
        }
        app.activeSessions.finish(sessionId.get(), summary -> { },
                ExerciseProgressFlowTest::rethrow);
    }

    private static AppContainer container() {
        return ((GymApplication) ApplicationProvider.getApplicationContext()).container();
    }

    private static List<ExerciseRef> refsOf(AppContainer app, String exerciseName) {
        List<ExerciseSummary> library;
        try {
            library = LiveDataTestUtil.getOrAwaitValue(
                    app.exercises.observeLibrary(ExerciseFilter.none()));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        for (ExerciseSummary summary : library) {
            if (summary.name().equals(exerciseName)) {
                AtomicReference<List<ExerciseRef>> refs = new AtomicReference<>();
                app.exercises.loadRefs(Collections.singletonList(summary.id()), refs::set,
                        ExerciseProgressFlowTest::rethrow);
                return refs.get();
            }
        }
        throw new AssertionError("Sem exercicio " + exerciseName);
    }

    private static void rethrow(Throwable error) {
        throw new AssertionError(error);
    }
}

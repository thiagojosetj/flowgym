package io.github.thiagojosetj.gym.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.matcher.ViewMatchers.hasSibling;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

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
 * "Progresso" (PRODUCT_SPEC PRG-04) as the screen shows it.
 *
 * <p>The test clock stands on Monday 21 September 2026, so a Sunday-first week runs from the 20th
 * to the 26th and the arrows have somewhere to go in both directions once there are sessions on
 * either side of it.
 */
@RunWith(AndroidJUnit4.class)
@Config(application = TestGymApplication.class, qualifiers = "pt-rBR-w411dp-h891dp")
public class ProgressFlowTest {

    private static final String SUPINO = "Supino reto com barra";

    @Test
    public void withNothingFinishedTheScreenSaysSoInsteadOfShowingZeroes() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            openProgress();

            onView(withText(R.string.progress_empty_title)).check(matches(isDisplayed()));
            onView(withId(R.id.totals_card)).check(matches(not(isDisplayed())));
            // Nowhere to go: the arrows are bounded by the data, and there is none.
            onView(withId(R.id.previous_period)).check(matches(not(isEnabled())));
            onView(withId(R.id.next_period)).check(matches(not(isEnabled())));
        }
    }

    @Test
    public void theWeekAddsUpWhatWasDoneInIt() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            performWorkout(40, 10);
            openProgress();

            onView(withText("1 treino")).check(matches(isDisplayed()));
            // Three sets of 40 kg x 10 is 1.200 kg.
            onView(withId(R.id.volume)).check(matches(withText("Volume de carga: 1200 kg")));
            onView(withId(R.id.sets))
                    .check(matches(withText("Séries feitas: 3 · repetições: 30")));
        }
    }

    @Test
    public void eachMuscleGroupSaysHowManySetsItGotAndInWhichRole() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            performWorkout(40, 10);
            openProgress();

            onView(withText(R.string.progress_muscles_title)).check(matches(isDisplayed()));
            onView(withText("Peito")).check(matches(isDisplayed()));
            // The bench press is filed under chest.middle, a subgroup: the row reads "Peito".
            onView(withText("3 séries como principal")).check(matches(isDisplayed()));
            // The bench press also uses shoulders and triceps, and only as assisting muscles.
            // Reported, in their own words: there is no honest weight that would let them be
            // added into the chest's number, and leaving them out would hide real work.
            onView(withText("Ombros")).check(matches(isDisplayed()));
            onView(withText("Tríceps")).check(matches(isDisplayed()));
            onView(allOf(withId(R.id.muscle_counts), hasSibling(withText("Tríceps"))))
                    .check(matches(withText("3 séries como auxiliar")));
        }
    }

    @Test
    public void theArrowsStopWhereTheTrainingDoes() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            // One session, in this week. There is no older week and no newer one to go to: a week
            // with nothing in it either side of the training is not a place to be.
            performWorkout(40, 10);
            openProgress();

            onView(withId(R.id.previous_period)).check(matches(not(isEnabled())));
            onView(withId(R.id.next_period)).check(matches(not(isEnabled())));
        }
    }

    @Test
    public void aWeekWithNothingInItBetweenTwoThatHaveSaysSo() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            // Training on the 21st and again a fortnight later leaves the week between them empty
            // and reachable: skipping a week is a fact about the training, not a gap to hide.
            performWorkout(40, 10);
            aFortnightLater();
            performWorkout(40, 10);
            openProgress();
            onView(withText("1 treino")).check(matches(isDisplayed()));

            onView(withId(R.id.previous_period)).perform(click());

            onView(withText(R.string.progress_empty_period)).check(matches(isDisplayed()));
            // No numbers at all rather than a column of zeroes.
            onView(withId(R.id.totals_card)).check(matches(not(isDisplayed())));
            onView(withText(R.string.progress_muscles_title)).check(matches(not(isDisplayed())));
            onView(withId(R.id.next_period)).check(matches(isEnabled()));
            onView(withId(R.id.previous_period)).check(matches(isEnabled()));
        }
    }

    @Test
    public void theMonthGathersTheWeeksAndTheArrowsFollowIt() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            performWorkout(40, 10);
            aWeekLater();
            performWorkout(40, 10);
            openProgress();

            // The second session is in the next week, so this week knows only the first.
            onView(withText("1 treino")).check(matches(isDisplayed()));

            onView(withId(R.id.kind_month)).perform(click());

            onView(withText("setembro 2026")).check(matches(isDisplayed()));
            onView(withText("2 treinos")).check(matches(isDisplayed()));
            onView(withText("6 séries como principal")).check(matches(isDisplayed()));
        }
    }

    @Test
    public void switchingBackToTheWeekReturnsToTheWeekOfToday() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            performWorkout(40, 10);
            openProgress();
            onView(withId(R.id.kind_month)).perform(click());

            onView(withId(R.id.kind_week)).perform(click());

            onView(withText("1 treino")).check(matches(isDisplayed()));
            onView(withText("20 set. – 26 set.")).check(matches(isDisplayed()));
        }
    }

    // ------------------------------------------------------------------ helpers

    private static void openProgress() {
        onView(withId(R.id.progressFragment)).perform(click());
    }

    private static void aFortnightLater() {
        ((TestGymApplication) ApplicationProvider.getApplicationContext()).clock()
                .advance(Duration.ofDays(14));
    }

    private static void aWeekLater() {
        ((TestGymApplication) ApplicationProvider.getApplicationContext()).clock()
                .advance(Duration.ofDays(7));
    }

    /** A whole workout through the repositories: the point here is the screen, not the logging. */
    private static void performWorkout(double kilos, int reps) {
        AppContainer app = container();
        TemplateDraft draft = TemplateDraft.newTemplate(app.ids);
        draft.rename("Push A");
        draft.addExercises(refsOf(app, SUPINO),
                new TemplateDefaults(3, RepRange.exactly(10), 40, 90));
        AtomicReference<String> templateId = new AtomicReference<>();
        app.templates.save(draft, templateId::set, ProgressFlowTest::rethrow);
        assertNotNull("o treino devia ter sido criado", templateId.get());

        AtomicReference<String> sessionId = new AtomicReference<>();
        app.activeSessions.startFromTemplate(templateId.get(), sessionId::set,
                ProgressFlowTest::rethrow);
        assertNotNull("a sessao devia ter comecado", sessionId.get());

        AtomicReference<ActiveSession> session = new AtomicReference<>();
        app.activeSessions.loadSession(sessionId.get(), session::set, ProgressFlowTest::rethrow);
        for (LoggedSet set : session.get().exercises().get(0).sets()) {
            AtomicReference<Boolean> done = new AtomicReference<>(false);
            app.activeSessions.confirmSet(sessionId.get(), set.id(),
                    new SetValues(Weight.of(kilos, WeightUnit.KILOGRAM), reps, null, null, null),
                    () -> done.set(true), ProgressFlowTest::rethrow);
            assertTrue("a serie devia ter sido confirmada", done.get());
        }
        app.activeSessions.finish(sessionId.get(), summary -> { }, ProgressFlowTest::rethrow);
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
                        ProgressFlowTest::rethrow);
                return refs.get();
            }
        }
        throw new AssertionError("Sem exercicio " + exerciseName);
    }

    private static void rethrow(Throwable error) {
        throw new AssertionError(error);
    }
}

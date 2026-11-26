package io.github.thiagojosetj.gym.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isChecked;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.matcher.ViewMatchers.hasSibling;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.matcher.ViewMatchers.Visibility;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;
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
import io.github.thiagojosetj.gym.ui.progress.ProgressFragment;
import io.github.thiagojosetj.gym.ui.progress.ProgressViewModel;

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
    private static final String PUSH_UPS = "Flexão de braços";

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
            // Scrolled to: the period's chart now sits between the totals and the muscle rows,
            // so this row is below the fold. Where it sits on screen is not what is being tested.
            onView(withText("6 séries como principal")).perform(scrollTo())
                    .check(matches(isDisplayed()));
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

    @Test
    public void thePeriodChartDrawsOneMeasureAtATime() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            performWorkout(40, 10);
            aDayLater();
            performWorkout(45, 8);
            openProgress();

            // There is no "load of a session": a workout is many exercises, and the heaviest
            // weight in it is a fact about one of them. Volume, sets and repetitions are the
            // three that mean something for a whole session.
            onView(withId(R.id.chart_caption))
                    .perform(scrollTo()).check(matches(withText("Volume de carga por treino")));

            onView(withId(R.id.measure_sets)).perform(scrollTo(), click());
            onView(withId(R.id.chart_caption)).check(matches(withText("Séries por treino")));

            onView(withId(R.id.measure_reps)).perform(scrollTo(), click());
            onView(withId(R.id.chart_caption)).check(matches(withText("Repetições por treino")));
            // 3 sets of 8 reps on the newest day, read out without being touched.
            onView(withId(R.id.chart_reading)).check(matches(withText("22 set. · 24")));
        }
    }

    @Test
    public void onePeriodWorkoutIsNotATrendAndTheChartFollowsThePeriod() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            performWorkout(40, 10);
            aWeekLater();
            performWorkout(40, 10);
            openProgress();

            // This week knows only the second session, so there is nothing to join up.
            onView(withId(R.id.chart_card))
                    .check(matches(withEffectiveVisibility(Visibility.GONE)));
            onView(withId(R.id.chart_note)).perform(scrollTo()).check(matches(
                    withText(R.string.progress_chart_needs_two)));

            onView(withId(R.id.kind_month)).perform(click());

            // The month holds both, and the chart follows the same selector the totals do.
            onView(withId(R.id.chart_card)).perform(scrollTo())
                    .check(matches(withEffectiveVisibility(Visibility.VISIBLE)));
            onView(withId(R.id.chart)).check(matches(withContentDescription(
                    "Volume de carga por treino. 2 treinos, de 21 de set. de 2026"
                            + " a 28 de set. de 2026. Do menor, 1200 kg, ao maior, 1200 kg.")));
        }
    }

    @Test
    public void aPeriodOfOnlyBodyWeightWorkHasNoVolumeLine() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            performWorkout(PUSH_UPS, null, 12);
            aDayLater();
            performWorkout(PUSH_UPS, null, 15);
            openProgress();

            // Section 9 keeps body weight out of load volume, so a line at zero would be a
            // measurement of nothing. The sets and the repetitions are real and keep their line.
            onView(withId(R.id.chart_note)).perform(scrollTo()).check(matches(allOf(
                    withEffectiveVisibility(Visibility.VISIBLE),
                    withText(R.string.progress_chart_no_volume))));

            onView(withId(R.id.measure_reps)).perform(scrollTo(), click());

            onView(withId(R.id.chart_card))
                    .check(matches(withEffectiveVisibility(Visibility.VISIBLE)));
            onView(withId(R.id.chart_reading)).check(matches(withText("22 set. · 45")));
        }
    }

    @Test
    public void aCustomPeriodShowsItsOwnRangeAndStepsByItsOwnLength() {
        try (ActivityScenario<MainActivity> scenario =
                     ActivityScenario.launch(MainActivity.class)) {
            performWorkout(40, 10);
            aWeekLater();
            performWorkout(40, 10);
            openProgress();

            // Six days, by hand: 25 to 30 September, which holds the second session only.
            customPeriod(scenario, LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 30));

            onView(withId(R.id.kind_custom)).check(matches(isChecked()));
            onView(withId(R.id.period_title)).check(matches(withText("25 set. – 30 set.")));
            onView(withText("1 treino")).check(matches(isDisplayed()));

            onView(withId(R.id.previous_period)).perform(click());

            // Six days back, not a week back: a hand-picked range steps by its OWN length, or
            // the app would be answering about a period nobody asked for. A week step would
            // have landed on 18 September.
            onView(withId(R.id.period_title)).check(matches(withText("19 set. – 24 set.")));
            onView(withText("1 treino")).check(matches(isDisplayed()));
        }
    }

    @Test
    public void thePeriodButtonAsksForTwoDatesInsteadOfGuessingThem() {
        try (ActivityScenario<MainActivity> scenario =
                     ActivityScenario.launch(MainActivity.class)) {
            performWorkout(40, 10);
            openProgress();

            onView(withId(R.id.kind_custom)).perform(click());

            // Two dates cannot be derived from the week on screen, so the button opens the
            // picker and changes nothing by itself: the week is still what is shown.
            assertNotNull("o seletor de datas devia ter aberto", datePicker(scenario));
            onView(withId(R.id.period_title)).check(matches(withText("20 set. – 26 set.")));
        }
    }

    @Test
    public void confirmingThePickerKeepsTheDaysThatWereOnIt() {
        // Run west of Greenwich on purpose. Robolectric's default is UTC, which is the one zone
        // where reading the picker's milliseconds correctly and reading them wrongly give the
        // same answer - a test under it would pass with the bug in place. Set after the
        // application is built, so only the picker's conversion is affected and the week on
        // screen stays where the test clock put it.
        TimeZone original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            performWorkout(40, 10);
            openProgress();

            // The picker opens prefilled with the week on screen, so confirming it straight away
            // asks for exactly those two days back. Material hands them over as UTC
            // milliseconds; read in Sao Paulo's zone they come back a day early and the title
            // would say "19 set. – 25 set." (see PickedDays).
            onView(withId(R.id.kind_custom)).perform(click());
            onView(withId(com.google.android.material.R.id.confirm_button))
                    .inRoot(isDialog()).perform(click());

            onView(withId(R.id.period_title)).check(matches(withText("20 set. – 26 set.")));
            onView(withId(R.id.kind_custom)).check(matches(isChecked()));
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    public void dismissingThePickerPutsTheButtonBackWhereItWas() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            performWorkout(40, 10);
            openProgress();

            onView(withId(R.id.kind_custom)).perform(click());
            onView(withId(com.google.android.material.R.id.cancel_button))
                    .inRoot(isDialog()).perform(click());

            // "Período" still lit while the screen shows the week would be the control lying
            // about what it did.
            onView(withId(R.id.kind_week)).check(matches(isChecked()));
            onView(withId(R.id.period_title)).check(matches(withText("20 set. – 26 set.")));
        }
    }

    @Test
    public void goingBackToTheWeekFromACustomPeriodLandsOnTheWeekOfThatRange() {
        try (ActivityScenario<MainActivity> scenario =
                     ActivityScenario.launch(MainActivity.class)) {
            performWorkout(40, 10);
            openProgress();
            customPeriod(scenario, LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 30));

            onView(withId(R.id.kind_week)).perform(click());

            // Today (21 September) is not inside 25-30 September, so the week is the one of the
            // range's first day, not the one of today: the screen stays where the person was.
            onView(withId(R.id.period_title)).check(matches(withText("20 set. – 26 set.")));
            onView(withId(R.id.kind_week)).check(matches(isChecked()));
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Drives the screen's own ViewModel, the way the date picker's callback does. */
    private static void customPeriod(ActivityScenario<MainActivity> scenario, LocalDate from,
                                     LocalDate to) {
        scenario.onActivity(activity ->
                new ViewModelProvider(progressFragment(activity))
                        .get(ProgressViewModel.class).showCustom(from, to));
    }

    private static Fragment datePicker(ActivityScenario<MainActivity> scenario) {
        AtomicReference<Fragment> picker = new AtomicReference<>();
        scenario.onActivity(activity -> picker.set(progressFragment(activity)
                .getParentFragmentManager().findFragmentByTag("progress-date-range")));
        return picker.get();
    }

    private static Fragment progressFragment(MainActivity activity) {
        Fragment host = activity.getSupportFragmentManager().findFragmentById(R.id.nav_host);
        assertNotNull("sem NavHostFragment", host);
        for (Fragment child : host.getChildFragmentManager().getFragments()) {
            if (child instanceof ProgressFragment) {
                return child;
            }
        }
        throw new AssertionError("A tela Progresso nao esta aberta");
    }

    private static void aDayLater() {
        ((TestGymApplication) ApplicationProvider.getApplicationContext()).clock()
                .advance(Duration.ofDays(1));
    }

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

    private static void performWorkout(double kilos, int reps) {
        performWorkout(SUPINO, kilos, reps);
    }

    /**
     * A whole workout through the repositories: the point here is the screen, not the logging.
     *
     * @param kilos the load to confirm every set with, or {@code null} for no load at all
     */
    private static void performWorkout(String exerciseName, Double kilos, int reps) {
        AppContainer app = container();
        TemplateDraft draft = TemplateDraft.newTemplate(app.ids);
        draft.rename("Push " + exerciseName);
        draft.addExercises(refsOf(app, exerciseName),
                new TemplateDefaults(3, RepRange.exactly(reps), 40, 90));
        AtomicReference<String> templateId = new AtomicReference<>();
        app.templates.save(draft, templateId::set, ProgressFlowTest::rethrow);
        assertNotNull("o treino devia ter sido criado", templateId.get());

        AtomicReference<String> sessionId = new AtomicReference<>();
        app.activeSessions.startFromTemplate(templateId.get(), sessionId::set,
                ProgressFlowTest::rethrow);
        assertNotNull("a sessao devia ter comecado", sessionId.get());

        AtomicReference<ActiveSession> session = new AtomicReference<>();
        app.activeSessions.loadSession(sessionId.get(), session::set, ProgressFlowTest::rethrow);
        Weight weight = kilos == null ? null : Weight.of(kilos, WeightUnit.KILOGRAM);
        for (LoggedSet set : session.get().exercises().get(0).sets()) {
            AtomicReference<Boolean> done = new AtomicReference<>(false);
            app.activeSessions.confirmSet(sessionId.get(), set.id(),
                    new SetValues(weight, reps, null, null, null),
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

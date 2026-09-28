package io.github.thiagojosetj.gym.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withParent;
import static androidx.test.espresso.matcher.ViewMatchers.withParentIndex;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.robolectric.Shadows.shadowOf;

import android.os.Looper;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.contrib.RecyclerViewActions;
import androidx.test.espresso.matcher.ViewMatchers.Visibility;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.time.Duration;

import io.github.thiagojosetj.gym.R;
import io.github.thiagojosetj.gym.testutil.TestGymApplication;

/**
 * The workout in progress, through the real UI: start from a template, log a set, rest, and finish
 * (ACT-01 to ACT-09).
 *
 * <p>The test clock is fixed, so the timers are deterministic: a 90 s rest reads 1:30 until the test
 * changes it.
 */
@RunWith(AndroidJUnit4.class)
@Config(application = TestGymApplication.class, qualifiers = "pt-rBR-w411dp-h891dp")
public class ActiveWorkoutFlowTest {

    @Test
    public void startLogASetRestAndFinish() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            createTemplate("Push A", "Supino reto com barra");

            startWorkout();

            // The plan came across as a snapshot: three sets, 12 reps each.
            onView(withText("Supino reto com barra")).check(matches(isDisplayed()));
            onView(withId(R.id.progress)).check(matches(withText("0 de 3 séries")));
            onView(setRow(0, R.id.planned)).check(matches(withText(containsString("12 reps"))));
            // No previous session yet, so nothing is shown where "anterior" would go.
            onView(setRow(0, R.id.previous)).check(matches(withEffectiveVisibility(Visibility.GONE)));

            // Log the first set.
            onView(setRow(0, R.id.weight_input)).perform(replaceText("42,5"));
            onView(setRow(0, R.id.reps_input)).perform(replaceText("10"));
            onView(setRow(0, R.id.button_done)).perform(click());

            onView(withId(R.id.progress)).check(matches(withText("1 de 3 séries")));
            // The rest started, as a countdown that comes from the stored instant.
            onView(withId(R.id.rest_container)).check(matches(isDisplayed()));
            onView(withId(R.id.rest_remaining)).check(matches(withText("1:30")));
            onView(withId(R.id.rest_plus_30)).perform(click());
            onView(withId(R.id.rest_remaining)).check(matches(withText("2:00")));
            onView(withId(R.id.rest_minus)).perform(click());
            onView(withId(R.id.rest_remaining)).check(matches(withText("1:45")));
            onView(withId(R.id.rest_skip)).perform(click());
            onView(withId(R.id.rest_container)).check(matches(withEffectiveVisibility(Visibility.GONE)));

            // Pausing says so and offers to resume.
            onView(withId(R.id.button_pause)).perform(click());
            onView(withId(R.id.paused_label)).check(matches(isDisplayed()));
            onView(withId(R.id.button_pause)).check(matches(withText(R.string.session_resume)));
            onView(withId(R.id.button_pause)).perform(click());
            onView(withId(R.id.paused_label)).check(matches(withEffectiveVisibility(Visibility.GONE)));

            // Finishing says exactly what it will do to the two sets that were never confirmed.
            onView(withId(R.id.button_finish)).perform(click());
            onView(withText(R.string.finish_title)).inRoot(isDialog()).check(matches(isDisplayed()));
            onView(withText(containsString("2 séries vazias"))).inRoot(isDialog())
                    .check(matches(isDisplayed()));
            onView(withText(R.string.finish_confirm)).inRoot(isDialog()).perform(click());

            // Summary, then back to the list of templates.
            onView(withText(R.string.summary_title)).inRoot(isDialog()).check(matches(isDisplayed()));
            onView(withText(containsString("Séries feitas: 1"))).inRoot(isDialog())
                    .check(matches(isDisplayed()));
            onView(withText(containsString("425 kg"))).inRoot(isDialog()).check(matches(isDisplayed()));
            onView(withText(R.string.summary_close)).inRoot(isDialog()).perform(click());
            onView(withText("Push A")).check(matches(isDisplayed()));
        }
    }

    @Test
    public void aConfirmedSetSaysSoAndCannotBeEditedByAccident() {
        // Regression (review 2026-09-28): the only sign of "done" was setSelected(), which the stock
        // icon button does not draw, and the fields stayed editable while the value typed into them
        // was silently refused by the database.
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            createTemplate("Push A", "Supino reto com barra");
            startWorkout();

            onView(setRow(0, R.id.done_label)).check(matches(withEffectiveVisibility(Visibility.GONE)));
            onView(setRow(0, R.id.weight_input)).perform(replaceText("40"));
            onView(setRow(0, R.id.reps_input)).perform(replaceText("10"));
            onView(setRow(0, R.id.button_done)).perform(click());

            onView(setRow(0, R.id.done_label)).check(matches(isDisplayed()));
            onView(setRow(0, R.id.weight_input)).check(matches(not(isEnabled())));
            onView(setRow(0, R.id.reps_input)).check(matches(not(isEnabled())));

            // Undo makes it editable again, and the values typed are still there.
            onView(setRow(0, R.id.button_done)).perform(click());
            onView(setRow(0, R.id.done_label)).check(matches(withEffectiveVisibility(Visibility.GONE)));
            onView(setRow(0, R.id.weight_input)).check(matches(isEnabled()));
            onView(setRow(0, R.id.weight_input)).check(matches(withText("40")));
        }
    }

    @Test
    public void withEverythingConfirmedFinishingDoesNotOpenAnEmptyDialog() {
        // Regression (review 2026-09-28): the tidy path showed a dialog with a blank body.
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            createTemplate("Push A", "Supino reto com barra");
            startWorkout();
            for (int i = 0; i < 3; i++) {
                scrollToRow(i);
                onView(setRow(i, R.id.weight_input)).perform(replaceText("40"));
                onView(setRow(i, R.id.reps_input)).perform(replaceText("10"));
                onView(setRow(i, R.id.button_done)).perform(click());
            }

            onView(withId(R.id.button_finish)).perform(click());

            // Straight to the summary: there was nothing to warn about.
            onView(withText(R.string.summary_title)).inRoot(isDialog()).check(matches(isDisplayed()));
            onView(withText(containsString("Séries feitas: 3"))).inRoot(isDialog())
                    .check(matches(isDisplayed()));
            onView(withText(R.string.summary_close)).inRoot(isDialog()).perform(click());
            onView(withText("Push A")).check(matches(isDisplayed()));
        }
    }

    @Test
    public void aValueStillBeingTypedAndAZeroAreBothRecordedAsTyped() {
        // Regressions (review 2026-09-28): "42," made the check button fail with a generic error,
        // and a typed 0 was silently replaced by last session's suggestion.
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            createTemplate("Push A", "Supino reto com barra");
            startWorkout();

            // The comma is what a pt-BR keyboard offers, and the button does not take the focus.
            onView(setRow(0, R.id.weight_input)).perform(replaceText("42,"));
            onView(setRow(0, R.id.reps_input)).perform(replaceText("10"));
            onView(setRow(0, R.id.button_done)).perform(click());
            onView(withId(R.id.progress)).check(matches(withText("1 de 3 séries")));
            onView(setRow(0, R.id.done_label)).check(matches(isDisplayed()));
            onView(setRow(0, R.id.weight_input)).check(matches(withText("42")));

            // A deliberate zero stays a zero.
            onView(setRow(1, R.id.weight_input)).perform(replaceText("0"));
            onView(setRow(1, R.id.reps_input)).perform(replaceText("12"));
            onView(setRow(1, R.id.button_done)).perform(click());
            onView(setRow(1, R.id.weight_input)).check(matches(withText("0")));
        }
    }

    @Test
    public void whatWasTypedAndNotConfirmedSurvivesARotation() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            createTemplate("Push A", "Supino reto com barra");
            startWorkout();

            onView(setRow(1, R.id.weight_input)).perform(replaceText("37,5"));
            onView(setRow(1, R.id.reps_input)).perform(replaceText("9"));

            scenario.recreate();

            onView(setRow(1, R.id.weight_input)).check(matches(withText("37,5")));
            onView(setRow(1, R.id.reps_input)).check(matches(withText("9")));
            // And the other rows were not filled in with it.
            onView(setRow(0, R.id.weight_input)).check(matches(withText("")));
        }
    }

    @Test
    public void aWorkoutInProgressIsOfferedAgainOnTheHomeScreen() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            createTemplate("Push A", "Supino reto com barra");
            startWorkout();

            // Leaving the screen does not end the workout (ACT-08). The bottom navigation is
            // hidden while training, so the way out is the back gesture.
            pressBack();
            onView(withId(R.id.homeFragment)).perform(click());
            onView(withText(R.string.session_resume_banner_title)).check(matches(isDisplayed()));
            onView(withId(R.id.banner_name)).check(matches(withText("Push A")));

            onView(withId(R.id.banner_action)).perform(click());
            onView(withId(R.id.progress)).check(matches(withText("0 de 3 séries")));
        }
    }

    @Test
    public void theSecondSessionShowsWhatTheFirstOneDid() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            createTemplate("Push A", "Supino reto com barra");

            startWorkout();
            onView(setRow(0, R.id.weight_input)).perform(replaceText("40"));
            onView(setRow(0, R.id.reps_input)).perform(replaceText("10"));
            onView(setRow(0, R.id.button_done)).perform(click());
            onView(withId(R.id.button_finish)).perform(click());
            onView(withText(R.string.finish_confirm)).inRoot(isDialog()).perform(click());
            onView(withText(R.string.summary_close)).inRoot(isDialog()).perform(click());

            startWorkout();

            // "Anterior" is the result of the last finished session, in the same set.
            onView(setRow(0, R.id.previous)).check(matches(withText(containsString("10 reps"))));
            onView(setRow(0, R.id.previous)).check(matches(withText(containsString("40 kg"))));
            // It is a suggestion, not a result: the field is still empty.
            onView(setRow(0, R.id.weight_input)).check(matches(withText("")));
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Creates a template with one exercise through the UI and returns to the list. */
    private void createTemplate(String name, String exerciseName) {
        onView(withId(R.id.templateListFragment)).perform(click());
        onView(withId(R.id.fab_new)).perform(click());
        onView(withId(R.id.name_input)).perform(replaceText(name));
        onView(withId(R.id.button_add)).perform(click());
        // A partial query on purpose: the full name in the field would also match the list item.
        onView(withId(R.id.search_input)).perform(replaceText("supino reto"));
        waitForSearchDebounce();
        onView(withText(exerciseName)).perform(click());
        onView(withText("Adicionar 1 exercício")).perform(click());
        onView(withId(R.id.action_save)).perform(click());
    }

    private void startWorkout() {
        onView(withId(R.id.templateListFragment)).perform(click());
        onView(withId(R.id.button_more)).perform(click());
        onView(withText(R.string.session_start)).perform(click());
    }

    /** Brings a set row into view: the third one is below the fold on a phone-sized screen. */
    private static void scrollToRow(int index) {
        onView(withId(R.id.list)).perform(RecyclerViewActions.scrollToPosition(index + 1));
    }

    /** Targets a view inside the Nth set row of the workout list. */
    private static Matcher<View> setRow(int index, int viewId) {
        return allOf(withId(viewId), isDescendantOfA(
                allOf(withParent(withId(R.id.list)), withParentIndex(index + 1))));
    }

    /** The search field is debounced (250 ms); advance the paused main looper past it. */
    private static void waitForSearchDebounce() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300));
    }
}

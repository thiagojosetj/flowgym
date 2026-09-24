package io.github.thiagojosetj.gym.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.view.View;

import io.github.thiagojosetj.gym.R;

/**
 * Smoke test on a real device (a Galaxy S24+ is the reference phone): the vertical slice that the
 * JVM tests also cover, but with the real SQLite, the real keyboard and the real Material widgets.
 *
 * <p>Run with: {@code ./gradlew :app:connectedDebugAndroidTest}
 *
 * <p>It writes to the debug app's own database (applicationId ends in {@code .debug}), and removes
 * the template it creates at the end, so running it twice is safe.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class DeviceSmokeTest {

    private static final String TEMPLATE_NAME = "Teste automático";
    /** Espresso does not wait for delayed messages (the search box is debounced) or for Room. */
    private static final long TIMEOUT_MS = 10_000;

    @Test
    public void createsATemplateWithOneExerciseAndRemovesIt() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            // The bundled catalog is seeded on first launch.
            onView(withId(R.id.exerciseLibraryFragment)).perform(click());
            await(withText("Supino reto com barra"));

            onView(withId(R.id.templateListFragment)).perform(click());
            onView(withId(R.id.fab_new)).perform(click());
            onView(withId(R.id.name_input)).perform(replaceText(TEMPLATE_NAME), closeSoftKeyboard());
            onView(withId(R.id.button_add)).perform(click());

            onView(withId(R.id.search_input)).perform(replaceText("supino reto"), closeSoftKeyboard());
            await(withText("Supino reto com barra"));
            onView(withText("Supino reto com barra")).perform(click());
            onView(withText("Adicionar 1 exercício")).perform(click());

            // Default plan, then one edited set with a technique.
            await(withText("3 séries × 12 reps"));
            onView(withText("3 séries × 12 reps")).perform(click());
            onView(withId(R.id.rest_input)).inRoot(isDialog()).perform(replaceText("120"), closeSoftKeyboard());
            onView(withId(R.id.button_apply)).inRoot(isDialog()).perform(click());
            await(withText("descanso 2:00 min"));

            onView(withId(R.id.action_save)).perform(click());
            await(withText(TEMPLATE_NAME));
            onView(withText("1 exercício · 3 séries")).check(matches(isDisplayed()));

            // Clean up through the UI, which also exercises the delete confirmation.
            onView(withId(R.id.button_more)).perform(click());
            onView(withText(R.string.template_action_delete)).perform(click());
            onView(withText(R.string.action_delete)).inRoot(isDialog()).perform(click());
            await(withText(R.string.templates_empty_title));
        }
    }

    /** Waits for a view to show up, for the delays Espresso does not know about. */
    private static void await(Matcher<View> matcher) {
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        AssertionError last = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(matcher).check(matches(isDisplayed()));
                return;
            } catch (AssertionError | RuntimeException e) {
                last = e instanceof AssertionError error ? error : new AssertionError(e);
                sleep();
            }
        }
        throw last == null ? new AssertionError("never appeared: " + matcher) : last;
    }

    private static void await(int stringResId) {
        await(withText(stringResId));
    }

    private static void sleep() {
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}

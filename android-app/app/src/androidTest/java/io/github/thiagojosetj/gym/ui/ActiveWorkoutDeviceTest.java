package io.github.thiagojosetj.gym.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertTrue;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.service.notification.StatusBarNotification;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.github.thiagojosetj.gym.R;

/**
 * The parts of a workout that only a real device can prove (docs/ROADMAP.md): the foreground service
 * really being promoted with the health type on API 34+, the ongoing notification, and a session that
 * survives leaving the app.
 *
 * <p>Run with: {@code ./gradlew :app:connectedDebugAndroidTest}
 *
 * <p>It cleans up after itself: the session is finished and the template deleted.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class ActiveWorkoutDeviceTest {

    private static final String TEMPLATE = "Teste treino ativo";
    private static final long TIMEOUT_MS = 10_000;

    @Test
    public void startingAWorkoutPostsTheOngoingNotificationAndSurvivesLeavingTheApp() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            createTemplate();

            onView(withId(R.id.button_more)).perform(click());
            onView(withText(R.string.session_start)).perform(click());
            await(withId(R.id.button_finish));

            // Log one set with the real keyboard.
            onView(withId(R.id.weight_input)).perform(replaceText("40"), closeSoftKeyboard());
            onView(withId(R.id.reps_input)).perform(replaceText("10"), closeSoftKeyboard());
            onView(withId(R.id.button_done)).perform(click());
            await(withId(R.id.rest_container));

            // The service promoted itself: there is an ongoing notification in the shade.
            assertTrue("the workout notification must be posted", hasOngoingNotification());

            // Leaving the screen does not end the workout (ACT-08).
            pressBack();
            onView(withId(R.id.homeFragment)).perform(click());
            await(withText(R.string.session_resume_banner_title));
            onView(withId(R.id.banner_action)).perform(click());
            await(withId(R.id.button_finish));

            // Clean up: finish the session, then delete the template.
            onView(withId(R.id.button_finish)).perform(click());
            onView(withText(R.string.finish_confirm)).inRoot(isDialog()).perform(click());
            onView(withText(R.string.summary_close)).inRoot(isDialog()).perform(click());
            await(withText(TEMPLATE));
            onView(withId(R.id.button_more)).perform(click());
            onView(withText(R.string.template_action_delete)).perform(click());
            onView(withText(R.string.action_delete)).inRoot(isDialog()).perform(click());
        }
    }

    /** Polls the shade: the service posts from another thread, right after the transaction. */
    private boolean hasOngoingNotification() {
        Context context = ApplicationProvider.getApplicationContext();
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            return false;
        }
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            for (StatusBarNotification posted : manager.getActiveNotifications()) {
                Notification notification = posted.getNotification();
                if (notification != null && (notification.flags & Notification.FLAG_ONGOING_EVENT) != 0) {
                    return true;
                }
            }
            sleep();
        }
        return false;
    }

    private void createTemplate() {
        onView(withId(R.id.templateListFragment)).perform(click());
        onView(withId(R.id.fab_new)).perform(click());
        onView(withId(R.id.name_input)).perform(replaceText(TEMPLATE), closeSoftKeyboard());
        onView(withId(R.id.button_add)).perform(click());
        onView(withId(R.id.search_input)).perform(replaceText("supino reto"), closeSoftKeyboard());
        await(withText("Supino reto com barra"));
        onView(withText("Supino reto com barra")).perform(click());
        onView(withText("Adicionar 1 exercício")).perform(click());
        onView(withId(R.id.action_save)).perform(click());
        await(withText(TEMPLATE));
    }

    /** Waits for a view: Espresso does not know about debounces or database round trips. */
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

package io.github.thiagojosetj.gym.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import io.github.thiagojosetj.gym.BuildConfig;
import io.github.thiagojosetj.gym.R;
import io.github.thiagojosetj.gym.testutil.TestGymApplication;

/**
 * Which build is on the phone (Settings).
 *
 * <p>Side-loading the same version name twice is normal while the app is being built, so the name
 * alone cannot answer "is this the build I just installed?". The version CODE can: it is also the
 * only part Android itself compares when deciding whether an APK is an upgrade.
 *
 * <p>The expected values come from {@code BuildConfig} rather than being spelled out, so bumping
 * the version does not break this test - what it protects is that both parts are shown at all.
 */
@RunWith(AndroidJUnit4.class)
@Config(application = TestGymApplication.class, qualifiers = "pt-rBR-w411dp-h891dp")
public class SettingsVersionFlowTest {

    @Test
    public void settingsNamesBothTheVersionAndTheBuildCode() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            openSettings();

            onView(withId(R.id.version)).check(matches(allOf(isDisplayed(),
                    withText(containsString(BuildConfig.VERSION_NAME)),
                    withText(containsString(String.valueOf(BuildConfig.VERSION_CODE))))));
        }
    }

    /** The gear in the app bar: an icon-only action, so it is found by what it announces. */
    private static void openSettings() {
        onView(withContentDescription(R.string.action_settings)).perform(click());
    }
}

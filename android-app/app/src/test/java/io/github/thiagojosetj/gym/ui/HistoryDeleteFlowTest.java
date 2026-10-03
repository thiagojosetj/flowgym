package io.github.thiagojosetj.gym.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.hasSibling;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.assertNotNull;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import io.github.thiagojosetj.gym.AppContainer;
import io.github.thiagojosetj.gym.GymApplication;
import io.github.thiagojosetj.gym.R;
import io.github.thiagojosetj.gym.domain.library.ExerciseFilter;
import io.github.thiagojosetj.gym.domain.template.ExerciseRef;
import io.github.thiagojosetj.gym.domain.library.ExerciseSummary;
import io.github.thiagojosetj.gym.domain.model.RepRange;
import io.github.thiagojosetj.gym.domain.template.TemplateDefaults;
import io.github.thiagojosetj.gym.domain.template.TemplateDraft;
import io.github.thiagojosetj.gym.testutil.LiveDataTestUtil;
import io.github.thiagojosetj.gym.testutil.TestGymApplication;

/**
 * Removing a session from the history (PRODUCT_SPEC HIS-05).
 *
 * <p>What matters here is that it is asked for first and that cancelling really cancels: a
 * workout's numbers are gone for good, and a destructive action one tap away from an ordinary list
 * is the kind of thing people hit by accident.
 */
@RunWith(AndroidJUnit4.class)
@Config(application = TestGymApplication.class, qualifiers = "pt-rBR-w411dp-h891dp")
public class HistoryDeleteFlowTest {

    private static final String SUPINO = "Supino reto com barra";

    @Test
    public void removingASessionAsksFirstAndThenTakesItOutOfTheList() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            performWorkout("Push A");
            openHistory();
            onView(withText("Push A")).check(matches(isDisplayed()));

            openRowMenu("Push A");
            onView(withText(R.string.history_delete_action)).perform(click());

            // The dialog names the session, so nobody deletes the wrong day by muscle memory.
            onView(withText(R.string.history_delete_title)).inRoot(isDialog())
                    .check(matches(isDisplayed()));
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click());

            onView(withText("Push A")).check(doesNotExist());
        }
    }

    @Test
    public void cancellingLeavesTheSessionExactlyWhereItWas() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            performWorkout("Push A");
            openHistory();

            openRowMenu("Push A");
            onView(withText(R.string.history_delete_action)).perform(click());
            onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click());

            onView(withText("Push A")).check(matches(isDisplayed()));
        }
    }

    // ------------------------------------------------------------------ helpers

    private static void openRowMenu(String sessionName) {
        // The button sits beside the block of text, not inside it.
        onView(allOf(withId(R.id.button_more),
                hasSibling(hasDescendant(withText(sessionName))))).perform(click());
    }

    private static void openHistory() {
        onView(withId(R.id.historyListFragment)).perform(click());
    }

    /**
     * Puts one finished session in the database before the screen opens.
     *
     * <p>Through the repositories rather than the UI: the point of this test is the removal, and
     * driving a whole workout through the screens to reach it would make it fail for reasons that
     * have nothing to do with deleting anything.
     */
    private static void performWorkout(String name) {
        AppContainer app = ((GymApplication) ApplicationProvider.getApplicationContext())
                .container();

        TemplateDraft draft = TemplateDraft.newTemplate(app.ids);
        draft.rename(name);
        draft.addExercises(refsOf(app, SUPINO), new TemplateDefaults(3, RepRange.exactly(12), 30, 90));
        AtomicReference<String> templateId = new AtomicReference<>();
        app.templates.save(draft, templateId::set, HistoryDeleteFlowTest::rethrow);
        assertNotNull("o treino devia ter sido criado", templateId.get());

        AtomicReference<String> sessionId = new AtomicReference<>();
        app.activeSessions.startFromTemplate(templateId.get(), sessionId::set,
                HistoryDeleteFlowTest::rethrow);
        assertNotNull("a sessao devia ter comecado", sessionId.get());

        app.activeSessions.finish(sessionId.get(), summary -> { }, HistoryDeleteFlowTest::rethrow);
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
                        HistoryDeleteFlowTest::rethrow);
                return refs.get();
            }
        }
        throw new AssertionError("Sem exercicio " + exerciseName);
    }

    private static void rethrow(Throwable error) {
        throw new AssertionError(error);
    }
}

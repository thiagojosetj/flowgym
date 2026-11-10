package io.github.thiagojosetj.gym.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.not;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertNotNull;

import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import androidx.test.espresso.matcher.BoundedMatcher;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
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
import io.github.thiagojosetj.gym.domain.library.ExerciseSummary;
import io.github.thiagojosetj.gym.domain.model.RepRange;
import io.github.thiagojosetj.gym.domain.template.ExerciseRef;
import io.github.thiagojosetj.gym.domain.template.TemplateDefaults;
import io.github.thiagojosetj.gym.domain.template.TemplateDraft;
import io.github.thiagojosetj.gym.testutil.LiveDataTestUtil;
import io.github.thiagojosetj.gym.testutil.TestGymApplication;

/**
 * The calendar above the history (PRODUCT_SPEC HIS-02): which days were trained, and the list
 * narrowing to the one that was tapped.
 *
 * <p>The container's clock is fixed, so the second session's day is moved with one UPDATE rather
 * than by waiting a day. The column is what the calendar reads, and writing it directly is the
 * only way this test can have two different days to tell apart.
 *
 * <p>Dates are spelled out instead of being formatted by the same code the screen uses. A test
 * that builds its expectation with the production formatter agrees with any bug that formatter
 * has.
 */
@RunWith(AndroidJUnit4.class)
@Config(application = TestGymApplication.class, qualifiers = "pt-rBR-w411dp-h891dp")
public class HistoryCalendarFlowTest {

    private static final String SUPINO = "Supino reto com barra";
    /** The container's fixed clock, in its zone: every session starts on this day. */
    private static final String TRAINED_DAY = "21 de setembro · treinou";
    private static final String MOVED_DAY = "18 de setembro · treinou";
    private static final String FREE_DAY = "19 de setembro · sem treino";

    @Test
    public void theDaysThatWereTrainedAreMarkedOnTheMonth() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            performWorkout("Push A");
            openHistory();

            onView(withText("setembro 2026")).check(matches(isDisplayed()));
            onView(withContentDescription(TRAINED_DAY)).check(matches(isDisplayed()));
            onView(withContentDescription(FREE_DAY)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void tappingADayLeavesOnlyThatDaysSessionsInTheList() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            performWorkout("Push A");
            moveToAnotherDay(performWorkout("Pull B"));
            openHistory();
            onView(withText("Push A")).check(matches(isDisplayed()));
            onView(withText("Pull B")).check(matches(isDisplayed()));

            onView(withContentDescription(MOVED_DAY)).perform(click());

            onView(withText("Pull B")).check(matches(isDisplayed()));
            onView(withText("Push A")).check(doesNotExist());
            onView(withText("Mostrando 18 de setembro")).check(matches(isDisplayed()));
        }
    }

    @Test
    public void tappingTheSameDayAgainBringsTheWholeHistoryBack() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            performWorkout("Push A");
            moveToAnotherDay(performWorkout("Pull B"));
            openHistory();

            onView(withContentDescription(MOVED_DAY)).perform(click());
            onView(withText("Push A")).check(doesNotExist());
            onView(withContentDescription(MOVED_DAY)).perform(click());

            onView(withText("Push A")).check(matches(isDisplayed()));
            onView(withText("Pull B")).check(matches(isDisplayed()));
        }
    }

    @Test
    public void seeingEverythingAgainIsOneTapAwayFromAFilteredList() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            performWorkout("Push A");
            moveToAnotherDay(performWorkout("Pull B"));
            openHistory();

            onView(withContentDescription(MOVED_DAY)).perform(click());
            onView(withText(R.string.history_show_all_days)).perform(click());

            onView(withText("Push A")).check(matches(isDisplayed()));
            onView(withText("Pull B")).check(matches(isDisplayed()));
            onView(withId(R.id.filter_bar)).check(matches(not(isDisplayed())));
        }
    }

    @Test
    public void aDayWithoutTrainingSaysSoInsteadOfLookingBroken() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            performWorkout("Push A");
            openHistory();

            onView(withContentDescription(FREE_DAY)).perform(click());

            // An empty list and no explanation reads as a bug; the history is still there, and the
            // way back out is on screen next to the message.
            onView(withText(R.string.history_empty_day)).check(matches(isDisplayed()));
            onView(withText("Push A")).check(doesNotExist());
            onView(withText(R.string.history_show_all_days)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void withNothingEverFinishedThereIsNoMonthToLookAt() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            openHistory();

            onView(withText(R.string.history_empty_title)).check(matches(isDisplayed()));
            // An empty grid of a month nobody trained in is decoration, not information.
            onView(withId(R.id.calendar)).check(doesNotExist());
        }
    }

    @Test
    public void aSessionRecordedAheadOfTheClockIsStillReachable() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            // A phone whose clock ran ahead writes a day in the future. Stopping the calendar at
            // the current month would hide that session rather than show where it was recorded.
            moveDayOf(performWorkout("Push A"), "2026-11-02");
            openHistory();
            onView(withText("setembro 2026")).check(matches(isDisplayed()));

            onView(withId(R.id.next_month)).perform(click());
            onView(withId(R.id.next_month)).perform(click());

            onView(withText("novembro 2026")).check(matches(isDisplayed()));
            onView(withContentDescription("2 de novembro · treinou"))
                    .check(matches(isDisplayed()));
        }
    }

    @Test
    public void theMonthsStopWhereTheTrainingDoes() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            moveDayOf(performWorkout("Push A"), "2026-07-05");
            openHistory();

            // Back to July, where the oldest session is, and no further: a month with nothing in
            // it is not a place to be, and an arrow that answers with another empty grid teaches
            // nothing.
            onView(withId(R.id.previous_month)).perform(click());
            onView(withId(R.id.previous_month)).perform(click());
            onView(withText("julho 2026")).check(matches(isDisplayed()));
            onView(withContentDescription("5 de julho · treinou")).check(matches(isDisplayed()));
            onView(withId(R.id.previous_month)).check(matches(not(isEnabled())));

            onView(withId(R.id.previous_month)).perform(click());
            onView(withText("julho 2026")).check(matches(isDisplayed()));
        }
    }

    @Test
    public void aDayFilterDoesNotSurviveLeavingItsMonth() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            performWorkout("Push A");
            moveDayOf(performWorkout("Pull B"), "2026-07-05");
            openHistory();

            onView(withContentDescription(TRAINED_DAY)).perform(click());
            onView(withText("Mostrando 21 de setembro")).check(matches(isDisplayed()));

            onView(withId(R.id.previous_month)).perform(click());

            // The rows below would otherwise still be showing a day the grid above no longer has.
            onView(withId(R.id.filter_bar)).check(matches(not(isDisplayed())));
            onView(withText("Push A")).check(matches(isDisplayed()));
            onView(withText("Pull B")).check(matches(isDisplayed()));
        }
    }

    @Test
    public void aSquareReusedByTheNextMonthDoesNotKeepTheOldMonthsMark() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            // October 2026 fills all 35 squares; November starts on a Sunday and leaves the last
            // five empty. The square that held the 31st becomes padding, and the squares are
            // reused rather than rebuilt - so a mark left behind would show as a highlighted
            // blank: the calendar claiming a day that is not even a day.
            moveDayOf(performWorkout("Push A"), "2026-10-31");
            // A later session, so November is a month the arrows are allowed to reach at all.
            moveDayOf(performWorkout("Pull B"), "2026-11-15");
            openHistory();
            onView(withId(R.id.next_month)).perform(click());
            onView(withContentDescription("31 de outubro · treinou")).check(matches(isDisplayed()));

            onView(withId(R.id.next_month)).perform(click());

            onView(withText("novembro 2026")).check(matches(isDisplayed()));
            onView(withContentDescription("31 de outubro · treinou")).check(doesNotExist());
            onView(allOf(withText(""), isActivated())).check(doesNotExist());
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Espresso has isSelected() but no isActivated(), and the trained mark is the activated one. */
    private static Matcher<View> isActivated() {
        return new BoundedMatcher<View, View>(View.class) {
            @Override
            public void describeTo(Description description) {
                description.appendText("is activated");
            }

            @Override
            protected boolean matchesSafely(View view) {
                return view.isActivated();
            }
        };
    }

    private static void openHistory() {
        onView(withId(R.id.historyListFragment)).perform(click());
    }

    private static void moveToAnotherDay(String sessionId) {
        moveDayOf(sessionId, "2026-09-18");
    }

    /** Moves a finished session's day, the only way to get a second one out of a fixed clock. */
    private static void moveDayOf(String sessionId, String isoDay) {
        container().database.getOpenHelper().getWritableDatabase().execSQL(
                "UPDATE workout_session SET local_date = '" + isoDay + "' WHERE id = '"
                        + sessionId + "'");
    }

    /**
     * Puts one finished session in the database before the screen opens.
     *
     * <p>Through the repositories rather than the UI: the point of this test is the calendar, and
     * driving a whole workout through the screens to reach it would make it fail for reasons that
     * have nothing to do with which days are marked.
     */
    private static String performWorkout(String name) {
        AppContainer app = container();

        TemplateDraft draft = TemplateDraft.newTemplate(app.ids);
        draft.rename(name);
        draft.addExercises(refsOf(app, SUPINO), new TemplateDefaults(3, RepRange.exactly(12), 30, 90));
        AtomicReference<String> templateId = new AtomicReference<>();
        app.templates.save(draft, templateId::set, HistoryCalendarFlowTest::rethrow);
        assertNotNull("o treino devia ter sido criado", templateId.get());

        AtomicReference<String> sessionId = new AtomicReference<>();
        app.activeSessions.startFromTemplate(templateId.get(), sessionId::set,
                HistoryCalendarFlowTest::rethrow);
        assertNotNull("a sessao devia ter comecado", sessionId.get());

        app.activeSessions.finish(sessionId.get(), summary -> { }, HistoryCalendarFlowTest::rethrow);
        return sessionId.get();
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
                        HistoryCalendarFlowTest::rethrow);
                return refs.get();
            }
        }
        throw new AssertionError("Sem exercicio " + exerciseName);
    }

    private static void rethrow(Throwable error) {
        throw new AssertionError(error);
    }
}

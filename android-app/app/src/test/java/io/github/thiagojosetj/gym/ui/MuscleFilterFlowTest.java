package io.github.thiagojosetj.gym.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isSelected;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.not;

import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.contrib.RecyclerViewActions;
import androidx.test.espresso.matcher.ViewMatchers.Visibility;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import io.github.thiagojosetj.gym.R;
import io.github.thiagojosetj.gym.testutil.TestGymApplication;

/**
 * The parts of a muscle group, chosen by picture.
 *
 * <p>What these pin is that the row exists for the right group, that picking a part actually
 * narrows the filter, and that choosing a different group rebuilds it. The drawing itself is not
 * asserted here - MuscleArtTest already guarantees every muscle has one, and an Espresso test
 * cannot tell a correct silhouette from a wrong one anyway.
 */
@RunWith(AndroidJUnit4.class)
@Config(application = TestGymApplication.class, qualifiers = "pt-rBR-w411dp-h891dp")
public class MuscleFilterFlowTest {

    private static final String CHEST = "Peito";
    private static final String UPPER_CHEST = "Peitoral superior (porção clavicular)";
    private static final String BACK = "Costas";
    private static final String LATS = "Latíssimo do dorso";

    @Test
    public void thePartsOfAGroupOnlyAppearOnceAGroupIsChosen() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            openLibrary();

            // "Todos" is selected, so there is no group whose parts could be shown.
            onView(withId(R.id.subgroup_list))
                    .check(matches(withEffectiveVisibility(Visibility.GONE)));

            onView(withText(CHEST)).perform(click());

            onView(withId(R.id.subgroup_list)).check(matches(allOf(isDisplayed(),
                    hasDescendant(withText(UPPER_CHEST)))));
        }
    }

    @Test
    public void theWholeGroupIsAnOptionOfItsOwnAndComesFirst() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            openLibrary();
            onView(withText(CHEST)).perform(click());

            // It is a picture like the others, not an unlabelled way out of the row.
            onView(withText(R.string.library_filter_whole_group)).check(matches(isDisplayed()));
            onView(allOf(withId(R.id.muscle_filter_item),
                    hasDescendant(withText(R.string.library_filter_whole_group))))
                    .check(matches(isSelected()));
        }
    }

    @Test
    public void choosingAPartSelectsItAndLeavesTheWholeGroupBehind() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            openLibrary();
            onView(withText(CHEST)).perform(click());

            onView(withText(UPPER_CHEST)).perform(click());

            onView(allOf(withId(R.id.muscle_filter_item), hasDescendant(withText(UPPER_CHEST))))
                    .check(matches(isSelected()));
            onView(allOf(withId(R.id.muscle_filter_item),
                    hasDescendant(withText(R.string.library_filter_whole_group))))
                    .check(matches(not(isSelected())));
        }
    }

    @Test
    public void changingGroupReplacesThePartsInsteadOfKeepingTheOldOnes() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            openLibrary();
            onView(withText(CHEST)).perform(click());
            onView(withText(UPPER_CHEST)).perform(click());

            onView(withText(BACK)).perform(click());

            onView(withId(R.id.subgroup_list))
                    .perform(RecyclerViewActions.scrollTo(hasDescendant(withText(LATS))));
            onView(withText(LATS)).check(matches(isDisplayed()));
            onView(withText(UPPER_CHEST)).check(doesNotExist());
        }
    }

    @Test
    public void goingBackToAllGroupsHidesTheParts() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            openLibrary();
            onView(withText(CHEST)).perform(click());
            onView(withId(R.id.subgroup_list)).check(matches(isDisplayed()));

            onView(withText(R.string.library_filter_all_groups)).perform(click());

            onView(withId(R.id.subgroup_list))
                    .check(matches(withEffectiveVisibility(Visibility.GONE)));
        }
    }

    private static void openLibrary() {
        onView(withId(R.id.exerciseLibraryFragment)).perform(click());
    }
}

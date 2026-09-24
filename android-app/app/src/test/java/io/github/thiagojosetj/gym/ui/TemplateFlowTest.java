package io.github.thiagojosetj.gym.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withParent;
import static androidx.test.espresso.matcher.ViewMatchers.withParentIndex;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.hasSibling;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.robolectric.Shadows.shadowOf;

import android.os.Looper;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
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
 * The first vertical slice through the real UI (PRODUCT_SPEC TPL-01/02/03, LIB-02):
 * library → pick exercise → create template → save to Room → reopen it.
 * Runs on the JVM with Robolectric; the same scenario can later run on a device (androidTest).
 */
@RunWith(AndroidJUnit4.class)
@Config(application = TestGymApplication.class, qualifiers = "pt-rBR-w411dp-h891dp")
public class TemplateFlowTest {

    @Test
    public void createTemplateFromLibraryEditPlanSaveAndReopen() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            // "Treinos" tab → new template
            onView(withId(R.id.templateListFragment)).perform(click());
            onView(withText(R.string.templates_empty_title)).check(matches(isDisplayed()));
            onView(withId(R.id.fab_new)).perform(click());

            // Editor: name + open the library in select mode
            onView(withId(R.id.name_input)).perform(replaceText("Push A"));
            onView(withId(R.id.button_add)).perform(click());

            // Picker: accent-insensitive search, select, confirm
            onView(withId(R.id.search_input)).perform(replaceText("SUPINO RETO COM BARRA"));
            waitForSearchDebounce();
            onView(withText("Supino reto com barra")).perform(click());
            onView(withText("Adicionar 1 exercício")).perform(click());

            // Back in the editor: added with the 3 × 12 default
            onView(withText("Supino reto com barra")).check(matches(isDisplayed()));
            onView(withText("3 séries × 12 reps")).check(matches(isDisplayed()));

            // Edit the plan: 4 × 8–10 × 42,5 kg, 120 s rest (the sheet is a dialog window)
            onView(withText("3 séries × 12 reps")).perform(click());
            onView(withId(R.id.button_add_set)).inRoot(isDialog()).perform(click()); // 3 → 4 sets
            onView(setRow(0, R.id.reps_input)).inRoot(isDialog()).perform(replaceText("8-10"));
            onView(setRow(0, R.id.weight_input)).inRoot(isDialog()).perform(replaceText("42,5"));
            onView(withId(R.id.button_copy_first)).inRoot(isDialog()).perform(click());
            onView(withId(R.id.rest_input)).inRoot(isDialog()).perform(replaceText("120"));
            onView(withId(R.id.button_apply)).inRoot(isDialog()).perform(click());
            onView(withText("4 séries × 8–10 reps · 42,5 kg")).check(matches(isDisplayed()));
            onView(withText("descanso 2:00 min")).check(matches(isDisplayed()));

            // Save → back to the list, which is observed live from Room
            onView(withId(R.id.action_save)).perform(click());
            onView(withText("Push A")).check(matches(isDisplayed()));
            onView(withText("1 exercício · 4 séries")).check(matches(isDisplayed()));
            onView(withText(R.string.templates_empty_title)).check(matches(withEffectiveVisibility(Visibility.GONE)));

            // Reopen: everything comes back from the database
            onView(withText("Push A")).perform(click());
            onView(withId(R.id.name_input)).check(matches(withText("Push A")));
            onView(withText("4 séries × 8–10 reps · 42,5 kg")).check(matches(isDisplayed()));
        }
    }

    @Test
    public void savingWithoutNameShowsTheErrorAndKeepsTheEditorOpen() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.templateListFragment)).perform(click());
            onView(withId(R.id.fab_new)).perform(click());

            onView(withId(R.id.action_save)).perform(click());

            onView(withText(R.string.editor_error_name_blank)).check(matches(isDisplayed()));
            onView(withId(R.id.name_input)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void emptySearchResultSaysNoneInsteadOfZeroSingular() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.exerciseLibraryFragment)).perform(click());
            onView(withId(R.id.search_input)).perform(replaceText("zzzzz"));
            waitForSearchDebounce();

            // Portuguese plural rules would render "0 exercício" (singular) for the count.
            onView(withText(R.string.library_result_none)).check(matches(isDisplayed()));
            onView(withText(R.string.library_empty)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void duplicatingKeepsTheOriginalNamePlusTheSuffix() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            createSimpleTemplate("Push A");

            onView(withId(R.id.button_more)).perform(click());
            onView(withText(R.string.template_action_duplicate)).perform(click());

            // Regression (review 2026-09-22): the copy was named "Push APush A (cópia)".
            onView(withText("Push A (cópia)")).check(matches(isDisplayed()));
            onView(withText("Push A")).check(matches(isDisplayed()));
        }
    }

    @Test
    public void techniquePerSetIsPickedWithItsExplanationAndPersisted() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.templateListFragment)).perform(click());
            onView(withId(R.id.fab_new)).perform(click());
            onView(withId(R.id.name_input)).perform(replaceText("Treino de peito"));
            onView(withId(R.id.button_add)).perform(click());
            onView(withId(R.id.search_input)).perform(replaceText("supino reto com barra"));
            waitForSearchDebounce();
            onView(withText("Supino reto com barra")).perform(click());
            onView(withText("Adicionar 1 exercício")).perform(click());

            // Open the plan sheet and the technique picker of the first set.
            onView(withText("3 séries × 12 reps")).perform(click());
            onView(setRow(0, R.id.button_technique)).inRoot(isDialog()).perform(click());

            // The ⓘ explains the method (the picker is the focused window now).
            onView(allOf(withId(R.id.button_info), hasSibling(hasDescendant(withText("Aquecimento")))))
                    .inRoot(isDialog()).perform(click());
            onView(withText(containsString("Série leve"))).inRoot(isDialog()).check(matches(isDisplayed()));
            onView(withText(R.string.technique_understood)).inRoot(isDialog()).perform(click());

            // Pick it: the row shows the badge.
            onView(allOf(withId(R.id.name), withText("Aquecimento"))).inRoot(isDialog()).perform(click());
            onView(setRow(0, R.id.button_technique)).inRoot(isDialog()).check(matches(withText("AQ")));
            onView(withId(R.id.button_apply)).inRoot(isDialog()).perform(click());
            onView(withId(R.id.badges)).check(matches(withText("AQ")));

            // Saved and reopened, the warm-up is still there.
            onView(withId(R.id.action_save)).perform(click());
            onView(withText("Treino de peito")).perform(click());
            onView(withId(R.id.badges)).check(matches(withText("AQ")));
        }
    }

    /** Creates a template with one exercise and returns to the list. */
    private void createSimpleTemplate(String name) {
        onView(withId(R.id.templateListFragment)).perform(click());
        onView(withId(R.id.fab_new)).perform(click());
        onView(withId(R.id.name_input)).perform(replaceText(name));
        onView(withId(R.id.button_add)).perform(click());
        onView(withId(R.id.search_input)).perform(replaceText("supino reto com barra"));
        waitForSearchDebounce();
        onView(withText("Supino reto com barra")).perform(click());
        onView(withText("Adicionar 1 exercício")).perform(click());
        onView(withId(R.id.action_save)).perform(click());
    }

    @Test
    public void dumbbellLoadIsPlannedPerDumbbell() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.templateListFragment)).perform(click());
            onView(withId(R.id.fab_new)).perform(click());
            onView(withId(R.id.button_add)).perform(click());
            onView(withId(R.id.search_input)).perform(replaceText("martelo"));
            waitForSearchDebounce();
            onView(withText("Rosca martelo")).perform(click());
            onView(withText("Adicionar 1 exercício")).perform(click());

            onView(withText("3 séries × 12 reps")).perform(click());
            onView(setRow(0, R.id.weight_input)).inRoot(isDialog()).perform(replaceText("12"));
            onView(withId(R.id.button_copy_first)).inRoot(isDialog()).perform(click());
            onView(withId(R.id.button_apply)).inRoot(isDialog()).perform(click());

            onView(withText("3 séries × 12 reps · 12 kg por halter")).check(matches(isDisplayed()));
        }
    }

    /** Targets a view inside the Nth set row of the plan sheet (all rows share the same ids). */
    private static Matcher<View> setRow(int index, int viewId) {
        return allOf(withId(viewId), isDescendantOfA(
                allOf(withParent(withId(R.id.sets_container)), withParentIndex(index))));
    }

    /** The search field is debounced (250 ms); advance the paused main looper past it. */
    private static void waitForSearchDebounce() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300));
    }
}

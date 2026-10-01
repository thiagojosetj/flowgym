package io.github.thiagojosetj.gym.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isChecked;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.isNotChecked;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withParent;
import static androidx.test.espresso.matcher.ViewMatchers.withParentIndex;
import static androidx.test.espresso.matcher.ViewMatchers.hasSibling;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
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
import java.util.Locale;

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

    private static final String SUPINO = "Supino reto com barra";
    private static final String CRUCIFIXO = "Crucifixo com halteres";
    private static final String TRICEPS = "Tríceps na polia";

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
            onView(allOf(withId(R.id.button_info), hasSibling(withText("Aquecimento"))))
                    .inRoot(isDialog()).perform(click());
            onView(withText(containsString("Série leve"))).inRoot(isDialog()).check(matches(isDisplayed()));
            onView(withText(R.string.technique_understood)).inRoot(isDialog()).perform(click());

            // Pick it: the row shows the badge.
            onView(allOf(withId(R.id.name), withText("Aquecimento"))).inRoot(isDialog()).perform(click());
            onView(setRow(0, R.id.button_technique)).inRoot(isDialog()).check(matches(withText("AQ")));
            onView(withId(R.id.button_apply)).inRoot(isDialog()).perform(click());
            onView(withId(R.id.badges)).check(matches(withText("AQ")));
            // TalkBack must hear the method, not the abbreviation.
            onView(withId(R.id.badges)).check(matches(withContentDescription("Aquecimento")));

            // Saved and reopened, the warm-up is still there.
            onView(withId(R.id.action_save)).perform(click());
            onView(withText("Treino de peito")).perform(click());
            onView(withId(R.id.badges)).check(matches(withText("AQ")));
        }
    }

    @Test
    public void rotatingKeepsEachSetRowWithItsOwnValues() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.templateListFragment)).perform(click());
            onView(withId(R.id.fab_new)).perform(click());
            onView(withId(R.id.button_add)).perform(click());
            onView(withId(R.id.search_input)).perform(replaceText("supino reto com barra"));
            waitForSearchDebounce();
            onView(withText("Supino reto com barra")).perform(click());
            onView(withText("Adicionar 1 exercício")).perform(click());

            // Three sets with three different loads, nothing applied yet.
            onView(withText("3 séries × 12 reps")).perform(click());
            onView(setRow(0, R.id.weight_input)).inRoot(isDialog()).perform(replaceText("40"));
            onView(setRow(1, R.id.weight_input)).inRoot(isDialog()).perform(replaceText("45"));
            onView(setRow(2, R.id.weight_input)).inRoot(isDialog()).perform(replaceText("50"));

            scenario.recreate();

            // Rows share the same view ids, so the automatic view-state restore used to give every
            // row the last row's text ("50" three times) on top of the values restored by hand.
            onView(setRow(0, R.id.weight_input)).inRoot(isDialog()).check(matches(withText("40")));
            onView(setRow(1, R.id.weight_input)).inRoot(isDialog()).check(matches(withText("45")));
            onView(setRow(2, R.id.weight_input)).inRoot(isDialog()).check(matches(withText("50")));
            onView(withId(R.id.rest_input)).inRoot(isDialog()).check(matches(withText("90")));
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

    // ------------------------------------------------------------------ groups (PRODUCT_SPEC 6.3)

    @Test
    public void groupingTwoExercisesLabelsThemSetsTheRoundRestAndSavingKeepsTheGroup() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            createTemplate("Push A", SUPINO, CRUCIFIXO, TRICEPS);
            onView(withText("Push A")).perform(click());
            onView(withText(SUPINO)).check(matches(isDisplayed()));

            openGroupSheetOf(SUPINO);
            // The rest starts at the app's default, right there to read and to change.
            onView(withId(R.id.group_rest_input)).inRoot(isDialog()).check(matches(withText("90")));
            onView(withId(R.id.group_rest_input)).inRoot(isDialog()).perform(replaceText("120"));
            onView(withText(CRUCIFIXO)).inRoot(isDialog()).perform(click());
            onView(withId(R.id.button_group_confirm)).inRoot(isDialog())
                    .perform(scrollTo(), click());

            // Both say A1 and A2 before the name, and the label was never typed by anybody.
            onView(withText("A1 " + SUPINO)).check(matches(isDisplayed()));
            onView(withText("A2 " + CRUCIFIXO)).check(matches(isDisplayed()));
            onView(withText(R.string.editor_grouped)).check(matches(isDisplayed()));
            // The card shows the rest that applies: the group's, not the exercise's own 1:30.
            onView(restLineOf("A1 " + SUPINO))
                    .check(matches(withText("descanso 2:00 min depois da rodada")));
            onView(restLineOf("A2 " + CRUCIFIXO))
                    .check(matches(withText("descanso 2:00 min depois da rodada")));
            // The third one stands alone: no label, and still resting by itself.
            onView(withId(R.id.list)).perform(RecyclerViewActions.scrollToPosition(3));
            onView(withText(TRICEPS)).check(matches(isDisplayed()));
            onView(restLineOf(TRICEPS)).check(matches(withText("descanso 1:30 min")));

            // "Salvar" is another button and does not undo the group.
            onView(withId(R.id.action_save)).perform(click());
            onView(withText("Push A")).perform(click());
            onView(withText("A1 " + SUPINO)).check(matches(isDisplayed()));
            onView(withText("A2 " + CRUCIFIXO)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void aGroupMayHaveNoRestAtAllAndTheCardsSaySo() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            createTemplate("Push A", SUPINO, CRUCIFIXO);
            onView(withText("Push A")).perform(click());
            openGroupSheetOf(SUPINO);
            onView(withText(CRUCIFIXO)).inRoot(isDialog()).perform(click());
            onView(withId(R.id.group_rest_input)).inRoot(isDialog()).perform(replaceText("0"));
            onView(withId(R.id.button_group_confirm)).inRoot(isDialog())
                    .perform(scrollTo(), click());

            // Zero is a rest of its own kind, not an empty field that fell back to the default.
            onView(restLineOf("A1 " + SUPINO)).check(matches(withText("sem descanso automático")));
            onView(restLineOf("A2 " + CRUCIFIXO))
                    .check(matches(withText("sem descanso automático")));
        }
    }

    @Test
    public void ungroupingTakesTheLabelsOffAndKeepsBothExercises() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            createTemplate("Push A", SUPINO, CRUCIFIXO);
            onView(withText("Push A")).perform(click());
            openGroupSheetOf(SUPINO);
            onView(withText(CRUCIFIXO)).inRoot(isDialog()).perform(click());
            onView(withId(R.id.button_group_confirm)).inRoot(isDialog())
                    .perform(scrollTo(), click());
            onView(withText("A1 " + SUPINO)).check(matches(isDisplayed()));

            // A grouped exercise offers the way out, and no longer the way in.
            onView(withContentDescription("Opções de " + SUPINO)).perform(click());
            onView(withText(R.string.editor_group_with)).check(doesNotExist());
            onView(withText(R.string.editor_ungroup)).perform(click());

            onView(withText(R.string.editor_ungrouped)).check(matches(isDisplayed()));
            onView(withText(SUPINO)).check(matches(isDisplayed()));
            onView(withText(CRUCIFIXO)).check(matches(isDisplayed()));
            onView(withText("A1 " + SUPINO)).check(doesNotExist());
            onView(withText("A2 " + CRUCIFIXO)).check(doesNotExist());
            // Both are free again, so the menu is back to offering to group.
            onView(withContentDescription("Opções de " + SUPINO)).perform(click());
            onView(withText(R.string.editor_ungroup)).check(doesNotExist());
            onView(withText(R.string.editor_group_with)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void aGroupNeedsAnotherExerciseAndARestInRangeOrNothingIsMade() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            createTemplate("Push A", SUPINO, CRUCIFIXO);
            onView(withText("Push A")).perform(click());
            openGroupSheetOf(SUPINO);

            // Nobody else ticked: one exercise is no group, and the sheet says so and stays.
            onView(withId(R.id.button_group_confirm)).inRoot(isDialog())
                    .perform(scrollTo(), click());
            onView(withId(R.id.group_error)).inRoot(isDialog()).check(matches(allOf(
                    isDisplayed(), withText("Um grupo precisa de pelo menos 2 exercícios"))));

            // Ticking another one answers it; then a rest nobody could wait for is refused too.
            onView(withText(CRUCIFIXO)).inRoot(isDialog()).perform(click());
            onView(withId(R.id.group_error)).inRoot(isDialog())
                    .check(matches(withEffectiveVisibility(Visibility.GONE)));
            onView(withId(R.id.group_rest_input)).inRoot(isDialog())
                    .perform(replaceText("3601"));
            onView(withId(R.id.button_group_confirm)).inRoot(isDialog())
                    .perform(scrollTo(), click());
            onView(withText("Entre 0 e 3600 s")).inRoot(isDialog()).check(matches(isDisplayed()));

            // Cancelled: both are exactly as they were.
            onView(withId(R.id.button_group_cancel)).inRoot(isDialog())
                    .perform(scrollTo(), click());
            onView(withText(SUPINO)).check(matches(isDisplayed()));
            onView(withText(CRUCIFIXO)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void anExerciseJustAddedHasToBeSavedBeforeItCanBeGrouped() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.templateListFragment)).perform(click());
            onView(withId(R.id.fab_new)).perform(click());
            addExercises(SUPINO, CRUCIFIXO);

            // A group is made in the database at once, where these two are not yet.
            onView(withContentDescription("Opções de " + SUPINO)).perform(click());
            onView(withText(R.string.editor_group_with)).perform(click());

            onView(withText(R.string.editor_group_error_unsaved)).check(matches(isDisplayed()));
            onView(withId(R.id.button_group_confirm)).check(doesNotExist());
        }
    }

    @Test
    public void aTemplateWithOneExerciseHasNobodyToGroupItWith() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            createTemplate("Push A", SUPINO);
            onView(withText("Push A")).perform(click());

            onView(withContentDescription("Opções de " + SUPINO)).perform(click());
            onView(withText(R.string.editor_group_with)).perform(click());

            // Told, not shown a sheet with nothing to choose.
            onView(withText("Um grupo precisa de pelo menos 2 exercícios"))
                    .check(matches(isDisplayed()));
            onView(withId(R.id.button_group_confirm)).check(doesNotExist());
        }
    }

    @Test
    public void anExerciseAddedNowIsListedButCannotBeTickedUntilTheTemplateIsSaved() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            createTemplate("Push A", SUPINO, CRUCIFIXO);
            onView(withText("Push A")).perform(click());
            // The footer, which holds the button that adds exercises, comes after the two cards.
            onView(withId(R.id.list)).perform(RecyclerViewActions.scrollToPosition(3));
            addExercises(TRICEPS);
            onView(withId(R.id.list)).perform(RecyclerViewActions.scrollToPosition(1));

            openGroupSheetOf(SUPINO);

            // It is there, says why it cannot be picked yet, and the saved one still can be.
            onView(withText("Tríceps na polia (salve o treino antes)")).inRoot(isDialog())
                    .check(matches(not(isEnabled())));
            onView(withText(CRUCIFIXO)).inRoot(isDialog()).perform(click());
            onView(withId(R.id.button_group_confirm)).inRoot(isDialog())
                    .perform(scrollTo(), click());
            onView(withText("A1 " + SUPINO)).check(matches(isDisplayed()));
            onView(withText("A2 " + CRUCIFIXO)).check(matches(isDisplayed()));
            onView(withId(R.id.list)).perform(RecyclerViewActions.scrollToPosition(3));
            onView(withText(TRICEPS)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void aGroupedExerciseSaysThatTheRestOfTheGroupIsTheOneThatApplies() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            createTemplate("Push A", SUPINO, CRUCIFIXO);
            onView(withText("Push A")).perform(click());
            openGroupSheetOf(SUPINO);
            onView(withText(CRUCIFIXO)).inRoot(isDialog()).perform(click());
            onView(withId(R.id.button_group_confirm)).inRoot(isDialog())
                    .perform(scrollTo(), click());

            // Its own rest field is still there, but it is not the one a session would use.
            onView(withText("A1 " + SUPINO)).perform(click());
            onView(withText(R.string.plan_sheet_rest_grouped)).inRoot(isDialog())
                    .perform(scrollTo()).check(matches(isDisplayed()));
            onView(withId(R.id.button_cancel)).inRoot(isDialog()).perform(scrollTo(), click());
        }
    }

    @Test
    public void rotatingTheGroupSheetKeepsWhatWasTickedAndTheRest() {
        try (ActivityScenario<MainActivity> screen = ActivityScenario.launch(MainActivity.class)) {
            createTemplate("Push A", SUPINO, CRUCIFIXO, TRICEPS);
            onView(withText("Push A")).perform(click());
            openGroupSheetOf(SUPINO);
            onView(withText(TRICEPS)).inRoot(isDialog()).perform(click());
            onView(withId(R.id.group_rest_input)).inRoot(isDialog()).perform(replaceText("45"));

            screen.recreate();

            // The rows share a view id, so a restore by id would have given them one answer.
            onView(withText(CRUCIFIXO)).inRoot(isDialog()).check(matches(isNotChecked()));
            onView(withText(TRICEPS)).inRoot(isDialog()).check(matches(isChecked()));
            onView(withId(R.id.group_rest_input)).inRoot(isDialog())
                    .check(matches(withText("45")));
        }
    }

    /** Creates a template with these exercises, saves it and is back at the list. */
    private void createTemplate(String name, String... exerciseNames) {
        onView(withId(R.id.templateListFragment)).perform(click());
        onView(withId(R.id.fab_new)).perform(click());
        onView(withId(R.id.name_input)).perform(replaceText(name));
        addExercises(exerciseNames);
        onView(withId(R.id.action_save)).perform(click());
    }

    /**
     * From the editor, picks these exercises in the library, in this order, and adds them. The
     * search holds the name in lower case so that only the list item matches the exact name.
     */
    private void addExercises(String... exerciseNames) {
        onView(withId(R.id.button_add)).perform(click());
        for (String exerciseName : exerciseNames) {
            onView(withId(R.id.search_input))
                    .perform(replaceText(exerciseName.toLowerCase(Locale.ROOT)));
            waitForSearchDebounce();
            onView(withText(exerciseName)).perform(click());
        }
        onView(withText(exerciseNames.length == 1
                ? "Adicionar 1 exercício"
                : "Adicionar " + exerciseNames.length + " exercícios")).perform(click());
    }

    /** The exercise's overflow menu, then "Agrupar com…". */
    private static void openGroupSheetOf(String exerciseName) {
        onView(withContentDescription("Opções de " + exerciseName)).perform(click());
        onView(withText(R.string.editor_group_with)).perform(click());
    }

    /** The "descanso" line of the card whose title reads exactly this. */
    private static Matcher<View> restLineOf(String cardTitle) {
        return allOf(withId(R.id.rest), hasSibling(withText(cardTitle)));
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

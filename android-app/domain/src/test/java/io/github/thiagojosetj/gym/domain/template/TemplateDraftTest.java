package io.github.thiagojosetj.gym.domain.template;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import io.github.thiagojosetj.gym.domain.model.Laterality;
import io.github.thiagojosetj.gym.domain.model.LoadBasis;
import io.github.thiagojosetj.gym.domain.model.RepRange;
import io.github.thiagojosetj.gym.domain.model.SideMode;
import io.github.thiagojosetj.gym.domain.model.TrackingType;
import io.github.thiagojosetj.gym.domain.model.Weight;
import io.github.thiagojosetj.gym.domain.model.WeightUnit;
import io.github.thiagojosetj.gym.domain.util.IdGenerator;

public class TemplateDraftTest {

    private static final ExerciseRef BENCH = new ExerciseRef("ex-bench", "Supino reto com barra",
            TrackingType.WEIGHT_REPS, LoadBasis.TOTAL, 1, Laterality.BILATERAL, "Peitoral");
    private static final ExerciseRef ROW = new ExerciseRef("ex-row", "Remada unilateral com halter",
            TrackingType.WEIGHT_REPS, LoadBasis.PER_IMPLEMENT, 1, Laterality.UNILATERAL, "Latíssimo do dorso");
    private static final ExerciseRef PLANK = new ExerciseRef("ex-plank", "Prancha",
            TrackingType.DURATION, LoadBasis.TOTAL, 1, Laterality.BILATERAL, "Reto abdominal");
    private static final ExerciseRef PULL_UP = new ExerciseRef("ex-pullup", "Barra fixa",
            TrackingType.BODYWEIGHT_REPS, LoadBasis.TOTAL, 1, Laterality.BILATERAL, "Latíssimo do dorso");

    private IdGenerator ids;

    @Before
    public void setUp() {
        int[] counter = {0};
        ids = () -> "id-" + (++counter[0]);
    }

    @Test
    public void newTemplateStartsEmptyUnmodifiedAndInvalid() {
        TemplateDraft draft = TemplateDraft.newTemplate(ids);
        assertTrue(draft.isNew());
        assertFalse(draft.isModified());
        assertEquals(Arrays.asList(TemplateDraft.Error.NAME_BLANK, TemplateDraft.Error.NO_EXERCISES),
                draft.validate());
    }

    @Test
    public void addingAnExerciseAppliesThreeByTwelveDefaults() {
        TemplateDraft draft = TemplateDraft.newTemplate(ids);
        TemplateExerciseDraft added = draft.addExercises(
                Collections.singletonList(BENCH), TemplateDefaults.standard()).get(0);

        assertEquals(3, added.setCount());
        assertEquals(RepRange.exactly(12), added.uniformReps());
        assertNull(added.uniformWeight());
        assertEquals(90, added.restSeconds());
        assertEquals(SideMode.COMBINED, added.sideMode());
        assertTrue(draft.isModified());
    }

    @Test
    public void timedExercisesGetDurationInsteadOfReps() {
        TemplateDraft draft = TemplateDraft.newTemplate(ids);
        TemplateExerciseDraft plank = draft.addExercises(
                Collections.singletonList(PLANK), TemplateDefaults.standard()).get(0);

        assertNull(plank.uniformReps());
        assertEquals(Integer.valueOf(30), plank.uniformDurationSeconds());
    }

    @Test
    public void everyChildGetsAUniqueId() {
        TemplateDraft draft = TemplateDraft.newTemplate(ids);
        draft.addExercises(Arrays.asList(BENCH, ROW, BENCH), TemplateDefaults.standard());

        Set<String> seen = new HashSet<>();
        seen.add(draft.id());
        for (TemplateExerciseDraft e : draft.exercises()) {
            assertTrue(seen.add(e.id()));
            for (SetPlan s : e.sets()) {
                assertTrue(seen.add(s.id()));
            }
        }
        assertEquals(3, draft.exercises().size()); // the same exercise may appear twice
    }

    @Test
    public void validTemplateHasNoErrors() {
        TemplateDraft draft = TemplateDraft.newTemplate(ids);
        draft.rename("  Push A  ");
        draft.addExercises(Collections.singletonList(BENCH), TemplateDefaults.standard());
        assertEquals("Push A", draft.name());
        assertTrue(draft.validate().isEmpty());
    }

    @Test
    public void nameLongerThanLimitIsRejected() {
        TemplateDraft draft = TemplateDraft.newTemplate(ids);
        StringBuilder longName = new StringBuilder();
        for (int i = 0; i <= TemplateRules.MAX_NAME_LENGTH; i++) {
            longName.append('x');
        }
        draft.rename(longName.toString());
        assertTrue(draft.validate().contains(TemplateDraft.Error.NAME_TOO_LONG));
    }

    @Test
    public void moveReordersExercises() {
        TemplateDraft draft = TemplateDraft.newTemplate(ids);
        draft.addExercises(Arrays.asList(BENCH, ROW, PLANK), TemplateDefaults.standard());
        draft.markSaved();

        draft.moveExercise(2, 0);

        assertEquals(Arrays.asList("ex-plank", "ex-bench", "ex-row"), exerciseIds(draft));
        assertTrue(draft.isModified());
    }

    @Test
    public void moveOutOfBoundsFails() {
        TemplateDraft draft = TemplateDraft.newTemplate(ids);
        draft.addExercises(Collections.singletonList(BENCH), TemplateDefaults.standard());
        assertThrows(IndexOutOfBoundsException.class, () -> draft.moveExercise(0, 1));
    }

    @Test
    public void removeDeletesOnlyTheTargetEntry() {
        TemplateDraft draft = TemplateDraft.newTemplate(ids);
        List<TemplateExerciseDraft> added = draft.addExercises(Arrays.asList(BENCH, ROW), TemplateDefaults.standard());

        draft.removeExercise(added.get(0).id());

        assertEquals(Collections.singletonList("ex-row"), exerciseIds(draft));
    }

    @Test
    public void uniformPlanUpdateKeepsExistingSetIds() {
        TemplateDraft draft = TemplateDraft.newTemplate(ids);
        TemplateExerciseDraft bench = draft.addExercises(
                Collections.singletonList(BENCH), TemplateDefaults.standard()).get(0);
        List<String> originalSetIds = setIds(bench);

        List<ExercisePlanUpdate.Error> errors = draft.updateExercisePlan(bench.id(), new ExercisePlanUpdate(
                4, RepRange.between(8, 10), Weight.of(40, WeightUnit.KILOGRAM), null, 120,
                "  Banco no terceiro encaixe ", SideMode.COMBINED));

        assertTrue(errors.isEmpty());
        TemplateExerciseDraft updated = draft.findExercise(bench.id());
        assertEquals(4, updated.setCount());
        assertEquals(originalSetIds, setIds(updated).subList(0, 3));
        assertEquals(RepRange.between(8, 10), updated.uniformReps());
        assertEquals(Weight.ofGrams(40_000), updated.uniformWeight());
        assertEquals(120, updated.restSeconds());
        assertEquals("Banco no terceiro encaixe", updated.notes());
    }

    @Test
    public void reducingSetsDropsTheLastOnes() {
        TemplateDraft draft = TemplateDraft.newTemplate(ids);
        TemplateExerciseDraft bench = draft.addExercises(
                Collections.singletonList(BENCH), TemplateDefaults.standard()).get(0);
        List<String> originalSetIds = setIds(bench);

        draft.updateExercisePlan(bench.id(), new ExercisePlanUpdate(
                2, RepRange.exactly(5), null, null, 180, null, SideMode.COMBINED));

        assertEquals(originalSetIds.subList(0, 2), setIds(draft.findExercise(bench.id())));
    }

    @Test
    public void invalidPlanUpdateIsRejectedWithoutChangingTheDraft() {
        TemplateDraft draft = TemplateDraft.newTemplate(ids);
        TemplateExerciseDraft bench = draft.addExercises(
                Collections.singletonList(BENCH), TemplateDefaults.standard()).get(0);
        draft.markSaved();

        List<ExercisePlanUpdate.Error> errors = draft.updateExercisePlan(bench.id(), new ExercisePlanUpdate(
                0, RepRange.exactly(10), Weight.of(-10, WeightUnit.KILOGRAM), null, 90, null, SideMode.PER_SIDE));

        assertTrue(errors.contains(ExercisePlanUpdate.Error.SET_COUNT_OUT_OF_RANGE));
        assertTrue(errors.contains(ExercisePlanUpdate.Error.NEGATIVE_WEIGHT_NOT_ALLOWED));
        assertTrue(errors.contains(ExercisePlanUpdate.Error.PER_SIDE_REQUIRES_UNILATERAL));
        assertEquals(3, draft.findExercise(bench.id()).setCount());
        assertFalse(draft.isModified());
    }

    @Test
    public void bodyweightExercisesAcceptAssistanceAsNegativeLoad() {
        TemplateDraft draft = TemplateDraft.newTemplate(ids);
        TemplateExerciseDraft pullUp = draft.addExercises(
                Collections.singletonList(PULL_UP), TemplateDefaults.standard()).get(0);

        List<ExercisePlanUpdate.Error> errors = draft.updateExercisePlan(pullUp.id(), new ExercisePlanUpdate(
                3, RepRange.exactly(8), Weight.of(-20, WeightUnit.KILOGRAM), null, 120, null, SideMode.COMBINED));

        assertTrue(errors.isEmpty());
    }

    @Test
    public void timedExerciseRejectsWeightAndReps() {
        TemplateDraft draft = TemplateDraft.newTemplate(ids);
        TemplateExerciseDraft plank = draft.addExercises(
                Collections.singletonList(PLANK), TemplateDefaults.standard()).get(0);

        List<ExercisePlanUpdate.Error> errors = draft.updateExercisePlan(plank.id(), new ExercisePlanUpdate(
                3, RepRange.exactly(10), Weight.of(10, WeightUnit.KILOGRAM), 45, 60, null, SideMode.COMBINED));

        assertTrue(errors.contains(ExercisePlanUpdate.Error.WEIGHT_NOT_APPLICABLE));
        assertTrue(errors.contains(ExercisePlanUpdate.Error.REPS_NOT_APPLICABLE));
    }

    @Test
    public void unilateralExerciseMayBeLoggedPerSide() {
        TemplateDraft draft = TemplateDraft.newTemplate(ids);
        TemplateExerciseDraft row = draft.addExercises(
                Collections.singletonList(ROW), TemplateDefaults.standard()).get(0);

        draft.updateExercisePlan(row.id(), new ExercisePlanUpdate(
                3, RepRange.exactly(10), Weight.of(22, WeightUnit.KILOGRAM), null, 90, null, SideMode.PER_SIDE));

        assertEquals(SideMode.PER_SIDE, draft.findExercise(row.id()).sideMode());
    }

    @Test
    public void duplicateIsIndependentAndUsesFreshIds() {
        TemplateDraft original = TemplateDraft.newTemplate(ids);
        original.rename("Pull");
        original.addExercises(Arrays.asList(ROW, PULL_UP), TemplateDefaults.standard());

        TemplateDraft copy = original.duplicate("Pull (cópia)");

        assertTrue(copy.isNew());
        assertEquals("Pull (cópia)", copy.name());
        assertNotEquals(original.id(), copy.id());
        assertEquals(exerciseIds(original), exerciseIds(copy));
        for (int i = 0; i < copy.exercises().size(); i++) {
            assertNotEquals(original.exercises().get(i).id(), copy.exercises().get(i).id());
        }

        copy.removeExercise(copy.exercises().get(0).id());
        assertEquals(2, original.exercises().size());
    }

    @Test
    public void addingBeyondTheLimitFails() {
        TemplateDraft draft = TemplateDraft.newTemplate(ids);
        List<ExerciseRef> many = new ArrayList<>();
        for (int i = 0; i <= TemplateRules.MAX_EXERCISES; i++) {
            many.add(BENCH);
        }
        assertThrows(IllegalStateException.class, () -> draft.addExercises(many, TemplateDefaults.standard()));
        assertTrue(draft.exercises().isEmpty());
    }

    private static List<String> exerciseIds(TemplateDraft draft) {
        List<String> result = new ArrayList<>();
        for (TemplateExerciseDraft e : draft.exercises()) {
            result.add(e.exercise().id());
        }
        return result;
    }

    private static List<String> setIds(TemplateExerciseDraft exercise) {
        List<String> result = new ArrayList<>();
        for (SetPlan s : exercise.sets()) {
            result.add(s.id());
        }
        return result;
    }
}

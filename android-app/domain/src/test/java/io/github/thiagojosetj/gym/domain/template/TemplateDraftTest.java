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
import io.github.thiagojosetj.gym.domain.technique.TechniqueCatalog;
import io.github.thiagojosetj.gym.domain.technique.TechniqueScope;
import io.github.thiagojosetj.gym.domain.technique.TrainingTechnique;
import io.github.thiagojosetj.gym.domain.util.IdGenerator;

public class TemplateDraftTest {

    private static final ExerciseRef BENCH = new ExerciseRef("ex-bench", "Supino reto com barra",
            TrackingType.WEIGHT_REPS, LoadBasis.TOTAL, 1, Laterality.BILATERAL, "Peitoral", "barbell");
    private static final ExerciseRef ROW = new ExerciseRef("ex-row", "Remada unilateral com halter",
            TrackingType.WEIGHT_REPS, LoadBasis.PER_IMPLEMENT, 1, Laterality.UNILATERAL, "Latíssimo do dorso", "dumbbell");
    private static final ExerciseRef PLANK = new ExerciseRef("ex-plank", "Prancha",
            TrackingType.DURATION, LoadBasis.TOTAL, 1, Laterality.BILATERAL, "Reto abdominal", "bodyweight");
    private static final ExerciseRef PULL_UP = new ExerciseRef("ex-pullup", "Barra fixa",
            TrackingType.BODYWEIGHT_REPS, LoadBasis.TOTAL, 1, Laterality.BILATERAL, "Latíssimo do dorso", "pull_up_bar");

    private static final TechniqueCatalog NO_TECHNIQUES = TechniqueCatalog.empty();

    private static final TrainingTechnique WARM_UP = new TrainingTechnique("t-warmup", "AQ", "Aquecimento",
            TechniqueScope.SET, false, "Série leve", "Use 40% a 60% da carga");
    private static final TrainingTechnique DROP_SET = new TrainingTechnique("t-drop", "D", "Drop-set",
            TechniqueScope.SET, true, "Reduza a carga e continue", "20% a 30% por queda");
    private static final TrainingTechnique SUPERSET = new TrainingTechnique("t-superset", "SS", "Supersérie",
            TechniqueScope.GROUP, true, "Dois exercícios seguidos", "Descanse ao final da dupla");
    private static final TechniqueCatalog CATALOG =
            new TechniqueCatalog(Arrays.asList(WARM_UP, DROP_SET, SUPERSET));

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

        List<ExercisePlanUpdate.Error> errors = draft.updateExercisePlan(bench.id(), ExercisePlanUpdate.uniform(4, RepRange.between(8, 10), Weight.of(40, WeightUnit.KILOGRAM), null, 120,
                "  Banco no terceiro encaixe ", SideMode.COMBINED), NO_TECHNIQUES);

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

        draft.updateExercisePlan(bench.id(), ExercisePlanUpdate.uniform(2, RepRange.exactly(5), null, null, 180, null, SideMode.COMBINED), NO_TECHNIQUES);

        assertEquals(originalSetIds.subList(0, 2), setIds(draft.findExercise(bench.id())));
    }

    @Test
    public void invalidPlanUpdateIsRejectedWithoutChangingTheDraft() {
        TemplateDraft draft = TemplateDraft.newTemplate(ids);
        TemplateExerciseDraft bench = draft.addExercises(
                Collections.singletonList(BENCH), TemplateDefaults.standard()).get(0);
        draft.markSaved();

        // No sets at all: there are no set values left to check, only the aggregate problems.
        List<ExercisePlanUpdate.Error> empty = draft.updateExercisePlan(bench.id(),
                ExercisePlanUpdate.uniform(0, RepRange.exactly(10), null, null, 90, null, SideMode.PER_SIDE),
                NO_TECHNIQUES);
        assertTrue(empty.contains(ExercisePlanUpdate.Error.NO_SETS));
        assertTrue(empty.contains(ExercisePlanUpdate.Error.PER_SIDE_REQUIRES_UNILATERAL));

        // Assistance (negative load) only makes sense for body-weight exercises.
        List<ExercisePlanUpdate.Error> negative = draft.updateExercisePlan(bench.id(),
                ExercisePlanUpdate.uniform(3, RepRange.exactly(10), Weight.of(-10, WeightUnit.KILOGRAM),
                        null, 90, null, SideMode.COMBINED),
                NO_TECHNIQUES);
        assertTrue(negative.contains(ExercisePlanUpdate.Error.NEGATIVE_WEIGHT_NOT_ALLOWED));

        assertEquals(3, draft.findExercise(bench.id()).setCount());
        assertFalse(draft.isModified());
    }

    @Test
    public void setsCanDifferAndCarryTechniques() {
        // Warm-up first, then two working sets, the last one a drop-set (PRODUCT_SPEC §6.2).
        TemplateDraft draft = TemplateDraft.newTemplate(ids);
        TemplateExerciseDraft bench = draft.addExercises(
                Collections.singletonList(BENCH), TemplateDefaults.standard()).get(0);

        List<ExercisePlanUpdate.Error> errors = draft.updateExercisePlan(bench.id(), new ExercisePlanUpdate(
                Arrays.asList(
                        new SetSpec(RepRange.exactly(12), Weight.of(20, WeightUnit.KILOGRAM), null, WARM_UP.id()),
                        new SetSpec(RepRange.between(8, 10), Weight.of(40, WeightUnit.KILOGRAM), null, null),
                        new SetSpec(RepRange.between(8, 10), Weight.of(40, WeightUnit.KILOGRAM), null, DROP_SET.id())),
                120, null, SideMode.COMBINED), CATALOG);

        assertTrue(errors.isEmpty());
        TemplateExerciseDraft updated = draft.findExercise(bench.id());
        assertEquals(Arrays.asList(WARM_UP.id(), null, DROP_SET.id()), updated.techniqueIds());
        assertFalse("sets differ now", updated.hasUniformPlan());
        assertNull(updated.uniformReps());
        assertEquals(Weight.ofGrams(20_000), updated.sets().get(0).weight());
    }

    @Test
    public void techniqueMustExistAndApplyToSets() {
        TemplateDraft draft = TemplateDraft.newTemplate(ids);
        TemplateExerciseDraft bench = draft.addExercises(
                Collections.singletonList(BENCH), TemplateDefaults.standard()).get(0);

        List<ExercisePlanUpdate.Error> unknown = draft.updateExercisePlan(bench.id(), new ExercisePlanUpdate(
                Collections.singletonList(new SetSpec(RepRange.exactly(10), null, null, "gone")),
                90, null, SideMode.COMBINED), CATALOG);
        assertTrue(unknown.contains(ExercisePlanUpdate.Error.UNKNOWN_TECHNIQUE));

        // A superset links exercises; it cannot be attached to a single set.
        List<ExercisePlanUpdate.Error> wrongScope = draft.updateExercisePlan(bench.id(), new ExercisePlanUpdate(
                Collections.singletonList(new SetSpec(RepRange.exactly(10), null, null, SUPERSET.id())),
                90, null, SideMode.COMBINED), CATALOG);
        assertTrue(wrongScope.contains(ExercisePlanUpdate.Error.TECHNIQUE_NOT_FOR_SETS));

        assertEquals(3, draft.findExercise(bench.id()).setCount()); // unchanged
    }

    @Test
    public void duplicateKeepsTheTechniqueOfEachSet() {
        TemplateDraft draft = TemplateDraft.newTemplate(ids);
        draft.rename("Push A");
        TemplateExerciseDraft bench = draft.addExercises(
                Collections.singletonList(BENCH), TemplateDefaults.standard()).get(0);
        draft.updateExercisePlan(bench.id(), new ExercisePlanUpdate(
                Arrays.asList(new SetSpec(RepRange.exactly(12), null, null, WARM_UP.id()),
                        new SetSpec(RepRange.exactly(10), null, null, null)),
                90, null, SideMode.COMBINED), CATALOG);

        TemplateDraft copy = draft.duplicate(" (cópia)");

        assertEquals(Arrays.asList(WARM_UP.id(), null), copy.exercises().get(0).techniqueIds());
    }

    @Test
    public void bodyweightExercisesAcceptAssistanceAsNegativeLoad() {
        TemplateDraft draft = TemplateDraft.newTemplate(ids);
        TemplateExerciseDraft pullUp = draft.addExercises(
                Collections.singletonList(PULL_UP), TemplateDefaults.standard()).get(0);

        List<ExercisePlanUpdate.Error> errors = draft.updateExercisePlan(pullUp.id(), ExercisePlanUpdate.uniform(3, RepRange.exactly(8), Weight.of(-20, WeightUnit.KILOGRAM), null, 120, null, SideMode.COMBINED), NO_TECHNIQUES);

        assertTrue(errors.isEmpty());
    }

    @Test
    public void timedExerciseRejectsWeightAndReps() {
        TemplateDraft draft = TemplateDraft.newTemplate(ids);
        TemplateExerciseDraft plank = draft.addExercises(
                Collections.singletonList(PLANK), TemplateDefaults.standard()).get(0);

        List<ExercisePlanUpdate.Error> errors = draft.updateExercisePlan(plank.id(), ExercisePlanUpdate.uniform(3, RepRange.exactly(10), Weight.of(10, WeightUnit.KILOGRAM), 45, 60, null, SideMode.COMBINED), NO_TECHNIQUES);

        assertTrue(errors.contains(ExercisePlanUpdate.Error.WEIGHT_NOT_APPLICABLE));
        assertTrue(errors.contains(ExercisePlanUpdate.Error.REPS_NOT_APPLICABLE));
    }

    @Test
    public void unilateralExerciseMayBeLoggedPerSide() {
        TemplateDraft draft = TemplateDraft.newTemplate(ids);
        TemplateExerciseDraft row = draft.addExercises(
                Collections.singletonList(ROW), TemplateDefaults.standard()).get(0);

        draft.updateExercisePlan(row.id(), ExercisePlanUpdate.uniform(3, RepRange.exactly(10), Weight.of(22, WeightUnit.KILOGRAM), null, 90, null, SideMode.PER_SIDE), NO_TECHNIQUES);

        assertEquals(SideMode.PER_SIDE, draft.findExercise(row.id()).sideMode());
    }

    @Test
    public void duplicateIsIndependentAndUsesFreshIds() {
        TemplateDraft original = TemplateDraft.newTemplate(ids);
        original.rename("Pull");
        original.addExercises(Arrays.asList(ROW, PULL_UP), TemplateDefaults.standard());

        TemplateDraft copy = original.duplicate(" (cópia)");

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
    public void duplicateOfALongNameStillRespectsTheLimit() {
        // Regression (review 2026-09-22): a 60-char name + " (cópia)" used to be saved as 68 chars.
        TemplateDraft original = TemplateDraft.newTemplate(ids);
        original.rename(repeat('x', TemplateRules.MAX_NAME_LENGTH));
        original.addExercises(Collections.singletonList(BENCH), TemplateDefaults.standard());

        TemplateDraft copy = original.duplicate(" (cópia)");

        assertEquals(TemplateRules.MAX_NAME_LENGTH, copy.name().length());
        assertTrue(copy.name().endsWith(" (cópia)"));
        assertTrue(copy.validate().isEmpty());
        assertTrue(copy.duplicate(" (cópia)").validate().isEmpty()); // copies of copies too
    }

    @Test
    public void copyNameNeverSplitsAnEmoji() {
        // 51 chars + U+1F4AA (flexed biceps, 2 UTF-16 units) = 53; only 52 fit before the suffix.
        String base = repeat('x', 51) + "💪";
        String name = TemplateDraft.copyName(base, " (cópia)");
        assertEquals(repeat('x', 51) + " (cópia)", name); // the emoji is dropped whole, never halved
        assertTrue(name.length() <= TemplateRules.MAX_NAME_LENGTH);
    }

    @Test
    public void reapplyingTheSamePlanIsNotAnUnsavedChange() {
        // Regression (review 2026-09-22): opening "Editar séries" and tapping Aplicar without
        // changes used to trigger the "Descartar alterações?" guard.
        TemplateDraft draft = TemplateDraft.newTemplate(ids);
        TemplateExerciseDraft bench = draft.addExercises(
                Collections.singletonList(BENCH), TemplateDefaults.standard()).get(0);
        draft.markSaved();

        draft.updateExercisePlan(bench.id(), ExercisePlanUpdate.uniform(3, RepRange.exactly(12), null, null, 90, "  ", SideMode.COMBINED), NO_TECHNIQUES);

        assertFalse(draft.isModified());
    }

    @Test
    public void changingOnlyTheNoteIsAnUnsavedChange() {
        TemplateDraft draft = TemplateDraft.newTemplate(ids);
        TemplateExerciseDraft bench = draft.addExercises(
                Collections.singletonList(BENCH), TemplateDefaults.standard()).get(0);
        draft.markSaved();

        draft.updateExercisePlan(bench.id(), ExercisePlanUpdate.uniform(3, RepRange.exactly(12), null, null, 90, "Banco no 3º encaixe", SideMode.COMBINED), NO_TECHNIQUES);

        assertTrue(draft.isModified());
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

    private static String repeat(char c, int times) {
        StringBuilder sb = new StringBuilder(times);
        for (int i = 0; i < times; i++) {
            sb.append(c);
        }
        return sb.toString();
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

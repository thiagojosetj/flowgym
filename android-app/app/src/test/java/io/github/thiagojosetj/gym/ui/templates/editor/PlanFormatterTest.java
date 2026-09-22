package io.github.thiagojosetj.gym.ui.templates.editor;

import static org.junit.Assert.assertEquals;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import io.github.thiagojosetj.gym.domain.model.LoadBasis;
import io.github.thiagojosetj.gym.domain.model.RepRange;
import io.github.thiagojosetj.gym.domain.model.SideMode;
import io.github.thiagojosetj.gym.domain.model.TrackingType;
import io.github.thiagojosetj.gym.domain.model.Weight;
import io.github.thiagojosetj.gym.domain.model.WeightUnit;

@RunWith(AndroidJUnit4.class)
@Config(qualifiers = "pt-rBR")
public class PlanFormatterTest {

    private PlanFormatter formatter;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        formatter = new PlanFormatter(context.getResources(), WeightUnit.KILOGRAM);
    }

    @Test
    public void defaultPlan() {
        assertEquals("3 séries × 12 reps", formatter.planLine(item(TrackingType.WEIGHT_REPS, LoadBasis.TOTAL,
                "barbell", 3, RepRange.exactly(12), null, null)));
        assertEquals("descanso 1:30 min", formatter.restLine(item(TrackingType.WEIGHT_REPS, LoadBasis.TOTAL,
                "barbell", 3, RepRange.exactly(12), null, null)));
    }

    @Test
    public void rangeAndTotalLoadWithDecimalComma() {
        assertEquals("4 séries × 8–10 reps · 42,5 kg", formatter.planLine(item(TrackingType.WEIGHT_REPS,
                LoadBasis.TOTAL, "barbell", 4, RepRange.between(8, 10), Weight.ofGrams(42_500), null)));
    }

    @Test
    public void dumbbellLoadIsShownPerDumbbellNeverSummed() {
        assertEquals("3 séries × 12 reps · 12 kg por halter", formatter.planLine(item(TrackingType.WEIGHT_REPS,
                LoadBasis.PER_IMPLEMENT, "dumbbell", 3, RepRange.exactly(12), Weight.ofGrams(12_000), null)));
        assertEquals("3 séries × 12 reps · 15 kg por polia", formatter.planLine(item(TrackingType.WEIGHT_REPS,
                LoadBasis.PER_IMPLEMENT, "cable", 3, RepRange.exactly(12), Weight.ofGrams(15_000), null)));
    }

    @Test
    public void bodyweightShowsExtraLoadOrAssistanceWithoutInventingNumbers() {
        assertEquals("1 série × 8 reps · peso corporal", formatter.planLine(item(TrackingType.BODYWEIGHT_REPS,
                LoadBasis.TOTAL, "pull_up_bar", 1, RepRange.exactly(8), null, null)));
        assertEquals("3 séries × 8 reps · peso corporal + 10 kg", formatter.planLine(item(
                TrackingType.BODYWEIGHT_REPS, LoadBasis.TOTAL, "pull_up_bar", 3, RepRange.exactly(8),
                Weight.ofGrams(10_000), null)));
        assertEquals("3 séries × 8 reps · peso corporal − 25 kg (assistência)", formatter.planLine(item(
                TrackingType.BODYWEIGHT_REPS, LoadBasis.TOTAL, "pull_up_bar", 3, RepRange.exactly(8),
                Weight.ofGrams(-25_000), null)));
    }

    @Test
    public void timedExercise() {
        assertEquals("3 séries × 45 s", formatter.planLine(item(TrackingType.DURATION, LoadBasis.TOTAL,
                "bodyweight", 3, null, null, 45)));
    }

    @Test
    public void noRestAndPerSide() {
        TemplateExerciseItem item = new TemplateExerciseItem("id", "ex", "Búlgaro", null, TrackingType.WEIGHT_REPS,
                LoadBasis.PER_IMPLEMENT, "dumbbell", true, 3, RepRange.exactly(10), null, null, true, 0, null,
                SideMode.PER_SIDE);
        assertEquals("sem descanso automático · cada lado registrado separadamente", formatter.restLine(item));
    }

    private static TemplateExerciseItem item(TrackingType tracking, LoadBasis basis, String equipment, int sets,
                                             RepRange reps, Weight weight, Integer duration) {
        return new TemplateExerciseItem("id", "ex", "Exercício", null, tracking, basis, equipment, false, sets,
                reps, weight, duration, true, 90, null, SideMode.COMBINED);
    }
}

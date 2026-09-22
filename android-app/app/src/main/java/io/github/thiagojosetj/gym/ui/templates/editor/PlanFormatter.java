package io.github.thiagojosetj.gym.ui.templates.editor;

import android.content.res.Resources;

import java.util.Locale;

import io.github.thiagojosetj.gym.R;
import io.github.thiagojosetj.gym.domain.model.LoadBasis;
import io.github.thiagojosetj.gym.domain.model.SideMode;
import io.github.thiagojosetj.gym.domain.model.TrackingType;
import io.github.thiagojosetj.gym.domain.model.Weight;
import io.github.thiagojosetj.gym.domain.model.WeightUnit;
import io.github.thiagojosetj.gym.ui.common.NumberInput;

/**
 * Human-readable plan lines for template cards, e.g. "4 séries × 8–10 reps · 12 kg por halter".
 * Dumbbell loads are always shown per dumbbell, never summed (PRODUCT_SPEC §6.4).
 */
final class PlanFormatter {

    private final Resources res;
    private final Locale locale;
    private final WeightUnit unit;
    private final String separator;

    PlanFormatter(Resources res, WeightUnit unit) {
        this.res = res;
        this.locale = res.getConfiguration().getLocales().get(0);
        this.unit = unit;
        this.separator = res.getString(R.string.separator_dot);
    }

    String planLine(TemplateExerciseItem item) {
        String sets = res.getQuantityString(R.plurals.plan_sets, item.setCount(), item.setCount());
        if (!item.uniformPlan()) {
            return res.getString(R.string.plan_mixed, sets);
        }
        String main;
        if (item.reps() != null) {
            main = res.getString(R.string.plan_sets_reps, sets, item.reps().format());
        } else if (item.durationSeconds() != null) {
            main = res.getString(R.string.plan_sets_duration, sets, duration(item.durationSeconds()));
        } else {
            main = sets;
        }
        String load = loadText(item);
        return load == null ? main : main + separator + load;
    }

    String restLine(TemplateExerciseItem item) {
        String rest = item.restSeconds() == 0
                ? res.getString(R.string.plan_no_rest)
                : res.getString(R.string.plan_rest, duration(item.restSeconds()));
        if (item.sideMode() == SideMode.PER_SIDE) {
            rest = rest + separator + res.getString(R.string.plan_per_side);
        }
        return rest;
    }

    private String loadText(TemplateExerciseItem item) {
        Weight weight = item.weight();
        if (item.trackingType() == TrackingType.BODYWEIGHT_REPS) {
            if (weight == null || weight.grams() == 0) {
                return res.getString(R.string.plan_bodyweight);
            }
            return weight.isNegative()
                    ? res.getString(R.string.plan_bodyweight_assisted, weight(weight))
                    : res.getString(R.string.plan_bodyweight_plus, weight(weight));
        }
        if (!item.trackingType().usesWeight() || weight == null) {
            return null;
        }
        String value = weight(weight);
        if (item.loadBasis() != LoadBasis.PER_IMPLEMENT) {
            return value;
        }
        return res.getString(perImplementLabel(item.primaryEquipmentCode()), value);
    }

    /** "12 kg por halter" for dumbbells, "por polia" for cables; generic "cada" otherwise. */
    static int perImplementLabel(String equipmentCode) {
        if (equipmentCode == null) {
            return R.string.load_per_implement;
        }
        return switch (equipmentCode) {
            case "dumbbell" -> R.string.load_per_dumbbell;
            case "kettlebell" -> R.string.load_per_kettlebell;
            case "cable" -> R.string.load_per_cable;
            default -> R.string.load_per_implement;
        };
    }

    /** Absolute value in the display unit: the sign is expressed by the surrounding text. */
    String weight(Weight weight) {
        double value = Math.abs(weight.in(unit));
        return res.getString(R.string.weight_value, NumberInput.formatDecimal(value, locale), unit.symbol());
    }

    String duration(int seconds) {
        if (seconds < 60) {
            return res.getString(R.string.duration_seconds, seconds);
        }
        return res.getString(R.string.duration_minutes_seconds, seconds / 60, seconds % 60);
    }
}

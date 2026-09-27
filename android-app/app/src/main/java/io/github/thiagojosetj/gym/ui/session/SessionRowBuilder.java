package io.github.thiagojosetj.gym.ui.session;

import android.content.res.Resources;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import io.github.thiagojosetj.gym.R;
import io.github.thiagojosetj.gym.domain.model.LoadBasis;
import io.github.thiagojosetj.gym.domain.model.SideMode;
import io.github.thiagojosetj.gym.domain.model.Weight;
import io.github.thiagojosetj.gym.domain.model.WeightUnit;
import io.github.thiagojosetj.gym.domain.session.ActiveSession;
import io.github.thiagojosetj.gym.domain.session.LoggedSet;
import io.github.thiagojosetj.gym.domain.session.SessionExercise;
import io.github.thiagojosetj.gym.domain.session.SetValues;
import io.github.thiagojosetj.gym.ui.common.Durations;
import io.github.thiagojosetj.gym.ui.common.NumberInput;

/**
 * Turns a session into the flat list of rows the screen draws, and writes every user-facing number.
 *
 * <p>Two rules that come straight from the product spec: a load per implement is always shown per
 * implement ("12 kg/halter", never 24), and a suggestion is a HINT on an empty field - it only
 * becomes text when the user types it or confirms the set (section 6.5).
 */
final class SessionRowBuilder {

    private final Resources res;
    private final Locale locale;
    private final WeightUnit unit;

    SessionRowBuilder(Resources res, WeightUnit unit) {
        this.res = res;
        this.locale = res.getConfiguration().getLocales().get(0);
        this.unit = unit;
    }

    List<SessionRow> build(ActiveSession session, Set<String> collapsed, Map<String, SetDraft> drafts) {
        List<SessionRow> rows = new ArrayList<>();
        for (SessionExercise exercise : session.exercises()) {
            boolean isCollapsed = collapsed.contains(exercise.id());
            rows.add(new SessionRow.ExerciseHeader(
                    "header:" + exercise.id(),
                    exercise.name(),
                    exercise.completedSets(),
                    exercise.sets().size(),
                    isCollapsed,
                    emptyToNull(exercise.permanentNotes()),
                    exercise.restSeconds() > 0 ? Durations.restLabel(exercise.restSeconds()) : null));
            if (isCollapsed) {
                continue;
            }
            for (LoggedSet set : exercise.sets()) {
                rows.add(setRow(exercise, set, drafts.get(set.id())));
            }
            rows.add(new SessionRow.AddSet("add:" + exercise.id(), exercise.id(), exercise.name()));
        }
        return rows;
    }

    private SessionRow.SetRow setRow(SessionExercise exercise, LoggedSet set, @Nullable SetDraft draft) {
        boolean timed = exercise.trackingType().usesDuration() && !exercise.trackingType().usesReps();
        boolean showWeight = exercise.trackingType().usesWeight();
        SetValues suggestion = set.suggestion();
        String number = set.workingNumber() == null ? null : String.valueOf(set.workingNumber());

        String weightText = draft != null && draft.weightText() != null
                ? draft.weightText()
                : weightOf(set.values());
        String repsText = draft != null && draft.repsText() != null
                ? draft.repsText()
                : timed ? intOf(set.values().durationSeconds()) : intOf(set.values().reps());

        return new SessionRow.SetRow(
                set.id(),
                exercise.id(),
                set.workingNumber(),
                set.techniqueCode(),
                plannedText(exercise, set, timed),
                previousText(exercise, set, timed),
                showWeight,
                true,
                weightText,
                repsText,
                weightOf(suggestion),
                timed ? intOf(suggestion.durationSeconds()) : intOf(suggestion.reps()),
                weightLabel(exercise),
                res.getString(timed ? R.string.session_duration_hint : R.string.session_reps_hint),
                set.isCompleted(),
                exercise.sets().size() > 1);
    }

    /** "Planejado: 12 reps · 40 kg" - what the template asked for, never overwritten. */
    private String plannedText(SessionExercise exercise, LoggedSet set, boolean timed) {
        StringBuilder text = new StringBuilder();
        if (timed && set.plannedDurationSeconds() != null) {
            text.append(res.getString(R.string.session_duration_value, set.plannedDurationSeconds()));
        } else if (set.plannedReps() != null) {
            text.append(res.getString(R.string.session_reps_value, set.plannedReps().format()));
        }
        if (set.plannedWeight() != null && exercise.trackingType().usesWeight()) {
            appendWithSeparator(text, weightWithUnit(set.plannedWeight(), exercise));
        }
        if (text.length() == 0) {
            return null;
        }
        return res.getString(R.string.session_planned, text.toString());
    }

    /** "Anterior: 10 reps · 40 kg", or null when there is no comparable set - never a made-up value. */
    private String previousText(SessionExercise exercise, LoggedSet set, boolean timed) {
        SetValues previous = set.previous();
        if (previous == null || previous.isEmpty()) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        if (timed && previous.durationSeconds() != null) {
            text.append(res.getString(R.string.session_duration_value, previous.durationSeconds()));
        } else if (previous.repsLeft() != null || previous.repsRight() != null) {
            text.append(res.getString(R.string.session_reps_per_side,
                    orDash(previous.repsLeft()), orDash(previous.repsRight())));
        } else if (previous.reps() != null) {
            text.append(res.getString(R.string.session_reps_value, String.valueOf(previous.reps())));
        }
        if (previous.weight() != null && exercise.trackingType().usesWeight()) {
            appendWithSeparator(text, weightWithUnit(previous.weight(), exercise));
        }
        if (text.length() == 0) {
            return null;
        }
        return res.getString(R.string.session_previous, text.toString());
    }

    private String weightLabel(SessionExercise exercise) {
        return res.getString(exercise.loadBasis() == LoadBasis.PER_IMPLEMENT
                ? R.string.session_weight_hint_per_implement
                : R.string.session_weight_hint);
    }

    /** The number as typed, per implement for dumbbells (PRODUCT_SPEC section 6.4). */
    private String weightWithUnit(Weight weight, SessionExercise exercise) {
        String value = NumberInput.formatDecimal(Math.abs(weight.in(unit)), locale);
        if (exercise.loadBasis() == LoadBasis.PER_IMPLEMENT) {
            return res.getString(R.string.session_weight_per_implement_value, value, unit.symbol());
        }
        return res.getString(R.string.session_weight_value, value, unit.symbol());
    }

    private String weightOf(SetValues values) {
        Weight weight = values.weight();
        if (weight == null || weight.grams() == 0) {
            return null;
        }
        return NumberInput.formatDecimal(Math.abs(weight.in(unit)), locale);
    }

    private static String intOf(@Nullable Integer value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String orDash(@Nullable Integer value) {
        return value == null ? "—" : String.valueOf(value);
    }

    private void appendWithSeparator(StringBuilder text, String value) {
        if (text.length() > 0) {
            text.append(res.getString(R.string.separator_dot));
        }
        text.append(value);
    }

    private static String emptyToNull(@Nullable String text) {
        return text == null || text.trim().isEmpty() ? null : text;
    }

    /** Per-side logging is not editable in this slice; the flag decides what the row explains. */
    static boolean isPerSide(SessionExercise exercise) {
        return exercise.sideMode() == SideMode.PER_SIDE;
    }
}

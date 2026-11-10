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
import io.github.thiagojosetj.gym.domain.session.SessionGroup;
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
    private final WeightUnit unit;

    SessionRowBuilder(Resources res, WeightUnit unit) {
        this.res = res;
        this.unit = unit;
    }

    /**
     * Read per call, never cached: the ViewModel survives a configuration change, so a locale
     * captured in the constructor would keep formatting "42,5" after the user switches the phone to
     * English while the strings around it had already switched.
     */
    private Locale locale() {
        return res.getConfiguration().getLocales().get(0);
    }

    List<SessionRow> build(ActiveSession session, Set<String> collapsed, Map<String, SetDraft> drafts) {
        List<SessionRow> rows = new ArrayList<>();
        for (SessionExercise exercise : session.exercises()) {
            boolean isCollapsed = collapsed.contains(exercise.id());
            SessionGroup group = session.groupOf(exercise.id());
            int numberInGroup = group == null ? 0 : numberInGroup(session, group, exercise);
            rows.add(new SessionRow.ExerciseHeader(
                    "header:" + exercise.id(),
                    groupedName(exercise, group, numberInGroup),
                    spokenGroupedName(exercise, group, numberInGroup),
                    exercise.completedSets(),
                    exercise.sets().size(),
                    isCollapsed,
                    emptyToNull(exercise.permanentNotes()),
                    restLabelOf(exercise, group)));
            if (isCollapsed) {
                continue;
            }
            for (LoggedSet set : exercise.sets()) {
                rows.add(setRow(exercise, set, drafts.get(set.id())));
                // The drops follow their set, in order. Numbered from 1 and never counting the set:
                // the set itself is the first step of a drop-set (PRODUCT_SPEC 9.1).
                List<LoggedSet> segments = set.segments();
                for (int i = 0; i < segments.size(); i++) {
                    LoggedSet segment = segments.get(i);
                    rows.add(segmentRow(exercise, set, segment, i + 1, drafts.get(segment.id())));
                }
            }
            rows.add(new SessionRow.AddSet("add:" + exercise.id(), exercise.id(), exercise.name()));
        }
        return rows;
    }

    private SessionRow.SetRow setRow(SessionExercise exercise, LoggedSet set, @Nullable SetDraft draft) {
        boolean timed = isTimed(exercise);
        Fields fields = fieldsOf(exercise, set, draft);
        return new SessionRow.SetRow(
                set.id(),
                exercise.id(),
                set.workingNumber(),
                set.techniqueCode(),
                plannedText(exercise, set, timed),
                previousText(exercise, set, timed),
                fields.showWeight(),
                true,
                fields.weightText(),
                fields.repsText(),
                fields.weightHint(),
                fields.repsHint(),
                fields.weightLabel(),
                fields.repsLabel(),
                fields.perSide(),
                fields.repsLeftText(),
                fields.repsRightText(),
                fields.repsLeftHint(),
                fields.repsRightHint(),
                set.isCompleted(),
                exercise.sets().size() > 1);
    }

    /**
     * One drop of a set. Its fields come from the same {@link #fieldsOf} a set uses, so a draft, a
     * suggestion and a confirmed value mean exactly the same thing on both rows.
     */
    private SessionRow.Segment segmentRow(SessionExercise exercise, LoggedSet parent,
                                          LoggedSet segment, int index, @Nullable SetDraft draft) {
        Fields fields = fieldsOf(exercise, segment, draft);
        return new SessionRow.Segment(
                segment.id(),
                parent.id(),
                index,
                parent.workingNumber(),
                fields.showWeight(),
                fields.weightText(),
                fields.repsText(),
                fields.weightHint(),
                fields.repsHint(),
                fields.weightLabel(),
                fields.repsLabel(),
                fields.perSide(),
                fields.repsLeftText(),
                fields.repsRightText(),
                fields.repsLeftHint(),
                fields.repsRightHint(),
                segment.isCompleted());
    }

    /** What a set row and a segment row have in common: the fields the user types into. */
    private record Fields(
            boolean showWeight,
            String weightText,
            String repsText,
            String weightHint,
            String repsHint,
            String weightLabel,
            String repsLabel,
            boolean perSide,
            String repsLeftText,
            String repsRightText,
            String repsLeftHint,
            String repsRightHint) {
    }

    /**
     * Worked out in ONE place for sets and for drops, because the rules in here are the ones this
     * screen has already got wrong once (a confirmed row showing a draft). A second copy would be
     * free to get them wrong again by itself.
     */
    private Fields fieldsOf(SessionExercise exercise, LoggedSet set, @Nullable SetDraft draft) {
        boolean timed = isTimed(exercise);
        SetValues suggestion = set.suggestion();

        // A confirmed set shows what is stored, never a draft: the row must not display a number
        // the database refused to keep.
        SetDraft pending = set.isCompleted() ? null : draft;
        String weightText = pending != null && pending.weightText() != null
                ? pending.weightText()
                : weightOf(set.values());
        String repsText = pending != null && pending.repsText() != null
                ? pending.repsText()
                : timed ? intOf(set.values().durationSeconds()) : intOf(set.values().reps());
        // Per-side only makes sense for a unilateral exercise measured in repetitions: there is no
        // left and right of a plank, and the domain rejects PER_SIDE on a bilateral exercise.
        boolean perSide = !timed && exercise.isUnilateral() && isPerSide(exercise);
        String repsLeftText = pending != null && pending.repsLeftText() != null
                ? pending.repsLeftText()
                : intOf(set.values().repsLeft());
        String repsRightText = pending != null && pending.repsRightText() != null
                ? pending.repsRightText()
                : intOf(set.values().repsRight());

        return new Fields(
                exercise.trackingType().usesWeight(),
                weightText,
                repsText,
                weightOf(suggestion),
                timed ? intOf(suggestion.durationSeconds()) : intOf(suggestion.reps()),
                weightLabel(exercise),
                res.getString(timed ? R.string.session_duration_hint : R.string.session_reps_hint),
                perSide,
                repsLeftText,
                repsRightText,
                sideSuggestion(suggestion, suggestion.repsLeft()),
                sideSuggestion(suggestion, suggestion.repsRight()));
    }

    /** Measured in time only (a plank): its "reps" field holds seconds. */
    private static boolean isTimed(SessionExercise exercise) {
        return exercise.trackingType().usesDuration() && !exercise.trackingType().usesReps();
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
        String value = NumberInput.formatDecimal(Math.abs(weight.in(unit)), locale());
        if (exercise.loadBasis() == LoadBasis.PER_IMPLEMENT) {
            return res.getString(R.string.session_weight_per_implement_value, value, unit.symbol());
        }
        return res.getString(R.string.session_weight_value, value, unit.symbol());
    }

    private String weightOf(SetValues values) {
        Weight weight = values.weight();
        if (weight == null) {
            return null; // zero IS a value and is shown as "0"
        }
        return NumberInput.formatDecimal(Math.abs(weight.in(unit)), locale());
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

    /** "A1 Supino reto" inside a group (PRODUCT_SPEC 6.3), the plain name otherwise. */
    private String groupedName(SessionExercise exercise, @Nullable SessionGroup group, int number) {
        if (group == null) {
            return exercise.name();
        }
        return res.getString(R.string.editor_group_exercise_name, group.label(), number,
                exercise.name());
    }

    /** "A1" is spelled out as a code by a screen reader, so it hears the words instead. */
    private String spokenGroupedName(SessionExercise exercise, @Nullable SessionGroup group,
                                     int number) {
        if (group == null) {
            return null;
        }
        return res.getString(R.string.editor_group_exercise_description, group.label(), number,
                exercise.name());
    }

    /**
     * The rest a grouped exercise actually obeys is the GROUP's, which starts when the round ends
     * (PRODUCT_SPEC 6.3). Showing the exercise's own rest here would be a number the app never uses.
     */
    private String restLabelOf(SessionExercise exercise, @Nullable SessionGroup group) {
        int seconds = group == null ? exercise.restSeconds() : group.restAfterRoundSeconds();
        return seconds > 0 ? Durations.restLabel(seconds) : null;
    }

    /** 1-based position of this exercise among its group's members, in workout order. */
    private static int numberInGroup(ActiveSession session, SessionGroup group,
                                     SessionExercise exercise) {
        List<SessionExercise> members = session.exercisesOfGroup(group.id());
        for (int i = 0; i < members.size(); i++) {
            if (members.get(i).id().equals(exercise.id())) {
                return i + 1;
            }
        }
        return 1;
    }

    private static String emptyToNull(@Nullable String text) {
        return text == null || text.trim().isEmpty() ? null : text;
    }

    static boolean isPerSide(SessionExercise exercise) {
        return exercise.sideMode() == SideMode.PER_SIDE;
    }

    /**
     * The hint for one side. When the suggestion already has that side (the last session was also
     * logged per side) it is used as it is; otherwise the combined suggestion is shown, because on
     * a unilateral exercise "10 reps" has always meant 10 per side (PRODUCT_SPEC 6.4).
     */
    private static String sideSuggestion(SetValues suggestion, Integer side) {
        return intOf(side != null ? side : suggestion.reps());
    }
}

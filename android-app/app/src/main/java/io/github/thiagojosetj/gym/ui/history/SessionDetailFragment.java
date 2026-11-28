package io.github.thiagojosetj.gym.ui.history;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import com.google.android.material.snackbar.Snackbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.LongFunction;

import io.github.thiagojosetj.gym.AppContainer;
import io.github.thiagojosetj.gym.core.Event;
import io.github.thiagojosetj.gym.R;
import io.github.thiagojosetj.gym.databinding.FragmentSessionDetailBinding;
import io.github.thiagojosetj.gym.domain.model.TrackingType;
import io.github.thiagojosetj.gym.domain.model.Weight;
import io.github.thiagojosetj.gym.domain.model.WeightUnit;
import io.github.thiagojosetj.gym.domain.session.ExerciseComparison;
import io.github.thiagojosetj.gym.domain.session.LoggedSet;
import io.github.thiagojosetj.gym.domain.session.MetricChange;
import io.github.thiagojosetj.gym.domain.session.SessionComparison;
import io.github.thiagojosetj.gym.domain.session.SessionDetail;
import io.github.thiagojosetj.gym.domain.session.SessionExercise;
import io.github.thiagojosetj.gym.domain.session.SessionExerciseSummary;
import io.github.thiagojosetj.gym.domain.session.SessionSummary;
import io.github.thiagojosetj.gym.domain.session.SetValues;
import io.github.thiagojosetj.gym.ui.common.Durations;
import io.github.thiagojosetj.gym.ui.common.NumberInput;
import io.github.thiagojosetj.gym.ui.common.SessionDates;
import io.github.thiagojosetj.gym.ui.common.ViewModelFactories;

/** Finished-session detail (PRODUCT_SPEC HIS-01, HIS-03, HIS-04): summary, comparison and sets. */
public class SessionDetailFragment extends Fragment implements SessionDetailAdapter.Listener {

    private static final String ARG_SESSION_ID = "sessionId";
    /** Units are kg for now; lb support is planned (docs/ROADMAP.md). */
    private static final WeightUnit UNIT = WeightUnit.KILOGRAM;

    private FragmentSessionDetailBinding binding;
    private SessionDetailAdapter adapter;
    /** A field because a tap on the rating arrives long after onViewCreated has returned. */
    private SessionDetailViewModel viewModel;

    /** Navigation arguments for this screen (ADR-0018). */
    public static Bundle args(String sessionId) {
        Bundle bundle = new Bundle();
        bundle.putString(ARG_SESSION_ID, sessionId);
        return bundle;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSessionDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        String sessionId = requireArguments().getString(ARG_SESSION_ID);
        AppContainer app = ViewModelFactories.container(this);
        viewModel = new ViewModelProvider(this, ViewModelFactories.of(
                SessionDetailViewModel.class,
                () -> new SessionDetailViewModel(app.history, sessionId)))
                .get(SessionDetailViewModel.class);

        adapter = new SessionDetailAdapter(this, app.executors.diskIO());
        binding.list.setAdapter(adapter);
        // The only edit is opening an exercise's sets, and those rows should simply be there when
        // the button is tapped: an insert animation would make them arrive after the tap.
        binding.list.setItemAnimator(null);

        viewModel.state().observe(getViewLifecycleOwner(), this::render);
        viewModel.ratingFailures().observe(getViewLifecycleOwner(), event ->
                sayItFailed(event, R.string.history_rating_failed));
        viewModel.noteFailures().observe(getViewLifecycleOwner(), event ->
                sayItFailed(event, R.string.history_note_failed));
    }

    private void render(SessionDetailViewModel.State state) {
        if (state.loading()) {
            return; // a single local query: nothing to show yet, and no spinner worth drawing
        }
        // A missing detail is a failure too: there is nothing else it could mean.
        boolean failed = state.failed() || state.detail() == null;
        binding.loadFailed.setVisibility(failed ? View.VISIBLE : View.GONE);
        binding.list.setVisibility(failed ? View.GONE : View.VISIBLE);
        if (!failed) {
            adapter.submitList(rowsOf(state.detail(), state));
        }
    }

    @Override
    public void onToggleSets(String sessionExerciseId) {
        viewModel.toggleSets(sessionExerciseId);
    }

    @Override
    public void onRate(@Nullable Integer rating) {
        viewModel.rate(rating);
    }

    @Override
    public void onNote(@Nullable String notes) {
        viewModel.note(notes);
    }

    /**
     * Writes whatever is in the note field before the screen stops being used.
     *
     * <p>Losing focus is not enough on its own: leaving by the back button, by the recents
     * screen or by the home button never takes focus away, and the text would go with the
     * screen. What a kill takes while the keyboard is still open is accepted, and is the same
     * rule ADR-0031 sets for text in typing.
     */
    @Override
    public void onPause() {
        super.onPause();
        if (binding == null || viewModel == null) {
            return;
        }
        RecyclerView.ViewHolder holder = binding.list.findViewHolderForAdapterPosition(0);
        if (holder instanceof SessionDetailAdapter.SummaryHolder summary) {
            viewModel.note(summary.noteText());
        }
    }

    /** The screen is showing what IS stored again; this says why it changed back. */
    private void sayItFailed(Event<Boolean> event, @StringRes int message) {
        if (event.consume() != null && binding != null) {
            Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
        }
    }

    // ------------------------------------------------------------------ rows

    /**
     * The whole screen as one flat list: summary, the exercise-by-exercise comparison, then each
     * exercise followed by its sets.
     */
    private List<DetailRow> rowsOf(SessionDetail detail, SessionDetailViewModel.State state) {
        List<DetailRow> rows = new ArrayList<>();
        rows.add(summaryRow(detail));
        addComparisonRows(rows, detail, state);
        for (SessionExerciseSummary summary : detail.exercises()) {
            rows.add(exerciseRow(summary));
            SessionExercise exercise = detail.session().exerciseById(summary.sessionExerciseId());
            if (exercise == null) {
                continue; // cannot happen: the summary was built from this very exercise
            }
            List<LoggedSet> sets = new ArrayList<>(exercise.sets());
            sets.sort(Comparator.comparingInt(LoggedSet::position));
            for (LoggedSet set : sets) {
                rows.add(setRow(exercise, set));
            }
        }
        return rows;
    }

    private DetailRow.Summary summaryRow(SessionDetail detail) {
        SessionSummary summary = detail.summary();
        String zone = detail.timeZone();
        long startedAt = detail.session().header().clock().startedAt();
        String dateTime = getString(R.string.history_session_date,
                SessionDates.day(startedAt, zone), SessionDates.timeOfDay(startedAt, zone));

        // Same wording as the dialog shown when the workout was finished, so the two never differ.
        DetailRow.Line duration = new DetailRow.Line(
                getString(R.string.summary_duration, Durations.clock(summary.totalMs()),
                        Durations.clock(summary.effectiveMs())),
                getString(R.string.summary_duration, Durations.spoken(summary.totalMs()),
                        Durations.spoken(summary.effectiveMs())));
        String sets = getString(R.string.summary_sets,
                summary.performedSets(), summary.totalReps());
        String volume = summary.hasVolume()
                ? getString(R.string.summary_volume, loadText(summary.volumeGrams()))
                : getString(R.string.history_no_volume);
        // PRODUCT_SPEC section 9: whatever is not in the total has to be said, never hidden.
        int outside = summary.setsOutsideVolume();
        String outsideVolume = outside > 0
                ? getResources().getQuantityString(
                        R.plurals.summary_outside_volume, outside, outside)
                : null;
        DetailRow.Line timeUnderTension = null;
        if (summary.durationSeconds() > 0) {
            long millis = summary.durationSeconds() * 1000L;
            timeUnderTension = new DetailRow.Line(
                    getString(R.string.history_time_under_tension, Durations.clock(millis)),
                    getString(R.string.history_time_under_tension, Durations.spoken(millis)));
        }
        DetailRow.Comparison comparison = detail.hasComparison() ? comparisonOf(detail) : null;
        String noComparison = comparison == null
                ? getString(R.string.history_comparison_none) : null;
        String exercisesTitle = detail.exercises().isEmpty()
                ? null : getString(R.string.history_exercises_title);
        return new DetailRow.Summary("summary", summary.name(), dateTime, duration, sets, volume,
                // Null means no rating at all, and the control shows nothing chosen: it is never
                // drawn as a rating of zero.
                outsideVolume, timeUnderTension, detail.rating(), detail.notes(), comparison,
                noComparison, exercisesTitle);
    }

    // ------------------------------------------------------------------ comparison

    private DetailRow.Comparison comparisonOf(SessionDetail detail) {
        SessionComparison comparison = detail.comparison();
        // The previous session's zone, not this one's: the same workout either side of a flight
        // belongs to the day it was performed on, and the history list already names it that way.
        String previousDay = SessionDates.day(comparison.previousStartedAt(),
                comparison.previousTimeZone());
        return new DetailRow.Comparison(
                getString(R.string.history_comparison_subtitle, previousDay),
                metricLine(R.string.history_comparison_volume, comparison.volumeGrams(),
                        this::loadText, this::loadText),
                metricLine(R.string.history_comparison_sets, comparison.performedSets(),
                        Long::toString, Long::toString),
                metricLine(R.string.history_comparison_reps, comparison.totalReps(),
                        Long::toString, Long::toString),
                metricLine(R.string.history_comparison_time, comparison.effectiveMs(),
                        Durations::clock, Durations::spoken));
    }

    /**
     * "Volume: 4200 kg (arrow, difference, percent)", and the same line as it should be spoken.
     * The arrow alone means nothing to a screen reader, so the spoken form says "aumentou" (rose),
     * "diminuiu" (fell) or "sem mudanca" (no change) in its place.
     *
     * @param shown  formats a value of this metric for the eye ("3:20")
     * @param spoken formats it for a screen reader ("3 minutos e 20 segundos")
     */
    private DetailRow.Line metricLine(@StringRes int label, MetricChange change,
                                      LongFunction<String> shown, LongFunction<String> spoken) {
        String name = getString(label);
        long size = Math.abs(change.delta());
        return new DetailRow.Line(
                getString(R.string.history_comparison_line, name, shown.apply(change.current()),
                        visibleChange(change, shown.apply(size))),
                getString(R.string.history_comparison_line, name, spoken.apply(change.current()),
                        spokenChange(change, spoken.apply(size))));
    }

    /** "up 200 kg - 5.0%", "down 3", or "= igual" for a tie. Direction lives in the arrow only. */
    private String visibleChange(MetricChange change, String size) {
        String arrow = getString(arrowOf(change.direction()));
        if (change.direction() == MetricChange.Direction.SAME) {
            // A tie has no size to report, and its percentage could only ever be zero.
            return getString(R.string.history_comparison_change_absolute, arrow,
                    getString(R.string.history_comparison_unchanged));
        }
        return withPercent(change, arrow, size);
    }

    private String spokenChange(MetricChange change, String size) {
        String word = getString(wordOf(change.direction()));
        if (change.direction() == MetricChange.Direction.SAME) {
            return word;
        }
        return withPercent(change, word, size);
    }

    /**
     * The change with its percentage, but only when there is one to give (PRODUCT_SPEC section 11).
     *
     * <p>{@link MetricChange#hasPercent()} is asked FIRST, always: {@code percent()} throws when
     * the earlier value was zero, and a made-up percentage there is exactly the number nobody could
     * justify. When it is false the change is still shown, as the arrow and the absolute
     * difference.
     */
    private String withPercent(MetricChange change, String marker, String size) {
        if (change.hasPercent()) {
            // Direction is already in the marker, so the percentage is a size like the difference.
            String percent = getString(R.string.history_comparison_percent_value,
                    String.format(locale(), "%.1f", Math.abs(change.percent())));
            return getString(R.string.history_comparison_change_percent, marker, size, percent);
        }
        return getString(R.string.history_comparison_change_absolute, marker, size);
    }

    @StringRes
    private static int arrowOf(MetricChange.Direction direction) {
        return switch (direction) {
            case UP -> R.string.history_comparison_up;
            case DOWN -> R.string.history_comparison_down;
            case SAME -> R.string.history_comparison_same;
        };
    }

    @StringRes
    private static int wordOf(MetricChange.Direction direction) {
        return switch (direction) {
            case UP -> R.string.history_comparison_direction_up;
            case DOWN -> R.string.history_comparison_direction_down;
            case SAME -> R.string.history_comparison_direction_same;
        };
    }

    // ------------------------------------------------- the comparison, exercise by exercise

    /**
     * The HIS-04 table: one row per exercise both sessions had, its sets underneath when opened,
     * and a line naming the exercises only one of them had.
     *
     * <p>Nothing here is drawn when there is no previous session: the summary already says so, and
     * an empty table under a sentence explaining the emptiness is noise.
     */
    private void addComparisonRows(List<DetailRow> rows, SessionDetail detail,
                                   SessionDetailViewModel.State state) {
        SessionComparison comparison = detail.comparison();
        if (comparison == null) {
            return;
        }
        boolean anything = !comparison.exercises().isEmpty()
                || !comparison.addedExercises().isEmpty()
                || !comparison.droppedExercises().isEmpty();
        if (!anything) {
            return;
        }
        rows.add(new DetailRow.SectionTitle("comparison:title",
                getString(R.string.history_comparison_by_exercise)));
        for (ExerciseComparison exercise : comparison.exercises()) {
            boolean expanded = state.isExpanded(exercise.current().id());
            rows.add(comparisonRow(exercise, expanded));
            if (expanded) {
                for (ExerciseComparison.SetPair pair : exercise.sets()) {
                    rows.add(setComparisonRow(exercise, pair));
                }
            }
        }
        // Named, never dropped: an exercise that is in one session and not the other is a fact
        // about the workout, and leaving it out would make the table look like the whole story.
        addNote(rows, "comparison:added", R.string.history_comparison_added,
                comparison.addedExercises());
        addNote(rows, "comparison:dropped", R.string.history_comparison_dropped,
                comparison.droppedExercises());
    }

    private void addNote(List<DetailRow> rows, String id, @StringRes int label,
                         List<String> names) {
        if (!names.isEmpty()) {
            rows.add(new DetailRow.ComparisonNote(id, getString(label, joined(names))));
        }
    }

    private DetailRow.ExerciseComparisonRow comparisonRow(ExerciseComparison exercise,
                                                          boolean expanded) {
        int sets = exercise.sets().size();
        String expandLabel = null;
        if (sets > 0) {
            expandLabel = expanded
                    ? getString(R.string.history_comparison_hide_sets)
                    : getResources().getQuantityString(
                            R.plurals.history_comparison_show_sets, sets, sets);
        }
        // A bodyweight or timed exercise has no load volume on either side. "=" there would read
        // as "the same amount of load", when the truth is that load is not what moved.
        boolean hasVolume = exercise.hasVolume();
        return new DetailRow.ExerciseComparisonRow(
                "comparison:" + exercise.current().id(),
                exercise.current().id(),
                exercise.name(),
                hasVolume ? metricLine(R.string.history_comparison_volume, exercise.volumeGrams(),
                        this::loadText, this::loadText) : null,
                hasVolume ? null : getString(R.string.history_exercise_no_volume),
                metricLine(R.string.history_comparison_sets, exercise.performedSets(),
                        Long::toString, Long::toString),
                metricLine(R.string.history_comparison_reps, exercise.totalReps(),
                        Long::toString, Long::toString),
                expandLabel,
                expanded);
    }

    private DetailRow.SetComparisonRow setComparisonRow(ExerciseComparison exercise,
                                                        ExerciseComparison.SetPair pair) {
        // Spelled out, not the bare "1" the set list uses: here the number sits next to two sets
        // of values and a column of its own would be the only thing saying what it is.
        String number = getString(R.string.history_comparison_set_number, pair.number());
        // Each side read with ITS OWN snapshot: the same exercise can have been logged with
        // dumbbells then and a barbell now, and the two numbers are not the same measurement.
        String previous = cellOf(exercise.previous(), pair.previous());
        String current = cellOf(exercise.current(), pair.current());
        String change = pair.volumeGrams() == null
                ? null
                : getString(arrowOf(pair.volumeGrams().direction()));
        String spokenChange = pair.volumeGrams() == null
                ? getString(R.string.history_comparison_no_change_to_show)
                : getString(wordOf(pair.volumeGrams().direction()));
        String spoken = getString(R.string.history_comparison_set_spoken, number,
                spokenCellOf(exercise.previous(), pair.previous()),
                spokenCellOf(exercise.current(), pair.current()),
                spokenChange);
        return new DetailRow.SetComparisonRow(
                "comparison:set:" + exercise.current().id() + ":" + pair.number()
                        + (pair.current() == null ? ":previous-only" : ""),
                number, previous, current, change, spoken);
    }

    /** What one side of a set row shows: what was done, that it was not done, or that it is absent. */
    private String cellOf(SessionExercise exercise, @Nullable LoggedSet set) {
        if (set == null) {
            return getString(R.string.history_set_no_value);
        }
        return set.isCompleted()
                ? performedText(exercise, set.values())
                : getString(R.string.history_set_skipped);
    }

    /** The same, spoken: a dash is not a word, and "nothing" is not the same as "not done". */
    private String spokenCellOf(SessionExercise exercise, @Nullable LoggedSet set) {
        return set == null ? getString(R.string.history_comparison_set_absent)
                : cellOf(exercise, set);
    }

    // ------------------------------------------------------------------ exercises and sets

    private DetailRow.ExerciseHeader exerciseRow(SessionExerciseSummary summary) {
        // Performed out of everything the exercise had, so the skipped sets show in the gap.
        int total = summary.performedSets() + summary.skippedSets();
        String sets = getResources().getQuantityString(R.plurals.session_progress, total,
                summary.performedSets(), total);
        String volume = summary.hasVolume()
                ? getString(R.string.summary_volume, loadText(summary.totals().loadGrams()))
                : getString(R.string.history_exercise_no_volume);
        return new DetailRow.ExerciseHeader("exercise:" + summary.sessionExerciseId(),
                summary.name(), sets, volume);
    }

    private DetailRow.SetRow setRow(SessionExercise exercise, LoggedSet set) {
        String number = set.workingNumber() == null
                ? getString(R.string.session_set_warmup_badge)
                : getString(R.string.session_set_number, set.workingNumber());
        // Only a performed set has results. A skipped one may still hold typed values (they are
        // kept on purpose), but they were not performed, so they are not shown as if they were.
        String performed = set.isCompleted()
                ? performedText(exercise, set.values())
                : getString(R.string.history_set_skipped);
        return new DetailRow.SetRow("set:" + set.id(), number, performed,
                plannedText(exercise, set));
    }

    /** What was actually done, slot by slot: a slot nobody filled in is a dash, never a zero. */
    private String performedText(SessionExercise exercise, SetValues values) {
        TrackingType tracking = exercise.trackingType();
        String none = getString(R.string.history_set_no_value);
        List<String> parts = new ArrayList<>();
        if (tracking.usesDuration()) {
            Integer seconds = values.durationSeconds();
            parts.add(seconds == null ? none : getString(R.string.session_duration_value, seconds));
        }
        if (tracking.usesReps()) {
            String reps = repsText(values);
            parts.add(reps == null ? none : reps);
        }
        if (tracking.usesWeight()) {
            Weight weight = values.weight();
            parts.add(weight == null ? none : weightText(exercise, weight));
        }
        return parts.isEmpty() ? none : joined(parts);
    }

    /** What the session's snapshot planned ("Planejado: 12 reps - 40 kg"), or null for nothing. */
    @Nullable
    private String plannedText(SessionExercise exercise, LoggedSet set) {
        TrackingType tracking = exercise.trackingType();
        boolean timed = tracking.usesDuration() && !tracking.usesReps();
        List<String> parts = new ArrayList<>();
        if (timed && set.plannedDurationSeconds() != null) {
            parts.add(getString(R.string.session_duration_value, set.plannedDurationSeconds()));
        } else if (set.plannedReps() != null) {
            parts.add(getString(R.string.session_reps_value, set.plannedReps().format()));
        }
        if (set.plannedWeight() != null && tracking.usesWeight()) {
            parts.add(weightText(exercise, set.plannedWeight()));
        }
        return parts.isEmpty() ? null : getString(R.string.history_set_planned, joined(parts));
    }

    /** "10 reps", "E 10 / D 9" when each side was logged, or null when no reps were informed. */
    @Nullable
    private String repsText(SetValues values) {
        if (values.repsLeft() != null || values.repsRight() != null) {
            return getString(R.string.session_reps_per_side,
                    sideReps(values.repsLeft()), sideReps(values.repsRight()));
        }
        if (values.reps() != null) {
            return getString(R.string.session_reps_value, String.valueOf(values.reps()));
        }
        return null;
    }

    /** One side of a per-side set: the side that was not informed is a dash, not a zero. */
    private String sideReps(@Nullable Integer reps) {
        return reps == null ? getString(R.string.history_set_no_value) : String.valueOf(reps);
    }

    /**
     * The load as it was typed: "40 kg", or "12 kg/halter" when it is per implement. The summed
     * load of two dumbbells is never shown as the set's load (PRODUCT_SPEC section 6.4).
     */
    private String weightText(SessionExercise exercise, Weight weight) {
        String value = NumberInput.formatDecimal(weight.in(UNIT), locale());
        return getString(exercise.isLoadPerImplement()
                        ? R.string.session_weight_per_implement_value
                        : R.string.session_weight_value,
                value, UNIT.symbol());
    }

    /** Grams in the display unit, formatted exactly like the summary dialog does. */
    private String loadText(long grams) {
        String value = NumberInput.formatDecimal(UNIT.fromGrams(grams), locale());
        return getString(R.string.session_weight_value, value, UNIT.symbol());
    }

    private String joined(List<String> parts) {
        return String.join(getString(R.string.separator_dot), parts);
    }

    private Locale locale() {
        return getResources().getConfiguration().getLocales().get(0);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding.list.setAdapter(null);
        adapter = null;
        binding = null;
    }
}

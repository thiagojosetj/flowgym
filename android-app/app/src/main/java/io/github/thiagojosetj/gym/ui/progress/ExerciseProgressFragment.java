package io.github.thiagojosetj.gym.ui.progress;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import io.github.thiagojosetj.gym.AppContainer;
import io.github.thiagojosetj.gym.R;
import io.github.thiagojosetj.gym.databinding.FragmentExerciseProgressBinding;
import io.github.thiagojosetj.gym.databinding.ItemExerciseProgressRecordBinding;
import io.github.thiagojosetj.gym.databinding.ItemExerciseProgressSessionBinding;
import io.github.thiagojosetj.gym.domain.model.WeightUnit;
import io.github.thiagojosetj.gym.domain.progress.ExercisePersonalRecords;
import io.github.thiagojosetj.gym.domain.progress.ExerciseProgress;
import io.github.thiagojosetj.gym.domain.progress.ProgressWindow;
import io.github.thiagojosetj.gym.ui.common.NumberInput;
import io.github.thiagojosetj.gym.ui.common.ViewModelFactories;

/**
 * One exercise across its history, with the personal records of PRODUCT_SPEC section 10 (PRG-03).
 *
 * <p>The records are read from the WHOLE history and say so on screen. A "record" measured over a
 * slice of the history would not be a record, so the window filter of PRG-02 scopes the list and
 * the chart below and never these three figures.
 */
public class ExerciseProgressFragment extends Fragment {

    private static final String ARG_EXERCISE_ID = "exerciseId";

    /** Units are kg for now; lb support is planned (docs/ROADMAP.md). */
    private static final WeightUnit UNIT = WeightUnit.KILOGRAM;

    /**
     * How many loads the "most repetitions at this load" list shows.
     *
     * <p>Section 10 keeps one record per exact load, and somebody who has trained an exercise for
     * a year has dozens. The rest are counted out loud below the list rather than dropped in
     * silence.
     */
    private static final int MAX_REPS_ROWS = 5;

    /** Material's own disabled opacity, so a dimmed window button matches the rest of the app. */
    private static final float DISABLED_ALPHA = 0.38f;

    private FragmentExerciseProgressBinding binding;
    private ExerciseProgressViewModel viewModel;
    /** Set while the stored window is applied, so checking a button does not report a tap. */
    private boolean applyingState;

    /** Navigation arguments for this screen (ADR-0018: no Safe Args plugin). */
    public static Bundle args(String exerciseId) {
        Bundle bundle = new Bundle();
        bundle.putString(ARG_EXERCISE_ID, exerciseId);
        return bundle;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentExerciseProgressBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        String exerciseId = requireArguments().getString(ARG_EXERCISE_ID);
        AppContainer app = ViewModelFactories.container(this);
        viewModel = new ViewModelProvider(this, ViewModelFactories.of(
                ExerciseProgressViewModel.class,
                () -> new ExerciseProgressViewModel(app.progress, exerciseId)))
                .get(ExerciseProgressViewModel.class);

        binding.windowGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (applyingState || !isChecked) {
                return;
            }
            viewModel.show(windowOf(checkedId));
        });
        binding.measureGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (applyingState || !isChecked) {
                return;
            }
            viewModel.show(measureOf(checkedId));
        });
        binding.chart.setOnPointSelectedListener(this::readOut);

        viewModel.state().observe(getViewLifecycleOwner(), this::render);
    }

    private void render(ExerciseProgressViewModel.State state) {
        if (state.loading()) {
            // Nothing is drawn while the read is in flight. A skeleton of the records would be a
            // picture of numbers nobody has measured yet.
            return;
        }
        binding.failed.setVisibility(state.failed() ? View.VISIBLE : View.GONE);
        ExerciseProgress progress = state.progress();
        if (progress == null) {
            binding.content.setVisibility(View.GONE);
            binding.emptyState.setVisibility(View.GONE);
            return;
        }
        binding.exerciseName.setText(progress.name());
        boolean any = !progress.isEmpty();
        binding.span.setVisibility(any ? View.VISIBLE : View.GONE);
        binding.content.setVisibility(any ? View.VISIBLE : View.GONE);
        binding.emptyState.setVisibility(any ? View.GONE : View.VISIBLE);
        if (!any) {
            return;
        }
        binding.span.setText(spanOf(progress));
        renderRecords(progress.records());
        renderWindow(state.window(), progress.sessions().size(), state.shown().size());
        renderChart(state.measure(), state.shown(), progress.records());
        renderSessions(state.shown(), progress.records());
    }

    /**
     * The chart of PRG-01, or the reason there is none.
     *
     * <p>Every case that cannot be drawn is a sentence, never a blank space: one session is not a
     * trend, a body-weight exercise has no load volume, and an exercise logged on two different
     * bases has no honest load line at all - 20 kg per dumbbell and a 20 kg bar are the same
     * number and twice the work, so a line through both would be comparing nothing.
     */
    private void renderChart(ChartMeasure measure, List<ExerciseProgress.Point> points,
                             ExercisePersonalRecords records) {
        applyingState = true;
        binding.measureGroup.check(idOf(measure));
        applyingState = false;

        String blocked = whyNoChart(measure, points);
        binding.chartNote.setVisibility(blocked == null ? View.GONE : View.VISIBLE);
        binding.chartCard.setVisibility(blocked == null ? View.VISIBLE : View.GONE);
        if (blocked != null) {
            binding.chartNote.setText(blocked);
            return;
        }
        boolean perImplement = measure == ChartMeasure.LOAD && points.get(0).loadPerImplement();
        String recordSession = recordSessionOf(measure, records);
        List<SessionChartView.Point> plotted = new ArrayList<>(points.size());
        for (ExerciseProgress.Point point : points) {
            plotted.add(new SessionChartView.Point(valueOf(measure, point),
                    dayOf(point.localDate()), point.sessionId().equals(recordSession)));
        }
        binding.chartCaption.setText(captionOf(measure, perImplement));
        // The basis is in the caption, not on every axis tick: the axis stays short and the claim
        // is still stated, once, where it is read first.
        binding.chart.setPoints(plotted, value -> measure == ChartMeasure.REPS
                ? String.valueOf(value)
                : loadText(value, false));
        binding.chartHalo.setVisibility(recordSession == null ? View.GONE : View.VISIBLE);
        binding.chart.setContentDescription(spokenChart(measure, perImplement, points));
        readOut(binding.chart.selectedIndex());
    }

    /** The line under the chart: what the selected session actually was, as text. */
    private void readOut(int index) {
        ExerciseProgressViewModel.State state = viewModel.state().getValue();
        if (state == null || index < 0 || index >= state.shown().size()) {
            return;
        }
        ExerciseProgress.Point point = state.shown().get(index);
        ChartMeasure measure = state.measure();
        String value = measure == ChartMeasure.REPS
                ? String.valueOf(point.totalReps())
                : loadText(valueOf(measure, point),
                        measure == ChartMeasure.LOAD && point.loadPerImplement());
        String record = state.progress() == null
                ? null : recordSessionOf(measure, state.progress().records());
        binding.chartReading.setText(getString(
                point.sessionId().equals(record)
                        ? R.string.exercise_progress_chart_reading_record
                        : R.string.exercise_progress_chart_reading,
                dayOf(point.localDate()), value));
    }

    @Nullable
    private String whyNoChart(ChartMeasure measure, List<ExerciseProgress.Point> points) {
        if (points.size() < 2) {
            return getString(R.string.exercise_progress_chart_needs_two);
        }
        if (measure == ChartMeasure.LOAD && mixesBases(points)) {
            return getString(R.string.exercise_progress_chart_mixed_basis);
        }
        boolean anything = false;
        for (ExerciseProgress.Point point : points) {
            anything |= valueOf(measure, point) != 0L;
        }
        if (anything) {
            return null;
        }
        switch (measure) {
            case LOAD:
                return getString(R.string.exercise_progress_chart_no_load);
            case VOLUME:
                return getString(R.string.exercise_progress_chart_no_volume);
            default:
                // Repetitions at zero across every session cannot happen: a session with no
                // completed set is not an appearance.
                return getString(R.string.exercise_progress_chart_needs_two);
        }
    }

    private static boolean mixesBases(List<ExerciseProgress.Point> points) {
        boolean first = points.get(0).loadPerImplement();
        for (ExerciseProgress.Point point : points) {
            if (point.loadPerImplement() != first) {
                return true;
            }
        }
        return false;
    }

    private static long valueOf(ChartMeasure measure, ExerciseProgress.Point point) {
        switch (measure) {
            case VOLUME:
                return point.volumeGrams();
            case REPS:
                return point.totalReps();
            default:
                return point.maxLoadGrams();
        }
    }

    /**
     * Which session the halo marks, for this measure.
     *
     * <p>Null for repetitions on purpose: section 10 has no "most repetitions in a session"
     * record - its repetition record is per load - and drawing a halo for a record that does not
     * exist would be inventing one.
     */
    @Nullable
    private static String recordSessionOf(ChartMeasure measure,
                                          ExercisePersonalRecords records) {
        ExercisePersonalRecords.Record record = measure == ChartMeasure.LOAD
                ? records.maxLoad()
                : measure == ChartMeasure.VOLUME ? records.maxSessionVolume() : null;
        return record == null ? null : record.sessionId();
    }

    private String captionOf(ChartMeasure measure, boolean perImplement) {
        switch (measure) {
            case VOLUME:
                return getString(R.string.exercise_progress_chart_volume);
            case REPS:
                return getString(R.string.exercise_progress_chart_reps);
            default:
                return getString(perImplement
                        ? R.string.exercise_progress_chart_load_per_implement
                        : R.string.exercise_progress_chart_load);
        }
    }

    /** A canvas says nothing to a screen reader, so the chart states what it draws. */
    private String spokenChart(ChartMeasure measure, boolean perImplement,
                               List<ExerciseProgress.Point> points) {
        long min = valueOf(measure, points.get(0));
        long max = min;
        for (ExerciseProgress.Point point : points) {
            min = Math.min(min, valueOf(measure, point));
            max = Math.max(max, valueOf(measure, point));
        }
        return getResources().getQuantityString(R.plurals.exercise_progress_chart_spoken,
                points.size(), captionOf(measure, perImplement), points.size(),
                dayOf(points.get(0).localDate()),
                dayOf(points.get(points.size() - 1).localDate()),
                measure == ChartMeasure.REPS ? String.valueOf(min) : loadText(min, false),
                measure == ChartMeasure.REPS ? String.valueOf(max) : loadText(max, false));
    }

    private static int idOf(ChartMeasure measure) {
        switch (measure) {
            case VOLUME:
                return R.id.measure_volume;
            case REPS:
                return R.id.measure_reps;
            default:
                return R.id.measure_load;
        }
    }

    private static ChartMeasure measureOf(int buttonId) {
        if (buttonId == R.id.measure_volume) {
            return ChartMeasure.VOLUME;
        }
        if (buttonId == R.id.measure_reps) {
            return ChartMeasure.REPS;
        }
        return ChartMeasure.LOAD;
    }

    /**
     * The window buttons: their labels, which of them lead anywhere, and which one is on.
     *
     * <p>A window that would leave nothing out is disabled and dimmed rather than quietly
     * equivalent to "Tudo" - the same rule the history calendar's arrows follow, and for the same
     * reason: a control that looks live and does nothing reads as a defect.
     */
    private void renderWindow(ProgressWindow window, int total, int shown) {
        applyingState = true;
        for (ProgressWindow option : ProgressWindow.values()) {
            View button = binding.getRoot().findViewById(idOf(option));
            if (option != ProgressWindow.ALL) {
                ((android.widget.Button) button).setText(numberOf(option.size()));
                button.setContentDescription(getResources().getQuantityString(
                        R.plurals.exercise_progress_window_last, option.size(), option.size()));
            } else {
                button.setContentDescription(
                        getString(R.string.exercise_progress_window_all_spoken));
            }
            boolean usable = option == ProgressWindow.ALL || option.narrows(total);
            button.setEnabled(usable);
            button.setAlpha(usable ? 1f : DISABLED_ALPHA);
        }
        binding.windowGroup.check(idOf(window));
        applyingState = false;
        // Nothing disappears in silence: when the window hides sessions, it says how many of how
        // many are on screen.
        boolean narrowed = shown < total;
        binding.showing.setVisibility(narrowed ? View.VISIBLE : View.GONE);
        if (narrowed) {
            binding.showing.setText(getResources().getQuantityString(
                    R.plurals.exercise_progress_showing, total, shown, total));
        }
    }

    private static int idOf(ProgressWindow window) {
        switch (window) {
            case LAST_5:
                return R.id.window_5;
            case LAST_10:
                return R.id.window_10;
            case LAST_30:
                return R.id.window_30;
            default:
                return R.id.window_all;
        }
    }

    private static ProgressWindow windowOf(int buttonId) {
        if (buttonId == R.id.window_5) {
            return ProgressWindow.LAST_5;
        }
        if (buttonId == R.id.window_10) {
            return ProgressWindow.LAST_10;
        }
        if (buttonId == R.id.window_30) {
            return ProgressWindow.LAST_30;
        }
        return ProgressWindow.ALL;
    }

    /** The window's size in the reader's own digits, not a hardcoded numeral. */
    private String numberOf(int value) {
        return NumberFormat.getIntegerInstance(locale()).format(value);
    }

    /** "12 sessões · de 2 de jun. a 28 de set.", or one date when there is only one session. */
    private String spanOf(ExerciseProgress progress) {
        List<ExerciseProgress.Point> points = progress.sessions();
        String count = getResources().getQuantityString(R.plurals.exercise_progress_sessions,
                points.size(), points.size());
        String first = dayOf(points.get(0).localDate());
        if (progress.isSingleSession()) {
            return getString(R.string.exercise_progress_span_single, count, first);
        }
        return getString(R.string.exercise_progress_span, count, first,
                dayOf(points.get(points.size() - 1).localDate()));
    }

    private void renderRecords(ExercisePersonalRecords records) {
        binding.recordRows.removeAllViews();
        boolean anyLoadRecord = records.maxLoad() != null || records.maxSetVolume() != null
                || records.maxSessionVolume() != null;
        binding.recordsCard.setVisibility(anyLoadRecord ? View.VISIBLE : View.GONE);
        binding.noRecords.setVisibility(anyLoadRecord ? View.GONE : View.VISIBLE);
        if (anyLoadRecord) {
            addRecord(R.string.exercise_progress_record_max_load, records.maxLoad());
            addRecord(R.string.exercise_progress_record_set_volume, records.maxSetVolume());
            addRecord(R.string.exercise_progress_record_session_volume,
                    records.maxSessionVolume());
        }
        renderRepsAtLoad(records.repsAtLoad());
    }

    /** A record that was never set draws no row: an absent record is not a zero. */
    private void addRecord(int labelId, @Nullable ExercisePersonalRecords.Record record) {
        if (record == null) {
            return;
        }
        String value = getString(R.string.exercise_progress_record_value,
                loadText(record.value(), record.loadPerImplement()), dayOf(record.localDate()));
        // Section 10: the first time is a baseline, not an achievement. Saying so stops the screen
        // congratulating somebody for turning up once.
        if (record.isBaseline()) {
            value = value + getString(R.string.separator_dot)
                    + getString(R.string.exercise_progress_record_baseline);
        }
        addLabelled(binding.recordRows, getString(labelId), value);
    }

    private void renderRepsAtLoad(List<ExercisePersonalRecords.RepsAtLoad> reps) {
        binding.repsRows.removeAllViews();
        boolean any = !reps.isEmpty();
        binding.repsTitle.setVisibility(any ? View.VISIBLE : View.GONE);
        int shown = Math.min(reps.size(), MAX_REPS_ROWS);
        for (int i = 0; i < shown; i++) {
            ExercisePersonalRecords.RepsAtLoad row = reps.get(i);
            String count = getResources().getQuantityString(R.plurals.exercise_progress_reps,
                    row.reps(), row.reps());
            addLabelled(binding.repsRows, loadText(row.loadGrams(), row.loadPerImplement()),
                    count + getString(R.string.separator_dot) + dayOf(row.localDate()));
        }
        int hidden = reps.size() - shown;
        binding.moreLoads.setVisibility(hidden > 0 ? View.VISIBLE : View.GONE);
        if (hidden > 0) {
            // Nothing is dropped in silence (PRODUCT_SPEC section 9's rule, applied to a list).
            binding.moreLoads.setText(getResources().getQuantityString(
                    R.plurals.exercise_progress_more_loads, hidden, hidden));
        }
    }

    private void addLabelled(ViewGroup parent, String label, String value) {
        ItemExerciseProgressRecordBinding row = ItemExerciseProgressRecordBinding.inflate(
                LayoutInflater.from(requireContext()), parent, false);
        row.recordLabel.setText(label);
        row.recordValue.setText(value);
        row.getRoot().setContentDescription(getString(R.string.exercise_progress_record_spoken,
                label, value));
        parent.addView(row.getRoot());
    }

    /**
     * The sessions, newest first.
     *
     * <p>The domain hands them over oldest first, because that is the order a chart reads and the
     * order that decides what counts as a baseline. A list is scanned the other way: what happened
     * last is what the person is looking for.
     */
    private void renderSessions(List<ExerciseProgress.Point> points,
                                ExercisePersonalRecords records) {
        binding.sessionRows.removeAllViews();
        Set<String> recordHolders = recordHolders(records);
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (int i = points.size() - 1; i >= 0; i--) {
            ExerciseProgress.Point point = points.get(i);
            ItemExerciseProgressSessionBinding row = ItemExerciseProgressSessionBinding.inflate(
                    inflater, binding.sessionRows, false);
            String day = dayOf(point.localDate());
            String counts = countsOf(point);
            row.sessionDay.setText(day);
            row.sessionCounts.setText(counts);
            row.sessionRecord.setVisibility(
                    recordHolders.contains(point.sessionId()) ? View.VISIBLE : View.GONE);
            row.getRoot().setContentDescription(
                    getString(R.string.exercise_progress_point_spoken, day, counts));
            binding.sessionRows.addView(row.getRoot());
        }
    }

    /**
     * Which sessions the badge marks: the holders of the three headline records.
     *
     * <p>Deliberately not the "most repetitions at this load" holders. There is one of those per
     * distinct load, so any session with a weight nobody else used holds one - the badge would
     * land on nearly every row and stop meaning anything.
     */
    private static Set<String> recordHolders(ExercisePersonalRecords records) {
        Set<String> holders = new HashSet<>(4);
        for (ExercisePersonalRecords.Record record : new ExercisePersonalRecords.Record[] {
                records.maxLoad(), records.maxSetVolume(), records.maxSessionVolume()}) {
            if (record != null) {
                holders.add(record.sessionId());
            }
        }
        return holders;
    }

    private String countsOf(ExerciseProgress.Point point) {
        List<String> parts = new ArrayList<>(3);
        parts.add(getString(R.string.exercise_progress_point_sets, point.performedSets(),
                point.totalReps()));
        parts.add(point.hasLoad()
                ? getString(R.string.exercise_progress_point_max_load,
                        loadText(point.maxLoadGrams(), point.loadPerImplement()))
                : getString(R.string.exercise_progress_point_no_load));
        // Section 9: a body-weight or timed exercise has no load volume, and that is said rather
        // than printed as a zero - the repetitions beside it are the work that was done.
        parts.add(point.hasVolume()
                ? getString(R.string.exercise_progress_point_volume,
                        loadText(point.volumeGrams(), false))
                : getString(R.string.exercise_progress_point_no_volume));
        return String.join(getString(R.string.separator_dot), parts);
    }

    /** "40 kg", or "20 kg/halter" where that is the basis it was logged on (section 6.4). */
    private String loadText(long grams, boolean perImplement) {
        String value = NumberInput.formatDecimal(UNIT.fromGrams(grams), locale());
        return getString(perImplement
                        ? R.string.session_weight_per_implement_value
                        : R.string.session_weight_value,
                value, UNIT.symbol());
    }

    /**
     * The day as it was lived.
     *
     * <p>{@code local_date} is the calendar day of the workout in the zone it was performed in, so
     * there is no zone to apply here and no chance of the date moving when the phone travels. A
     * day that cannot be parsed is shown as stored rather than hidden.
     */
    private String dayOf(String isoDate) {
        try {
            return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                    .withLocale(locale())
                    .format(LocalDate.parse(isoDate));
        } catch (DateTimeParseException e) {
            return isoDate;
        }
    }

    private Locale locale() {
        return getResources().getConfiguration().getLocales().get(0);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}

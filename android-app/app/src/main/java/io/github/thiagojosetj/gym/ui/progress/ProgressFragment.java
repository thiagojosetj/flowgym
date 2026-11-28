package io.github.thiagojosetj.gym.ui.progress;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.datepicker.MaterialDatePicker;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.time.temporal.WeekFields;
import java.util.Locale;

import io.github.thiagojosetj.gym.AppContainer;
import io.github.thiagojosetj.gym.R;
import io.github.thiagojosetj.gym.databinding.FragmentProgressBinding;
import io.github.thiagojosetj.gym.databinding.ItemProgressMuscleBinding;
import io.github.thiagojosetj.gym.domain.progress.MuscleWorkload;
import io.github.thiagojosetj.gym.domain.progress.PeriodSession;
import io.github.thiagojosetj.gym.domain.progress.PeriodStatistics;
import io.github.thiagojosetj.gym.domain.progress.ProgressSnapshot;
import io.github.thiagojosetj.gym.domain.progress.TrainingPeriod;
import io.github.thiagojosetj.gym.ui.common.Durations;
import io.github.thiagojosetj.gym.ui.common.NumberInput;
import io.github.thiagojosetj.gym.ui.common.PickedDays;
import io.github.thiagojosetj.gym.ui.common.ViewModelFactories;
import io.github.thiagojosetj.gym.domain.model.WeightUnit;

/**
 * "Progresso": what a week or a month of training came to, and how much each muscle group got
 * (PRODUCT_SPEC PRG-04).
 *
 * <p>Everything is drawn from one state object, so the arrows, the totals and the muscle rows can
 * never be showing different reads of the database.
 */
public class ProgressFragment extends Fragment {

    /** Units are kg for now; lb support is planned (docs/ROADMAP.md). */
    private static final WeightUnit UNIT = WeightUnit.KILOGRAM;

    /** Tag the range picker is found again by after a rotation. */
    private static final String DATE_PICKER_TAG = "progress-date-range";

    private FragmentProgressBinding binding;
    private ProgressViewModel viewModel;
    /** Set while the stored period is applied, so checking a button does not report a tap. */
    private boolean applyingState;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentProgressBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        AppContainer app = ViewModelFactories.container(this);
        viewModel = new ViewModelProvider(this, ViewModelFactories.of(ProgressViewModel.class,
                () -> new ProgressViewModel(app.progress, app.clock, app.zone)))
                .get(ProgressViewModel.class);

        // Where a week starts belongs to the locale, and the locale belongs to the screen: the
        // ViewModel survives a configuration change, so it is told rather than left to guess.
        viewModel.setFirstDayOfWeek(WeekFields.of(locale()).getFirstDayOfWeek());

        binding.kindGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (applyingState || !isChecked) {
                return;
            }
            if (checkedId == R.id.kind_custom) {
                askForDates();
                return;
            }
            viewModel.show(checkedId == R.id.kind_month
                    ? TrainingPeriod.Kind.MONTH
                    : TrainingPeriod.Kind.WEEK);
        });
        // A picker open across a rotation comes back without its listeners, and the dates the
        // person chose would land nowhere. Found again by tag and rewired.
        Fragment open = getParentFragmentManager().findFragmentByTag(DATE_PICKER_TAG);
        if (open instanceof MaterialDatePicker) {
            listenTo(asRangePicker(open));
        }
        binding.measureGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (applyingState || !isChecked) {
                return;
            }
            viewModel.show(measureOf(checkedId));
        });
        binding.chart.setOnPointSelectedListener(this::readOut);
        binding.previousPeriod.setOnClickListener(v -> viewModel.step(-1));
        binding.nextPeriod.setOnClickListener(v -> viewModel.step(1));

        viewModel.state().observe(getViewLifecycleOwner(), this::render);
    }

    private void render(ProgressViewModel.State state) {
        TrainingPeriod period = state.period();
        applyingState = true;
        binding.kindGroup.check(idOf(period.kind()));
        applyingState = false;

        binding.periodTitle.setText(titleOf(period));
        arrow(binding.previousPeriod, state.canGoBack());
        arrow(binding.nextPeriod, state.canGoForward());

        ProgressSnapshot snapshot = state.snapshot();
        // Nothing ever finished is a different thing from nothing in THIS period, and the second
        // one still has arrows worth using.
        boolean noHistory = snapshot.hasNoHistory();
        boolean emptyPeriod = snapshot.statistics().isEmpty();
        binding.emptyState.setVisibility(noHistory ? View.VISIBLE : View.GONE);
        // The card holds numbers. A period with nothing in it has none, so it says that instead of
        // printing a column of zeroes - and "0 treino", which is what CLDR's rule for pt makes of
        // a zero, would be wrong twice over.
        binding.totalsCard.setVisibility(noHistory || emptyPeriod ? View.GONE : View.VISIBLE);
        binding.emptyPeriod.setVisibility(!noHistory && emptyPeriod ? View.VISIBLE : View.GONE);
        binding.chartTitle.setVisibility(noHistory || emptyPeriod ? View.GONE : View.VISIBLE);
        binding.measureGroup.setVisibility(noHistory || emptyPeriod ? View.GONE : View.VISIBLE);
        if (noHistory || emptyPeriod) {
            binding.musclesTitle.setVisibility(View.GONE);
            binding.musclesNote.setVisibility(View.GONE);
            binding.muscleRows.removeAllViews();
            binding.chartCard.setVisibility(View.GONE);
            binding.chartNote.setVisibility(View.GONE);
            return;
        }
        renderTotals(snapshot.statistics());
        renderChart(state.measure(), snapshot.sessions());
        renderMuscles(snapshot.statistics());
    }

    /**
     * The period's chart (PRG-01), or the sentence that replaces it.
     *
     * <p>Drawn from {@code snapshot.sessions()}, the very same read the totals above come from,
     * so a point and a total can never be two opinions of the same sets. Three measures and not
     * four: there is no honest "load of a session" when a session is many exercises.
     */
    private void renderChart(PeriodMeasure measure, List<PeriodSession> sessions) {
        applyingState = true;
        binding.measureGroup.check(idOf(measure));
        applyingState = false;

        String blocked = whyNoChart(measure, sessions);
        binding.chartCard.setVisibility(blocked == null ? View.VISIBLE : View.GONE);
        binding.chartNote.setVisibility(blocked == null ? View.GONE : View.VISIBLE);
        if (blocked != null) {
            binding.chartNote.setText(blocked);
            return;
        }
        List<SessionChartView.Point> plotted = new ArrayList<>(sessions.size());
        for (PeriodSession session : sessions) {
            // No halo: a period has no record of its own, and inventing one would be the chart
            // making a claim the spec never defined.
            plotted.add(new SessionChartView.Point(valueOf(measure, session),
                    dayOf(session.localDate()), false));
        }
        binding.chartCaption.setText(captionOf(measure));
        binding.chart.setPoints(plotted, value -> measure == PeriodMeasure.VOLUME
                ? loadText(value)
                : String.valueOf(value));
        binding.chart.setContentDescription(spokenChart(measure, sessions));
        readOut(binding.chart.selectedIndex());
    }

    /** The line under the chart: what the selected workout actually was, as text. */
    private void readOut(int index) {
        ProgressViewModel.State state = viewModel.state().getValue();
        if (state == null || index < 0 || index >= state.snapshot().sessions().size()) {
            return;
        }
        PeriodSession session = state.snapshot().sessions().get(index);
        PeriodMeasure measure = state.measure();
        binding.chartReading.setText(getString(R.string.progress_chart_reading,
                dayOf(session.localDate()), textOf(measure, session)));
    }

    @Nullable
    private String whyNoChart(PeriodMeasure measure, List<PeriodSession> sessions) {
        if (sessions.size() < 2) {
            return getString(R.string.progress_chart_needs_two);
        }
        if (measure != PeriodMeasure.VOLUME) {
            return null;
        }
        for (PeriodSession session : sessions) {
            if (session.volumeGrams() > 0L) {
                return null;
            }
        }
        // A flat line at zero would be a measurement. Section 9 keeps body weight and timed work
        // out of load volume, and a week of only those has none.
        return getString(R.string.progress_chart_no_volume);
    }

    private static long valueOf(PeriodMeasure measure, PeriodSession session) {
        switch (measure) {
            case SETS:
                return session.performedSets();
            case REPS:
                return session.totalReps();
            default:
                return session.volumeGrams();
        }
    }

    private String textOf(PeriodMeasure measure, PeriodSession session) {
        return measure == PeriodMeasure.VOLUME
                ? loadText(session.volumeGrams())
                : String.valueOf(valueOf(measure, session));
    }

    private String captionOf(PeriodMeasure measure) {
        switch (measure) {
            case SETS:
                return getString(R.string.progress_chart_sets);
            case REPS:
                return getString(R.string.progress_chart_reps);
            default:
                return getString(R.string.progress_chart_volume);
        }
    }

    /** A canvas says nothing to a screen reader, so the chart states what it draws. */
    private String spokenChart(PeriodMeasure measure, List<PeriodSession> sessions) {
        long min = valueOf(measure, sessions.get(0));
        long max = min;
        for (PeriodSession session : sessions) {
            min = Math.min(min, valueOf(measure, session));
            max = Math.max(max, valueOf(measure, session));
        }
        return getResources().getQuantityString(R.plurals.progress_chart_spoken, sessions.size(),
                captionOf(measure), sessions.size(), spokenDayOf(sessions.get(0).localDate()),
                spokenDayOf(sessions.get(sessions.size() - 1).localDate()),
                measure == PeriodMeasure.VOLUME ? loadText(min) : String.valueOf(min),
                measure == PeriodMeasure.VOLUME ? loadText(max) : String.valueOf(max));
    }

    private static int idOf(PeriodMeasure measure) {
        switch (measure) {
            case SETS:
                return R.id.measure_sets;
            case REPS:
                return R.id.measure_reps;
            default:
                return R.id.measure_volume;
        }
    }

    private static PeriodMeasure measureOf(int buttonId) {
        if (buttonId == R.id.measure_sets) {
            return PeriodMeasure.SETS;
        }
        if (buttonId == R.id.measure_reps) {
            return PeriodMeasure.REPS;
        }
        return PeriodMeasure.VOLUME;
    }

    /**
     * The day as it was lived, short, for an axis label; {@code local_date} already carries the
     * session's own zone, so there is no zone to apply and nothing to move when the phone travels.
     */
    private String dayOf(String isoDate) {
        return formatted(isoDate, DateTimeFormatter.ofPattern("d MMM", locale()));
    }

    /**
     * The same day, written out, for the chart's spoken description.
     *
     * <p>Not the short form: in Portuguese it abbreviates to "28 set.", and a sentence that
     * continues after it reads "28 set.. Do menor" - two full stops, because the month already
     * brought one. Spoken text is where that is least excusable.
     */
    private String spokenDayOf(String isoDate) {
        return formatted(isoDate, DateTimeFormatter
                .ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(locale()));
    }

    /** A day that cannot be parsed is shown as stored rather than hidden. */
    private String formatted(String isoDate, DateTimeFormatter formatter) {
        try {
            return formatter.format(java.time.LocalDate.parse(isoDate));
        } catch (java.time.format.DateTimeParseException e) {
            return isoDate;
        }
    }

    private void renderTotals(PeriodStatistics statistics) {
        binding.sessions.setText(getResources().getQuantityString(R.plurals.progress_sessions,
                statistics.sessions(), statistics.sessions()));
        binding.sets.setText(getString(R.string.progress_sets, statistics.performedSets(),
                statistics.totalReps()));
        show(binding.warmUps, statistics.warmUpSets() > 0
                ? getResources().getQuantityString(R.plurals.progress_warm_ups,
                        statistics.warmUpSets(), statistics.warmUpSets())
                : null);
        binding.volume.setText(statistics.hasVolume()
                ? getString(R.string.progress_volume, loadText(statistics.volumeGrams()))
                : getString(R.string.progress_no_volume));
        // PRODUCT_SPEC section 9: whatever is not in the total has to be said, never hidden.
        int outside = statistics.setsOutsideVolume();
        show(binding.outsideVolume, outside > 0
                ? getResources().getQuantityString(R.plurals.summary_outside_volume, outside,
                        outside)
                : null);
        binding.effectiveTime.setText(getString(R.string.progress_effective_time,
                Durations.clock(statistics.effectiveMs())));
    }

    private void renderMuscles(PeriodStatistics statistics) {
        boolean any = !statistics.muscles().isEmpty();
        binding.musclesTitle.setVisibility(any ? View.VISIBLE : View.GONE);
        binding.musclesNote.setVisibility(any ? View.VISIBLE : View.GONE);
        // Rebuilt rather than reused: thirteen rows at most, none of them focusable or clickable,
        // so there is no selection or accessibility focus that destroying them could throw away.
        binding.muscleRows.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (MuscleWorkload workload : statistics.muscles()) {
            ItemProgressMuscleBinding row =
                    ItemProgressMuscleBinding.inflate(inflater, binding.muscleRows, false);
            String counts = countsOf(workload);
            row.muscleName.setText(workload.name());
            row.muscleCounts.setText(counts);
            // Read as one line: a name and a number announced apart do not say which is which.
            row.getRoot().setContentDescription(getString(R.string.progress_muscle_spoken,
                    workload.name(), counts));
            binding.muscleRows.addView(row.getRoot());
        }
    }

    /**
     * Only the roles that happened.
     *
     * <p>"0 série como auxiliar" is noise, and worse than noise in Portuguese: CLDR's rule for pt
     * puts zero in the singular, so it would read "0 série". A muscle that got no assisting work
     * says nothing about assisting work.
     */
    private String countsOf(MuscleWorkload workload) {
        List<String> parts = new ArrayList<>(2);
        if (workload.primarySets() > 0) {
            parts.add(getResources().getQuantityString(R.plurals.progress_muscle_primary,
                    workload.primarySets(), workload.primarySets()));
        }
        if (workload.secondarySets() > 0) {
            parts.add(getResources().getQuantityString(R.plurals.progress_muscle_secondary,
                    workload.secondarySets(), workload.secondarySets()));
        }
        return String.join(getString(R.string.separator_dot), parts);
    }

    private String titleOf(TrainingPeriod period) {
        if (period.kind() == TrainingPeriod.Kind.MONTH) {
            return DateTimeFormatter.ofPattern("MMMM yyyy", locale()).format(period.from());
        }
        // A week and a hand-picked range read the same way: two ends. Only a month is named.
        DateTimeFormatter day = DateTimeFormatter.ofPattern("d MMM", locale());
        return getString(R.string.progress_range, day.format(period.from()),
                day.format(period.to()));
    }

    private static int idOf(TrainingPeriod.Kind kind) {
        switch (kind) {
            case MONTH:
                return R.id.kind_month;
            case CUSTOM:
                return R.id.kind_custom;
            default:
                return R.id.kind_week;
        }
    }

    /**
     * Opens Material's range picker, prefilled with whatever is on screen.
     *
     * <p>If it is dismissed without a choice the button goes back to the period that is actually
     * showing. Leaving "Período" selected while the screen still shows the week would be the
     * control lying about what it did.
     */
    private void askForDates() {
        ProgressViewModel.State state = viewModel.state().getValue();
        if (state == null) {
            return;
        }
        MaterialDatePicker<Pair<Long, Long>> picker = MaterialDatePicker.Builder.dateRangePicker()
                .setTitleText(R.string.progress_custom_title)
                .setSelection(new Pair<>(PickedDays.millisOf(state.period().from()),
                        PickedDays.millisOf(state.period().to())))
                .build();
        listenTo(picker);
        picker.show(getParentFragmentManager(), DATE_PICKER_TAG);
    }

    private void listenTo(MaterialDatePicker<Pair<Long, Long>> picker) {
        picker.addOnPositiveButtonClickListener(selection -> {
            if (selection == null || selection.first == null || selection.second == null) {
                restoreKind();
                return;
            }
            // The picker's milliseconds are UTC whatever zone the phone is in; see PickedDays.
            viewModel.showCustom(PickedDays.of(selection.first), PickedDays.of(selection.second));
        });
        picker.addOnNegativeButtonClickListener(click -> restoreKind());
        picker.addOnCancelListener(dialog -> restoreKind());
    }

    @SuppressWarnings("unchecked")
    private static MaterialDatePicker<Pair<Long, Long>> asRangePicker(Fragment fragment) {
        return (MaterialDatePicker<Pair<Long, Long>>) fragment;
    }

    /** Puts the toggle back on the period actually being shown. */
    private void restoreKind() {
        ProgressViewModel.State state = viewModel.state().getValue();
        if (binding == null || state == null) {
            return;
        }
        applyingState = true;
        binding.kindGroup.check(idOf(state.period().kind()));
        applyingState = false;
    }

    /** Dimmed as well as disabled: an arrow that looks live and does nothing reads as a defect. */
    private void arrow(View button, boolean enabled) {
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : 0.38f);
    }

    private void show(android.widget.TextView view, @Nullable String text) {
        view.setText(text);
        view.setVisibility(text == null ? View.GONE : View.VISIBLE);
    }

    private String loadText(long grams) {
        return getString(R.string.session_weight_value,
                NumberInput.formatDecimal(UNIT.fromGrams(grams), locale()), UNIT.symbol());
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

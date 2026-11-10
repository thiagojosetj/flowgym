package io.github.thiagojosetj.gym.ui.progress;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

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
import io.github.thiagojosetj.gym.domain.progress.PeriodStatistics;
import io.github.thiagojosetj.gym.domain.progress.ProgressSnapshot;
import io.github.thiagojosetj.gym.domain.progress.TrainingPeriod;
import io.github.thiagojosetj.gym.ui.common.Durations;
import io.github.thiagojosetj.gym.ui.common.NumberInput;
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
            viewModel.show(checkedId == R.id.kind_month
                    ? TrainingPeriod.Kind.MONTH
                    : TrainingPeriod.Kind.WEEK);
        });
        binding.previousPeriod.setOnClickListener(v -> viewModel.step(-1));
        binding.nextPeriod.setOnClickListener(v -> viewModel.step(1));

        viewModel.state().observe(getViewLifecycleOwner(), this::render);
    }

    private void render(ProgressViewModel.State state) {
        TrainingPeriod period = state.period();
        applyingState = true;
        binding.kindGroup.check(period.kind() == TrainingPeriod.Kind.MONTH
                ? R.id.kind_month : R.id.kind_week);
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
        if (noHistory || emptyPeriod) {
            binding.musclesTitle.setVisibility(View.GONE);
            binding.musclesNote.setVisibility(View.GONE);
            binding.muscleRows.removeAllViews();
            return;
        }
        renderTotals(snapshot.statistics());
        renderMuscles(snapshot.statistics());
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
        DateTimeFormatter day = DateTimeFormatter.ofPattern("d MMM", locale());
        return getString(R.string.progress_range, day.format(period.from()),
                day.format(period.to()));
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

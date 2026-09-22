package io.github.thiagojosetj.gym.ui.library;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;

import io.github.thiagojosetj.gym.AppContainer;
import io.github.thiagojosetj.gym.R;
import io.github.thiagojosetj.gym.databinding.FragmentExerciseDetailBinding;
import io.github.thiagojosetj.gym.domain.library.ExerciseDetail;
import io.github.thiagojosetj.gym.domain.library.MuscleLink;
import io.github.thiagojosetj.gym.domain.model.LoadBasis;
import io.github.thiagojosetj.gym.domain.model.Laterality;
import io.github.thiagojosetj.gym.ui.common.ViewModelFactories;

/** Exercise detail (PRODUCT_SPEC LIB-04): primary muscle highlighted, secondaries smaller. */
public class ExerciseDetailFragment extends Fragment {

    private static final String ARG_EXERCISE_ID = "exerciseId";

    private FragmentExerciseDetailBinding binding;

    /** Navigation arguments for this screen (ADR-0018). */
    public static Bundle args(String exerciseId) {
        Bundle bundle = new Bundle();
        bundle.putString(ARG_EXERCISE_ID, exerciseId);
        return bundle;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentExerciseDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        String exerciseId = requireArguments().getString(ARG_EXERCISE_ID);
        AppContainer app = ViewModelFactories.container(this);
        ExerciseDetailViewModel viewModel = new ViewModelProvider(this, ViewModelFactories.of(
                ExerciseDetailViewModel.class, () -> new ExerciseDetailViewModel(app.exercises, exerciseId)))
                .get(ExerciseDetailViewModel.class);

        viewModel.state().observe(getViewLifecycleOwner(), state -> {
            if (state.loading()) {
                return;
            }
            if (state.detail() == null) {
                binding.notFound.setVisibility(View.VISIBLE);
                binding.content.setVisibility(View.GONE);
            } else {
                bind(state.detail());
                binding.content.setVisibility(View.VISIBLE);
            }
        });
    }

    private void bind(ExerciseDetail detail) {
        String separator = getString(R.string.separator_dot);
        binding.name.setText(detail.name());
        binding.customBadge.setVisibility(detail.custom() ? View.VISIBLE : View.GONE);
        setOptional(binding.description, null, detail.description());

        List<String> primaries = new ArrayList<>();
        for (MuscleLink link : detail.primaryMuscles()) {
            primaries.add(link.qualifiedName());
        }
        binding.primaryMuscles.setText(String.join("\n", primaries));

        binding.secondaryChips.removeAllViews();
        for (MuscleLink link : detail.secondaryMuscles()) {
            Chip chip = new Chip(requireContext());
            chip.setText(link.name());
            chip.setClickable(false);
            chip.setCheckable(false);
            chip.setFocusable(false);
            binding.secondaryChips.addView(chip);
        }
        boolean hasSecondary = !detail.secondaryMuscles().isEmpty();
        binding.secondaryTitle.setVisibility(hasSecondary ? View.VISIBLE : View.GONE);
        binding.secondaryChips.setVisibility(hasSecondary ? View.VISIBLE : View.GONE);

        binding.equipment.setText(String.join(separator, detail.equipmentNames()));
        binding.logging.setText(loggingGuidance(detail));

        binding.steps.removeAllViews();
        int number = 1;
        for (String step : detail.instructionSteps()) {
            TextView row = (TextView) getLayoutInflater().inflate(R.layout.view_instruction_step, binding.steps, false);
            row.setText(getString(R.string.detail_step, number++, step));
            binding.steps.addView(row);
        }
        binding.howToTitle.setVisibility(detail.instructionSteps().isEmpty() ? View.GONE : View.VISIBLE);

        setOptional(binding.tips, binding.tipsTitle, detail.tips());
        setOptional(binding.mistakes, binding.mistakesTitle, detail.commonMistakes());
        setOptional(binding.notes, binding.notesTitle, detail.notes());
    }

    /** Explains how loads and sides are recorded for this exercise (PRODUCT_SPEC §6.4). */
    private String loggingGuidance(ExerciseDetail detail) {
        List<String> lines = new ArrayList<>();
        switch (detail.trackingType()) {
            case BODYWEIGHT_REPS -> lines.add(getString(R.string.detail_logging_bodyweight));
            case DURATION -> lines.add(getString(R.string.detail_logging_duration));
            case REPS_ONLY -> lines.add(getString(R.string.detail_logging_reps_only));
            case WEIGHT_REPS, WEIGHT_DURATION -> lines.add(getString(detail.loadBasis() == LoadBasis.PER_IMPLEMENT
                    ? R.string.detail_logging_per_implement
                    : R.string.detail_logging_total));
        }
        if (detail.laterality() == Laterality.UNILATERAL) {
            lines.add(getString(R.string.detail_logging_unilateral));
        }
        return String.join("\n\n", lines);
    }

    private static void setOptional(TextView body, @Nullable View title, @Nullable String text) {
        boolean visible = text != null && !text.trim().isEmpty();
        body.setText(visible ? text : null);
        body.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (title != null) {
            title.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}

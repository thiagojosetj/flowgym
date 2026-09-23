package io.github.thiagojosetj.gym.ui.templates.editor;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;
import java.util.Locale;

import io.github.thiagojosetj.gym.R;
import io.github.thiagojosetj.gym.databinding.SheetExercisePlanBinding;
import io.github.thiagojosetj.gym.domain.model.LoadBasis;
import io.github.thiagojosetj.gym.domain.model.RepRange;
import io.github.thiagojosetj.gym.domain.model.SideMode;
import io.github.thiagojosetj.gym.domain.model.TrackingType;
import io.github.thiagojosetj.gym.domain.model.Weight;
import io.github.thiagojosetj.gym.domain.model.WeightUnit;
import io.github.thiagojosetj.gym.domain.template.ExercisePlanUpdate;
import io.github.thiagojosetj.gym.domain.template.TemplateRules;
import io.github.thiagojosetj.gym.ui.common.NumberInput;

/**
 * Edits the plan of one template exercise in "simple mode": same sets × reps × load for every set
 * (e.g. 4 × 8–10 × 40 kg, 120 s rest). Input parsing happens here; the rules (ranges, what an
 * exercise type accepts) are enforced by the domain through the ViewModel.
 */
public class ExercisePlanSheet extends BottomSheetDialogFragment {

    private static final String ARG_ITEM_ID = "templateExerciseId";
    private static final int[] REST_SHORTCUTS = {60, 90, 120, 180};
    /** Units are kg for now; lb support is planned (docs/ROADMAP.md). */
    private static final WeightUnit UNIT = WeightUnit.KILOGRAM;

    private SheetExercisePlanBinding binding;
    private TemplateEditorViewModel viewModel;
    private TemplateExerciseItem item;

    static ExercisePlanSheet newInstance(String templateExerciseId) {
        ExercisePlanSheet sheet = new ExercisePlanSheet();
        Bundle args = new Bundle();
        args.putString(ARG_ITEM_ID, templateExerciseId);
        sheet.setArguments(args);
        return sheet;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        dialog.getBehavior().setSkipCollapsed(true);
        return dialog;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = SheetExercisePlanBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        TemplateEditorFragment editor = (TemplateEditorFragment) requireParentFragment();
        viewModel = new ViewModelProvider(editor, editor.viewModelFactory()).get(TemplateEditorViewModel.class);
        item = viewModel.findItem(requireArguments().getString(ARG_ITEM_ID));
        if (item == null) {
            // e.g. the process died with an unsaved new template: nothing left to edit.
            dismissAllowingStateLoss();
            return;
        }
        configureFields();
        if (savedInstanceState == null) {
            fillFromItem();
        }
        // Accessibility: the selected option must not be signalled by colour alone.
        markCheckedWithIcon(binding.bodyweightToggle);
        markCheckedWithIcon(binding.sideToggle);
        binding.buttonDecreaseSets.setOnClickListener(v -> stepSets(-1));
        binding.buttonIncreaseSets.setOnClickListener(v -> stepSets(+1));
        binding.buttonCancel.setOnClickListener(v -> dismiss());
        binding.buttonApply.setOnClickListener(v -> apply());
    }

    /** Shows only the fields that make sense for this exercise type. */
    private void configureFields() {
        TrackingType tracking = item.trackingType();
        binding.title.setText(item.name());
        boolean reps = tracking.usesReps();
        binding.repsRow.setVisibility(reps ? View.VISIBLE : View.GONE);
        binding.repsHelper.setVisibility(reps ? View.VISIBLE : View.GONE);
        binding.durationLayout.setVisibility(tracking.usesDuration() ? View.VISIBLE : View.GONE);
        binding.durationLayout.setHint(getString(R.string.plan_sheet_duration));

        boolean weight = tracking.usesWeight();
        boolean bodyweight = tracking == TrackingType.BODYWEIGHT_REPS;
        binding.weightLayout.setVisibility(weight ? View.VISIBLE : View.GONE);
        binding.weightLayout.setSuffixText(UNIT.symbol());
        binding.bodyweightToggle.setVisibility(bodyweight ? View.VISIBLE : View.GONE);
        if (bodyweight) {
            binding.weightLayout.setHint(getString(R.string.plan_sheet_weight_bodyweight));
            binding.weightLayout.setHelperText(getString(R.string.plan_sheet_bodyweight_helper));
        } else if (item.loadBasis() == LoadBasis.PER_IMPLEMENT) {
            binding.weightLayout.setHint(getString(R.string.plan_sheet_weight_per_implement));
            // Same wording as the card: "kg por halter" / "kg por polia".
            binding.weightLayout.setHelperText(getString(PlanFormatter.perImplementLabel(item.primaryEquipmentCode()),
                    UNIT.symbol()));
        } else {
            binding.weightLayout.setHint(getString(R.string.plan_sheet_weight));
        }

        binding.sideLabel.setVisibility(item.unilateral() ? View.VISIBLE : View.GONE);
        binding.sideToggle.setVisibility(item.unilateral() ? View.VISIBLE : View.GONE);

        binding.restChips.removeAllViews();
        for (int seconds : REST_SHORTCUTS) {
            Chip chip = new Chip(requireContext());
            chip.setText(getString(R.string.duration_seconds, seconds));
            chip.setOnClickListener(v -> binding.restInput.setText(String.valueOf(seconds)));
            binding.restChips.addView(chip);
        }
    }

    private void fillFromItem() {
        binding.setsInput.setText(String.valueOf(item.setCount()));
        RepRange reps = item.reps();
        if (reps != null) {
            binding.repsInput.setText(String.valueOf(reps.min()));
            binding.repsMaxInput.setText(reps.isFixed() ? "" : String.valueOf(reps.max()));
        }
        if (item.durationSeconds() != null) {
            binding.durationInput.setText(String.valueOf(item.durationSeconds()));
        }
        Weight weight = item.weight();
        if (weight != null && weight.grams() != 0) {
            Locale locale = getResources().getConfiguration().getLocales().get(0);
            binding.weightInput.setText(NumberInput.formatDecimal(Math.abs(weight.in(UNIT)), locale));
            binding.bodyweightToggle.check(weight.isNegative() ? R.id.button_assisted : R.id.button_added);
        }
        binding.restInput.setText(String.valueOf(item.restSeconds()));
        binding.sideToggle.check(item.sideMode() == SideMode.PER_SIDE
                ? R.id.button_side_per_side : R.id.button_side_combined);
        binding.notesInput.setText(item.notes());
    }

    /** Shows a check icon on the selected button of a toggle group, and keeps it in sync. */
    private static void markCheckedWithIcon(MaterialButtonToggleGroup group) {
        group.addOnButtonCheckedListener((g, checkedId, isChecked) -> updateToggleIcons(g));
        updateToggleIcons(group);
    }

    private static void updateToggleIcons(MaterialButtonToggleGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            if (group.getChildAt(i) instanceof MaterialButton button) {
                button.setIconResource(button.getId() == group.getCheckedButtonId() ? R.drawable.ic_check : 0);
            }
        }
    }

    private void stepSets(int delta) {
        Integer current = parseOrNull(binding.setsInput.getText());
        int next = Math.max(1, Math.min(TemplateRules.MAX_SETS_PER_EXERCISE, (current == null ? 0 : current) + delta));
        binding.setsInput.setText(String.valueOf(next));
    }

    // ------------------------------------------------------------------ apply

    private void apply() {
        clearErrors();
        boolean valid = true;

        Integer sets = parseOrNull(binding.setsInput.getText());
        if (sets == null || sets < 1 || sets > TemplateRules.MAX_SETS_PER_EXERCISE) {
            showSetsError(getResources().getQuantityString(R.plurals.plan_error_sets_range,
                    TemplateRules.MAX_SETS_PER_EXERCISE, TemplateRules.MAX_SETS_PER_EXERCISE));
            valid = false;
        }

        RepRange reps = null;
        if (item.trackingType().usesReps()) {
            Integer min = parse(binding.repsLayout, binding.repsInput.getText());
            Integer max = parse(binding.repsMaxLayout, binding.repsMaxInput.getText());
            if (min == null && max != null) {
                binding.repsLayout.setError(getString(R.string.plan_error_reps_max_without_min));
                valid = false;
            } else if (min != null) {
                int upper = max == null ? min : max;
                if (min < 1 || min > RepRange.MAX_REPS) {
                    binding.repsLayout.setError(getString(R.string.plan_error_reps_range, RepRange.MAX_REPS));
                    valid = false;
                } else if (upper < min || upper > RepRange.MAX_REPS) {
                    binding.repsMaxLayout.setError(getString(R.string.plan_error_reps_order));
                    valid = false;
                } else {
                    reps = RepRange.between(min, upper);
                }
            }
        }

        Integer duration = null;
        if (item.trackingType().usesDuration()) {
            duration = parse(binding.durationLayout, binding.durationInput.getText());
            if (duration != null && (duration < 1 || duration > TemplateRules.MAX_DURATION_SECONDS)) {
                binding.durationLayout.setError(getString(R.string.plan_error_duration_range,
                        TemplateRules.MAX_DURATION_SECONDS));
                valid = false;
            }
        }

        Weight weight = null;
        if (item.trackingType().usesWeight()) {
            try {
                Double value = NumberInput.parseDecimal(text(binding.weightInput.getText()));
                if (value != null && value > 0) {
                    boolean assisted = item.trackingType() == TrackingType.BODYWEIGHT_REPS
                            && binding.bodyweightToggle.getCheckedButtonId() == R.id.button_assisted;
                    weight = Weight.of(assisted ? -value : value, UNIT);
                }
            } catch (IllegalArgumentException e) { // NumberFormatException or out of Weight range
                binding.weightLayout.setError(getString(R.string.plan_error_weight));
                valid = false;
            }
        }

        Integer rest = parse(binding.restLayout, binding.restInput.getText());
        if (rest == null || rest > TemplateRules.MAX_REST_SECONDS) {
            binding.restLayout.setError(getString(R.string.plan_error_rest_range, TemplateRules.MAX_REST_SECONDS));
            valid = false;
        }

        if (!valid) {
            return;
        }
        SideMode side = binding.sideToggle.getCheckedButtonId() == R.id.button_side_per_side
                ? SideMode.PER_SIDE : SideMode.COMBINED;
        List<ExercisePlanUpdate.Error> errors = viewModel.updatePlan(item.id(), new ExercisePlanUpdate(
                sets, reps, weight, duration, rest, text(binding.notesInput.getText()),
                item.unilateral() ? side : SideMode.COMBINED));
        if (errors.isEmpty()) {
            dismiss();
        } else {
            showDomainErrors(errors);
        }
    }

    /** Maps the domain's error codes to the matching field. */
    private void showDomainErrors(List<ExercisePlanUpdate.Error> errors) {
        for (ExercisePlanUpdate.Error error : errors) {
            switch (error) {
                case SET_COUNT_OUT_OF_RANGE -> showSetsError(
                        getResources().getQuantityString(R.plurals.plan_error_sets_range,
                    TemplateRules.MAX_SETS_PER_EXERCISE, TemplateRules.MAX_SETS_PER_EXERCISE));
                case REST_OUT_OF_RANGE -> binding.restLayout.setError(
                        getString(R.string.plan_error_rest_range, TemplateRules.MAX_REST_SECONDS));
                case WEIGHT_NOT_APPLICABLE, NEGATIVE_WEIGHT_NOT_ALLOWED ->
                        binding.weightLayout.setError(getString(R.string.plan_error_weight));
                case DURATION_NOT_APPLICABLE, DURATION_OUT_OF_RANGE -> binding.durationLayout.setError(
                        getString(R.string.plan_error_duration_range, TemplateRules.MAX_DURATION_SECONDS));
                case REPS_NOT_APPLICABLE -> binding.repsLayout.setError(getString(R.string.plan_error_number));
                case NOTES_TOO_LONG -> binding.notesLayout.setError(getString(R.string.plan_error_notes_long));
                case PER_SIDE_REQUIRES_UNILATERAL -> binding.sideToggle.check(R.id.button_side_combined);
            }
        }
    }

    private void clearErrors() {
        for (TextInputLayout layout : new TextInputLayout[]{binding.repsLayout, binding.repsMaxLayout,
                binding.durationLayout, binding.weightLayout, binding.restLayout, binding.notesLayout}) {
            layout.setError(null);
        }
        binding.setsError.setVisibility(View.GONE);
    }

    private void showSetsError(String message) {
        binding.setsError.setText(message);
        binding.setsError.setVisibility(View.VISIBLE);
    }

    @Nullable
    private Integer parse(TextInputLayout layout, @Nullable CharSequence value) {
        try {
            return NumberInput.parseWholeNumber(text(value));
        } catch (NumberFormatException e) {
            layout.setError(getString(R.string.plan_error_number));
            return null;
        }
    }

    @Nullable
    private static Integer parseOrNull(@Nullable CharSequence value) {
        try {
            return NumberInput.parseWholeNumber(text(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String text(@Nullable CharSequence value) {
        return value == null ? "" : value.toString();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}

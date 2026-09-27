package io.github.thiagojosetj.gym.ui.templates.editor;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import io.github.thiagojosetj.gym.R;
import io.github.thiagojosetj.gym.databinding.ItemPlanSetBinding;
import io.github.thiagojosetj.gym.databinding.SheetExercisePlanBinding;
import io.github.thiagojosetj.gym.domain.model.LoadBasis;
import io.github.thiagojosetj.gym.domain.model.RepRange;
import io.github.thiagojosetj.gym.domain.model.SideMode;
import io.github.thiagojosetj.gym.domain.model.TrackingType;
import io.github.thiagojosetj.gym.domain.model.Weight;
import io.github.thiagojosetj.gym.domain.model.WeightUnit;
import io.github.thiagojosetj.gym.domain.technique.TechniqueCatalog;
import io.github.thiagojosetj.gym.domain.technique.TechniqueScope;
import io.github.thiagojosetj.gym.domain.technique.TrainingTechnique;
import io.github.thiagojosetj.gym.domain.template.ExercisePlanUpdate;
import io.github.thiagojosetj.gym.domain.template.SetSpec;
import io.github.thiagojosetj.gym.domain.template.TemplateRules;
import io.github.thiagojosetj.gym.ui.common.NumberInput;
import io.github.thiagojosetj.gym.ui.common.TechniqueDialogs;
import io.github.thiagojosetj.gym.ui.common.RepRangeInput;

/**
 * Edits the sets of one template exercise, one row per set: technique (with ⓘ), repetitions (or
 * time) and load, plus rest, side mode and the permanent note.
 *
 * <p>Rows are plain views inside a LinearLayout (at most 20 sets, nothing to recycle), so text
 * fields keep focus while typing. Their content is saved and restored by hand because every row
 * shares the same view ids.
 */
public class ExercisePlanSheet extends BottomSheetDialogFragment {

    private static final String ARG_ITEM_ID = "templateExerciseId";
    private static final String STATE_REPS = "reps";
    private static final String STATE_WEIGHTS = "weights";
    private static final String STATE_TECHNIQUES = "techniques";
    private static final int[] REST_SHORTCUTS = {60, 90, 120, 180};
    /** Units are kg for now; lb support is planned (docs/ROADMAP.md). */
    private static final WeightUnit UNIT = WeightUnit.KILOGRAM;
    private static final String NO_TECHNIQUE = "";

    private SheetExercisePlanBinding binding;
    private TemplateEditorViewModel viewModel;
    private TemplateExerciseItem item;
    private final List<Row> rows = new ArrayList<>();
    private List<TrainingTechnique> setTechniques = Collections.emptyList();
    /** True while the catalog has not arrived, or if reading it failed. */
    private boolean techniquesUnavailable = true;
    /** Open plain dialogs, dismissed with the view so they do not leak their window on rotation. */
    @Nullable
    private AlertDialog techniquePicker;
    @Nullable
    private AlertDialog techniqueExplanation;

    /** A set row: its views plus the technique chosen for it. */
    private static final class Row {
        final ItemPlanSetBinding views;
        String techniqueId;

        Row(ItemPlanSetBinding views, String techniqueId) {
            this.views = views;
            this.techniqueId = techniqueId;
        }
    }

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
            for (TemplateSetItem set : item.sets()) {
                addRow(repsTextOf(set), weightTextOf(set), set.techniqueId());
            }
            binding.restInput.setText(String.valueOf(item.restSeconds()));
            binding.notesInput.setText(item.notes());
            Weight weight = item.weight();
            binding.bodyweightToggle.check(weight != null && weight.isNegative()
                    ? R.id.button_assisted : R.id.button_added);
            binding.sideToggle.check(item.sideMode() == SideMode.PER_SIDE
                    ? R.id.button_side_per_side : R.id.button_side_combined);
        } else {
            restoreRows(savedInstanceState);
        }

        viewModel.techniqueCatalog().observe(getViewLifecycleOwner(), catalog -> {
            techniquesUnavailable = catalog == null;
            setTechniques = catalog == null ? Collections.emptyList() : catalog.ofScope(TechniqueScope.SET);
            refreshAllRowTechniques();
        });

        markCheckedWithIcon(binding.bodyweightToggle);
        markCheckedWithIcon(binding.sideToggle);
        binding.buttonAddSet.setOnClickListener(v -> addRowLikeLast());
        binding.buttonCopyFirst.setOnClickListener(v -> copyFirstRowToAll());
        binding.buttonCancel.setOnClickListener(v -> dismiss());
        binding.buttonApply.setOnClickListener(v -> apply());
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (binding == null) {
            return;
        }
        ArrayList<String> reps = new ArrayList<>();
        ArrayList<String> weights = new ArrayList<>();
        ArrayList<String> techniques = new ArrayList<>();
        for (Row row : rows) {
            reps.add(text(row.views.repsInput.getText()));
            weights.add(text(row.views.weightInput.getText()));
            techniques.add(row.techniqueId == null ? NO_TECHNIQUE : row.techniqueId);
        }
        outState.putStringArrayList(STATE_REPS, reps);
        outState.putStringArrayList(STATE_WEIGHTS, weights);
        outState.putStringArrayList(STATE_TECHNIQUES, techniques);
    }

    private void restoreRows(Bundle state) {
        List<String> reps = state.getStringArrayList(STATE_REPS);
        List<String> weights = state.getStringArrayList(STATE_WEIGHTS);
        List<String> techniques = state.getStringArrayList(STATE_TECHNIQUES);
        if (reps == null || weights == null || techniques == null) {
            return;
        }
        for (int i = 0; i < reps.size(); i++) {
            String techniqueId = techniques.get(i);
            addRow(reps.get(i), weights.get(i), NO_TECHNIQUE.equals(techniqueId) ? null : techniqueId);
        }
    }

    // ------------------------------------------------------------------ layout

    /** Shows only what makes sense for this exercise type. */
    private void configureFields() {
        TrackingType tracking = item.trackingType();
        binding.title.setText(item.name());
        boolean bodyweight = tracking == TrackingType.BODYWEIGHT_REPS;
        binding.bodyweightToggle.setVisibility(bodyweight ? View.VISIBLE : View.GONE);
        binding.bodyweightHint.setVisibility(bodyweight ? View.VISIBLE : View.GONE);

        String loadHint = null;
        if (!tracking.usesWeight()) {
            loadHint = null;
        } else if (bodyweight) {
            loadHint = getString(R.string.plan_sheet_weight_bodyweight);
        } else if (item.loadBasis() == LoadBasis.PER_IMPLEMENT) {
            // Same wording as the card: "kg por halter" / "kg por polia".
            loadHint = getString(PlanFormatter.perImplementLabel(item.primaryEquipmentCode()), UNIT.symbol());
        }
        binding.loadHint.setText(loadHint);
        binding.loadHint.setVisibility(loadHint == null ? View.GONE : View.VISIBLE);

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

    private void addRow(String repsText, String weightText, @Nullable String techniqueId) {
        if (rows.size() >= TemplateRules.MAX_SETS_PER_EXERCISE) {
            showSetsError(getString(R.string.plan_error_too_many_sets, TemplateRules.MAX_SETS_PER_EXERCISE));
            return;
        }
        ItemPlanSetBinding views = ItemPlanSetBinding.inflate(getLayoutInflater(), binding.setsContainer, false);
        // Every row repeats the same view ids, and the framework saves view state in a map keyed by
        // id: the last row would overwrite the others and, on restore, hand its text to all of them
        // (verified by rotatingKeepsEachSetRowWithItsOwnValues). Rows are saved by hand instead.
        views.getRoot().setSaveFromParentEnabled(false);
        Row row = new Row(views, techniqueId);
        boolean timed = item.trackingType().usesDuration() && !item.trackingType().usesReps();
        views.repsLayout.setHint(getString(timed
                ? R.string.plan_sheet_duration_hint
                : R.string.plan_sheet_reps_range_hint));
        if (timed) {
            views.repsInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        }
        views.repsInput.setText(repsText);
        views.weightLayout.setVisibility(item.trackingType().usesWeight() ? View.VISIBLE : View.GONE);
        views.weightLayout.setSuffixText(UNIT.symbol());
        views.weightInput.setText(weightText);
        views.buttonTechnique.setOnClickListener(v -> pickTechnique(row));
        views.buttonTechniqueInfo.setOnClickListener(v -> {
            TrainingTechnique technique = findTechnique(row.techniqueId);
            if (technique != null) {
                techniqueExplanation = TechniqueDialogs.showExplanation(requireContext(), technique);
            }
        });
        views.buttonRemoveSet.setOnClickListener(v -> removeRow(row));
        rows.add(row);
        binding.setsContainer.addView(views.getRoot());
        refreshRowLabels();
        refreshRowTechnique(row);
    }

    private void addRowLikeLast() {
        if (rows.isEmpty()) {
            addRow("", "", null);
            return;
        }
        Row last = rows.get(rows.size() - 1);
        addRow(text(last.views.repsInput.getText()), text(last.views.weightInput.getText()), null);
    }

    private void removeRow(Row row) {
        binding.setsContainer.removeView(row.views.getRoot());
        rows.remove(row);
        refreshRowLabels();
        hideSetsError();
    }

    private void copyFirstRowToAll() {
        if (rows.size() < 2) {
            return;
        }
        Row first = rows.get(0);
        String reps = text(first.views.repsInput.getText());
        String weight = text(first.views.weightInput.getText());
        for (int i = 1; i < rows.size(); i++) {
            rows.get(i).views.repsInput.setText(reps);
            rows.get(i).views.weightInput.setText(weight);
        }
    }

    private void refreshRowLabels() {
        for (int i = 0; i < rows.size(); i++) {
            ItemPlanSetBinding views = rows.get(i).views;
            views.setNumber.setText(getString(R.string.plan_sheet_set_number, i + 1));
            views.buttonTechnique.setContentDescription(
                    getString(R.string.plan_sheet_technique_of_set, i + 1));
            views.buttonRemoveSet.setContentDescription(getString(R.string.plan_sheet_remove_set, i + 1));
            views.buttonRemoveSet.setEnabled(rows.size() > 1);
        }
    }

    private void refreshAllRowTechniques() {
        for (Row row : rows) {
            refreshRowTechnique(row);
        }
    }

    /** Badge + ⓘ of one row, from the loaded catalog. */
    private void refreshRowTechnique(Row row) {
        TrainingTechnique technique = findTechnique(row.techniqueId);
        row.views.buttonTechnique.setText(technique == null
                ? getString(R.string.plan_sheet_technique_none)
                : technique.code());
        row.views.buttonTechniqueInfo.setVisibility(technique == null ? View.GONE : View.VISIBLE);
        if (technique != null) {
            row.views.buttonTechniqueInfo.setContentDescription(
                    getString(R.string.plan_sheet_technique_explain, technique.name()));
        }
    }

    private void pickTechnique(Row row) {
        if (setTechniques.isEmpty()) {
            // Saying nothing would look like a broken button.
            showSetsError(getString(techniquesUnavailable
                    ? R.string.plan_error_technique_unavailable
                    : R.string.plan_error_technique_invalid));
            return;
        }
        techniquePicker = TechniqueDialogs.showPicker(requireContext(), setTechniques, row.techniqueId,
                techniqueId -> {
                    row.techniqueId = techniqueId;
                    refreshRowTechnique(row);
                });
    }

    @Nullable
    private TrainingTechnique findTechnique(@Nullable String techniqueId) {
        if (techniqueId == null) {
            return null;
        }
        for (TrainingTechnique technique : setTechniques) {
            if (technique.id().equals(techniqueId)) {
                return technique;
            }
        }
        return null;
    }

    /** Shows a check icon on the selected button of a toggle group (not colour alone). */
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

    // ------------------------------------------------------------------ apply

    private void apply() {
        clearErrors();
        boolean valid = true;
        boolean timed = item.trackingType().usesDuration() && !item.trackingType().usesReps();
        boolean assisted = item.trackingType() == TrackingType.BODYWEIGHT_REPS
                && binding.bodyweightToggle.getCheckedButtonId() == R.id.button_assisted;

        List<SetSpec> specs = new ArrayList<>(rows.size());
        for (Row row : rows) {
            RepRange reps = null;
            Integer duration = null;
            if (timed) {
                try {
                    duration = NumberInput.parseWholeNumber(text(row.views.repsInput.getText()));
                    if (duration != null && (duration < 1 || duration > TemplateRules.MAX_DURATION_SECONDS)) {
                        row.views.repsLayout.setError(getString(R.string.plan_error_duration_range,
                                TemplateRules.MAX_DURATION_SECONDS));
                        valid = false;
                    }
                } catch (NumberFormatException e) {
                    row.views.repsLayout.setError(getString(R.string.plan_error_number));
                    valid = false;
                }
            } else if (item.trackingType().usesReps()) {
                try {
                    reps = RepRangeInput.parse(text(row.views.repsInput.getText()));
                } catch (IllegalArgumentException e) {
                    row.views.repsLayout.setError(getString(R.string.plan_error_reps_format));
                    valid = false;
                }
            }

            Weight weight = null;
            if (item.trackingType().usesWeight()) {
                try {
                    Double value = NumberInput.parseDecimal(text(row.views.weightInput.getText()));
                    if (value != null && value > 0) {
                        weight = Weight.of(assisted ? -value : value, UNIT);
                    }
                } catch (IllegalArgumentException e) { // bad number or beyond the Weight range
                    row.views.weightLayout.setError(getString(R.string.plan_error_weight));
                    valid = false;
                }
            }
            specs.add(new SetSpec(reps, weight, duration, row.techniqueId));
        }

        Integer rest = null;
        try {
            rest = NumberInput.parseWholeNumber(text(binding.restInput.getText()));
        } catch (NumberFormatException e) {
            binding.restLayout.setError(getString(R.string.plan_error_number));
            valid = false;
        }
        if (rest == null || rest > TemplateRules.MAX_REST_SECONDS) {
            binding.restLayout.setError(getString(R.string.plan_error_rest_range, TemplateRules.MAX_REST_SECONDS));
            valid = false;
        }
        if (specs.isEmpty()) {
            showSetsError(getString(R.string.plan_error_no_sets));
            valid = false;
        }
        if (!valid) {
            return;
        }

        SideMode side = binding.sideToggle.getCheckedButtonId() == R.id.button_side_per_side
                ? SideMode.PER_SIDE : SideMode.COMBINED;
        List<ExercisePlanUpdate.Error> errors = viewModel.updatePlan(item.id(), new ExercisePlanUpdate(
                specs, rest, text(binding.notesInput.getText()),
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
                case NO_SETS -> showSetsError(getString(R.string.plan_error_no_sets));
                case TOO_MANY_SETS -> showSetsError(
                        getString(R.string.plan_error_too_many_sets, TemplateRules.MAX_SETS_PER_EXERCISE));
                case REST_OUT_OF_RANGE -> binding.restLayout.setError(
                        getString(R.string.plan_error_rest_range, TemplateRules.MAX_REST_SECONDS));
                case WEIGHT_NOT_APPLICABLE, NEGATIVE_WEIGHT_NOT_ALLOWED -> firstRowWeightError();
                case DURATION_NOT_APPLICABLE, DURATION_OUT_OF_RANGE, REPS_NOT_APPLICABLE -> firstRowRepsError();
                case NOTES_TOO_LONG -> binding.notesLayout.setError(getString(R.string.plan_error_notes_long));
                case PER_SIDE_REQUIRES_UNILATERAL -> binding.sideToggle.check(R.id.button_side_combined);
                case UNKNOWN_TECHNIQUE, TECHNIQUE_NOT_FOR_SETS -> showSetsError(getString(
                        techniquesUnavailable
                                ? R.string.plan_error_technique_unavailable
                                : R.string.plan_error_technique_invalid));
            }
        }
    }

    private void firstRowWeightError() {
        if (!rows.isEmpty()) {
            rows.get(0).views.weightLayout.setError(getString(R.string.plan_error_weight));
        }
    }

    private void firstRowRepsError() {
        if (!rows.isEmpty()) {
            rows.get(0).views.repsLayout.setError(getString(R.string.plan_error_number));
        }
    }

    private void clearErrors() {
        binding.restLayout.setError(null);
        binding.notesLayout.setError(null);
        for (Row row : rows) {
            row.views.repsLayout.setError(null);
            row.views.weightLayout.setError(null);
        }
        hideSetsError();
    }

    private void showSetsError(String message) {
        binding.setsError.setText(message);
        binding.setsError.setVisibility(View.VISIBLE);
    }

    private void hideSetsError() {
        binding.setsError.setVisibility(View.GONE);
    }

    private String repsTextOf(TemplateSetItem set) {
        if (item.trackingType().usesDuration() && !item.trackingType().usesReps()) {
            return set.durationSeconds() == null ? "" : String.valueOf(set.durationSeconds());
        }
        return RepRangeInput.format(set.reps());
    }

    private static String weightTextOf(TemplateSetItem set) {
        if (set.weight() == null || set.weight().grams() == 0) {
            return "";
        }
        return NumberInput.formatDecimal(Math.abs(set.weight().in(UNIT)), Locale.getDefault());
    }

    private static String text(@Nullable CharSequence value) {
        return value == null ? "" : value.toString();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        techniquePicker = dismiss(techniquePicker);
        techniqueExplanation = dismiss(techniqueExplanation);
        rows.clear();
        binding = null;
    }

    @Nullable
    private static AlertDialog dismiss(@Nullable AlertDialog dialog) {
        if (dialog != null) {
            dialog.dismiss();
        }
        return null;
    }
}

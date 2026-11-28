package io.github.thiagojosetj.gym.ui.templates.editor;

import android.app.Dialog;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.Editable;
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

import java.util.ArrayList;
import java.util.List;

import io.github.thiagojosetj.gym.R;
import io.github.thiagojosetj.gym.databinding.ItemGroupCandidateBinding;
import io.github.thiagojosetj.gym.databinding.SheetGroupExercisesBinding;
import io.github.thiagojosetj.gym.domain.technique.TrainingTechnique;
import io.github.thiagojosetj.gym.domain.template.TemplateRules;
import io.github.thiagojosetj.gym.ui.common.NumberInput;
import io.github.thiagojosetj.gym.ui.common.TechniqueDialogs;

/**
 * The sheet that makes a group and the sheet that edits one (PRODUCT_SPEC section 6.3).
 *
 * <p><b>Why one sheet and not two.</b> What is being decided is the same in both cases - the
 * method of the group and the rest after each round - and the only difference is whether the
 * members are being chosen at the same time. Two sheets would mean two copies of the rest
 * validation and of the technique picker, and those are exactly the parts that must not drift
 * apart. {@link #newInstance} opens it to create, {@link #forGroup} to edit.
 *
 * <p><b>Editing never moves who is in the group.</b> Taking an exercise out or putting one in is
 * ungrouping and grouping again - a different decision, with a different letter as a consequence -
 * and the sheet says so rather than offering checkboxes that would quietly reletter the template.
 *
 * <p>Nothing here names the group. Its letter is derived by the data layer and shows on the cards.
 * The group is written when the button is tapped, in the database, and not when the editor is
 * saved (ADR-0038), which is why the sheet says so before it is confirmed.
 *
 * <p>Rows are plain views, like the set rows of {@link ExercisePlanSheet}: at most
 * {@link TemplateRules#MAX_EXERCISES}, nothing to recycle, and they share one view id, so what is
 * ticked is saved and restored by hand.
 */
public class GroupExercisesSheet extends BottomSheetDialogFragment {

    private static final String ARG_ITEM_ID = "templateExerciseId";
    private static final String ARG_GROUP_ID = "groupId";
    private static final String STATE_CHECKED = "checked";
    private static final String STATE_TECHNIQUE = "techniqueId";
    private static final String NO_TECHNIQUE = "";

    private SheetGroupExercisesBinding binding;
    private TemplateEditorViewModel viewModel;
    /** The exercise the new group is built around; null when editing one that exists. */
    private TemplateExerciseItem anchor;
    /** The group being edited; null when creating one. */
    private TemplateExerciseItem.GroupMark editing;
    @Nullable
    private String techniqueId;
    private final List<Row> rows = new ArrayList<>();
    @Nullable
    private AlertDialog techniquePicker;
    @Nullable
    private AlertDialog techniqueExplanation;

    /** A candidate: its card, and the checkbox that picks it. */
    private static final class Row {
        final TemplateExerciseItem item;
        final ItemGroupCandidateBinding views;

        Row(TemplateExerciseItem item, ItemGroupCandidateBinding views) {
            this.item = item;
            this.views = views;
        }
    }

    static GroupExercisesSheet newInstance(String templateExerciseId) {
        GroupExercisesSheet sheet = new GroupExercisesSheet();
        Bundle args = new Bundle();
        args.putString(ARG_ITEM_ID, templateExerciseId);
        sheet.setArguments(args);
        return sheet;
    }

    /** Opens on a group that already exists, to change its method and its rest. */
    static GroupExercisesSheet forGroup(String groupId) {
        GroupExercisesSheet sheet = new GroupExercisesSheet();
        Bundle args = new Bundle();
        args.putString(ARG_GROUP_ID, groupId);
        sheet.setArguments(args);
        return sheet;
    }

    /** A group of one is no group: said by this sheet and by the editor, in the same words. */
    static String tooSmallMessage(Resources res) {
        return res.getQuantityString(R.plurals.editor_group_error_too_small,
                TemplateRules.MIN_GROUP_SIZE, TemplateRules.MIN_GROUP_SIZE);
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
        binding = SheetGroupExercisesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        TemplateEditorFragment editor = (TemplateEditorFragment) requireParentFragment();
        viewModel = new ViewModelProvider(editor, editor.viewModelFactory())
                .get(TemplateEditorViewModel.class);
        String groupId = requireArguments().getString(ARG_GROUP_ID);
        if (groupId == null ? !startCreating(savedInstanceState) : !startEditing(groupId)) {
            dismissAllowingStateLoss();
            return;
        }
        if (savedInstanceState != null) {
            String stored = savedInstanceState.getString(STATE_TECHNIQUE, NO_TECHNIQUE);
            techniqueId = NO_TECHNIQUE.equals(stored) ? null : stored;
        }

        binding.buttonGroupTechnique.setOnClickListener(v -> pickTechnique());
        binding.buttonGroupTechniqueInfo.setOnClickListener(v -> {
            TrainingTechnique technique = findTechnique(techniqueId);
            if (technique != null) {
                techniqueExplanation = TechniqueDialogs.showExplanation(requireContext(), technique);
            }
        });
        // The catalog arrives asynchronously: the button shows a name as soon as it does.
        viewModel.techniqueCatalog().observe(getViewLifecycleOwner(),
                catalog -> refreshTechnique());
        refreshTechnique();

        binding.buttonGroupCancel.setOnClickListener(v -> dismiss());
        binding.buttonGroupConfirm.setOnClickListener(v -> apply());
    }

    /** @return false when there is nothing left to group (e.g. the draft was lost) */
    private boolean startCreating(@Nullable Bundle savedInstanceState) {
        anchor = viewModel.findItem(requireArguments().getString(ARG_ITEM_ID));
        if (anchor == null || anchor.group() != null) {
            return false;
        }
        binding.groupIntro.setText(getString(R.string.editor_group_intro, anchor.name()));
        List<String> ticked = savedInstanceState == null
                ? null : savedInstanceState.getStringArrayList(STATE_CHECKED);
        for (TemplateExerciseItem candidate : viewModel.groupCandidates(anchor.id())) {
            addRow(candidate, ticked != null && ticked.contains(candidate.id()));
        }
        if (savedInstanceState == null) {
            // Visible and editable, not applied behind the user's back: it is the app's default.
            binding.groupRestInput.setText(String.valueOf(viewModel.defaultGroupRestSeconds()));
        }
        return true;
    }

    /** @return false when the group is gone (another tap ungrouped it while this was opening) */
    private boolean startEditing(String groupId) {
        editing = viewModel.groupOf(groupId);
        if (editing == null) {
            return false;
        }
        binding.groupTitle.setText(getString(R.string.editor_group_edit_title, editing.label()));
        binding.groupIntro.setText(R.string.editor_group_edit_intro);
        // No checkboxes: who is in the group is not what this sheet decides.
        binding.groupCandidates.setVisibility(View.GONE);
        binding.buttonGroupConfirm.setText(R.string.editor_group_edit_confirm);
        // O EditText tem id e se restaura sozinho na rotação: só preenche quando veio vazio,
        // senão o valor que a pessoa estava digitando seria substituído pelo que está gravado.
        Editable typed = binding.groupRestInput.getText();
        if (typed == null || typed.length() == 0) {
            binding.groupRestInput.setText(String.valueOf(editing.restAfterRoundSeconds()));
        }
        techniqueId = editing.techniqueId();
        return true;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (binding == null) {
            return;
        }
        ArrayList<String> ticked = new ArrayList<>();
        for (Row row : rows) {
            if (row.views.check.isChecked()) {
                ticked.add(row.item.id());
            }
        }
        outState.putStringArrayList(STATE_CHECKED, ticked);
        outState.putString(STATE_TECHNIQUE, techniqueId == null ? NO_TECHNIQUE : techniqueId);
    }

    private void addRow(TemplateExerciseItem candidate, boolean ticked) {
        ItemGroupCandidateBinding views = ItemGroupCandidateBinding.inflate(getLayoutInflater(),
                binding.groupCandidates, false);
        // Every row has the same view id, so the automatic restore would give them all one answer.
        views.getRoot().setSaveFromParentEnabled(false);
        boolean saved = viewModel.isSaved(candidate.id());
        views.check.setText(saved
                ? candidate.name()
                : getString(R.string.editor_group_candidate_unsaved, candidate.name()));
        views.check.setEnabled(saved);
        views.check.setChecked(saved && ticked);
        views.check.setOnCheckedChangeListener((box, isChecked) -> hideError());
        rows.add(new Row(candidate, views));
        binding.groupCandidates.addView(views.getRoot());
    }

    /** The method's name on the button, and the ⓘ only when there is something to explain. */
    private void refreshTechnique() {
        if (binding == null) {
            return;
        }
        TrainingTechnique technique = findTechnique(techniqueId);
        binding.buttonGroupTechnique.setText(technique == null
                ? getString(R.string.editor_group_technique_none)
                : technique.name());
        binding.buttonGroupTechniqueInfo.setVisibility(technique == null ? View.GONE : View.VISIBLE);
        if (technique != null) {
            binding.buttonGroupTechniqueInfo.setContentDescription(
                    getString(R.string.plan_sheet_technique_explain, technique.name()));
        }
    }

    private void pickTechnique() {
        List<TrainingTechnique> options = viewModel.groupTechniques();
        if (options.isEmpty()) {
            // Saying nothing would look like a broken button.
            binding.groupError.setText(R.string.plan_error_technique_unavailable);
            binding.groupError.setVisibility(View.VISIBLE);
            return;
        }
        techniquePicker = TechniqueDialogs.showPicker(requireContext(),
                R.string.editor_group_technique_title, options, techniqueId, picked -> {
                    techniqueId = picked;
                    refreshTechnique();
                });
    }

    @Nullable
    private TrainingTechnique findTechnique(@Nullable String id) {
        if (id == null) {
            return null;
        }
        for (TrainingTechnique technique : viewModel.groupTechniques()) {
            if (technique.id().equals(id)) {
                return technique;
            }
        }
        return null;
    }

    private void apply() {
        hideError();
        binding.groupRestLayout.setError(null);
        if (editing != null) {
            Integer rest = parseRest();
            if (rest != null) {
                viewModel.updateGroup(editing.groupId(), techniqueId, rest);
                dismiss();
            }
            return;
        }
        List<String> members = new ArrayList<>();
        members.add(anchor.id());
        for (Row row : rows) {
            if (row.views.check.isChecked()) {
                members.add(row.item.id());
            }
        }
        boolean enough = members.size() >= TemplateRules.MIN_GROUP_SIZE;
        if (!enough) {
            binding.groupError.setText(tooSmallMessage(getResources()));
            binding.groupError.setVisibility(View.VISIBLE);
        }
        Integer rest = parseRest(); // always asked, so both problems show at once
        if (enough && rest != null) {
            viewModel.createGroup(members, techniqueId, rest);
            dismiss();
        }
    }

    /** The rest in seconds, or null after saying in the field what is wrong with it. */
    @Nullable
    private Integer parseRest() {
        try {
            Editable typed = binding.groupRestInput.getText();
            Integer rest = NumberInput.parseWholeNumber(typed == null ? "" : typed.toString());
            if (rest != null && rest <= TemplateRules.MAX_REST_SECONDS) {
                return rest;
            }
            binding.groupRestLayout.setError(
                    getString(R.string.plan_error_rest_range, TemplateRules.MAX_REST_SECONDS));
        } catch (NumberFormatException e) {
            binding.groupRestLayout.setError(getString(R.string.plan_error_number));
        }
        return null;
    }

    private void hideError() {
        binding.groupError.setVisibility(View.GONE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Plain dialogs are not recreated with the fragment and would leak their window.
        if (techniquePicker != null) {
            techniquePicker.dismiss();
            techniquePicker = null;
        }
        if (techniqueExplanation != null) {
            techniqueExplanation.dismiss();
            techniqueExplanation = null;
        }
        rows.clear();
        binding = null;
    }
}

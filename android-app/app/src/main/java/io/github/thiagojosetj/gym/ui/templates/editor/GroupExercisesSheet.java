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
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;

import io.github.thiagojosetj.gym.R;
import io.github.thiagojosetj.gym.databinding.ItemGroupCandidateBinding;
import io.github.thiagojosetj.gym.databinding.SheetGroupExercisesBinding;
import io.github.thiagojosetj.gym.domain.template.TemplateRules;
import io.github.thiagojosetj.gym.ui.common.NumberInput;

/**
 * "Agrupar com…": picks the exercises that join this one in a new group (PRODUCT_SPEC section 6.3)
 * and the rest that follows every round of it.
 *
 * <p>Nothing here names the group. Its letter is derived by the data layer and shows on the cards
 * once the group exists. The group is made when "Agrupar" is tapped, in the database, and not when
 * the editor is saved, which is why the sheet says so before it is confirmed.
 *
 * <p>Rows are plain views, like the set rows of {@link ExercisePlanSheet}: at most
 * {@link TemplateRules#MAX_EXERCISES}, nothing to recycle, and they share one view id, so what is
 * ticked is saved and restored by hand.
 */
public class GroupExercisesSheet extends BottomSheetDialogFragment {

    private static final String ARG_ITEM_ID = "templateExerciseId";
    private static final String STATE_CHECKED = "checked";

    private SheetGroupExercisesBinding binding;
    private TemplateEditorViewModel viewModel;
    private TemplateExerciseItem anchor;
    private final List<Row> rows = new ArrayList<>();

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
        anchor = viewModel.findItem(requireArguments().getString(ARG_ITEM_ID));
        if (anchor == null || anchor.group() != null) {
            // e.g. the process died with an unsaved template: nothing left to group.
            dismissAllowingStateLoss();
            return;
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
        binding.buttonGroupCancel.setOnClickListener(v -> dismiss());
        binding.buttonGroupConfirm.setOnClickListener(v -> apply());
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

    private void apply() {
        hideError();
        binding.groupRestLayout.setError(null);
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
            viewModel.createGroup(members, rest);
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
        rows.clear();
        binding = null;
    }
}

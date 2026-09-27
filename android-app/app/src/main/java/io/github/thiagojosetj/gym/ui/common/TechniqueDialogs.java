package io.github.thiagojosetj.gym.ui.common;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

import io.github.thiagojosetj.gym.R;
import io.github.thiagojosetj.gym.databinding.ItemTechniqueOptionBinding;
import io.github.thiagojosetj.gym.domain.technique.TrainingTechnique;

/**
 * Choosing and explaining a set technique (PRODUCT_SPEC §6.2). Every option carries an ⓘ that opens
 * the method's description and how to record it, so the app teaches instead of showing a code.
 *
 * <p>Both methods return the dialog so the caller can dismiss it in {@code onDestroyView}: a plain
 * dialog is not recreated with the fragment and would leak its window on rotation.
 */
public final class TechniqueDialogs {

    public interface OnPicked {
        /** @param techniqueId null for a normal working set */
        void onPicked(@Nullable String techniqueId);
    }

    private TechniqueDialogs() {
    }

    public static AlertDialog showPicker(Context context, List<TrainingTechnique> options,
                                  @Nullable String selectedId, OnPicked callback) {
        LayoutInflater inflater = LayoutInflater.from(context);
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(context);
        scroll.addView(list);

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.technique_picker_title)
                .setView(scroll)
                .setNegativeButton(R.string.action_cancel, null)
                .create();
        // An explanation opened from a row goes away with the picker, so closing the picker (or the
        // sheet) never leaves an orphan window behind.
        AlertDialog[] explanation = new AlertDialog[1];
        dialog.setOnDismissListener(d -> {
            if (explanation[0] != null) {
                explanation[0].dismiss();
                explanation[0] = null;
            }
        });

        // "Normal" first: the common case and the way to clear a technique.
        ItemTechniqueOptionBinding none = ItemTechniqueOptionBinding.inflate(inflater, list, false);
        none.badge.setText(R.string.plan_sheet_technique_none);
        none.name.setText(R.string.technique_none_option);
        none.check.setVisibility(selectedId == null ? View.VISIBLE : View.INVISIBLE);
        none.buttonInfo.setVisibility(View.GONE);
        none.option.setOnClickListener(v -> {
            callback.onPicked(null);
            dialog.dismiss();
        });
        list.addView(none.getRoot());

        for (TrainingTechnique technique : options) {
            ItemTechniqueOptionBinding row = ItemTechniqueOptionBinding.inflate(inflater, list, false);
            row.badge.setText(technique.code());
            row.name.setText(technique.name());
            row.check.setVisibility(technique.id().equals(selectedId) ? View.VISIBLE : View.INVISIBLE);
            row.option.setContentDescription(technique.name());
            row.buttonInfo.setContentDescription(
                    context.getString(R.string.plan_sheet_technique_explain, technique.name()));
            row.buttonInfo.setOnClickListener(v -> explanation[0] = showExplanation(context, technique));
            row.option.setOnClickListener(v -> {
                callback.onPicked(technique.id());
                dialog.dismiss();
            });
            list.addView(row.getRoot());
        }
        dialog.show();
        return dialog;
    }

    /** The ⓘ content: what the method is and how to record it. */
    public static AlertDialog showExplanation(Context context, TrainingTechnique technique) {
        StringBuilder message = new StringBuilder();
        if (technique.description() != null) {
            message.append(technique.description());
        }
        if (technique.instructions() != null && !technique.instructions().isEmpty()) {
            if (message.length() > 0) {
                message.append("\n\n");
            }
            message.append(technique.instructions());
        }
        return new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.technique_explain_title, technique.code(), technique.name()))
                .setMessage(message.toString())
                .setPositiveButton(R.string.technique_understood, null)
                .show();
    }
}

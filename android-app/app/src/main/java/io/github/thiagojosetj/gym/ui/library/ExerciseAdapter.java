package io.github.thiagojosetj.gym.ui.library;

import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;

import androidx.annotation.NonNull;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import io.github.thiagojosetj.gym.R;
import io.github.thiagojosetj.gym.databinding.ItemExerciseBinding;
import io.github.thiagojosetj.gym.domain.library.ExerciseSummary;

/** Library rows. In select mode each row behaves like a checkbox (visually and for TalkBack). */
final class ExerciseAdapter extends ListAdapter<ExerciseSummary, ExerciseAdapter.Holder> {

    interface Listener {
        void onClick(ExerciseSummary exercise);

        void onInfo(ExerciseSummary exercise);
    }

    private static final Object PAYLOAD_SELECTION = new Object();

    private static final DiffUtil.ItemCallback<ExerciseSummary> DIFF = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(@NonNull ExerciseSummary a, @NonNull ExerciseSummary b) {
            return a.id().equals(b.id());
        }

        @Override
        public boolean areContentsTheSame(@NonNull ExerciseSummary a, @NonNull ExerciseSummary b) {
            return a.equals(b);
        }
    };

    private final boolean selectMode;
    private final Listener listener;
    private Set<String> selected = Collections.emptySet();

    ExerciseAdapter(boolean selectMode, Listener listener) {
        super(DIFF);
        this.selectMode = selectMode;
        this.listener = listener;
    }

    /** Updates only the rows whose selection state changed (payload bind, no full rebind). */
    void setSelected(List<String> selectedIds) {
        Set<String> next = new HashSet<>(selectedIds);
        Set<String> changed = new HashSet<>(selected);
        changed.addAll(next);
        Set<String> unchanged = new HashSet<>(selected);
        unchanged.retainAll(next);
        changed.removeAll(unchanged);
        selected = next;
        List<ExerciseSummary> items = new ArrayList<>(getCurrentList());
        for (int i = 0; i < items.size(); i++) {
            if (changed.contains(items.get(i).id())) {
                notifyItemChanged(i, PAYLOAD_SELECTION);
            }
        }
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemExerciseBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(getItem(position));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.contains(PAYLOAD_SELECTION)) {
            holder.bindSelection(getItem(position));
        } else {
            super.onBindViewHolder(holder, position, payloads);
        }
    }

    final class Holder extends RecyclerView.ViewHolder {

        private final ItemExerciseBinding binding;
        private boolean checked;

        Holder(ItemExerciseBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            ViewCompat.setAccessibilityDelegate(binding.getRoot(), new AccessibilityDelegateCompat() {
                @Override
                public void onInitializeAccessibilityNodeInfo(@NonNull View host,
                                                              @NonNull AccessibilityNodeInfoCompat info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    info.setClassName(selectMode ? CheckBox.class.getName() : Button.class.getName());
                    info.setCheckable(selectMode);
                    info.setChecked(selectMode && checked
                            ? AccessibilityNodeInfoCompat.CHECKED_STATE_TRUE
                            : AccessibilityNodeInfoCompat.CHECKED_STATE_FALSE);
                }
            });
        }

        void bind(ExerciseSummary exercise) {
            Resources res = binding.getRoot().getResources();
            binding.name.setText(exercise.name());
            binding.subtitle.setText(subtitle(exercise, res.getString(R.string.separator_dot)));
            binding.chevron.setVisibility(selectMode ? View.GONE : View.VISIBLE);
            binding.buttonInfo.setVisibility(selectMode ? View.VISIBLE : View.GONE);
            binding.buttonInfo.setContentDescription(res.getString(R.string.library_open_details, exercise.name()));
            binding.buttonInfo.setOnClickListener(v -> listener.onInfo(exercise));
            binding.getRoot().setOnClickListener(v -> listener.onClick(exercise));
            bindSelection(exercise);
        }

        void bindSelection(ExerciseSummary exercise) {
            checked = selected.contains(exercise.id());
            binding.check.setVisibility(selectMode ? View.VISIBLE : View.GONE);
            binding.check.setChecked(checked);
            if (selectMode) {
                ViewCompat.setStateDescription(binding.getRoot(), binding.getRoot().getResources().getString(
                        checked ? R.string.library_selected_state : R.string.library_not_selected_state));
            }
        }
    }

    /** "Peito · Peitoral médio (porção esternal) · Barra" — skipping repeated or missing parts. */
    static String subtitle(ExerciseSummary exercise, String separator) {
        List<String> parts = new ArrayList<>();
        if (exercise.primaryGroupName() != null) {
            parts.add(exercise.primaryGroupName());
        }
        if (exercise.primaryMuscleName() != null
                && !Objects.equals(exercise.primaryMuscleName(), exercise.primaryGroupName())) {
            parts.add(exercise.primaryMuscleName());
        }
        if (exercise.primaryEquipmentName() != null) {
            parts.add(exercise.primaryEquipmentName());
        }
        return String.join(separator, parts);
    }
}

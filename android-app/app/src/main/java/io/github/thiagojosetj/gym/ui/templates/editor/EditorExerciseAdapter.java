package io.github.thiagojosetj.gym.ui.templates.editor;

import android.annotation.SuppressLint;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import io.github.thiagojosetj.gym.R;
import io.github.thiagojosetj.gym.databinding.ItemTemplateExerciseBinding;

/**
 * Exercise cards of the editor, reorderable by drag and drop.
 *
 * <p>During a drag the adapter moves items in its own list (instant feedback); the final order is
 * committed to the ViewModel once, when the item is dropped. The ViewModel then publishes a list
 * in the same order, so DiffUtil finds nothing to change.
 */
final class EditorExerciseAdapter extends RecyclerView.Adapter<EditorExerciseAdapter.Holder> {

    interface Listener {
        void onEdit(TemplateExerciseItem item);

        void onMore(TemplateExerciseItem item, int position, View anchor);

        void onStartDrag(RecyclerView.ViewHolder holder);
    }

    private final Listener listener;
    private final PlanFormatter formatter;
    private final List<TemplateExerciseItem> items = new ArrayList<>();

    EditorExerciseAdapter(Listener listener, PlanFormatter formatter) {
        this.listener = listener;
        this.formatter = formatter;
    }

    void submit(List<TemplateExerciseItem> next) {
        List<TemplateExerciseItem> old = new ArrayList<>(items);
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return old.size();
            }

            @Override
            public int getNewListSize() {
                return next.size();
            }

            @Override
            public boolean areItemsTheSame(int oldPos, int newPos) {
                return old.get(oldPos).id().equals(next.get(newPos).id());
            }

            @Override
            public boolean areContentsTheSame(int oldPos, int newPos) {
                return old.get(oldPos).equals(next.get(newPos));
            }
        });
        items.clear();
        items.addAll(next);
        diff.dispatchUpdatesTo(this);
    }

    /** Visual-only move while dragging (a fast drag may jump several positions at once). */
    void moveLocal(int from, int to) {
        TemplateExerciseItem moved = items.remove(from);
        items.add(to, moved);
        notifyItemMoved(from, to);
    }

    int size() {
        return items.size();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemTemplateExerciseBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(items.get(position));
    }

    final class Holder extends RecyclerView.ViewHolder {

        private final ItemTemplateExerciseBinding binding;

        @SuppressLint("ClickableViewAccessibility") // drag is optional; the menu offers move up/down
        Holder(ItemTemplateExerciseBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            binding.dragHandle.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    listener.onStartDrag(this);
                }
                return false;
            });
        }

        void bind(TemplateExerciseItem item) {
            Resources res = binding.getRoot().getResources();
            binding.name.setText(item.name());
            binding.muscle.setText(item.primaryMuscleName());
            binding.muscle.setVisibility(item.primaryMuscleName() == null ? View.GONE : View.VISIBLE);
            binding.plan.setText(formatter.planLine(item));
            binding.rest.setText(formatter.restLine(item));
            binding.notes.setText(item.notes());
            binding.notes.setVisibility(item.notes() == null ? View.GONE : View.VISIBLE);
            binding.dragHandle.setContentDescription(res.getString(R.string.editor_drag_handle, item.name()));
            binding.buttonMore.setContentDescription(res.getString(R.string.editor_exercise_options, item.name()));
            // Position ("2 of 5") is announced by RecyclerView's own collection accessibility info.
            binding.getRoot().setOnClickListener(v -> listener.onEdit(item));
            binding.buttonMore.setOnClickListener(v -> listener.onMore(item, getBindingAdapterPosition(), v));
        }
    }
}

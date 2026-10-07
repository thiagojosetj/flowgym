package io.github.thiagojosetj.gym.ui.templates.editor;

import android.annotation.SuppressLint;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
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

        /** Reorder requested without dragging (overflow menu or TalkBack action). */
        void onMoveRequested(TemplateExerciseItem item, int fromPosition, int toPosition);

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
        /** Ids of the accessibility actions added on the last bind, so rebinds don't stack them. */
        private final List<Integer> accessibilityActionIds = new ArrayList<>(2);

        @SuppressLint("ClickableViewAccessibility") // drag is optional; see the accessibility actions
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
            bindName(item, res);
            binding.muscle.setText(item.primaryMuscleName());
            binding.muscle.setVisibility(item.primaryMuscleName() == null ? View.GONE : View.VISIBLE);
            binding.plan.setText(formatter.planLine(item));
            binding.rest.setText(formatter.restLine(item));
            List<String> codes = new ArrayList<>(item.badges().size());
            List<String> names = new ArrayList<>(item.badges().size());
            for (TemplateExerciseItem.Badge badge : item.badges()) {
                codes.add(badge.code());
                names.add(badge.name());
            }
            String separator = res.getString(R.string.separator_dot);
            binding.badges.setText(String.join(separator, codes));
            // A screen reader would otherwise spell out "AQ": read the technique names instead.
            binding.badges.setContentDescription(names.isEmpty() ? null : String.join(separator, names));
            binding.badges.setVisibility(codes.isEmpty() ? View.GONE : View.VISIBLE);
            binding.notes.setText(item.notes());
            binding.notes.setVisibility(item.notes() == null ? View.GONE : View.VISIBLE);
            binding.buttonMore.setContentDescription(res.getString(R.string.editor_exercise_options, item.name()));
            // Position ("2 of 5") is announced by RecyclerView's own collection accessibility info.
            binding.getRoot().setOnClickListener(v -> listener.onEdit(item));
            binding.buttonMore.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    listener.onMore(item, position, v);
                }
            });
            bindAccessibilityActions(item, res);
        }

        /** "A1 Supino reto" for an exercise in a group (PRODUCT_SPEC 6.3), its name otherwise. */
        private void bindName(TemplateExerciseItem item, Resources res) {
            TemplateExerciseItem.GroupMark group = item.group();
            if (group == null) {
                binding.name.setText(item.name());
                binding.name.setContentDescription(null); // a recycled card may have had one
                return;
            }
            binding.name.setText(res.getString(R.string.editor_group_exercise_name,
                    group.label(), group.number(), item.name()));
            // A screen reader would otherwise spell "A1" out as a code, like it would "AQ".
            binding.name.setContentDescription(res.getString(
                    R.string.editor_group_exercise_description, group.label(), group.number(),
                    item.name()));
        }

        /**
         * Dragging is a pointer gesture TalkBack cannot perform, so reordering is also offered as
         * accessibility actions on the card (and in the overflow menu).
         */
        private void bindAccessibilityActions(TemplateExerciseItem item, Resources res) {
            for (Integer id : accessibilityActionIds) {
                ViewCompat.removeAccessibilityAction(binding.getRoot(), id);
            }
            accessibilityActionIds.clear();
            int position = getBindingAdapterPosition();
            if (position == RecyclerView.NO_POSITION) {
                return;
            }
            if (position > 0) {
                accessibilityActionIds.add(ViewCompat.addAccessibilityAction(binding.getRoot(),
                        res.getString(R.string.editor_move_up), (view, arguments) ->
                                requestMove(item, -1)));
            }
            if (position < items.size() - 1) {
                accessibilityActionIds.add(ViewCompat.addAccessibilityAction(binding.getRoot(),
                        res.getString(R.string.editor_move_down), (view, arguments) ->
                                requestMove(item, +1)));
            }
        }

        private boolean requestMove(TemplateExerciseItem item, int delta) {
            int position = getBindingAdapterPosition();
            if (position == RecyclerView.NO_POSITION) {
                return false;
            }
            int target = position + delta;
            if (target < 0 || target >= items.size()) {
                return false;
            }
            listener.onMoveRequested(item, position, target);
            return true;
        }
    }
}

package io.github.thiagojosetj.gym.ui.library;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.thiagojosetj.gym.R;
import io.github.thiagojosetj.gym.ui.common.MuscleMapView;

/**
 * The parts of a muscle group, shown as pictures.
 *
 * <p>This replaced a row of name-only chips for one reason: the names are the problem. Someone who
 * does not already know what "vasto medial" or "semitendinoso" means cannot choose from a list of
 * them, and the subgroups exist precisely for people narrowing down what they want to train.
 * Showing the five figures side by side lets the choice be made by looking.
 */
public class MuscleFilterAdapter extends RecyclerView.Adapter<MuscleFilterAdapter.Holder> {

    /**
     * One option in the row.
     *
     * @param muscleId null for the "whole group" option, which the filter reads as no subgroup
     * @param muscleCode the code the drawing is keyed by
     */
    public record Option(@Nullable String muscleId, String muscleCode, String name) {
    }

    public interface OnMuscleSelected {
        void onMuscleSelected(@Nullable String muscleId);
    }

    private final List<Option> options = new ArrayList<>();
    private final OnMuscleSelected listener;
    @Nullable
    private String selectedId;

    public MuscleFilterAdapter(OnMuscleSelected listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    /**
     * Replaces the row, which is what changing muscle group actually does: none of the old parts
     * belong to the new group. Said as a removal and an insertion rather than
     * {@code notifyDataSetChanged}, so RecyclerView can animate it and lint stays quiet about a
     * blanket invalidation that would also be a lie about what changed.
     */
    public void submit(List<Option> newOptions, @Nullable String newSelectedId) {
        int previousCount = options.size();
        options.clear();
        notifyItemRangeRemoved(0, previousCount);
        options.addAll(newOptions);
        selectedId = newSelectedId;
        notifyItemRangeInserted(0, options.size());
    }

    /** Moves the selection without rebuilding the row, so picking a part does not flicker. */
    public void select(@Nullable String newSelectedId) {
        if (Objects.equals(selectedId, newSelectedId)) {
            return;
        }
        selectedId = newSelectedId;
        notifyItemRangeChanged(0, options.size());
    }

    @Override
    public long getItemId(int position) {
        String id = options.get(position).muscleId();
        return id == null ? 0L : id.hashCode();
    }

    @Override
    public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_muscle_filter, parent, false));
    }

    @Override
    public void onBindViewHolder(Holder holder, int position) {
        Option option = options.get(position);
        holder.name.setText(option.name());
        holder.map.setMuscle(option.muscleCode());

        boolean selected = Objects.equals(selectedId, option.muscleId());
        holder.itemView.setSelected(selected);
        // The name alone is what a screen reader should read; the picture is the same information
        // for people who can see it, and announcing both would say everything twice.
        holder.itemView.setContentDescription(option.name());
        holder.itemView.setOnClickListener(v -> listener.onMuscleSelected(option.muscleId()));
    }

    @Override
    public int getItemCount() {
        return options.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final MuscleMapView map;
        final TextView name;

        Holder(View itemView) {
            super(itemView);
            map = itemView.findViewById(R.id.muscle_map);
            name = itemView.findViewById(R.id.muscle_name);
        }
    }
}

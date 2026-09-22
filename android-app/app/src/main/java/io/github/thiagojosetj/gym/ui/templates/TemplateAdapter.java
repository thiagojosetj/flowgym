package io.github.thiagojosetj.gym.ui.templates;

import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import io.github.thiagojosetj.gym.R;
import io.github.thiagojosetj.gym.databinding.ItemTemplateBinding;
import io.github.thiagojosetj.gym.domain.template.TemplateSummary;

/** Cards of "Meus treinos". DiffUtil rebinds only the cards whose content changed. */
final class TemplateAdapter extends ListAdapter<TemplateSummary, TemplateAdapter.Holder> {

    interface Listener {
        void onOpen(TemplateSummary template);

        void onMore(TemplateSummary template, View anchor);
    }

    private static final DiffUtil.ItemCallback<TemplateSummary> DIFF = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(@NonNull TemplateSummary a, @NonNull TemplateSummary b) {
            return a.id().equals(b.id());
        }

        @Override
        public boolean areContentsTheSame(@NonNull TemplateSummary a, @NonNull TemplateSummary b) {
            return a.equals(b); // records compare every field
        }
    };

    private final Listener listener;

    TemplateAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemTemplateBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(getItem(position));
    }

    final class Holder extends RecyclerView.ViewHolder {

        private final ItemTemplateBinding binding;

        Holder(ItemTemplateBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(TemplateSummary template) {
            Resources res = binding.getRoot().getResources();
            binding.name.setText(template.name());
            String groups = String.join(res.getString(R.string.separator_dot), template.muscleGroups());
            binding.muscleGroups.setText(groups);
            binding.muscleGroups.setVisibility(groups.isEmpty() ? View.GONE : View.VISIBLE);
            String exercises = res.getQuantityString(R.plurals.template_exercise_count,
                    template.exerciseCount(), template.exerciseCount());
            String sets = res.getQuantityString(R.plurals.template_set_count, template.setCount(), template.setCount());
            binding.summary.setText(res.getString(R.string.template_summary_line, exercises, sets));
            binding.buttonMore.setContentDescription(res.getString(R.string.template_more_options, template.name()));
            binding.getRoot().setOnClickListener(v -> listener.onOpen(template));
            binding.buttonMore.setOnClickListener(v -> listener.onMore(template, v));
        }
    }
}

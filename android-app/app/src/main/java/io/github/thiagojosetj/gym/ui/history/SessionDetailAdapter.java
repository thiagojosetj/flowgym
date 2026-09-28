package io.github.thiagojosetj.gym.ui.history;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Objects;
import java.util.concurrent.Executor;

import io.github.thiagojosetj.gym.databinding.ItemHistoryExerciseBinding;
import io.github.thiagojosetj.gym.databinding.ItemHistorySetBinding;
import io.github.thiagojosetj.gym.databinding.ItemHistorySummaryBinding;
import io.github.thiagojosetj.gym.ui.common.ListDiffing;

/** Rows of the finished-session detail (PRODUCT_SPEC HIS-01, HIS-03, HIS-04), in one flat list. */
final class SessionDetailAdapter extends ListAdapter<DetailRow, RecyclerView.ViewHolder> {

    // Nothing here decides what to say. Every row arrives with its text already written, and a
    // line that has nothing to say is hidden rather than left blank: a blank line would read as a
    // value nobody entered.
    private static final int TYPE_SUMMARY = 0;
    private static final int TYPE_EXERCISE = 1;
    private static final int TYPE_SET = 2;

    SessionDetailAdapter(Executor diffExecutor) {
        super(ListDiffing.config(DIFF, diffExecutor));
    }

    @Override
    public int getItemViewType(int position) {
        DetailRow row = getItem(position);
        if (row instanceof DetailRow.Summary) {
            return TYPE_SUMMARY;
        }
        return row instanceof DetailRow.ExerciseHeader ? TYPE_EXERCISE : TYPE_SET;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_SUMMARY) {
            return new SummaryHolder(ItemHistorySummaryBinding.inflate(inflater, parent, false));
        }
        if (viewType == TYPE_EXERCISE) {
            return new ExerciseHolder(ItemHistoryExerciseBinding.inflate(inflater, parent, false));
        }
        return new SetHolder(ItemHistorySetBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        DetailRow row = getItem(position);
        if (holder instanceof SummaryHolder && row instanceof DetailRow.Summary summary) {
            ((SummaryHolder) holder).bind(summary);
        } else if (holder instanceof ExerciseHolder
                && row instanceof DetailRow.ExerciseHeader header) {
            ((ExerciseHolder) holder).bind(header);
        } else if (holder instanceof SetHolder && row instanceof DetailRow.SetRow set) {
            ((SetHolder) holder).bind(set);
        }
    }

    // ------------------------------------------------------------------ holders

    static final class SummaryHolder extends RecyclerView.ViewHolder {
        private final ItemHistorySummaryBinding views;

        SummaryHolder(ItemHistorySummaryBinding views) {
            super(views.getRoot());
            this.views = views;
        }

        void bind(DetailRow.Summary row) {
            boolean named = row.name() != null && !row.name().trim().isEmpty();
            setOptional(views.name, named ? row.name() : null);
            views.dateTime.setText(row.dateTime());
            setLine(views.duration, row.duration());
            views.sets.setText(row.sets());
            views.volume.setText(row.volume());
            setOptional(views.outsideVolume, row.outsideVolume());
            setLine(views.timeUnderTension, row.timeUnderTension());
            setOptional(views.rating, row.rating());
            bindComparison(row);
            setOptional(views.exercisesTitle, row.exercisesTitle());
        }

        private void bindComparison(DetailRow.Summary row) {
            DetailRow.Comparison comparison = row.comparison();
            setOptional(views.comparisonNone, row.noComparison());
            views.comparisonDetails.setVisibility(comparison == null ? View.GONE : View.VISIBLE);
            if (comparison == null) {
                return;
            }
            views.comparisonSubtitle.setText(comparison.subtitle());
            setLine(views.comparisonVolume, comparison.volume());
            setLine(views.comparisonSets, comparison.sets());
            setLine(views.comparisonReps, comparison.reps());
            setLine(views.comparisonTime, comparison.time());
        }
    }

    static final class ExerciseHolder extends RecyclerView.ViewHolder {
        private final ItemHistoryExerciseBinding views;

        ExerciseHolder(ItemHistoryExerciseBinding views) {
            super(views.getRoot());
            this.views = views;
        }

        void bind(DetailRow.ExerciseHeader row) {
            views.name.setText(row.name());
            views.sets.setText(row.sets());
            views.volume.setText(row.volume());
        }
    }

    static final class SetHolder extends RecyclerView.ViewHolder {
        private final ItemHistorySetBinding views;

        SetHolder(ItemHistorySetBinding views) {
            super(views.getRoot());
            this.views = views;
        }

        void bind(DetailRow.SetRow row) {
            views.setNumber.setText(row.number());
            views.performed.setText(row.performed());
            setOptional(views.planned, row.planned());
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Shows the text, or hides the view (and clears it) when there is none. */
    private static void setOptional(TextView view, @Nullable String text) {
        view.setText(text);
        view.setVisibility(text == null ? View.GONE : View.VISIBLE);
    }

    /** Like {@link #setOptional}, and hands a screen reader the spoken form when there is one. */
    private static void setLine(TextView view, @Nullable DetailRow.Line line) {
        view.setText(line == null ? null : line.text());
        view.setContentDescription(line == null ? null : line.spoken());
        view.setVisibility(line == null ? View.GONE : View.VISIBLE);
    }

    private static final DiffUtil.ItemCallback<DetailRow> DIFF = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(@NonNull DetailRow oldItem, @NonNull DetailRow newItem) {
            return oldItem.id().equals(newItem.id());
        }

        @Override
        public boolean areContentsTheSame(@NonNull DetailRow oldItem, @NonNull DetailRow newItem) {
            return Objects.equals(oldItem, newItem);
        }
    };
}

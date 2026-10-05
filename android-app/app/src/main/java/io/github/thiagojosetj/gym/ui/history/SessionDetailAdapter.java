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

import com.google.android.material.button.MaterialButtonToggleGroup;

import io.github.thiagojosetj.gym.R;
import io.github.thiagojosetj.gym.databinding.ItemHistoryComparisonExerciseBinding;
import io.github.thiagojosetj.gym.databinding.ItemHistoryComparisonNoteBinding;
import io.github.thiagojosetj.gym.databinding.ItemHistoryComparisonSetBinding;
import io.github.thiagojosetj.gym.databinding.ItemHistoryExerciseBinding;
import io.github.thiagojosetj.gym.databinding.ItemHistorySectionTitleBinding;
import io.github.thiagojosetj.gym.databinding.ItemHistorySetBinding;
import io.github.thiagojosetj.gym.databinding.ItemHistorySummaryBinding;
import io.github.thiagojosetj.gym.ui.common.ListDiffing;

/** Rows of the finished-session detail (PRODUCT_SPEC HIS-01, HIS-03, HIS-04), in one flat list. */
final class SessionDetailAdapter extends ListAdapter<DetailRow, RecyclerView.ViewHolder> {

    interface Listener {
        void onToggleSets(String sessionExerciseId);

        /** @param rating 1 to 5, or null when the chosen number was tapped again */
        void onRate(@Nullable Integer rating);
    }

    /** Left to right, so the index is the rating minus one. */
    private static final int[] RATING_BUTTONS = {
            R.id.rating_1, R.id.rating_2, R.id.rating_3, R.id.rating_4, R.id.rating_5};

    // Nothing here decides what to say. Every row arrives with its text already written, and a
    // line that has nothing to say is hidden rather than left blank: a blank line would read as a
    // value nobody entered.
    private static final int TYPE_SUMMARY = 0;
    private static final int TYPE_EXERCISE = 1;
    private static final int TYPE_SET = 2;
    private static final int TYPE_SECTION_TITLE = 3;
    private static final int TYPE_COMPARISON_EXERCISE = 4;
    private static final int TYPE_COMPARISON_SET = 5;
    private static final int TYPE_COMPARISON_NOTE = 6;

    private final Listener listener;

    SessionDetailAdapter(Listener listener, Executor diffExecutor) {
        super(ListDiffing.config(DIFF, diffExecutor));
        this.listener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        DetailRow row = getItem(position);
        if (row instanceof DetailRow.Summary) {
            return TYPE_SUMMARY;
        }
        if (row instanceof DetailRow.SectionTitle) {
            return TYPE_SECTION_TITLE;
        }
        if (row instanceof DetailRow.ExerciseComparisonRow) {
            return TYPE_COMPARISON_EXERCISE;
        }
        if (row instanceof DetailRow.SetComparisonRow) {
            return TYPE_COMPARISON_SET;
        }
        if (row instanceof DetailRow.ComparisonNote) {
            return TYPE_COMPARISON_NOTE;
        }
        return row instanceof DetailRow.ExerciseHeader ? TYPE_EXERCISE : TYPE_SET;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_SUMMARY) {
            return new SummaryHolder(ItemHistorySummaryBinding.inflate(inflater, parent, false),
                    listener);
        }
        if (viewType == TYPE_EXERCISE) {
            return new ExerciseHolder(ItemHistoryExerciseBinding.inflate(inflater, parent, false));
        }
        if (viewType == TYPE_SECTION_TITLE) {
            return new TitleHolder(ItemHistorySectionTitleBinding.inflate(inflater, parent, false));
        }
        if (viewType == TYPE_COMPARISON_EXERCISE) {
            return new ComparisonExerciseHolder(
                    ItemHistoryComparisonExerciseBinding.inflate(inflater, parent, false), listener);
        }
        if (viewType == TYPE_COMPARISON_SET) {
            return new ComparisonSetHolder(
                    ItemHistoryComparisonSetBinding.inflate(inflater, parent, false));
        }
        if (viewType == TYPE_COMPARISON_NOTE) {
            return new NoteHolder(
                    ItemHistoryComparisonNoteBinding.inflate(inflater, parent, false));
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
        } else if (holder instanceof TitleHolder && row instanceof DetailRow.SectionTitle title) {
            ((TitleHolder) holder).bind(title);
        } else if (holder instanceof ComparisonExerciseHolder
                && row instanceof DetailRow.ExerciseComparisonRow comparison) {
            ((ComparisonExerciseHolder) holder).bind(comparison);
        } else if (holder instanceof ComparisonSetHolder
                && row instanceof DetailRow.SetComparisonRow pair) {
            ((ComparisonSetHolder) holder).bind(pair);
        } else if (holder instanceof NoteHolder && row instanceof DetailRow.ComparisonNote note) {
            ((NoteHolder) holder).bind(note);
        }
    }

    // ------------------------------------------------------------------ holders

    static final class SummaryHolder extends RecyclerView.ViewHolder {
        private final ItemHistorySummaryBinding views;
        private final Listener listener;

        SummaryHolder(ItemHistorySummaryBinding views, Listener listener) {
            super(views.getRoot());
            this.views = views;
            this.listener = listener;
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
            bindRating(row);
            bindComparison(row);
            setOptional(views.exercisesTitle, row.exercisesTitle());
        }

        /**
         * Shows the stored rating and writes a new one on each tap (PRODUCT_SPEC HIS-06).
         *
         * <p>The listener is detached while the stored value is applied. Checking a button fires
         * the same callback a tap does, so leaving it attached would make every redraw write back
         * what it had just read - and the one case that matters, a failed write being put back,
         * would write the wrong value again.
         */
        private void bindRating(DetailRow.Summary row) {
            MaterialButtonToggleGroup group = views.rating.ratingGroup;
            group.clearOnButtonCheckedListeners();
            Integer rating = row.rating();
            if (rating == null || rating < 1 || rating > RATING_BUTTONS.length) {
                group.clearChecked();
            } else {
                group.check(RATING_BUTTONS[rating - 1]);
            }
            for (int i = 0; i < RATING_BUTTONS.length; i++) {
                // "4" alone tells a screen reader nothing about what it does.
                views.rating.getRoot().findViewById(RATING_BUTTONS[i]).setContentDescription(
                        itemView.getContext().getString(R.string.summary_rating_spoken, i + 1,
                                RATING_BUTTONS.length));
            }
            group.addOnButtonCheckedListener((toggleGroup, checkedId, isChecked) -> {
                if (!isChecked && toggleGroup.getCheckedButtonId() == View.NO_ID) {
                    listener.onRate(null); // the chosen number was tapped again: no rating
                    return;
                }
                if (isChecked) {
                    for (int i = 0; i < RATING_BUTTONS.length; i++) {
                        if (RATING_BUTTONS[i] == checkedId) {
                            listener.onRate(i + 1);
                            return;
                        }
                    }
                }
            });
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

    static final class TitleHolder extends RecyclerView.ViewHolder {
        private final ItemHistorySectionTitleBinding views;

        TitleHolder(ItemHistorySectionTitleBinding views) {
            super(views.getRoot());
            this.views = views;
        }

        void bind(DetailRow.SectionTitle row) {
            views.title.setText(row.text());
        }
    }

    static final class ComparisonExerciseHolder extends RecyclerView.ViewHolder {
        private final ItemHistoryComparisonExerciseBinding views;
        /** Set on bind; the button exists before the row it belongs to is known. */
        @Nullable
        private String sessionExerciseId;

        ComparisonExerciseHolder(ItemHistoryComparisonExerciseBinding views, Listener listener) {
            super(views.getRoot());
            this.views = views;
            views.buttonComparisonSets.setOnClickListener(v -> {
                if (sessionExerciseId != null) {
                    listener.onToggleSets(sessionExerciseId);
                }
            });
        }

        void bind(DetailRow.ExerciseComparisonRow row) {
            sessionExerciseId = row.sessionExerciseId();
            views.comparisonExerciseName.setText(row.name());
            setLine(views.comparisonExerciseVolume, row.volume());
            setOptional(views.comparisonExerciseNoVolume, row.noVolume());
            setLine(views.comparisonExerciseSets, row.sets());
            setLine(views.comparisonExerciseReps, row.reps());
            setOptional(views.buttonComparisonSets, row.expandLabel());
        }
    }

    static final class ComparisonSetHolder extends RecyclerView.ViewHolder {
        private final ItemHistoryComparisonSetBinding views;

        ComparisonSetHolder(ItemHistoryComparisonSetBinding views) {
            super(views.getRoot());
            this.views = views;
        }

        void bind(DetailRow.SetComparisonRow row) {
            views.comparisonSetNumber.setText(row.number());
            views.comparisonSetPrevious.setText(row.previous());
            views.comparisonSetCurrent.setText(row.current());
            views.comparisonSetChange.setText(row.change());
            // The cells are marked unimportant in the layout, so this is the whole row's voice.
            views.getRoot().setContentDescription(row.spoken());
        }
    }

    static final class NoteHolder extends RecyclerView.ViewHolder {
        private final ItemHistoryComparisonNoteBinding views;

        NoteHolder(ItemHistoryComparisonNoteBinding views) {
            super(views.getRoot());
            this.views = views;
        }

        void bind(DetailRow.ComparisonNote row) {
            views.note.setText(row.text());
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

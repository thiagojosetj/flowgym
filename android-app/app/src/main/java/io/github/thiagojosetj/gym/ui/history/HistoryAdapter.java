package io.github.thiagojosetj.gym.ui.history;

import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.time.Clock;
import java.util.concurrent.Executor;

import io.github.thiagojosetj.gym.R;
import io.github.thiagojosetj.gym.databinding.ItemHistorySessionBinding;
import io.github.thiagojosetj.gym.domain.session.SessionHistoryEntry;
import io.github.thiagojosetj.gym.ui.common.Durations;
import io.github.thiagojosetj.gym.ui.common.ListDiffing;
import io.github.thiagojosetj.gym.ui.common.SessionDates;

/** Rows of "Histórico" (PRODUCT_SPEC HIS-03). DiffUtil rebinds only the rows that changed. */
final class HistoryAdapter extends ListAdapter<SessionHistoryEntry, HistoryAdapter.Holder> {

    interface Listener {
        void onOpen(SessionHistoryEntry entry);
    }

    private static final DiffUtil.ItemCallback<SessionHistoryEntry> DIFF =
            new DiffUtil.ItemCallback<>() {
                @Override
                public boolean areItemsTheSame(@NonNull SessionHistoryEntry a,
                                               @NonNull SessionHistoryEntry b) {
                    return a.sessionId().equals(b.sessionId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull SessionHistoryEntry a,
                                                  @NonNull SessionHistoryEntry b) {
                    return a.equals(b); // records compare every field
                }
            };

    private final Listener listener;
    /** "Hoje" and "Ontem" depend on when the list is read, so the clock is injected, not read. */
    private final Clock clock;

    HistoryAdapter(Listener listener, Clock clock, Executor diffExecutor) {
        super(ListDiffing.config(DIFF, diffExecutor));
        this.listener = listener;
        this.clock = clock;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemHistorySessionBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(getItem(position));
    }

    final class Holder extends RecyclerView.ViewHolder {

        private final ItemHistorySessionBinding binding;

        Holder(ItemHistorySessionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(SessionHistoryEntry entry) {
            Resources res = binding.getRoot().getResources();
            binding.name.setText(entry.name());
            String date = res.getString(R.string.history_session_date,
                    SessionDates.relativeDay(res, entry.startedAt(), entry.timeZone(),
                            clock.millis()),
                    SessionDates.timeOfDay(entry.startedAt(), entry.timeZone()));
            binding.date.setText(date);
            binding.duration.setText(String.join(res.getString(R.string.separator_dot),
                    Durations.clock(entry.totalMs()),
                    res.getString(R.string.history_effective_time,
                            Durations.clock(entry.effectiveMs()))));
            String exercises = res.getQuantityString(R.plurals.template_exercise_count,
                    entry.exercises(), entry.exercises());
            String sets = res.getQuantityString(R.plurals.template_set_count,
                    entry.performedSets(), entry.performedSets());
            binding.summary.setText(
                    res.getString(R.string.template_summary_line, exercises, sets));
            // A contentDescription on the card replaces what TalkBack would read from its
            // children, so it has to carry the whole row - otherwise the date, the duration and
            // the counts simply stop existing for anyone using a screen reader.
            binding.getRoot().setContentDescription(String.join(
                    res.getString(R.string.separator_dot),
                    res.getString(R.string.history_open_session, entry.name()),
                    date,
                    binding.duration.getText().toString(),
                    binding.summary.getText().toString()));
            binding.getRoot().setOnClickListener(v -> listener.onOpen(entry));
        }
    }
}

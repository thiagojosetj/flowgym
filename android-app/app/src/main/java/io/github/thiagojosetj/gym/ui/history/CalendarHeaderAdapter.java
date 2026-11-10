package io.github.thiagojosetj.gym.ui.history;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

import io.github.thiagojosetj.gym.R;
import io.github.thiagojosetj.gym.databinding.ItemHistoryCalendarBinding;
import io.github.thiagojosetj.gym.ui.history.HistoryListViewModel.CalendarState;

/**
 * The one row above the history: the month of trained days and what the list is filtered to
 * (PRODUCT_SPEC HIS-02).
 *
 * <p>No state of its own. It holds the last value it was given and rebinds only when that value
 * differs, which is what keeps a grid of forty-two squares from being rebuilt every time a row of
 * the list changes.
 */
final class CalendarHeaderAdapter extends RecyclerView.Adapter<CalendarHeaderAdapter.Holder> {

    interface Listener {
        void onDaySelected(LocalDate date);

        void onMonthStep(int months);

        void onShowAllDays();
    }

    private final Listener listener;
    /** Null until the first finished session exists; the header is then simply not there. */
    @Nullable
    private CalendarState state;

    CalendarHeaderAdapter(Listener listener) {
        this.listener = listener;
    }

    void submit(@Nullable CalendarState next) {
        if (Objects.equals(state, next)) {
            return;
        }
        boolean had = state != null;
        state = next;
        if (had && next != null) {
            notifyItemChanged(0);
        } else if (next != null) {
            notifyItemInserted(0);
        } else {
            notifyItemRemoved(0);
        }
    }

    @Override
    public int getItemCount() {
        return state == null ? 0 : 1;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemHistoryCalendarBinding binding = ItemHistoryCalendarBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        binding.calendar.setListener(new TrainingCalendarView.Listener() {
            @Override
            public void onDaySelected(LocalDate date) {
                listener.onDaySelected(date);
            }

            @Override
            public void onMonthStep(int months) {
                listener.onMonthStep(months);
            }
        });
        binding.buttonShowAll.setOnClickListener(v -> listener.onShowAllDays());
        return new Holder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        CalendarState current = state;
        if (current == null) {
            return;
        }
        ItemHistoryCalendarBinding binding = holder.binding;
        binding.calendar.render(current.calendar(), current.selectedDay());
        binding.calendar.setMonthStepEnabled(current.canGoBack(), current.canGoForward());

        LocalDate selected = current.selectedDay();
        binding.filterBar.setVisibility(selected == null ? View.GONE : View.VISIBLE);
        if (selected != null) {
            Locale locale = binding.getRoot().getResources().getConfiguration().getLocales().get(0);
            binding.filterLabel.setText(binding.getRoot().getResources().getString(
                    R.string.history_filtered_by_day,
                    DateTimeFormatter.ofPattern("d 'de' MMMM", locale).format(selected)));
        }
        binding.emptyDay.setVisibility(current.selectedDayIsEmpty() ? View.VISIBLE : View.GONE);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ItemHistoryCalendarBinding binding;

        Holder(ItemHistoryCalendarBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}

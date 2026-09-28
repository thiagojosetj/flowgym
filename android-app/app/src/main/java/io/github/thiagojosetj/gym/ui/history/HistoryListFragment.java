package io.github.thiagojosetj.gym.ui.history;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import io.github.thiagojosetj.gym.AppContainer;
import io.github.thiagojosetj.gym.R;
import io.github.thiagojosetj.gym.databinding.FragmentHistoryListBinding;
import io.github.thiagojosetj.gym.domain.session.SessionHistoryEntry;
import io.github.thiagojosetj.gym.ui.common.SafeNavigation;
import io.github.thiagojosetj.gym.ui.common.ViewModelFactories;

/** "Histórico": finished sessions, newest first, each opening its detail (PRODUCT_SPEC HIS-03). */
public class HistoryListFragment extends Fragment implements HistoryAdapter.Listener {

    private FragmentHistoryListBinding binding;
    private HistoryAdapter adapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHistoryListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        AppContainer app = ViewModelFactories.container(this);
        HistoryListViewModel viewModel = new ViewModelProvider(this,
                ViewModelFactories.of(HistoryListViewModel.class,
                        () -> new HistoryListViewModel(app.history)))
                .get(HistoryListViewModel.class);

        adapter = new HistoryAdapter(this, app.clock, app.executors.diskIO());
        binding.list.setAdapter(adapter);

        viewModel.sessions().observe(getViewLifecycleOwner(), sessions -> {
            adapter.submitList(sessions);
            binding.emptyState.setVisibility(sessions.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    @Override
    public void onOpen(SessionHistoryEntry entry) {
        SafeNavigation.navigate(this, R.id.action_history_to_detail,
                SessionDetailFragment.args(entry.sessionId()));
    }

    /**
     * Rebinds the visible rows so "Hoje" and "Ontem" cannot go stale.
     *
     * <p>Those labels are a function of the current time, but nothing re-binds a row on its own:
     * Room does not re-emit when no table changed, and {@code submitList} returns early when handed
     * the same list instance. Without this, finishing a workout at 23:50 and reopening the app at
     * 00:05 leaves the row saying "Hoje" about yesterday - and once some rows are recycled and
     * others are not, the same day is labelled two different ways in one list.
     */
    @Override
    public void onResume() {
        super.onResume();
        if (adapter != null && adapter.getItemCount() > 0) {
            adapter.notifyItemRangeChanged(0, adapter.getItemCount());
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding.list.setAdapter(null);
        adapter = null;
        binding = null;
    }
}

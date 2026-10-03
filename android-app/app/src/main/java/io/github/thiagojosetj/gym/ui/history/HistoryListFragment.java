package io.github.thiagojosetj.gym.ui.history;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
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
    /** A field because the row's menu arrives long after onViewCreated has returned. */
    private HistoryListViewModel viewModel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHistoryListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        AppContainer app = ViewModelFactories.container(this);
        viewModel = new ViewModelProvider(this,
                ViewModelFactories.of(HistoryListViewModel.class,
                        () -> new HistoryListViewModel(app.history)))
                .get(HistoryListViewModel.class);

        adapter = new HistoryAdapter(this, app.clock, app.executors.diskIO());
        binding.list.setAdapter(adapter);

        viewModel.sessions().observe(getViewLifecycleOwner(), sessions -> {
            adapter.submitList(sessions);
            binding.emptyState.setVisibility(sessions.isEmpty() ? View.VISIBLE : View.GONE);
        });
        viewModel.failures().observe(getViewLifecycleOwner(), event -> {
            if (event.consume() != null && binding != null) {
                Snackbar.make(binding.getRoot(), R.string.history_delete_failed,
                        Snackbar.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onMore(SessionHistoryEntry entry, View anchor) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        popup.inflate(R.menu.menu_history_item);
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_delete) {
                confirmDelete(entry);
                return true;
            }
            return false;
        });
        popup.show();
    }

    /**
     * Asks before removing, and says what is lost. The numbers of that day go for good; the
     * template that produced them is a different thing and stays.
     */
    private void confirmDelete(SessionHistoryEntry entry) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.history_delete_title)
                .setMessage(getString(R.string.history_delete_message, entry.name()))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.history_delete_action,
                        (dialog, which) -> viewModel.delete(entry.sessionId()))
                .show();
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

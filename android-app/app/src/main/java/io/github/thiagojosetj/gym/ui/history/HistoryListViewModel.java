package io.github.thiagojosetj.gym.ui.history;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;

import io.github.thiagojosetj.gym.data.repository.HistoryRepository;
import io.github.thiagojosetj.gym.domain.session.SessionHistoryEntry;

/** State of "Histórico" (PRODUCT_SPEC HIS-03): every finished session, newest first. */
public final class HistoryListViewModel extends ViewModel {

    private final LiveData<List<SessionHistoryEntry>> list;

    public HistoryListViewModel(HistoryRepository history) {
        this.list = history.observeHistory();
    }

    public LiveData<List<SessionHistoryEntry>> sessions() {
        return list;
    }
}

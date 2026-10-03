package io.github.thiagojosetj.gym.ui.history;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;

import io.github.thiagojosetj.gym.data.repository.HistoryRepository;
import io.github.thiagojosetj.gym.domain.session.SessionHistoryEntry;
import io.github.thiagojosetj.gym.core.Event;

/** State of "Histórico" (PRODUCT_SPEC HIS-03): every finished session, newest first. */
public final class HistoryListViewModel extends ViewModel {

    private final HistoryRepository history;
    private final LiveData<List<SessionHistoryEntry>> list;
    private final MutableLiveData<Event<Boolean>> failures = new MutableLiveData<>();

    public HistoryListViewModel(HistoryRepository history) {
        this.history = history;
        this.list = history.observeHistory();
    }

    public LiveData<List<SessionHistoryEntry>> sessions() {
        return list;
    }

    /** Raised only when a removal failed; the list removes the row by itself when it works. */
    public LiveData<Event<Boolean>> failures() {
        return failures;
    }

    /**
     * Takes a session out of the history. The screen is expected to have asked first.
     *
     * <p>No success event: the list is observed, so a removal that worked makes the row leave on
     * its own. Announcing it as well would be the app telling the user what they just watched
     * happen.
     */
    public void delete(String sessionId) {
        history.delete(sessionId, removed -> {
            if (!Boolean.TRUE.equals(removed)) {
                failures.setValue(new Event<>(true));
            }
        }, error -> failures.setValue(new Event<>(true)));
    }
}

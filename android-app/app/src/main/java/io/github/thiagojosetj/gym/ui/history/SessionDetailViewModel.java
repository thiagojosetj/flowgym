package io.github.thiagojosetj.gym.ui.history;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import io.github.thiagojosetj.gym.data.repository.HistoryRepository;
import io.github.thiagojosetj.gym.domain.session.SessionDetail;

/** State of the finished-session detail (PRODUCT_SPEC HIS-01, HIS-03, HIS-04), loaded once. */
public final class SessionDetailViewModel extends ViewModel {

    /**
     * Loading, loaded or failed, so the screen can tell "not loaded yet" from "could not open".
     *
     * @param detail set only when the load succeeded
     */
    public record State(boolean loading, SessionDetail detail, boolean failed) {
        static State loadingState() {
            return new State(true, null, false);
        }
    }

    private final MutableLiveData<State> state = new MutableLiveData<>(State.loadingState());

    public SessionDetailViewModel(HistoryRepository history, String sessionId) {
        // Loaded once per ViewModel, and never observed: a finished session cannot change, so a
        // rotation does not hit the database again and there is no second emission to wait for.
        history.loadDetail(sessionId,
                detail -> state.setValue(new State(false, detail, false)),
                error -> state.setValue(new State(false, null, true)));
    }

    public LiveData<State> state() {
        return state;
    }
}

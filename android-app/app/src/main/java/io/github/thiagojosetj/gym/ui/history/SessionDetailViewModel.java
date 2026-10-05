package io.github.thiagojosetj.gym.ui.history;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import io.github.thiagojosetj.gym.data.repository.HistoryRepository;
import io.github.thiagojosetj.gym.domain.session.SessionDetail;

/** State of the finished-session detail (PRODUCT_SPEC HIS-01, HIS-03, HIS-04), loaded once. */
public final class SessionDetailViewModel extends ViewModel {

    /**
     * Loading, loaded or failed, so the screen can tell "not loaded yet" from "could not open".
     *
     * @param detail            set only when the load succeeded
     * @param expandedExercises ids of the comparison rows showing their sets (HIS-04). Part of the
     *                          state rather than a field of the screen, so a rotation does not
     *                          collapse what the person had just opened
     */
    public record State(boolean loading, SessionDetail detail, boolean failed,
                        Set<String> expandedExercises) {

        public State {
            expandedExercises = expandedExercises == null
                    ? Collections.emptySet()
                    : Collections.unmodifiableSet(new LinkedHashSet<>(expandedExercises));
        }

        static State loadingState() {
            return new State(true, null, false, Collections.emptySet());
        }

        public boolean isExpanded(String exerciseId) {
            return expandedExercises.contains(exerciseId);
        }
    }

    private final MutableLiveData<State> state = new MutableLiveData<>(State.loadingState());

    public SessionDetailViewModel(HistoryRepository history, String sessionId) {
        // Loaded once per ViewModel, and never observed: a finished session cannot change, so a
        // rotation does not hit the database again and there is no second emission to wait for.
        history.loadDetail(sessionId,
                detail -> state.setValue(new State(false, detail, false, Collections.emptySet())),
                error -> state.setValue(new State(false, null, true, Collections.emptySet())));
    }

    public LiveData<State> state() {
        return state;
    }

    /**
     * Opens or closes one exercise's set-by-set comparison (PRODUCT_SPEC HIS-04).
     *
     * <p>Rewrites the state rather than keeping a second source: the detail itself cannot change,
     * so this is the same screen with one more thing shown, and it arrives in one emission.
     */
    public void toggleSets(String sessionExerciseId) {
        State current = state.getValue();
        if (current == null || current.detail() == null) {
            return;
        }
        Set<String> expanded = new LinkedHashSet<>(current.expandedExercises());
        if (!expanded.remove(sessionExerciseId)) {
            expanded.add(sessionExerciseId);
        }
        state.setValue(new State(false, current.detail(), false, expanded));
    }
}

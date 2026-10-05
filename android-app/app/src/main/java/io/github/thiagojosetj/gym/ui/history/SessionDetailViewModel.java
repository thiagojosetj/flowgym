package io.github.thiagojosetj.gym.ui.history;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import io.github.thiagojosetj.gym.core.Event;
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
    private final MutableLiveData<Event<Boolean>> ratingFailures = new MutableLiveData<>();
    private final HistoryRepository history;
    private final String sessionId;

    public SessionDetailViewModel(HistoryRepository history, String sessionId) {
        this.history = history;
        this.sessionId = sessionId;
        // Loaded once per ViewModel, and never observed: a finished session cannot change, so a
        // rotation does not hit the database again and there is no second emission to wait for.
        history.loadDetail(sessionId,
                detail -> state.setValue(new State(false, detail, false, Collections.emptySet())),
                error -> state.setValue(new State(false, null, true, Collections.emptySet())));
    }

    public LiveData<State> state() {
        return state;
    }

    /** Raised only when a rating could not be written; the screen then shows what IS stored. */
    public LiveData<Event<Boolean>> ratingFailures() {
        return ratingFailures;
    }

    /**
     * Records how the workout felt, or clears it when given null (PRODUCT_SPEC HIS-06).
     *
     * <p>Written on each tap, like the dialog at the end of a workout: there is no save button to
     * press and nothing waiting in memory to be lost (ADR-0031). The screen shows the new value at
     * once, and puts the old one back if the write fails - a star left on screen that is not in
     * the database is the app telling the person something that is not true.
     */
    public void rate(@Nullable Integer rating) {
        State current = state.getValue();
        if (current == null || current.detail() == null) {
            return;
        }
        Integer previous = current.detail().rating();
        show(current, rating);
        history.rate(sessionId, rating, saved -> {
            if (!Boolean.TRUE.equals(saved)) {
                failed(previous);
            }
        }, error -> failed(previous));
    }

    private void failed(Integer previous) {
        State current = state.getValue();
        if (current != null && current.detail() != null) {
            show(current, previous);
        }
        ratingFailures.setValue(new Event<>(true));
    }

    private void show(State current, Integer rating) {
        state.setValue(new State(false, current.detail().withRating(rating), false,
                current.expandedExercises()));
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

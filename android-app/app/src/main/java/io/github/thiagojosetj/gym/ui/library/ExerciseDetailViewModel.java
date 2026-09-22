package io.github.thiagojosetj.gym.ui.library;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import io.github.thiagojosetj.gym.data.repository.ExerciseRepository;
import io.github.thiagojosetj.gym.domain.library.ExerciseDetail;

public final class ExerciseDetailViewModel extends ViewModel {

    /** Loading, found or missing, so the screen can tell "not loaded yet" from "does not exist". */
    public record State(boolean loading, ExerciseDetail detail) {
        static State loadingState() {
            return new State(true, null);
        }
    }

    private final MutableLiveData<State> state = new MutableLiveData<>(State.loadingState());

    public ExerciseDetailViewModel(ExerciseRepository exercises, String exerciseId) {
        // Loaded once per ViewModel: a rotation does not hit the database again.
        exercises.loadDetail(exerciseId,
                detail -> state.setValue(new State(false, detail)),
                error -> state.setValue(new State(false, null)));
    }

    public LiveData<State> state() {
        return state;
    }
}

package io.github.thiagojosetj.gym.ui.progress;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import io.github.thiagojosetj.gym.data.repository.ProgressRepository;
import io.github.thiagojosetj.gym.domain.progress.ExerciseProgress;

/**
 * State of "Progresso" for one exercise (PRODUCT_SPEC PRG-03).
 *
 * <p><b>Read once.</b> The history is immutable (ADR-0036): a finished session never changes, and
 * a workout finished while this screen is open is not part of what it is showing. So this is a
 * single read, not an observed query - there is no second emission to wait for, no half-built
 * state to draw, and no chance of a slow result landing on top of a newer one. Reopening the
 * screen reads again.
 *
 * <p>The read lives here rather than in the fragment so a rotation does not repeat it.
 */
public final class ExerciseProgressViewModel extends ViewModel {

    /**
     * Everything the screen draws.
     *
     * <p>{@code failed} is its own state and not an empty progress: "you have never done this
     * exercise" and "the app could not read the database" are different facts, and showing the
     * first when the second happened would be the app inventing an answer.
     */
    public record State(boolean loading, @Nullable ExerciseProgress progress, boolean failed) {
    }

    private final MutableLiveData<State> state =
            new MutableLiveData<>(new State(true, null, false));

    public ExerciseProgressViewModel(ProgressRepository progress, String exerciseId) {
        progress.loadExercise(exerciseId,
                loaded -> state.setValue(new State(false, loaded, false)),
                error -> state.setValue(new State(false, null, true)));
    }

    public LiveData<State> state() {
        return state;
    }
}

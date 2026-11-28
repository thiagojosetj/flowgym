package io.github.thiagojosetj.gym.ui.progress;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.Collections;
import java.util.List;

import io.github.thiagojosetj.gym.data.repository.ProgressRepository;
import io.github.thiagojosetj.gym.domain.progress.ExerciseProgress;
import io.github.thiagojosetj.gym.domain.progress.ProgressWindow;

/**
 * State of "Progresso" for one exercise (PRODUCT_SPEC PRG-03, with the windows of PRG-02).
 *
 * <p><b>Read once.</b> The history is immutable (ADR-0036): a finished session never changes, and
 * a workout finished while this screen is open is not part of what it is showing. So this is a
 * single read, not an observed query - there is no second emission to wait for, no half-built
 * state to draw, and no chance of a slow result landing on top of a newer one. Reopening the
 * screen reads again.
 *
 * <p>The read lives here rather than in the fragment so a rotation does not repeat it, and
 * changing the window is free: it slices a list already in memory and touches no database.
 */
public final class ExerciseProgressViewModel extends ViewModel {

    /**
     * Everything the screen draws.
     *
     * <p>{@code progress} is the WHOLE history and {@code shown} is the part the window leaves.
     * Both travel together on purpose: the records are read from all of it and the list from the
     * slice, so a state that carried only one of them would force the screen to choose which of
     * the two figures to trust.
     *
     * <p>{@code failed} is its own state and not an empty progress: "you have never done this
     * exercise" and "the app could not read the database" are different facts, and showing the
     * first when the second happened would be the app inventing an answer.
     */
    public record State(boolean loading, @Nullable ExerciseProgress progress, boolean failed,
                        ProgressWindow window, ChartMeasure measure,
                        List<ExerciseProgress.Point> shown) {
    }

    private final MutableLiveData<State> state = new MutableLiveData<>(
            new State(true, null, false, ProgressWindow.ALL, ChartMeasure.LOAD,
                    Collections.emptyList()));

    @Nullable
    private ExerciseProgress loaded;
    private ProgressWindow window = ProgressWindow.ALL;
    private ChartMeasure measure = ChartMeasure.LOAD;

    public ExerciseProgressViewModel(ProgressRepository progress, String exerciseId) {
        progress.loadExercise(exerciseId, result -> {
            loaded = result;
            publish(false);
        }, error -> publish(true));
    }

    public LiveData<State> state() {
        return state;
    }

    /**
     * Narrows the list to the last few sessions, or widens it back (PRG-02).
     *
     * <p>Opens on {@link ProgressWindow#ALL}: a screen that started already hiding sessions would
     * be answering a question nobody asked, and the count of what is left out is only honest once
     * somebody has chosen to leave it out.
     */
    public void show(ProgressWindow next) {
        if (next == null || next == window) {
            return;
        }
        window = next;
        publish(state.getValue() != null && state.getValue().failed());
    }

    /** Which measure the chart draws (PRG-01). Kept here so a rotation does not reset it. */
    public void show(ChartMeasure next) {
        if (next == null || next == measure) {
            return;
        }
        measure = next;
        publish(state.getValue() != null && state.getValue().failed());
    }

    private void publish(boolean failed) {
        List<ExerciseProgress.Point> shown = loaded == null
                ? Collections.emptyList()
                : window.applyTo(loaded.sessions());
        state.setValue(new State(false, loaded, failed, window, measure, shown));
    }
}

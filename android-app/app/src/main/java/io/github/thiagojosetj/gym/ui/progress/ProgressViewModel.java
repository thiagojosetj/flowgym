package io.github.thiagojosetj.gym.ui.progress;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

import io.github.thiagojosetj.gym.data.repository.ProgressRepository;
import io.github.thiagojosetj.gym.domain.progress.ProgressSnapshot;
import io.github.thiagojosetj.gym.domain.progress.TrainingPeriod;
import io.github.thiagojosetj.gym.ui.common.Today;

/**
 * State of "Progresso" (PRODUCT_SPEC PRG-04): one week or one month at a time.
 *
 * <p>Everything on screen comes from one read. The arrows' limits travel with the numbers in the
 * same {@link ProgressSnapshot}, so an arrow can never be offering a step into a month the figures
 * beside it have never heard of.
 */
public final class ProgressViewModel extends ViewModel {

    /** Everything the screen draws, in one value. */
    public record State(TrainingPeriod period, ProgressSnapshot snapshot, boolean canGoBack,
                        boolean canGoForward) {
    }

    private final Clock clock;
    private final ZoneId zone;
    private final MutableLiveData<TrainingPeriod> period = new MutableLiveData<>();
    private final LiveData<State> state;
    private DayOfWeek firstDayOfWeek;

    public ProgressViewModel(ProgressRepository progress, Clock clock, ZoneId zone) {
        this.clock = clock;
        this.zone = zone;
        this.firstDayOfWeek = DayOfWeek.SUNDAY;
        period.setValue(TrainingPeriod.weekOf(today(), firstDayOfWeek));
        this.state = Transformations.switchMap(period, shown ->
                Transformations.map(progress.observePeriod(shown),
                        snapshot -> toState(shown, snapshot)));
    }

    public LiveData<State> state() {
        return state;
    }

    /**
     * Which day a week starts on, taken from the locale by the screen.
     *
     * <p>Passed in rather than read here: a ViewModel outlives the configuration, so a locale
     * changed under it would keep cutting the weeks on the old day.
     */
    public void setFirstDayOfWeek(DayOfWeek first) {
        if (first == null || first == firstDayOfWeek) {
            return;
        }
        firstDayOfWeek = first;
        TrainingPeriod shown = period.getValue();
        if (shown != null && shown.kind() == TrainingPeriod.Kind.WEEK) {
            TrainingPeriod recut = TrainingPeriod.weekOf(shown.from(), first);
            if (!recut.equals(shown)) {
                period.setValue(recut);
            }
        }
    }

    /** Switches between a week and a month, staying on what is on screen. */
    public void show(TrainingPeriod.Kind kind) {
        TrainingPeriod shown = period.getValue();
        if (shown == null) {
            return;
        }
        TrainingPeriod next = shown.as(kind, firstDayOfWeek, today());
        if (!Objects.equals(next, shown)) {
            period.setValue(next);
        }
    }

    /**
     * Moves by whole periods.
     *
     * <p>Not bounded here. Where the training ends is decided once, in the state the screen is
     * drawn from, and the arrow that would lead past it is disabled there - the same single
     * mechanism the history calendar uses.
     */
    public void step(int periods) {
        TrainingPeriod shown = period.getValue();
        if (shown != null) {
            period.setValue(shown.shifted(periods));
        }
    }

    private State toState(TrainingPeriod shown, ProgressSnapshot snapshot) {
        // Bounded by the data and not by today: a phone whose clock ran ahead wrote a day in the
        // future, and stopping at this week would hide that session rather than show it.
        boolean back = snapshot.firstTrainedDay() != null
                && !shown.startsOnOrBefore(snapshot.firstTrainedDay());
        boolean forward = snapshot.lastTrainedDay() != null
                && !shown.endsOnOrAfter(snapshot.lastTrainedDay());
        return new State(shown, snapshot, back, forward);
    }

    /** Where the person is, not where the clock was wired. See {@link Today}. */
    private LocalDate today() {
        return Today.of(clock, zone);
    }
}

package io.github.thiagojosetj.gym.ui.history;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import io.github.thiagojosetj.gym.core.Event;
import io.github.thiagojosetj.gym.data.repository.HistoryRepository;
import io.github.thiagojosetj.gym.domain.session.SessionHistoryEntry;
import io.github.thiagojosetj.gym.domain.session.TrainingCalendar;

/**
 * State of "Histórico": the calendar of trained days (PRODUCT_SPEC HIS-02) above every finished
 * session, newest first (HIS-03).
 *
 * <p>Both come from the <b>same</b> emission. {@link HistoryRepository#observeTrainedDates()} is a
 * map of the very LiveData the list is made of, not a query of its own, so the grid can never claim
 * a day the rows below it do not have. This is structural, not something a test here could prove:
 * the tests run on synchronised executors, where two queries would look consistent too.
 */
public final class HistoryListViewModel extends ViewModel {

    /**
     * Everything the month header draws, in one value.
     *
     * @param selectedDay        the day filtering the list, or null when the whole history shows
     * @param selectedDayIsEmpty true when a day with no session was tapped - said out loud rather
     *                           than shown as an empty screen, which reads like a bug
     */
    public record CalendarState(TrainingCalendar calendar, @Nullable LocalDate selectedDay,
                                boolean canGoBack, boolean canGoForward,
                                boolean selectedDayIsEmpty) {
    }

    private final HistoryRepository history;
    /** The current month is a question about now, so the clock is injected rather than read. */
    private final Clock clock;
    private final LiveData<List<SessionHistoryEntry>> all;
    private final LiveData<Set<LocalDate>> trainedDates;

    private final MutableLiveData<Event<Boolean>> failures = new MutableLiveData<>();
    private final MutableLiveData<YearMonth> month = new MutableLiveData<>();
    private final MutableLiveData<LocalDate> selectedDay = new MutableLiveData<>();
    private final MutableLiveData<DayOfWeek> firstDayOfWeek = new MutableLiveData<>();
    private final MediatorLiveData<List<SessionHistoryEntry>> visible = new MediatorLiveData<>();
    private final MediatorLiveData<CalendarState> calendar = new MediatorLiveData<>();

    public HistoryListViewModel(HistoryRepository history, Clock clock) {
        this.history = history;
        this.clock = clock;
        this.all = history.observeHistory();
        this.trainedDates = history.observeTrainedDates();

        month.setValue(YearMonth.now(clock));

        visible.addSource(all, sessions -> refilter());
        visible.addSource(selectedDay, day -> refilter());

        calendar.addSource(trainedDates, dates -> rebuild());
        calendar.addSource(month, shown -> rebuild());
        calendar.addSource(selectedDay, day -> rebuild());
        calendar.addSource(firstDayOfWeek, first -> rebuild());
    }

    /** The sessions on screen: all of them, or only the selected day's. */
    public LiveData<List<SessionHistoryEntry>> sessions() {
        return visible;
    }

    /**
     * The month header, or null when nothing has ever been finished.
     *
     * <p>Null is the "no history at all" answer, and it is the only one: a day filter that matches
     * nothing still has a calendar, because the person is looking at a month they did train in.
     */
    public LiveData<CalendarState> calendar() {
        return calendar;
    }

    /** Raised only when a removal failed; the list removes the row by itself when it works. */
    public LiveData<Event<Boolean>> failures() {
        return failures;
    }

    /**
     * Which column the week starts in, taken from the locale by the screen.
     *
     * <p>Passed in rather than read here: a ViewModel outlives the configuration, so a locale
     * changed under it would leave the grid starting on the old day until the process died.
     */
    public void setFirstDayOfWeek(DayOfWeek first) {
        if (first != null && first != firstDayOfWeek.getValue()) {
            firstDayOfWeek.setValue(first);
        }
    }

    /**
     * Filters the list to one day, or clears the filter when that day is already the one showing.
     *
     * <p>The same tap that narrows widens again, so a filter cannot get stuck on with no way back
     * other than finding the button for it.
     */
    public void selectDay(LocalDate date) {
        selectedDay.setValue(Objects.equals(date, selectedDay.getValue()) ? null : date);
    }

    public void showAllDays() {
        clearSelection();
    }

    /**
     * Moves the grid by whole months.
     *
     * <p>Not bounded here. Where the months end is decided once, in the state the header is drawn
     * from, and the arrow that would lead into an empty month is disabled there. A second guard in
     * this method would be a rule nothing on screen reflects and no test could reach - it was
     * written first, and taking it out changed no test, which is the whole argument against it.
     *
     * <p>The selection goes with the move: a filter naming a day of a month no longer on screen
     * would leave the rows contradicting the grid above them.
     */
    public void stepMonth(int months) {
        YearMonth shown = month.getValue();
        if (shown == null) {
            return;
        }
        clearSelection();
        month.setValue(shown.plusMonths(months));
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

    private void clearSelection() {
        if (selectedDay.getValue() != null) {
            selectedDay.setValue(null);
        }
    }

    private void refilter() {
        List<SessionHistoryEntry> sessions = all.getValue();
        if (sessions == null) {
            return;
        }
        LocalDate day = selectedDay.getValue();
        if (day == null) {
            visible.setValue(sessions);
            return;
        }
        // Compared as the stored text. local_date is written as an ISO day and the calendar only
        // ever offers days that parsed out of that same column, so there is nothing to normalise.
        String iso = day.toString();
        List<SessionHistoryEntry> ofDay = new ArrayList<>();
        for (SessionHistoryEntry entry : sessions) {
            if (iso.equals(entry.localDate())) {
                ofDay.add(entry);
            }
        }
        visible.setValue(Collections.unmodifiableList(ofDay));
    }

    private void rebuild() {
        YearMonth shown = month.getValue();
        DayOfWeek first = firstDayOfWeek.getValue();
        Set<LocalDate> dates = trainedDates.getValue();
        if (shown == null || first == null || dates == null) {
            // Still waiting on the screen's locale or on the first read; drawing half of a month
            // is worse than drawing none.
            return;
        }
        if (dates.isEmpty()) {
            calendar.setValue(null);
            return;
        }
        LocalDate selected = selectedDay.getValue();
        calendar.setValue(new CalendarState(
                TrainingCalendar.of(shown, first, dates),
                selected,
                shown.isAfter(earliest(dates)),
                shown.isBefore(latest(dates)),
                selected != null && !dates.contains(selected)));
    }

    /** The first month worth showing: the oldest session's, or this one while it is older still. */
    private YearMonth earliest(Set<LocalDate> dates) {
        YearMonth earliest = YearMonth.now(clock);
        for (LocalDate date : dates) {
            YearMonth of = YearMonth.from(date);
            if (of.isBefore(earliest)) {
                earliest = of;
            }
        }
        return earliest;
    }

    /**
     * The last month worth showing.
     *
     * <p>Bounded by the newest session rather than by today, because a device whose clock ran ahead
     * writes a day in the future: stopping at the current month would hide that session instead of
     * showing it where it was recorded.
     */
    private YearMonth latest(Set<LocalDate> dates) {
        YearMonth latest = YearMonth.now(clock);
        for (LocalDate date : dates) {
            YearMonth of = YearMonth.from(date);
            if (of.isAfter(latest)) {
                latest = of;
            }
        }
        return latest;
    }
}

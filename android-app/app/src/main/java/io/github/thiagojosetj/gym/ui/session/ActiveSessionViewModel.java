package io.github.thiagojosetj.gym.ui.session;

import android.content.res.Resources;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.time.Clock;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.thiagojosetj.gym.core.Event;
import io.github.thiagojosetj.gym.data.repository.ActiveSessionRepository;
import io.github.thiagojosetj.gym.data.repository.TechniqueRepository;
import io.github.thiagojosetj.gym.domain.model.Weight;
import io.github.thiagojosetj.gym.domain.model.WeightUnit;
import io.github.thiagojosetj.gym.domain.session.ActiveSession;
import io.github.thiagojosetj.gym.domain.session.FinishReview;
import io.github.thiagojosetj.gym.domain.session.LoggedSet;
import io.github.thiagojosetj.gym.domain.session.SessionExercise;
import io.github.thiagojosetj.gym.domain.session.SessionSummary;
import io.github.thiagojosetj.gym.domain.session.SetValues;
import io.github.thiagojosetj.gym.domain.technique.TechniqueCatalog;
import io.github.thiagojosetj.gym.ui.common.NumberInput;

/**
 * State of the active-workout screen. It owns almost nothing: the session comes from the database and
 * every action goes straight back to it (ADR-0031). The only things kept here are what cannot be
 * persisted yet - the text being typed and which cards are collapsed.
 */
public final class ActiveSessionViewModel extends ViewModel {

    /** One-shot outcomes for the screen. */
    public enum SessionEvent { FINISHED, DISCARDED, ACTION_FAILED, FINISH_FAILED }

    /** Units are kg for now; lb support is planned (docs/ROADMAP.md). */
    private static final WeightUnit UNIT = WeightUnit.KILOGRAM;

    private final ActiveSessionRepository sessions;
    private final Clock clock;
    private final String sessionId;
    private final SessionRowBuilder rowBuilder;

    private final LiveData<ActiveSession> session;
    private final TickerLiveData ticker;
    private final MutableLiveData<Event<SessionEvent>> events = new MutableLiveData<>();
    private final MediatorLiveData<List<SessionRow>> rows = new MediatorLiveData<>();
    private final MutableLiveData<Integer> localChanges = new MutableLiveData<>(0);
    /** Loaded once; null while loading and again if the read failed, so the UI can say so. */
    private final MutableLiveData<TechniqueCatalog> techniques = new MutableLiveData<>();

    private final Set<String> collapsed = new HashSet<>();
    private final Map<String, SetDraft> drafts = new HashMap<>();

    @Nullable
    private SessionSummary summary;

    public ActiveSessionViewModel(ActiveSessionRepository sessions, TechniqueRepository techniqueRepository,
                                  Clock clock, Resources resources, String sessionId) {
        this.sessions = sessions;
        this.clock = clock;
        this.sessionId = sessionId;
        this.rowBuilder = new SessionRowBuilder(resources, UNIT);
        this.session = sessions.observeSession(sessionId);
        this.ticker = new TickerLiveData(clock);
        rows.addSource(session, ignored -> rebuildRows());
        rows.addSource(localChanges, ignored -> rebuildRows());
        techniqueRepository.loadCatalog(techniques::setValue, error -> techniques.setValue(null));
    }

    public String sessionId() {
        return sessionId;
    }

    public LiveData<ActiveSession> session() {
        return session;
    }

    /** Emits every second while the screen is visible; only the time views observe it. */
    public LiveData<Long> ticker() {
        return ticker;
    }

    /** Techniques available for a set; null means the catalog could not be read. */
    public LiveData<TechniqueCatalog> techniques() {
        return techniques;
    }

    public LiveData<List<SessionRow>> rows() {
        return rows;
    }

    public LiveData<Event<SessionEvent>> events() {
        return events;
    }

    @Nullable
    public SessionSummary summary() {
        return summary;
    }

    public long now() {
        return clock.millis();
    }

    // ------------------------------------------------------------------ list state

    public void toggleCollapsed(String sessionExerciseId) {
        if (!collapsed.remove(sessionExerciseId)) {
            collapsed.add(sessionExerciseId);
        }
        bumpLocalChanges();
    }

    /** Stores what is being typed. Deliberately does not touch the database or re-emit the list. */
    public void onWeightTyped(String setId, String text) {
        drafts.put(setId, draftOf(setId).withWeight(text));
    }

    public void onRepsTyped(String setId, String text) {
        drafts.put(setId, draftOf(setId).withReps(text));
    }

    /** Called when a field loses focus and when the screen stops: keeps typing across a restart. */
    public void flushDraft(String setId) {
        SetDraft draft = drafts.get(setId);
        if (draft == null || draft.isEmpty()) {
            return;
        }
        ActiveSession current = session.getValue();
        if (current == null) {
            return;
        }
        SessionExercise exercise = current.exerciseOfSet(setId);
        LoggedSet set = current.setById(setId);
        if (exercise == null || set == null || set.isCompleted()) {
            return;
        }
        SetValues parsed = parse(exercise, set, draft);
        if (parsed != null) {
            sessions.saveTypedValues(setId, parsed);
        }
    }

    public void flushAllDrafts() {
        for (String setId : new HashSet<>(drafts.keySet())) {
            flushDraft(setId);
        }
    }

    // ------------------------------------------------------------------ actions

    /**
     * Confirms a set with what is on screen. An empty field adopts the suggestion, because that is
     * what the user is looking at - but only here, when they explicitly say the set is done.
     */
    public void confirmSet(String setId) {
        ActiveSession current = session.getValue();
        if (current == null) {
            return;
        }
        SessionExercise exercise = current.exerciseOfSet(setId);
        LoggedSet set = current.setById(setId);
        if (exercise == null || set == null) {
            return;
        }
        SetValues typed = parse(exercise, set, drafts.get(setId));
        if (typed == null) {
            events.setValue(new Event<>(SessionEvent.ACTION_FAILED));
            return;
        }
        SetValues values = withSuggestion(typed, set.suggestion(), exercise);
        drafts.remove(setId);
        sessions.confirmSet(sessionId, setId, values, this::noop, this::onActionFailed);
    }

    public void undoSet(String setId) {
        sessions.unconfirmSet(sessionId, setId, this::noop, this::onActionFailed);
    }

    public void addSet(String sessionExerciseId) {
        sessions.addSet(sessionId, sessionExerciseId, this::noop, this::onActionFailed);
    }

    public void removeSet(String sessionExerciseId, String setId) {
        drafts.remove(setId);
        sessions.removeSet(sessionId, sessionExerciseId, setId, this::noop, this::onActionFailed);
    }

    public void setTechnique(String setId, @Nullable String techniqueId) {
        sessions.setSetTechnique(sessionId, setId, techniqueId, this::noop, this::onActionFailed);
    }

    public void pause() {
        flushAllDrafts();
        sessions.pause(sessionId, this::noop, this::onActionFailed);
    }

    public void resume() {
        sessions.resume(sessionId, this::noop, this::onActionFailed);
    }

    public void adjustRest(int deltaSeconds) {
        sessions.adjustRest(sessionId, deltaSeconds, this::noop, this::onActionFailed);
    }

    public void skipRest() {
        sessions.skipRest(sessionId, this::noop, this::onActionFailed);
    }

    /** What finishing would do, so the screen can show it before anything happens (section 8). */
    @Nullable
    public FinishReview finishReview() {
        ActiveSession current = session.getValue();
        return current == null ? null : current.finishReview();
    }

    public void finish() {
        flushAllDrafts();
        sessions.finish(sessionId, result -> {
            summary = result;
            events.setValue(new Event<>(SessionEvent.FINISHED));
        }, error -> events.setValue(new Event<>(SessionEvent.FINISH_FAILED)));
    }

    public void discard() {
        sessions.discard(sessionId, () -> events.setValue(new Event<>(SessionEvent.DISCARDED)),
                error -> events.setValue(new Event<>(SessionEvent.FINISH_FAILED)));
    }

    // ------------------------------------------------------------------ internals

    private void rebuildRows() {
        ActiveSession current = session.getValue();
        rows.setValue(current == null
                ? Collections.emptyList()
                : rowBuilder.build(current, collapsed, drafts));
    }

    private void bumpLocalChanges() {
        Integer value = localChanges.getValue();
        localChanges.setValue(value == null ? 1 : value + 1);
    }

    private SetDraft draftOf(String setId) {
        SetDraft draft = drafts.get(setId);
        return draft == null ? SetDraft.EMPTY : draft;
    }

    /** Parses the typed text; null means the text is not a number the app can accept. */
    @Nullable
    private SetValues parse(SessionExercise exercise, LoggedSet set, @Nullable SetDraft draft) {
        boolean timed = exercise.trackingType().usesDuration() && !exercise.trackingType().usesReps();
        Weight weight = set.values().weight();
        Integer reps = set.values().reps();
        Integer duration = set.values().durationSeconds();
        if (draft != null) {
            try {
                if (draft.weightText() != null) {
                    Double value = NumberInput.parseDecimal(draft.weightText());
                    weight = value == null || value == 0 ? null : Weight.of(value, UNIT);
                }
                if (draft.repsText() != null) {
                    Integer value = NumberInput.parseWholeNumber(draft.repsText());
                    if (timed) {
                        duration = value;
                    } else {
                        reps = value;
                    }
                }
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return new SetValues(weight, timed ? null : reps, set.values().repsLeft(),
                set.values().repsRight(), timed ? duration : set.values().durationSeconds());
    }

    /** Empty fields adopt the suggestion the user was looking at when they confirmed the set. */
    private static SetValues withSuggestion(SetValues typed, SetValues suggestion, SessionExercise exercise) {
        boolean timed = exercise.trackingType().usesDuration() && !exercise.trackingType().usesReps();
        Weight weight = typed.weight() != null ? typed.weight() : suggestion.weight();
        Integer reps = typed.reps() != null ? typed.reps() : suggestion.reps();
        Integer duration = typed.durationSeconds() != null
                ? typed.durationSeconds()
                : suggestion.durationSeconds();
        return new SetValues(
                exercise.trackingType().usesWeight() ? weight : null,
                timed ? null : reps,
                typed.repsLeft(),
                typed.repsRight(),
                timed ? duration : null);
    }

    private void onActionFailed(Throwable error) {
        events.setValue(new Event<>(SessionEvent.ACTION_FAILED));
    }

    private void noop() {
        // The screen re-renders from the database; there is nothing to do on success.
    }
}

package io.github.thiagojosetj.gym.ui.templates;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import java.util.List;

import io.github.thiagojosetj.gym.core.Event;
import io.github.thiagojosetj.gym.data.repository.ActiveSessionRepository;
import io.github.thiagojosetj.gym.data.repository.TemplateRepository;
import io.github.thiagojosetj.gym.domain.template.TemplateSummary;

public final class TemplateListViewModel extends ViewModel {

    /** One-shot feedback shown as a snackbar. */
    public enum Message { DUPLICATED, DELETED, FAILED, ALREADY_TRAINING, START_FAILED }

    /** Which of the two lists the screen is showing. Archived templates are never in both. */
    public enum Mode { ACTIVE, ARCHIVED }

    /**
     * A template was put away or brought back.
     *
     * <p>Carries the id because the screen offers to undo it: the card leaves the list it was in,
     * and a snackbar that only says "arquivado" leaves the user looking for it. {@code undoable} is
     * false for the undo itself - a snackbar offering to undo the undo is a loop, not a safety net.
     */
    public record ArchiveChange(String templateId, boolean archived, boolean undoable) { }

    private final TemplateRepository templates;
    private final ActiveSessionRepository sessions;
    private final MutableLiveData<Mode> mode = new MutableLiveData<>(Mode.ACTIVE);
    private final LiveData<List<TemplateSummary>> list;
    private final LiveData<Integer> archivedCount;
    private final MutableLiveData<Event<Message>> messages = new MutableLiveData<>();
    private final MutableLiveData<Event<ArchiveChange>> archiveChanges = new MutableLiveData<>();
    private final MutableLiveData<Event<String>> startedSession = new MutableLiveData<>();

    public TemplateListViewModel(TemplateRepository templates, ActiveSessionRepository sessions) {
        this.templates = templates;
        this.sessions = sessions;
        // Both queries stay live, but only the one for the current mode is observed downstream, so
        // Room runs one of them at a time.
        LiveData<List<TemplateSummary>> active = templates.observeTemplates();
        LiveData<List<TemplateSummary>> archived = templates.observeArchived();
        this.list = Transformations.switchMap(mode, m -> m == Mode.ARCHIVED ? archived : active);
        this.archivedCount = templates.observeArchivedCount();
    }

    public LiveData<List<TemplateSummary>> templates() {
        return list;
    }

    public LiveData<Mode> mode() {
        return mode;
    }

    /** How many templates are put away, for the screen to offer the other list. */
    public LiveData<Integer> archivedCount() {
        return archivedCount;
    }

    public void showArchived(boolean archived) {
        Mode next = archived ? Mode.ARCHIVED : Mode.ACTIVE;
        if (next != mode.getValue()) {
            mode.setValue(next);
        }
    }

    /** True while the archived list is on screen. */
    public boolean showingArchived() {
        return mode.getValue() == Mode.ARCHIVED;
    }

    public LiveData<Event<Message>> messages() {
        return messages;
    }

    public LiveData<Event<ArchiveChange>> archiveChanges() {
        return archiveChanges;
    }

    /** The id of the session just started, for the screen to navigate to. */
    public LiveData<Event<String>> startedSession() {
        return startedSession;
    }

    /**
     * Starts a workout from this template. If one is already running the user is sent to it instead
     * of being told off: they almost certainly want to continue it (ACT-08).
     */
    public void startSession(String templateId) {
        sessions.startFromTemplate(templateId,
                sessionId -> startedSession.setValue(new Event<>(sessionId)),
                error -> {
                    if (error instanceof ActiveSessionRepository.ActiveSessionExistsException existing) {
                        messages.setValue(new Event<>(Message.ALREADY_TRAINING));
                        startedSession.setValue(new Event<>(existing.sessionId()));
                    } else {
                        messages.setValue(new Event<>(Message.START_FAILED));
                    }
                });
    }

    /** @param copySuffix localized suffix, e.g. " (cópia)"; the domain fits it to the name limit */
    public void duplicate(String templateId, String copySuffix) {
        templates.duplicate(templateId, copySuffix,
                newId -> messages.setValue(new Event<>(Message.DUPLICATED)),
                error -> messages.setValue(new Event<>(Message.FAILED)));
    }

    /**
     * Puts a template away, or brings it back. The template keeps everything it had and the
     * sessions trained from it are untouched; only which of the two lists shows it changes.
     *
     * @param undoable whether the screen should offer to undo this - false when this <em>is</em>
     *                 the undo
     */
    public void setArchived(String templateId, boolean archived, boolean undoable) {
        templates.setArchived(templateId, archived,
                () -> archiveChanges.setValue(
                        new Event<>(new ArchiveChange(templateId, archived, undoable))),
                error -> messages.setValue(new Event<>(Message.FAILED)));
    }

    public void delete(String templateId) {
        templates.delete(templateId,
                () -> messages.setValue(new Event<>(Message.DELETED)),
                error -> messages.setValue(new Event<>(Message.FAILED)));
    }
}

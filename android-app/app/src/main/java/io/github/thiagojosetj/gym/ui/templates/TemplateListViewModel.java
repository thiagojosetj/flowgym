package io.github.thiagojosetj.gym.ui.templates;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;

import io.github.thiagojosetj.gym.core.Event;
import io.github.thiagojosetj.gym.data.repository.ActiveSessionRepository;
import io.github.thiagojosetj.gym.data.repository.TemplateRepository;
import io.github.thiagojosetj.gym.domain.template.TemplateSummary;

public final class TemplateListViewModel extends ViewModel {

    /** One-shot feedback shown as a snackbar. */
    public enum Message { DUPLICATED, DELETED, FAILED, ALREADY_TRAINING, START_FAILED }

    private final TemplateRepository templates;
    private final ActiveSessionRepository sessions;
    private final LiveData<List<TemplateSummary>> list;
    private final MutableLiveData<Event<Message>> messages = new MutableLiveData<>();
    private final MutableLiveData<Event<String>> startedSession = new MutableLiveData<>();

    public TemplateListViewModel(TemplateRepository templates, ActiveSessionRepository sessions) {
        this.templates = templates;
        this.sessions = sessions;
        this.list = templates.observeTemplates();
    }

    public LiveData<List<TemplateSummary>> templates() {
        return list;
    }

    public LiveData<Event<Message>> messages() {
        return messages;
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

    public void delete(String templateId) {
        templates.delete(templateId,
                () -> messages.setValue(new Event<>(Message.DELETED)),
                error -> messages.setValue(new Event<>(Message.FAILED)));
    }
}

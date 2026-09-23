package io.github.thiagojosetj.gym.ui.templates;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;

import io.github.thiagojosetj.gym.core.Event;
import io.github.thiagojosetj.gym.data.repository.TemplateRepository;
import io.github.thiagojosetj.gym.domain.template.TemplateSummary;

public final class TemplateListViewModel extends ViewModel {

    /** One-shot feedback shown as a snackbar. */
    public enum Message { DUPLICATED, DELETED, FAILED }

    private final TemplateRepository templates;
    private final LiveData<List<TemplateSummary>> list;
    private final MutableLiveData<Event<Message>> messages = new MutableLiveData<>();

    public TemplateListViewModel(TemplateRepository templates) {
        this.templates = templates;
        this.list = templates.observeTemplates();
    }

    public LiveData<List<TemplateSummary>> templates() {
        return list;
    }

    public LiveData<Event<Message>> messages() {
        return messages;
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

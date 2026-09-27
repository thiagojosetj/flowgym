package io.github.thiagojosetj.gym.ui.home;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import io.github.thiagojosetj.gym.data.repository.ActiveSessionRepository;
import io.github.thiagojosetj.gym.data.repository.TemplateRepository;
import io.github.thiagojosetj.gym.domain.session.SessionHeader;

public final class HomeViewModel extends ViewModel {

    private final LiveData<Integer> templateCount;
    private final LiveData<SessionHeader> activeSession;

    public HomeViewModel(TemplateRepository templates, ActiveSessionRepository sessions) {
        this.templateCount = templates.observeTemplateCount();
        this.activeSession = sessions.observeActiveHeader();
    }

    public LiveData<Integer> templateCount() {
        return templateCount;
    }

    /**
     * The workout in progress, or null. The only source is the database: after the process was
     * killed there is no service and no flag left to ask (ACT-08).
     */
    public LiveData<SessionHeader> activeSession() {
        return activeSession;
    }
}

package io.github.thiagojosetj.gym.ui.home;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import io.github.thiagojosetj.gym.data.repository.TemplateRepository;

public final class HomeViewModel extends ViewModel {

    private final LiveData<Integer> templateCount;

    public HomeViewModel(TemplateRepository templates) {
        this.templateCount = templates.observeTemplateCount();
    }

    public LiveData<Integer> templateCount() {
        return templateCount;
    }
}

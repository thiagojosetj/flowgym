package io.github.thiagojosetj.gym.ui.settings;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import io.github.thiagojosetj.gym.data.repository.SettingsRepository;
import io.github.thiagojosetj.gym.domain.settings.AppSettings;

/** Account-level settings. Writes go straight to the repository; the screen re-renders from LiveData. */
public final class SettingsViewModel extends ViewModel {

    private final SettingsRepository repository;
    private final LiveData<AppSettings> settings;

    public SettingsViewModel(SettingsRepository repository) {
        this.repository = repository;
        this.settings = repository.observeSettings();
    }

    public LiveData<AppSettings> settings() {
        return settings;
    }

    public void setDefaultRestSeconds(int seconds) {
        repository.setDefaultRestSeconds(seconds);
    }

    public void setRestSoundEnabled(boolean enabled) {
        repository.setRestSoundEnabled(enabled);
    }

    public void setRestVibrationEnabled(boolean enabled) {
        repository.setRestVibrationEnabled(enabled);
    }
}

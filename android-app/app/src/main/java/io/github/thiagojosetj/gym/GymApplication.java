package io.github.thiagojosetj.gym;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;

/** Application entry point: owns the {@link AppContainer} for the whole process lifetime. */
public class GymApplication extends Application {

    private AppContainer container;

    @Override
    public void onCreate() {
        super.onCreate();
        container = createContainer();
        // Apply the saved theme before the first activity inflates (tiny prefs file, read once).
        AppCompatDelegate.setDefaultNightMode(container.uiPreferences.themeMode().nightMode());
        container.start();
    }

    /** Production wiring. UI tests override this to use an in-memory, synchronous container. */
    @NonNull
    protected AppContainer createContainer() {
        return AppContainer.create(this);
    }

    @NonNull
    public AppContainer container() {
        return container;
    }
}

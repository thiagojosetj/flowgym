package io.github.thiagojosetj.gym;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

/** Application entry point: owns the {@link AppContainer} for the whole process lifetime. */
public class GymApplication extends Application {

    private AppContainer container;

    @Override
    public void onCreate() {
        super.onCreate();
        container = AppContainer.create(this);
        container.start();
    }

    @NonNull
    public AppContainer container() {
        return container;
    }

    /** Lets tests swap in a container backed by an in-memory database. */
    @VisibleForTesting
    public void replaceContainer(@NonNull AppContainer testContainer) {
        container = testContainer;
    }
}

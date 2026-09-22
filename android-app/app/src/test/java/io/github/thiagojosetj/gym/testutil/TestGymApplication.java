package io.github.thiagojosetj.gym.testutil;

import androidx.annotation.NonNull;

import io.github.thiagojosetj.gym.AppContainer;
import io.github.thiagojosetj.gym.GymApplication;

/**
 * Application for UI tests (use with {@code @Config(application = TestGymApplication.class)}):
 * same app, but backed by a fresh in-memory database with synchronous executors.
 */
public class TestGymApplication extends GymApplication {

    @NonNull
    @Override
    protected AppContainer createContainer() {
        return TestContainers.create(this, TestContainers.inMemoryDatabase(this));
    }
}

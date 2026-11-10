package io.github.thiagojosetj.gym.testutil;

import androidx.annotation.NonNull;

import io.github.thiagojosetj.gym.AppContainer;
import io.github.thiagojosetj.gym.GymApplication;

/**
 * Application for UI tests (use with {@code @Config(application = TestGymApplication.class)}):
 * same app, but backed by a fresh in-memory database with synchronous executors.
 *
 * <p>Its clock can be moved. It starts exactly where {@link TestContainers#FIXED_CLOCK} does, so a
 * test that never touches it sees the same fixed instant as before. A test that needs two sessions
 * to be distinguishable in time - the comparison in PRODUCT_SPEC section 11 looks for a STRICTLY
 * older session of the same workout - advances it between them. Without that, two workouts
 * performed in the same test share an instant and neither can be the other's previous.
 *
 * <p>The clock belongs to the application instance, not to a static field: Robolectric builds a
 * new one per test, so nothing carries over into the next.
 */
public class TestGymApplication extends GymApplication {

    private final MutableClock clock = new MutableClock(TestContainers.FIXED_CLOCK.instant());

    @NonNull
    @Override
    protected AppContainer createContainer() {
        return TestContainers.create(this, TestContainers.inMemoryDatabase(this), clock);
    }

    /** The clock the whole app runs on in this test. */
    public MutableClock clock() {
        return clock;
    }
}

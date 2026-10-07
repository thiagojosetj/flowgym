package io.github.thiagojosetj.gym;

import android.content.Context;
import android.util.Log;

import java.time.Clock;
import java.time.ZoneId;

import io.github.thiagojosetj.gym.core.AppExecutors;
import io.github.thiagojosetj.gym.data.local.AppDatabase;
import io.github.thiagojosetj.gym.data.prefs.UiPreferences;
import io.github.thiagojosetj.gym.data.repository.ActiveSessionRepository;
import io.github.thiagojosetj.gym.data.repository.ExerciseRepository;
import io.github.thiagojosetj.gym.data.repository.HistoryRepository;
import io.github.thiagojosetj.gym.data.repository.ProgressRepository;
import io.github.thiagojosetj.gym.data.repository.SettingsRepository;
import io.github.thiagojosetj.gym.data.repository.TechniqueRepository;
import io.github.thiagojosetj.gym.data.repository.TemplateRepository;
import io.github.thiagojosetj.gym.data.repository.UserRepository;
import io.github.thiagojosetj.gym.data.seed.CatalogSeeder;
import io.github.thiagojosetj.gym.domain.util.IdGenerator;

/**
 * Composition root (manual dependency injection, ADR-0005).
 *
 * <p>This is the one place that decides which implementation of each dependency the app uses and
 * how long it lives. It is created once by {@link GymApplication}; screens receive what they need
 * through ViewModel factories and never build repositories or databases themselves.
 */
public final class AppContainer {

    private static final String TAG = "AppContainer";

    public final AppExecutors executors;
    public final Clock clock;
    /** Where the person is. Named beside the clock because a date needs both (see Today). */
    public final ZoneId zone;
    public final AppDatabase database;
    public final IdGenerator ids;
    public final UiPreferences uiPreferences;
    public final UserRepository users;
    public final ExerciseRepository exercises;
    public final TemplateRepository templates;
    public final TechniqueRepository techniques;
    public final SettingsRepository settings;
    public final ActiveSessionRepository activeSessions;
    public final HistoryRepository history;
    public final ProgressRepository progress;
    private final CatalogSeeder catalogSeeder;

    /** Production wiring. */
    public static AppContainer create(Context context) {
        Context app = context.getApplicationContext();
        return new AppContainer(
                AppDatabase.open(app),
                AppExecutors.create(),
                // The device's zone, not UTC. Every instant this clock produces is the same
                // either way - millis() does not know about zones - but "today" does: a screen
                // asking LocalDate.now(clock) under a UTC clock would roll over to tomorrow at
                // 21:00 in Sao Paulo, and the calendar would open on a month the person is not
                // in yet. The day always means the day where the person is (section 11).
                Clock.systemDefaultZone(),
                ZoneId.systemDefault(),
                IdGenerator.UUID_V7,
                () -> app.getAssets().open(CatalogSeeder.ASSET_PATH),
                new UiPreferences(app.getSharedPreferences(UiPreferences.FILE_NAME, Context.MODE_PRIVATE)));
    }

    /** Explicit wiring, also used by tests (in-memory database, direct executors, fixed clock). */
    public AppContainer(AppDatabase database, AppExecutors executors, Clock clock, ZoneId zone,
                        IdGenerator ids, CatalogSeeder.Source catalogSource, UiPreferences uiPreferences) {
        this.database = database;
        this.executors = executors;
        this.clock = clock;
        this.zone = zone;
        this.ids = ids;
        this.uiPreferences = uiPreferences;
        this.users = new UserRepository(database, clock, ids);
        this.exercises = new ExerciseRepository(database.exerciseDao(), database.catalogDao(), executors);
        this.templates = new TemplateRepository(database, users, executors, clock, ids);
        this.techniques = new TechniqueRepository(database.techniqueDao(), executors);
        this.settings = new SettingsRepository(database.userSettingDao(), users, executors, clock);
        // The zone is resolved per session (see the repository): the process can outlive a change.
        this.activeSessions = new ActiveSessionRepository(database, users, executors, clock,
                () -> zone, ids);
        this.history = new HistoryRepository(database, executors, clock);
        this.progress = new ProgressRepository(database, executors);
        this.catalogSeeder = new CatalogSeeder(database, catalogSource, clock);
    }

    /**
     * Start-up work, queued on the single disk thread so it completes before any later write.
     * Observed lists simply update when the seed finishes.
     */
    public void start() {
        executors.diskIO().execute(() -> {
            users.ensureCurrentUser();
            try {
                catalogSeeder.seedIfNeeded();
            } catch (Exception e) {
                // Never log catalog or user content; the exception is enough to diagnose.
                Log.e(TAG, "Catalog seeding failed", e);
            }
        });
    }
}

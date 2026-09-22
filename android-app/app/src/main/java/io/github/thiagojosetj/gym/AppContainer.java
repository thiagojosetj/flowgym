package io.github.thiagojosetj.gym;

import android.content.Context;
import android.util.Log;

import java.time.Clock;

import io.github.thiagojosetj.gym.core.AppExecutors;
import io.github.thiagojosetj.gym.data.local.AppDatabase;
import io.github.thiagojosetj.gym.data.prefs.UiPreferences;
import io.github.thiagojosetj.gym.data.repository.ExerciseRepository;
import io.github.thiagojosetj.gym.data.repository.TemplateRepository;
import io.github.thiagojosetj.gym.data.repository.UserRepository;
import io.github.thiagojosetj.gym.data.seed.CatalogSeeder;
import io.github.thiagojosetj.gym.domain.template.TemplateDefaults;
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
    public final AppDatabase database;
    public final IdGenerator ids;
    public final UiPreferences uiPreferences;
    public final UserRepository users;
    public final ExerciseRepository exercises;
    public final TemplateRepository templates;
    public final TemplateDefaults templateDefaults;
    private final CatalogSeeder catalogSeeder;

    /** Production wiring. */
    public static AppContainer create(Context context) {
        Context app = context.getApplicationContext();
        return new AppContainer(
                AppDatabase.open(app),
                AppExecutors.create(),
                Clock.systemUTC(),
                IdGenerator.UUID_V7,
                () -> app.getAssets().open(CatalogSeeder.ASSET_PATH),
                new UiPreferences(app.getSharedPreferences(UiPreferences.FILE_NAME, Context.MODE_PRIVATE)));
    }

    /** Explicit wiring, also used by tests (in-memory database, direct executors, fixed clock). */
    public AppContainer(AppDatabase database, AppExecutors executors, Clock clock, IdGenerator ids,
                        CatalogSeeder.Source catalogSource, UiPreferences uiPreferences) {
        this.database = database;
        this.executors = executors;
        this.ids = ids;
        this.uiPreferences = uiPreferences;
        this.users = new UserRepository(database, clock, ids);
        this.exercises = new ExerciseRepository(database.exerciseDao(), database.catalogDao(), executors);
        this.templates = new TemplateRepository(database, users, executors, clock, ids);
        this.templateDefaults = TemplateDefaults.standard();
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

package io.github.thiagojosetj.gym.testutil;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import io.github.thiagojosetj.gym.AppContainer;
import io.github.thiagojosetj.gym.core.AppExecutors;
import io.github.thiagojosetj.gym.data.local.AppDatabase;
import io.github.thiagojosetj.gym.data.seed.CatalogSeeder;
import io.github.thiagojosetj.gym.domain.util.IdGenerator;

/** Builds an {@link AppContainer} over an in-memory database with synchronous executors. */
public final class TestContainers {

    public static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-09-21T12:00:00Z"), ZoneOffset.UTC);

    private TestContainers() {
    }

    public static AppDatabase inMemoryDatabase() {
        Context context = ApplicationProvider.getApplicationContext();
        return Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries() // tests only
                .setQueryExecutor(Runnable::run)
                .setTransactionExecutor(Runnable::run)
                .build();
    }

    public static AppExecutors directExecutors() {
        return new AppExecutors(Runnable::run, Runnable::run);
    }

    /** Container with the real bundled catalog; call {@link AppContainer#start()} to seed it. */
    public static AppContainer create(AppDatabase database) {
        Context context = ApplicationProvider.getApplicationContext();
        return new AppContainer(database, directExecutors(), FIXED_CLOCK, IdGenerator.UUID_V7,
                () -> context.getAssets().open(CatalogSeeder.ASSET_PATH));
    }
}

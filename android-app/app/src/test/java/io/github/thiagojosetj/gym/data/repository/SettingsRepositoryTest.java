package io.github.thiagojosetj.gym.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.github.thiagojosetj.gym.AppContainer;
import io.github.thiagojosetj.gym.data.local.AppDatabase;
import io.github.thiagojosetj.gym.data.local.entity.UserSettingEntity;
import io.github.thiagojosetj.gym.domain.settings.AppSettings;
import io.github.thiagojosetj.gym.testutil.LiveDataTestUtil;
import io.github.thiagojosetj.gym.testutil.TestContainers;

@RunWith(AndroidJUnit4.class)
public class SettingsRepositoryTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private AppDatabase database;
    private AppContainer app;

    @Before
    public void setUp() {
        database = TestContainers.inMemoryDatabase();
        app = TestContainers.create(database);
        app.start();
    }

    @After
    public void tearDown() {
        database.close();
    }

    @Test
    public void freshInstallUsesTheStandardDefaults() throws Exception {
        AppSettings settings = LiveDataTestUtil.getOrAwaitValue(app.settings.observeSettings());
        assertEquals(90, settings.defaultRestSeconds());
        assertTrue(settings.restSoundEnabled());
        assertTrue(settings.restVibrationEnabled());
        assertEquals(90, settings.templateDefaults().restSeconds());
        assertEquals(3, settings.templateDefaults().setCount());
    }

    @Test
    public void changesArePersistedAndObserved() throws Exception {
        app.settings.setDefaultRestSeconds(120);
        app.settings.setRestSoundEnabled(false);

        AppSettings settings = LiveDataTestUtil.getOrAwaitValue(app.settings.observeSettings());
        assertEquals(120, settings.defaultRestSeconds());
        assertFalse(settings.restSoundEnabled());
        assertTrue(settings.restVibrationEnabled()); // untouched
        assertEquals(120, settings.templateDefaults().restSeconds());
        assertEquals("PENDING", // will be pushed once accounts exist (docs/SYNC.md)
                string("SELECT sync_status FROM user_setting WHERE setting_key = 'rest_default_seconds'"));
    }

    @Test
    public void aCorruptValueFallsBackToTheDefaultInsteadOfCrashing() {
        database.userSettingDao().upsert(new UserSettingEntity(app.users.requireCurrentUserId(),
                UserSettingEntity.KEY_REST_DEFAULT_SECONDS, "not a number", 0));
        database.userSettingDao().upsert(new UserSettingEntity(app.users.requireCurrentUserId(),
                "setting_from_a_newer_version", "whatever", 0));

        AppSettings settings = SettingsRepository.toSettings(database.userSettingDao().findCurrentUser());

        assertEquals(90, settings.defaultRestSeconds());
    }

    private String string(String sql) {
        try (android.database.Cursor c = database.getOpenHelper().getReadableDatabase().query(sql)) {
            assertTrue(c.moveToFirst());
            return c.getString(0);
        }
    }
}

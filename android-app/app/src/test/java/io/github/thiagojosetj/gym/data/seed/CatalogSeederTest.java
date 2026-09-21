package io.github.thiagojosetj.gym.data.seed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import io.github.thiagojosetj.gym.data.local.AppDatabase;
import io.github.thiagojosetj.gym.data.local.entity.AppMetadataEntity;
import io.github.thiagojosetj.gym.data.local.entity.ExerciseEntity;
import io.github.thiagojosetj.gym.data.local.entity.ExerciseMuscleEntity;
import io.github.thiagojosetj.gym.data.local.entity.MuscleEntity;
import io.github.thiagojosetj.gym.domain.model.MuscleRole;
import io.github.thiagojosetj.gym.testutil.TestContainers;

@RunWith(AndroidJUnit4.class)
public class CatalogSeederTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private AppDatabase database;
    private CatalogSeeder seeder;
    private String json;

    @Before
    public void setUp() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        database = TestContainers.inMemoryDatabase();
        seeder = new CatalogSeeder(database, () -> context.getAssets().open(CatalogSeeder.ASSET_PATH),
                TestContainers.FIXED_CLOCK);
        try (InputStream in = context.getAssets().open(CatalogSeeder.ASSET_PATH)) {
            json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @After
    public void tearDown() {
        database.close();
    }

    @Test
    public void bundledVersionConstantMatchesTheJson() throws Exception {
        assertEquals(CatalogSeeder.BUNDLED_VERSION, CatalogSeeder.parse(json, 0).version);
    }

    @Test
    public void catalogIsConsistent() throws Exception {
        CatalogSeeder.Parsed parsed = CatalogSeeder.parse(json, 0);

        Set<String> ids = new HashSet<>();
        for (MuscleEntity m : parsed.muscles) {
            assertTrue("duplicate id " + m.id, ids.add(m.id));
        }
        for (ExerciseEntity e : parsed.exercises) {
            assertTrue("duplicate id " + e.id, ids.add(e.id));
            assertFalse("blank search text for " + e.code, e.searchText.isEmpty());
            assertTrue("instructions for " + e.code, e.instructions != null && !e.instructions.isEmpty());
        }
        Set<String> withPrimary = new HashSet<>();
        for (ExerciseMuscleEntity link : parsed.exerciseMuscles) {
            if (link.role == MuscleRole.PRIMARY) {
                withPrimary.add(link.exerciseId);
            }
        }
        assertEquals("every exercise needs a primary muscle", parsed.exercises.size(), withPrimary.size());
        // The product asks for at least these 12 groups; we also ship "Adutores".
        long groups = parsed.muscles.stream().filter(m -> m.parentId == null).count();
        assertTrue(groups >= 12);
    }

    @Test
    public void searchTextIsNormalizedAndIncludesAliases() throws Exception {
        CatalogSeeder.Parsed parsed = CatalogSeeder.parse(json, 0);
        ExerciseEntity lateral = parsed.exercises.stream()
                .filter(e -> "lateral_raise".equals(e.code)).findFirst().orElseThrow();
        assertTrue(lateral.searchText.startsWith("elevacao lateral com halteres"));
        assertTrue(lateral.searchText.contains("lateral raise"));
    }

    @Test
    public void seedsOnceAndIsIdempotent() throws Exception {
        assertTrue(seeder.seedIfNeeded());
        int count = database.catalogDao().countActiveSystemExercises();
        assertTrue(count > 0);
        assertEquals(Integer.toString(CatalogSeeder.BUNDLED_VERSION),
                database.metadataDao().get(AppMetadataEntity.KEY_CATALOG_VERSION));

        assertFalse(seeder.seedIfNeeded());
        assertEquals(count, database.catalogDao().countActiveSystemExercises());
    }

    @Test
    public void reseedingAnOlderVersionUpdatesInPlaceWithoutDuplicates() throws Exception {
        seeder.seedIfNeeded();
        int count = database.catalogDao().countActiveSystemExercises();

        database.metadataDao().put(new AppMetadataEntity(AppMetadataEntity.KEY_CATALOG_VERSION, "0"));
        assertTrue(seeder.seedIfNeeded());

        assertEquals(count, database.catalogDao().countActiveSystemExercises());
    }
}

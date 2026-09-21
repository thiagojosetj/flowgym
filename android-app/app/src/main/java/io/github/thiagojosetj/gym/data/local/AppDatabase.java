package io.github.thiagojosetj.gym.data.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import io.github.thiagojosetj.gym.data.local.dao.CatalogDao;
import io.github.thiagojosetj.gym.data.local.dao.ExerciseDao;
import io.github.thiagojosetj.gym.data.local.dao.MetadataDao;
import io.github.thiagojosetj.gym.data.local.dao.TemplateDao;
import io.github.thiagojosetj.gym.data.local.dao.UserProfileDao;
import io.github.thiagojosetj.gym.data.local.entity.AppMetadataEntity;
import io.github.thiagojosetj.gym.data.local.entity.EquipmentEntity;
import io.github.thiagojosetj.gym.data.local.entity.ExerciseEntity;
import io.github.thiagojosetj.gym.data.local.entity.ExerciseEquipmentEntity;
import io.github.thiagojosetj.gym.data.local.entity.ExerciseMuscleEntity;
import io.github.thiagojosetj.gym.data.local.entity.MuscleEntity;
import io.github.thiagojosetj.gym.data.local.entity.TemplateExerciseEntity;
import io.github.thiagojosetj.gym.data.local.entity.TemplateSetEntity;
import io.github.thiagojosetj.gym.data.local.entity.UserProfileEntity;
import io.github.thiagojosetj.gym.data.local.entity.WorkoutTemplateEntity;

/**
 * The local database: the app's source of truth (offline-first). Schema docs: docs/DATABASE.md.
 *
 * <p>Every schema change needs a new version, an explicit Migration and a MigrationTestHelper
 * test. Destructive migrations are never used: people log real training history here.
 */
@Database(
        version = AppDatabase.VERSION,
        exportSchema = true,
        entities = {
                AppMetadataEntity.class,
                UserProfileEntity.class,
                MuscleEntity.class,
                EquipmentEntity.class,
                ExerciseEntity.class,
                ExerciseMuscleEntity.class,
                ExerciseEquipmentEntity.class,
                WorkoutTemplateEntity.class,
                TemplateExerciseEntity.class,
                TemplateSetEntity.class
        })
public abstract class AppDatabase extends RoomDatabase {

    /** Current schema version. Bump together with a Migration and a migration test. */
    public static final int VERSION = 1;

    public static final String FILE_NAME = "gym.db";

    public abstract MetadataDao metadataDao();

    public abstract UserProfileDao userProfileDao();

    public abstract CatalogDao catalogDao();

    public abstract ExerciseDao exerciseDao();

    public abstract TemplateDao templateDao();

    /**
     * Opens the on-disk database. Called once by the AppContainer, which owns the only instance
     * (no static singleton here - ADR-0005).
     */
    public static AppDatabase open(Context context) {
        return Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class, FILE_NAME).build();
    }
}

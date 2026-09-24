package io.github.thiagojosetj.gym.data.local;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import io.github.thiagojosetj.gym.data.local.dao.CatalogDao;
import io.github.thiagojosetj.gym.data.local.dao.ExerciseDao;
import io.github.thiagojosetj.gym.data.local.dao.MetadataDao;
import io.github.thiagojosetj.gym.data.local.dao.TechniqueDao;
import io.github.thiagojosetj.gym.data.local.dao.TemplateDao;
import io.github.thiagojosetj.gym.data.local.dao.UserSettingDao;
import io.github.thiagojosetj.gym.data.local.dao.UserProfileDao;
import io.github.thiagojosetj.gym.data.local.entity.AppMetadataEntity;
import io.github.thiagojosetj.gym.data.local.entity.EquipmentEntity;
import io.github.thiagojosetj.gym.data.local.entity.ExerciseEntity;
import io.github.thiagojosetj.gym.data.local.entity.ExerciseEquipmentEntity;
import io.github.thiagojosetj.gym.data.local.entity.ExerciseMuscleEntity;
import io.github.thiagojosetj.gym.data.local.entity.MuscleEntity;
import io.github.thiagojosetj.gym.data.local.entity.TemplateExerciseEntity;
import io.github.thiagojosetj.gym.data.local.entity.TemplateSetEntity;
import io.github.thiagojosetj.gym.data.local.entity.TrainingTechniqueEntity;
import io.github.thiagojosetj.gym.data.local.entity.UserProfileEntity;
import io.github.thiagojosetj.gym.data.local.entity.UserSettingEntity;
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
                TemplateSetEntity.class,
                TrainingTechniqueEntity.class,
                UserSettingEntity.class
        })
public abstract class AppDatabase extends RoomDatabase {

    /** Current schema version. Bump together with a Migration and a migration test. */
    public static final int VERSION = 2;

    public static final String FILE_NAME = "gym.db";

    public abstract MetadataDao metadataDao();

    public abstract UserProfileDao userProfileDao();

    public abstract CatalogDao catalogDao();

    public abstract ExerciseDao exerciseDao();

    public abstract TemplateDao templateDao();

    public abstract TechniqueDao techniqueDao();

    public abstract UserSettingDao userSettingDao();

    /**
     * v1 → v2: training techniques (data-driven, PRODUCT_SPEC §6.2), account-level settings, and the
     * technique of each planned set.
     *
     * <p>The statements are copied from the generated schema (app/schemas/.../2.json) so the result
     * matches what Room expects byte for byte; {@code MigrationTest} proves it.
     */
    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `training_technique` (`id` TEXT NOT NULL,"
                    + " `owner_user_id` TEXT, `code` TEXT NOT NULL, `name` TEXT NOT NULL, `scope` TEXT NOT NULL,"
                    + " `counts_as_working_set` INTEGER NOT NULL, `description` TEXT, `instructions` TEXT,"
                    + " `params_schema` TEXT, `sort_order` INTEGER NOT NULL, `is_active` INTEGER NOT NULL,"
                    + " `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`))");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_training_technique_code`"
                    + " ON `training_technique` (`code`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_training_technique_owner_user_id`"
                    + " ON `training_technique` (`owner_user_id`)");

            db.execSQL("CREATE TABLE IF NOT EXISTS `user_setting` (`owner_user_id` TEXT NOT NULL,"
                    + " `setting_key` TEXT NOT NULL, `value` TEXT NOT NULL, `updated_at` INTEGER NOT NULL,"
                    + " `sync_status` TEXT NOT NULL, PRIMARY KEY(`owner_user_id`, `setting_key`))");

            // A nullable column added with a REFERENCES clause defaults to NULL, which is what
            // SQLite requires while foreign keys are enforced.
            db.execSQL("ALTER TABLE `template_set` ADD COLUMN `technique_id` TEXT"
                    + " REFERENCES `training_technique`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_template_set_technique_id`"
                    + " ON `template_set` (`technique_id`)");
        }
    };

    /**
     * Opens the on-disk database. Called once by the AppContainer, which owns the only instance
     * (no static singleton here - ADR-0005).
     */
    public static AppDatabase open(Context context) {
        return Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class, FILE_NAME)
                .addMigrations(MIGRATION_1_2)
                .build();
    }
}

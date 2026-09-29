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
import io.github.thiagojosetj.gym.data.local.dao.SessionDao;
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
import io.github.thiagojosetj.gym.data.local.entity.SessionExerciseEntity;
import io.github.thiagojosetj.gym.data.local.entity.SessionExerciseGroupEntity;
import io.github.thiagojosetj.gym.data.local.entity.SessionPauseEntity;
import io.github.thiagojosetj.gym.data.local.entity.SetLogEntity;
import io.github.thiagojosetj.gym.data.local.entity.TemplateExerciseEntity;
import io.github.thiagojosetj.gym.data.local.entity.TemplateExerciseGroupEntity;
import io.github.thiagojosetj.gym.data.local.entity.TemplateSetEntity;
import io.github.thiagojosetj.gym.data.local.entity.TrainingTechniqueEntity;
import io.github.thiagojosetj.gym.data.local.entity.UserProfileEntity;
import io.github.thiagojosetj.gym.data.local.entity.UserSettingEntity;
import io.github.thiagojosetj.gym.data.local.entity.WorkoutSessionEntity;
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
                TemplateExerciseGroupEntity.class,
                TemplateSetEntity.class,
                TrainingTechniqueEntity.class,
                UserSettingEntity.class,
                WorkoutSessionEntity.class,
                SessionPauseEntity.class,
                SessionExerciseEntity.class,
                SessionExerciseGroupEntity.class,
                SetLogEntity.class
        })
public abstract class AppDatabase extends RoomDatabase {

    /** Current schema version. Bump together with a Migration and a migration test. */
    public static final int VERSION = 4;

    public static final String FILE_NAME = "gym.db";

    public abstract MetadataDao metadataDao();

    public abstract UserProfileDao userProfileDao();

    public abstract CatalogDao catalogDao();

    public abstract ExerciseDao exerciseDao();

    public abstract TemplateDao templateDao();

    public abstract TechniqueDao techniqueDao();

    public abstract SessionDao sessionDao();

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
     * v2 -> v3: the session tables (PRODUCT_SPEC section 4.3), so a workout can be performed and
     * kept as history. Statements copied from the generated schema (app/schemas/.../3.json).
     *
     * <p>Only new tables: nothing existing is touched, so an interrupted upgrade cannot damage the
     * templates the user already has.
     */
    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `workout_session` (`id` TEXT NOT NULL,"
                    + " `owner_user_id` TEXT NOT NULL, `template_id` TEXT, `name` TEXT NOT NULL, `notes` TEXT,"
                    + " `status` TEXT NOT NULL, `started_at` INTEGER NOT NULL, `ended_at` INTEGER,"
                    + " `time_zone` TEXT NOT NULL, `local_date` TEXT NOT NULL, `total_paused_ms` INTEGER NOT NULL,"
                    + " `rating` INTEGER, `rest_set_log_id` TEXT, `rest_ends_at` INTEGER,"
                    + " `rest_remaining_ms_when_paused` INTEGER, `created_at` INTEGER NOT NULL,"
                    + " `updated_at` INTEGER NOT NULL, `deleted_at` INTEGER, `sync_status` TEXT NOT NULL,"
                    + " `server_version` INTEGER, PRIMARY KEY(`id`))");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_session_owner_user_id`"
                    + " ON `workout_session` (`owner_user_id`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_session_owner_user_id_local_date`"
                    + " ON `workout_session` (`owner_user_id`, `local_date`)");

            db.execSQL("CREATE TABLE IF NOT EXISTS `session_pause` (`id` TEXT NOT NULL,"
                    + " `session_id` TEXT NOT NULL, `started_at` INTEGER NOT NULL, `ended_at` INTEGER,"
                    + " PRIMARY KEY(`id`), FOREIGN KEY(`session_id`) REFERENCES `workout_session`(`id`)"
                    + " ON UPDATE NO ACTION ON DELETE CASCADE )");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_session_pause_session_id`"
                    + " ON `session_pause` (`session_id`)");

            db.execSQL("CREATE TABLE IF NOT EXISTS `session_exercise` (`id` TEXT NOT NULL,"
                    + " `session_id` TEXT NOT NULL, `exercise_id` TEXT NOT NULL, `template_exercise_id` TEXT,"
                    + " `position` INTEGER NOT NULL, `exercise_name` TEXT NOT NULL, `tracking_type` TEXT NOT NULL,"
                    + " `load_basis` TEXT NOT NULL, `implement_count` INTEGER NOT NULL, `laterality` TEXT NOT NULL,"
                    + " `side_mode` TEXT NOT NULL, `rest_seconds` INTEGER NOT NULL, `permanent_notes` TEXT,"
                    + " `notes` TEXT, `previous_session_exercise_id` TEXT, `started_at` INTEGER,"
                    + " PRIMARY KEY(`id`), FOREIGN KEY(`session_id`) REFERENCES `workout_session`(`id`)"
                    + " ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`exercise_id`)"
                    + " REFERENCES `exercise`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_session_exercise_session_id`"
                    + " ON `session_exercise` (`session_id`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_session_exercise_exercise_id`"
                    + " ON `session_exercise` (`exercise_id`)");

            db.execSQL("CREATE TABLE IF NOT EXISTS `set_log` (`id` TEXT NOT NULL,"
                    + " `session_exercise_id` TEXT NOT NULL, `parent_set_id` TEXT, `position` INTEGER NOT NULL,"
                    + " `technique_id` TEXT, `planned_reps_min` INTEGER, `planned_reps_max` INTEGER,"
                    + " `planned_weight_g` INTEGER, `planned_duration_s` INTEGER,"
                    + " `planned_rest_seconds` INTEGER NOT NULL, `weight_g` INTEGER, `reps` INTEGER,"
                    + " `reps_left` INTEGER, `reps_right` INTEGER, `duration_s` INTEGER, `status` TEXT NOT NULL,"
                    + " `completed_at` INTEGER, `notes` TEXT, PRIMARY KEY(`id`),"
                    + " FOREIGN KEY(`session_exercise_id`) REFERENCES `session_exercise`(`id`)"
                    + " ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`parent_set_id`)"
                    + " REFERENCES `set_log`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE ,"
                    + " FOREIGN KEY(`technique_id`) REFERENCES `training_technique`(`id`)"
                    + " ON UPDATE NO ACTION ON DELETE NO ACTION )");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_set_log_session_exercise_id`"
                    + " ON `set_log` (`session_exercise_id`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_set_log_parent_set_id`"
                    + " ON `set_log` (`parent_set_id`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_set_log_technique_id`"
                    + " ON `set_log` (`technique_id`)");
        }
    };

    /**
     * Opens the on-disk database. Called once by the AppContainer, which owns the only instance
     * (no static singleton here - ADR-0005).
     */
    /**
     * v3 -> v4: exercise groups (superset, bi-set, tri-set, giant set — PRODUCT_SPEC section 6.3).
     *
     * <p>Only new tables and two nullable columns, so nothing existing is rewritten and every row
     * already stored stays exactly as it was: an exercise with no group reads {@code group_id
     * IS NULL}, which is what every template and session written before this migration has.
     *
     * <p>{@code ON DELETE SET NULL} on both columns is deliberate. Ungrouping is a normal edit, and
     * CASCADE there would delete the exercise along with its group — losing the user's work to fix
     * a label.
     */
    public static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `template_exercise_group` (`id` TEXT NOT NULL,"
                    + " `template_id` TEXT NOT NULL, `label` TEXT NOT NULL, `technique_id` TEXT,"
                    + " `rest_after_round_s` INTEGER NOT NULL, `position` INTEGER NOT NULL,"
                    + " PRIMARY KEY(`id`), FOREIGN KEY(`template_id`)"
                    + " REFERENCES `workout_template`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE ,"
                    + " FOREIGN KEY(`technique_id`) REFERENCES `training_technique`(`id`)"
                    + " ON UPDATE NO ACTION ON DELETE NO ACTION )");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_template_exercise_group_template_id`"
                    + " ON `template_exercise_group` (`template_id`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_template_exercise_group_technique_id`"
                    + " ON `template_exercise_group` (`technique_id`)");

            db.execSQL("CREATE TABLE IF NOT EXISTS `session_exercise_group` (`id` TEXT NOT NULL,"
                    + " `session_id` TEXT NOT NULL, `label` TEXT NOT NULL, `technique_id` TEXT,"
                    + " `technique_code` TEXT, `rest_after_round_s` INTEGER NOT NULL,"
                    + " `position` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`session_id`)"
                    + " REFERENCES `workout_session`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE ,"
                    + " FOREIGN KEY(`technique_id`) REFERENCES `training_technique`(`id`)"
                    + " ON UPDATE NO ACTION ON DELETE NO ACTION )");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_session_exercise_group_session_id`"
                    + " ON `session_exercise_group` (`session_id`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_session_exercise_group_technique_id`"
                    + " ON `session_exercise_group` (`technique_id`)");

            db.execSQL("ALTER TABLE `template_exercise` ADD COLUMN `group_id` TEXT"
                    + " REFERENCES `template_exercise_group`(`id`)"
                    + " ON UPDATE NO ACTION ON DELETE SET NULL");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_template_exercise_group_id`"
                    + " ON `template_exercise` (`group_id`)");

            db.execSQL("ALTER TABLE `session_exercise` ADD COLUMN `group_id` TEXT"
                    + " REFERENCES `session_exercise_group`(`id`)"
                    + " ON UPDATE NO ACTION ON DELETE SET NULL");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_session_exercise_group_id`"
                    + " ON `session_exercise` (`group_id`)");
        }
    };

    public static AppDatabase open(Context context) {
        return Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class, FILE_NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build();
    }
}

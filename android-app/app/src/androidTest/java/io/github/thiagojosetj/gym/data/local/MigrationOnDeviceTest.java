package io.github.thiagojosetj.gym.data.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.database.Cursor;

import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

/**
 * The same migration the JVM test covers, but on the device's real SQLite, with Room's own
 * {@link MigrationTestHelper} (which cannot run under Robolectric on Windows — ADR-0012).
 *
 * <p>Run with: {@code ./gradlew :app:connectedDebugAndroidTest}
 */
@RunWith(AndroidJUnit4.class)
public class MigrationOnDeviceTest {

    private static final String DB_NAME = "migration-device-test.db";
    private static final String SET_ID = "set-1";

    @Rule
    public MigrationTestHelper helper = new MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(), AppDatabase.class);

    @Test
    public void migratesFromVersion1KeepingThePlannedSets() throws IOException {
        SupportSQLiteDatabase db = helper.createDatabase(DB_NAME, 1);
        db.execSQL("INSERT INTO app_metadata (meta_key, meta_value) VALUES ('current_user_id', 'user-1')");
        db.execSQL("INSERT INTO user_profile (id, is_local, created_at, updated_at) VALUES ('user-1', 1, 0, 0)");
        db.execSQL("INSERT INTO exercise (id, name, search_text, tracking_type, load_basis, implement_count,"
                + " laterality, is_active, created_at, updated_at, sync_status)"
                + " VALUES ('ex-1', 'Supino', 'supino', 'WEIGHT_REPS', 'TOTAL', 1, 'BILATERAL', 1, 0, 0, 'SYNCED')");
        db.execSQL("INSERT INTO workout_template (id, owner_user_id, name, sort_order, origin,"
                + " created_at, updated_at, sync_status)"
                + " VALUES ('tpl-1', 'user-1', 'Push A', 0, 'CREATED', 0, 0, 'PENDING')");
        db.execSQL("INSERT INTO template_exercise (id, template_id, exercise_id, position, rest_seconds, side_mode)"
                + " VALUES ('te-1', 'tpl-1', 'ex-1', 0, 90, 'COMBINED')");
        db.execSQL("INSERT INTO template_set (id, template_exercise_id, position, target_reps_min, target_reps_max)"
                + " VALUES ('" + SET_ID + "', 'te-1', 0, 12, 12)");
        db.close();

        // Validates every table, column, index and foreign key against the exported schema.
        SupportSQLiteDatabase migrated = helper.runMigrationsAndValidate(DB_NAME, 4, true,
                AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4);

        try (Cursor cursor = migrated.query(
                "SELECT target_reps_min, technique_id FROM template_set WHERE id = '" + SET_ID + "'")) {
            assertTrue("the planned set must survive the migration", cursor.moveToFirst());
            assertEquals(12, cursor.getInt(0));
            assertTrue("a migrated set has no technique yet", cursor.isNull(1));
        }
        migrated.close();
    }

    @Test
    public void migratesFromVersion2CreatingTheSessionTables() throws IOException {
        String name = "migration-device-test-2.db";
        SupportSQLiteDatabase db = helper.createDatabase(name, 2);
        db.execSQL("INSERT INTO app_metadata (meta_key, meta_value) VALUES ('current_user_id', 'user-1')");
        db.execSQL("INSERT INTO user_profile (id, is_local, created_at, updated_at) VALUES ('user-1', 1, 0, 0)");
        db.close();

        SupportSQLiteDatabase migrated =
                helper.runMigrationsAndValidate(name, 4, true, AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4);

        // The session tables exist and the rows that were there are untouched.
        try (Cursor cursor = migrated.query("SELECT COUNT(*) FROM workout_session")) {
            assertTrue(cursor.moveToFirst());
            assertEquals(0, cursor.getInt(0));
        }
        try (Cursor cursor = migrated.query("SELECT COUNT(*) FROM user_profile")) {
            assertTrue(cursor.moveToFirst());
            assertEquals(1, cursor.getInt(0));
        }
        migrated.close();
    }
}

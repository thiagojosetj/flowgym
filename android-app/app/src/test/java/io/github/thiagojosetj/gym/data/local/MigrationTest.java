package io.github.thiagojosetj.gym.data.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import io.github.thiagojosetj.gym.data.local.entity.TrainingTechniqueEntity;
import io.github.thiagojosetj.gym.data.local.entity.UserSettingEntity;

/**
 * Real migration coverage on the JVM: builds a version 1 database from the exported schema, puts a
 * template in it, then opens it with Room. Room runs {@link AppDatabase#MIGRATION_1_2} and validates
 * the result against the version 2 schema, so a wrong statement fails here.
 *
 * <p>Room's own MigrationTestHelper would be the usual tool, but it cannot run under Robolectric on
 * Windows (ADR-0012), and this test does the same job without it.
 */
@RunWith(AndroidJUnit4.class)
public class MigrationTest {

    private static final String DB_NAME = "migration-test.db";
    private static final String TEMPLATE_ID = "tpl-1";
    private static final String SET_ID = "set-1";

    private AppDatabase database;

    @After
    public void tearDown() {
        if (database != null) {
            database.close();
        }
        Context context = ApplicationProvider.getApplicationContext();
        context.getDatabasePath(DB_NAME).delete();
    }

    @Test
    public void migratesFromVersion1KeepingTemplatesAndAddingTechniquesAndSettings() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File file = context.getDatabasePath(DB_NAME);
        createVersion1Database(file);

        database = Room.databaseBuilder(context, AppDatabase.class, DB_NAME)
                .addMigrations(AppDatabase.MIGRATION_1_2)
                .allowMainThreadQueries()
                .build();
        // Opening runs the migration and validates the schema against 2.json.
        assertEquals(AppDatabase.VERSION, database.getOpenHelper().getWritableDatabase().getVersion());

        // The planned set survived and its new column starts empty (a normal working set).
        try (Cursor cursor = database.getOpenHelper().getReadableDatabase()
                .query("SELECT id, technique_id, target_reps_min FROM template_set WHERE id = '" + SET_ID + "'")) {
            assertTrue(cursor.moveToFirst());
            assertEquals(SET_ID, cursor.getString(0));
            assertTrue(cursor.isNull(1));
            assertEquals(12, cursor.getInt(2));
        }
        assertEquals(1, count("SELECT COUNT(*) FROM workout_template WHERE id = '" + TEMPLATE_ID + "'"));

        // The new tables work: a technique can be linked to the migrated set.
        TrainingTechniqueEntity technique = new TrainingTechniqueEntity();
        technique.id = "t-warmup";
        technique.code = "AQ";
        technique.name = "Aquecimento";
        technique.countsAsWorkingSet = false;
        database.runInTransaction(() ->
                database.getOpenHelper().getWritableDatabase().execSQL(
                        "INSERT INTO training_technique (id, code, name, scope, counts_as_working_set,"
                                + " sort_order, is_active, created_at, updated_at)"
                                + " VALUES ('t-warmup', 'AQ', 'Aquecimento', 'SET', 0, 0, 1, 0, 0)"));
        database.getOpenHelper().getWritableDatabase().execSQL(
                "UPDATE template_set SET technique_id = 't-warmup' WHERE id = '" + SET_ID + "'");
        assertEquals(1, count("SELECT COUNT(*) FROM template_set WHERE technique_id = 't-warmup'"));

        database.userSettingDao().upsert(new UserSettingEntity("user-1",
                UserSettingEntity.KEY_REST_DEFAULT_SECONDS, "120", 0));
        assertEquals(1, count("SELECT COUNT(*) FROM user_setting WHERE owner_user_id = 'user-1'"));
        assertNull(database.metadataDao().get("nothing-here")); // sanity: old DAOs still work
    }

    /** Creates the v1 database exactly as Room would have left it (schema + identity hash). */
    private static void createVersion1Database(File file) throws Exception {
        File schemaFile = new File("schemas/" + AppDatabase.class.getName() + "/1.json");
        assertTrue("v1 schema must stay in the repository", schemaFile.exists());
        JSONObject schema = new JSONObject(new String(Files.readAllBytes(schemaFile.toPath()), StandardCharsets.UTF_8))
                .getJSONObject("database");

        file.getParentFile().mkdirs();
        file.delete();
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(file, null);
        try {
            db.beginTransaction();
            JSONArray entities = schema.getJSONArray("entities");
            for (int i = 0; i < entities.length(); i++) {
                JSONObject entity = entities.getJSONObject(i);
                String table = entity.getString("tableName");
                db.execSQL(entity.getString("createSql").replace("${TABLE_NAME}", table));
                JSONArray indices = entity.optJSONArray("indices");
                for (int j = 0; indices != null && j < indices.length(); j++) {
                    db.execSQL(indices.getJSONObject(j).getString("createSql").replace("${TABLE_NAME}", table));
                }
            }
            // Room checks this table on open; without the v1 hash it refuses the database.
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)");
            db.execSQL("INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, ?)",
                    new Object[]{schema.getString("identityHash")});

            // A template with one exercise and one planned set, to prove data survives.
            db.execSQL("INSERT INTO app_metadata (meta_key, meta_value) VALUES ('current_user_id', 'user-1')");
            db.execSQL("INSERT INTO user_profile (id, is_local, created_at, updated_at)"
                    + " VALUES ('user-1', 1, 0, 0)");
            db.execSQL("INSERT INTO muscle (id, code, name, sort_order) VALUES ('m-1', 'chest', 'Peito', 0)");
            db.execSQL("INSERT INTO exercise (id, name, search_text, tracking_type, load_basis, implement_count,"
                    + " laterality, is_active, created_at, updated_at, sync_status)"
                    + " VALUES ('ex-1', 'Supino', 'supino', 'WEIGHT_REPS', 'TOTAL', 1, 'BILATERAL', 1, 0, 0, 'SYNCED')");
            db.execSQL("INSERT INTO workout_template (id, owner_user_id, name, sort_order, origin,"
                    + " created_at, updated_at, sync_status)"
                    + " VALUES ('" + TEMPLATE_ID + "', 'user-1', 'Push A', 0, 'CREATED', 0, 0, 'PENDING')");
            db.execSQL("INSERT INTO template_exercise (id, template_id, exercise_id, position, rest_seconds, side_mode)"
                    + " VALUES ('te-1', '" + TEMPLATE_ID + "', 'ex-1', 0, 90, 'COMBINED')");
            db.execSQL("INSERT INTO template_set (id, template_exercise_id, position, target_reps_min,"
                    + " target_reps_max) VALUES ('" + SET_ID + "', 'te-1', 0, 12, 12)");
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            db.setVersion(1);
            db.close();
        }
    }

    private int count(String sql) {
        try (Cursor cursor = database.getOpenHelper().getReadableDatabase().query(sql)) {
            assertTrue(cursor.moveToFirst());
            return cursor.getInt(0);
        }
    }
}

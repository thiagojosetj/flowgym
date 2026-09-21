package io.github.thiagojosetj.gym.data.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.database.Cursor;

import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import io.github.thiagojosetj.gym.testutil.TestContainers;

/**
 * Checks that the exported schema (app/schemas/.../VERSION.json) is present and identical to what
 * Room builds from the current entities. It fails when an entity changes without bumping
 * {@link AppDatabase#VERSION} (and therefore without a Migration).
 *
 * <p>Why not MigrationTestHelper here: in Room 2.8.5 its Android driver compares database paths
 * with '/' and fails under Robolectric on Windows. Migration tests (from version 2 on) run as
 * instrumented tests in app/src/androidTest, where the helper works. See docs/DECISIONS.md.
 */
@RunWith(AndroidJUnit4.class)
public class SchemaTest {

    private AppDatabase database;

    @Before
    public void setUp() {
        database = TestContainers.inMemoryDatabase();
    }

    @After
    public void tearDown() {
        database.close();
    }

    @Test
    public void exportedSchemaMatchesTheEntities() throws Exception {
        File file = new File("schemas/" + AppDatabase.class.getName() + "/" + AppDatabase.VERSION + ".json");
        assertTrue("Schema not exported: " + file.getAbsolutePath(), file.exists());
        JSONObject schema = new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8))
                .getJSONObject("database");
        assertEquals(AppDatabase.VERSION, schema.getInt("version"));

        SupportSQLiteDatabase db = database.getOpenHelper().getWritableDatabase();
        JSONArray entities = schema.getJSONArray("entities");
        for (int i = 0; i < entities.length(); i++) {
            JSONObject entity = entities.getJSONObject(i);
            String table = entity.getString("tableName");
            // sqlite_master stores the statement without "IF NOT EXISTS".
            String expected = entity.getString("createSql")
                    .replace("${TABLE_NAME}", table)
                    .replace("CREATE TABLE IF NOT EXISTS ", "CREATE TABLE ");
            assertEquals("table " + table, expected, tableSql(db, table));
        }
        assertEquals(schema.getString("identityHash"), identityHash(db));
    }

    private static String tableSql(SupportSQLiteDatabase db, String table) {
        try (Cursor c = db.query("SELECT sql FROM sqlite_master WHERE type = 'table' AND name = ?",
                new Object[]{table})) {
            assertTrue("missing table " + table, c.moveToFirst());
            return c.getString(0);
        }
    }

    private static String identityHash(SupportSQLiteDatabase db) {
        try (Cursor c = db.query("SELECT identity_hash FROM room_master_table")) {
            assertTrue(c.moveToFirst());
            return c.getString(0);
        }
    }
}
